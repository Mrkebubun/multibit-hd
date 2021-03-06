package org.multibit.hd.ui.services;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Queues;
import com.google.common.eventbus.Subscribe;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;
import org.multibit.hd.core.dto.PaymentSessionSummary;
import org.multibit.hd.core.events.CoreEvents;
import org.multibit.hd.core.events.ShutdownEvent;
import org.multibit.hd.core.services.AbstractService;
import org.multibit.hd.core.services.PaymentProtocolService;
import org.multibit.hd.ui.events.controller.ControllerEvents;
import org.multibit.hd.ui.models.AlertModel;
import org.multibit.hd.ui.models.Models;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.Queue;

/**
 * <p>Service to maintain a localhost server socket on port 8330.</p>
 *
 * <p>If this socket is taken, then another instance of MultiBit HD is likely to be already
 * running and so this service will hand over any data it was given on startup to whatever
 * is listening on that port.</p>
 */
public class ExternalDataListeningService extends AbstractService {

  private static final Logger log = LoggerFactory.getLogger(ExternalDataListeningService.class);

  /**
   * MultiBit HD port number as specified in <a href="http://en.wikipedia.org/wiki/List_of_TCP_and_UDP_port_numbers">the list of TCP and UDP port numbers</a>.
   */
  public static final int MULTIBIT_HD_NETWORK_SOCKET = 8330;

  /**
   * Message start token (to ensure this isn't an accidental bunch of characters being sent)
   */
  public static final String MESSAGE_START = "$$MBHD-Start$$";

  /**
   * Message end token
   */
  public static final String MESSAGE_END = "$$MBHD-End$$";

  /**
   * Track the incoming Bitcoin URIs (BIP21)
   */
  private static final Queue<BitcoinURI> bitcoinURIQueue = Queues.newArrayBlockingQueue(10);
  /**
   * Track the incoming Payment Protocol sessions
   */
  private static final Queue<PaymentSessionSummary> paymentSessionSummaryQueue = Queues.newArrayBlockingQueue(10);

  private Optional<ServerSocket> serverSocket = Optional.absent();

  private String rawData;

  /**
   * @param args The command line arguments
   */
  public ExternalDataListeningService(String[] args) {

    super();

    if (args == null || args.length == 0) {
      return;
    }

    // Must have some command line arguments to be here
    addToQueues(args[0], false);

    // May need to hand over
    rawData = args[0];

  }

  @Override
  public boolean startInternal() {

    // This service will run a single background thread
    requireFixedThreadPoolExecutor(1, "uri-listener");

    try {

      // Attempt to own the localhost server socket allowing for a backlog of connections
      serverSocket = Optional.of(
        new ServerSocket(
          MULTIBIT_HD_NETWORK_SOCKET,
          10,
          InetAddress.getLoopbackAddress()
        ));

      // Successfully owned the server port

      // Handle ongoing messages as master
      ListenableFuture future = getExecutorService().submit(getInstanceServerRunnable(serverSocket.get()));
      Futures.addCallback(
        future, new FutureCallback() {
          @Override
          public void onSuccess(Object result) {
            log.debug("Stopping BitcoinURIListeningService executor (success)");
            getExecutorService().shutdownNow();
          }

          @Override
          public void onFailure(Throwable t) {
            log.debug("Stopping BitcoinURIListeningService executor (failure)", t);
            getExecutorService().shutdownNow();
          }
        });

      log.info("Listening for MultiBit HD instances on socket: '{}'", MULTIBIT_HD_NETWORK_SOCKET);

      // Must be OK to be here
      return true;

    } catch (UnknownHostException e) {

      // Indicates that there is no loop back address on this machine
      log.error(e.getMessage(), e);

      // Indicate that a shutdown should be performed
      return false;

    } catch (IOException e) {

      // Failed to own the server port so notify the other instance
      log.info("Port is already taken. Notifying first instance.");

      if (!bitcoinURIQueue.isEmpty() || !paymentSessionSummaryQueue.isEmpty()) {
        // Must have successfully parsed the data into something meaningful so resend
        writeToSocket(rawData);
      }

      // Indicate that a shutdown should be performed
      return false;
    }
  }

  @Override
  protected boolean shutdownNowInternal(ShutdownEvent.ShutdownType shutdownType) {

    // Service can survive a switch
    return preventCleanupOnSwitch(shutdownType);

  }

