package org.multibit.hd.core.services;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;
import com.google.protobuf.InvalidProtocolBufferException;
import org.bitcoin.protocols.payments.Protos;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.TrustStoreLoader;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.bitcoinj.protocols.payments.PaymentProtocolException;
import org.bitcoinj.protocols.payments.PaymentSession;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;
import org.multibit.hd.core.dto.*;
import org.multibit.hd.core.events.ShutdownEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <p>Service to provide the following to GUI classes:</p>
 * <ul>
 * <li>Handle Payment Protocol requests and sessions</li>
 * </ul>
 * <p/>
 */
public class PaymentProtocolService extends AbstractService {

  private static final Logger log = LoggerFactory.getLogger(PaymentProtocolService.class);

  /**
   * The timeout before a server is deemed to be unresponsive to a payment request
   */
  private static final int PAYMENT_REQUEST_TIMEOUT_SECONDS = 30;

  /**
   * The Bitcoin network parameters
   */
  private final NetworkParameters networkParameters;

  public PaymentProtocolService(NetworkParameters networkParameters) {

    super();

    Preconditions.checkNotNull(networkParameters, "'networkParameters' must be present");

    this.networkParameters = networkParameters;

  }

  @Override
  protected boolean startInternal() {

    return true;
  }

  @Override
  protected boolean shutdownNowInternal(ShutdownEvent.ShutdownType shutdownType) {

    // Always treat as a hard shutdown
    return true;

  }