  /**
   * <p>Write to the MultiBit HD network socket</p>
   *
   * @param message The message to send (appropriate wrapping will be added)
   */
  public static synchronized void writeToSocket(String message) {

    Preconditions.checkNotNull(message, "'message' must be present");

    try {
      Socket clientSocket = new Socket(
        InetAddress.getLoopbackAddress(),
        MULTIBIT_HD_NETWORK_SOCKET
      );

      try (OutputStream out = clientSocket.getOutputStream()) {

        // Write out the raw external data for parsing by the other instance
        out.write(MESSAGE_START.getBytes(Charsets.UTF_8));
        out.write(message.getBytes(Charsets.UTF_8));
        out.write(MESSAGE_END.getBytes(Charsets.UTF_8));

        out.close();
      }
      clientSocket.close();

    } catch (IOException e) {
      log.error(e.getMessage(), e);
    }

    log.debug("Successful write to external data listening service.");

  }

  /**
   * <p>The queue of Bitcoin URIs provided externally (command line arguments or via the socket)</p>
   *
   * @return The Bitcoin URI queue (may be empty)
   */
  public Queue<BitcoinURI> getBitcoinURIQueue() {
    return bitcoinURIQueue;
  }

  /**
   * <p>The queue of PaymentSession summaries provided externally (command line arguments or via the socket)</p>
   *
   * @return The payment session summary queue (may be empty)
   */
  public Queue<PaymentSessionSummary> getPaymentSessionSummaryQueue() {
    return paymentSessionSummaryQueue;
  }

  /**
   * <p>The server socket created as the master if no other was in place</p>
   * <p>Reduced visibility for testing</p>
   *
   * @return The server socket
   */
  Optional<ServerSocket> getServerSocket() {
    return serverSocket;
  }

  /**
   * <p>Parse the raw data into a Bitcoin URI or a PaymentSessionSummary as required</p>
   *
   * @param rawData           The raw data from the external source
   * @param fireAddAlertEvent True if the act of adding to the queues should trigger the add alert event (requires an unlocked wallet)
   */
  public static void addToQueues(String rawData, boolean fireAddAlertEvent) {

    // Quick check for BIP21 Bitcoin URI
    if (rawData.startsWith("bitcoin") && !rawData.contains("r=")) {
      // Treat as BIP21 Bitcoin URI
      parseBitcoinURI(rawData);
    } else {
      // Treat as a Payment Protocol URI (could be direct via "http", "https", "file", "classpath" or use BIP72 variant of "bitcoin")
      parsePaymentSessionSummary(rawData);
    }

    if (fireAddAlertEvent) {

      log.debug("Checking for alert requirement");

      // Check for BIP21 Bitcoin URI
      if (bitcoinURIQueue.peek() != null) {

        SwingUtilities.invokeLater(
          new Runnable() {
            @Override
            public void run() {
              // Attempt to create an alert model from the Bitcoin URI
              Optional<AlertModel> alertModel = Models.newBitcoinURIAlertModel(bitcoinURIQueue.poll());

              // If successful the fire the event
              if (alertModel.isPresent()) {
                ControllerEvents.fireAddAlertEvent(alertModel.get());
              }
            }
          });
      }

      // Check for Payment Protocol session
      if (paymentSessionSummaryQueue.peek() != null) {

        SwingUtilities.invokeLater(
          new Runnable() {
            @Override
            public void run() {
              // Attempt to create an alert model
              Optional<AlertModel> alertModel = Models.newPaymentRequestAlertModel(paymentSessionSummaryQueue.poll());

              // If successful the fire the event
              if (alertModel.isPresent()) {
                ControllerEvents.fireAddAlertEvent(alertModel.get());
              }
            }
          });
      }
    }

  }

  /**
   * @param serverSocket The server socket on which messages will arrive
   *
   * @return The Runnable handling incoming messages
   */
  private Runnable getInstanceServerRunnable(final ServerSocket serverSocket) {

    return new MessageServerRunnable(serverSocket);
  }

  /**
   * <p>Attempt to detect if the raw Bitcoin URI is valid.</p>
   * <p>There are many reasons why a raw Bitcoin URI may be invalid:</p>
   * <ul>
   * <li>IE6-8 strips URL encoding when passing in URIs to a protocol handler</li>
   * <li>a user could hand-craft a URI and pass it in with non-ASCII character encoding present in the label</li>
   * </ul>
   *
   * <p>This is a really limited approach (no consideration of "amount=10.0&label=Black & White")
   * but should be OK for <a href="https://github.com/bitcoin/bips/blob/master/bip-0021.mediawiki">BIP21</a> use cases.</p>
   *
   * @param rawData The raw data straight from an assumed untrusted external source
   */
  private static void parseBitcoinURI(String rawData) {

    log.debug("Decoding BIP21 Bitcoin URI from '{}'", rawData);

    if (Strings.isNullOrEmpty(rawData)) {
      return;
    }

    try {

      // Basic initial checking for URL encoding
      int queryParamIndex = rawData.indexOf('?');
      if (queryParamIndex > 0 && !rawData.contains("%")) {
        // Possibly encoded but more likely not
        String encodedQueryParams = URLEncoder.encode(rawData.substring(queryParamIndex + 1), "UTF-8");
        rawData = rawData.substring(0, queryParamIndex) + "?" + encodedQueryParams;
        rawData = rawData.replaceAll("%3D", "=");
        rawData = rawData.replaceAll("%26", "&");
      }

      log.debug("Using '{}' to create BIP21 Bitcoin URI", rawData);
      bitcoinURIQueue.add(new BitcoinURI(rawData));

    } catch (UnsupportedEncodingException e) {
      log.error("UTF-8 is not supported on this platform");
    } catch (BitcoinURIParseException e) {
      log.error("BIP21 Bitcoin URI not valid. Error ", e);
    }

  }

  /**
   * <p>Attempt to detect if the raw data is a valid URI for Payment Protocol.</p>
   * <p>There are many reasons why a raw URI may be invalid:</p>
   * <ul>
   * <li>IE6-8 strips URL encoding when passing in URIs to a protocol handler</li>
   * <li>a user could hand-craft a URI and pass it in with non-ASCII character encoding present in the label</li>
   * </ul>
   *
   * <p>This is a substantial improvement over BIP21 URI handling
   * and should be OK for <a href="https://github.com/bitcoin/bips/blob/master/bip-0072.mediawiki">BIP72</a> use cases.</p>
   *
   * @param rawData The raw data straight from an assumed untrusted external source
   */
  private static Optional<PaymentSessionSummary> parsePaymentSessionSummary(String rawData) {

    log.debug("Decoding URI from '{}'", rawData);

    if (Strings.isNullOrEmpty(rawData)) {
      return Optional.absent();
    }

    try {

      // Basic initial checking for URL encoding
      int queryParamIndex = rawData.indexOf('?');
      if (queryParamIndex > 0 && !rawData.contains("%")) {
        // Possibly encoded but more likely not
        String encodedQueryParams = URLEncoder.encode(rawData.substring(queryParamIndex + 1), "UTF-8");
        rawData = rawData.substring(0, queryParamIndex) + "?" + encodedQueryParams;
        rawData = rawData.replaceAll("%3D", "=");
        rawData = rawData.replaceAll("%26", "&");
      }

      log.debug("Using '{}' to create payment protocol session summary", rawData);
      PaymentProtocolService paymentProtocolService = new PaymentProtocolService(MainNetParams.get());
      // TODO Switch this back to "true" for production
      final PaymentSessionSummary paymentSessionSummary = paymentProtocolService.probeForPaymentSession(URI.create(rawData), false, null);
      if (paymentSessionSummary != null) {
        paymentSessionSummaryQueue.add(paymentSessionSummary);
      }

    } catch (UnsupportedEncodingException e) {
      log.error("UTF-8 is not supported on this platform");
    }

    return Optional.absent();

  }

  /**
   * The listening server
   */
  private static class MessageServerRunnable implements Runnable {

    private final ServerSocket serverSocket;

    public MessageServerRunnable(ServerSocket serverSocket) {
      this.serverSocket = serverSocket;
    }

    // Guava will call this due to the annotation
    @SuppressFBWarnings({"UMAC_UNCALLABLE_METHOD_OF_ANONYMOUS_CLASS"})
    // Requires its own shutdown subscriber to ensure correct shutdown
    @Subscribe
    public void onShutdownEvent(ShutdownEvent event) {

      if (ShutdownEvent.ShutdownType.SWITCH.equals(event.getShutdownType())) {
        // Can ignore the shutdown
        log.debug("Instance server runnable ignoring wallet switch shutdown event");
        return;
      }

      try {
        serverSocket.close();
      } catch (IOException e) {
        log.error(e.getMessage(), e);
      }

    }

    @Override
    public void run() {

      CoreEvents.subscribe(this);

      boolean socketClosed = false;

      while (!socketClosed) {

        if (serverSocket.isClosed()) {
          socketClosed = true;
        } else {

          try {

            Socket client = serverSocket.accept();

            String rawData;
            try (InputStreamReader reader = new InputStreamReader(client.getInputStream(), Charsets.UTF_8)) {
              rawData = CharStreams.toString(reader);
              if (!rawData.startsWith(MESSAGE_START)) {
                // Message not following the correct format so is likely an error
                continue;
              }
              // Strip off the message start/end tags to leave a raw Bitcoin URI
              rawData = rawData.replace(MESSAGE_START, "").replace(MESSAGE_END, "");
            }
            client.close();

            log.debug("Received external data: '{}'", rawData);

            // Attempt to add to the queues and issue an alert
            ExternalDataListeningService.addToQueues(rawData, true);

          } catch (IOException e) {
            socketClosed = true;
          }
        }
      } // End of while


    }
  }
}