  /**
   * <p>Probe a location for a new payment session based on the information contained in the URI (http, https or bitcoin).</p>
   * <p>Normally this will result in a PaymentSessionSummary containing the PaymentSession however consumers must check the
   * status message to determine any problems that were encountered and how to respond to them.</p>
   * <p>MultiBit HD policy is that anyone issuing payment requests with PKI should have them signed by some
   * kind of CA or omit them. If the CA is not available then the user must decide how to proceed. Consequently consuming code
   * will have checkPKI set to false and the probing process will determine the trust level after the session is created.</p>
   *
   * @param paymentRequestUri The URI referencing the PaymentRequest
   * @param checkPKI          True if the PKI details should be checked (recommended - see policy note)
   * @param trustStoreLoader  The trust store loader linked to the local CA certs file
   *
   * @return A new payment session covering this payment request, absent if a failure occurred (see History)
   */
  public PaymentSessionSummary probeForPaymentSession(URI paymentRequestUri, boolean checkPKI, TrustStoreLoader trustStoreLoader) {

    Preconditions.checkNotNull(paymentRequestUri, "'paymentRequestUri' must be present");

    log.info("Probing '{}'", paymentRequestUri);

    String scheme = paymentRequestUri.getScheme() == null ? "" : paymentRequestUri.getScheme();
    String hostName = paymentRequestUri.getHost() == null ? "" : paymentRequestUri.getHost();

    Protos.PaymentRequest paymentRequest;

    try {

      // Determine how to obtain the payment request based on the scheme

      PaymentSession paymentSession = null;

      if (scheme.startsWith("bitcoin")) {
        final BitcoinURI bitcoinUri = new BitcoinURI(networkParameters, paymentRequestUri.toString());
        if (bitcoinUri.getPaymentRequestUrls().isEmpty()) {
          log.debug("Treating as BIP21 resource");
          paymentSession = PaymentSession
            .createFromBitcoinUri(bitcoinUri, false, null)
            .get();
          return new PaymentSessionSummary(
            Optional.of(paymentSession),
            null, PaymentSessionStatus.UNTRUSTED,
            RAGStatus.PINK,
            CoreMessageKey.PAYMENT_SESSION_PKI_INVALID,
            new String[]{paymentSession.getMemo()}
          );
        } else if (bitcoinUri.getPaymentRequestUrls().size() == 1) {
          log.debug("Treating as single BIP72 resource");
          paymentSession = PaymentSession
            .createFromBitcoinUri(bitcoinUri, checkPKI, trustStoreLoader)
            .get(PAYMENT_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } else {
          log.debug("Treating as multiple BIP72 resource");
          for (String r : bitcoinUri.getPaymentRequestUrls()) {
            try {
              paymentSession = PaymentSession
                .createFromUrl(r, checkPKI, trustStoreLoader)
                .get(PAYMENT_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
              break;
            } catch (Exception e) {
              // In multiple mode any exception is considered a reason to move on to the next
              // but we must trap the NPE later on
              log.warn("Payment request from '{}' produced an exception: {}", r, e.getMessage());
            }
          }
        }

      } else if (scheme.startsWith("http")) {
        log.debug("Treating as remote HTTP/S resource");
        paymentSession = PaymentSession
          .createFromUrl(paymentRequestUri.toString(), checkPKI, trustStoreLoader)
          .get(PAYMENT_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

      } else if (scheme.startsWith("file")) {
        log.debug("Treating as file based resource");
        byte[] paymentRequestBytes = Resources.toByteArray(paymentRequestUri.toURL());
        paymentRequest = Protos.PaymentRequest.parseFrom(paymentRequestBytes);
        paymentSession = new PaymentSession(paymentRequest, checkPKI, trustStoreLoader);

      } else {
        log.debug("Treating as classpath based resource");
        InputStream inputStream = PaymentProtocolService.class.getResourceAsStream(paymentRequestUri.toString());
        paymentRequest = Protos.PaymentRequest.parseFrom(inputStream);
        paymentSession = new PaymentSession(paymentRequest, checkPKI, trustStoreLoader);

      }

      // Check if payment session was created (all fallback URLs may have failed)
      if (paymentSession == null) {
        log.warn("Failed to create a payment session");
        throw new PaymentProtocolException.InvalidPaymentRequestURL("All payment request URLs have failed");
      }

      // Determine confidence in the payment request
      PaymentProtocol.PkiVerificationData pkiVerificationData = paymentSession.pkiVerificationData;
      if (!checkPKI) {
        final TrustStoreLoader loader = trustStoreLoader != null ? trustStoreLoader : new TrustStoreLoader.DefaultTrustStoreLoader();
        try {
          // Override the earlier PKI verification data (likely to be null since not checked)
          pkiVerificationData = PaymentProtocol.verifyPaymentRequestPki(
            paymentSession.getPaymentRequest(),
            loader.getKeyStore()
          );

        } catch (PaymentProtocolException | KeyStoreException e) {
          return PaymentSessionSummary.newPaymentSessionAlmostOK(paymentSession, e);
        }

      }

      // Must be OK to be here
      log.debug("Created payment session summary");
      return PaymentSessionSummary.newPaymentSessionOK(paymentSession, pkiVerificationData);

    } catch (PaymentProtocolException e) {
      // We can be more specific about handling the error
      return PaymentSessionSummary.newPaymentSessionFromException(e, hostName);
    } catch (BitcoinURIParseException e) {
      return PaymentSessionSummary.newPaymentSessionFromException(e, hostName);
    } catch (ExecutionException e) {
      return PaymentSessionSummary.newPaymentSessionFromException(e, hostName);
    } catch (InterruptedException e) {
      return PaymentSessionSummary.newPaymentSessionFromException(e, hostName);
    } catch (InvalidProtocolBufferException e) {
      return PaymentSessionSummary.newPaymentSessionFromException(e, hostName);
    } catch (MalformedURLException e) {
      return PaymentSessionSummary.newPaymentSessionFromException(e, hostName);
    } catch (IOException e) {
      return PaymentSessionSummary.newPaymentSessionFromException(e, hostName);
    } catch (TimeoutException e) {
      return PaymentSessionSummary.newPaymentSessionFromException(e, hostName);
    }
  }

  /**
   * @return A new signed BIP70 PaymentRequest or absent
   */
  public Optional<Protos.PaymentRequest> newSignedPaymentRequest(SignedPaymentRequestSummary signedPaymentRequestSummary) throws NoSuchAlgorithmException {
    KeyStore keyStore = signedPaymentRequestSummary.getKeyStore();
    String keyAlias = signedPaymentRequestSummary.getKeyAlias();
    char[] keyStorePassword = signedPaymentRequestSummary.getKeyStorePassword();

    Protos.PaymentRequest.Builder paymentRequest;
    try {

      // Populate the PaymentRequest
      paymentRequest = PaymentProtocol.createPaymentRequest(
        networkParameters,
        signedPaymentRequestSummary.getAmount(),
        signedPaymentRequestSummary.getPaymentAddress(),
        signedPaymentRequestSummary.getMemo(),
        signedPaymentRequestSummary.getPaymentUrl().toString(),
        signedPaymentRequestSummary.getMerchantData()
      );

      // Get the certificate chain and ensure it is X509
      final java.security.cert.Certificate[] certificateChain = keyStore.getCertificateChain(keyAlias);
      X509Certificate[] x509CertificateChain = new X509Certificate[certificateChain.length];
      for (int i = 0; i < certificateChain.length; i++) {
        if (certificateChain[i] instanceof X509Certificate) {
          x509CertificateChain[i] = (X509Certificate) certificateChain[i];
        } else {
          log.error("Key store has an inconsistent chain of certificates (expected X509 throughout)");
          return Optional.absent();
        }
      }

      PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyAlias, keyStorePassword);

      PaymentProtocol.signPaymentRequest(
        paymentRequest,
        x509CertificateChain,
        privateKey
      );

      return Optional.of(paymentRequest.build());

    } catch (KeyStoreException | UnrecoverableKeyException e) {
      log.error("Unexpected error in payment request", e);
    }

    // Must have failed to be here
    return Optional.absent();
  }

  /**
   * @param merchantData The merchant data bytes (from the PaymentRequest) to reference
   * @param transactions The transactions that paid the payment request
   * @param refundAmount The amount to refund
   * @param refundAddress The refund address
   * @param paymentMemo The memo for the payment
   * @return a new BIP70 payment
   */
  public Optional<Protos.Payment> newPayment(byte[] merchantData, List<Transaction> transactions, Coin refundAmount, Address refundAddress, String paymentMemo) {
        return Optional.of(PaymentProtocol.createPaymentMessage(transactions, refundAmount, refundAddress, paymentMemo, merchantData));
  }

  /**
    * @param payment The BIP70 payment to reference
    * @param paymentACKMemo The memo for the paymentACK
    * @return a new BIP70 payment
    */
   public Optional<Protos.PaymentACK> newPaymentACK(Protos.Payment payment, String paymentACKMemo) {
     return Optional.of(PaymentProtocol.createPaymentAck(payment, paymentACKMemo));
   }
 }
