package org.multibit.hd.ui.views.wizards.sign_message;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.wallet.KeyChain;
import org.multibit.commons.utils.Dates;
import org.multibit.hd.core.dto.CoreMessageKey;
import org.multibit.hd.core.dto.SignMessageResult;
import org.multibit.hd.core.managers.WalletManager;
import org.multibit.hd.core.services.ApplicationEventService;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.core.utils.BitcoinNetwork;
import org.multibit.hd.hardware.core.HardwareWalletService;
import org.multibit.hd.hardware.core.events.HardwareWalletEvent;
import org.multibit.hd.hardware.core.messages.MessageSignature;
import org.multibit.hd.ui.events.view.ViewEvents;
import org.multibit.hd.ui.views.wizards.AbstractHardwareWalletWizardModel;
import org.multibit.hd.ui.views.wizards.WizardButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Base64;

import java.security.SignatureException;
import java.util.Arrays;
import java.util.concurrent.Callable;

/**
 * <p>Model object to provide the following to "sign message" wizard:</p>
 * <ul>
 * <li>Storage of panel data</li>
 * <li>State transition management</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class SignMessageWizardModel extends AbstractHardwareWalletWizardModel<SignMessageState> {

  private static final Logger log = LoggerFactory.getLogger(SignMessageWizardModel.class);

  private Address signingAddress = null;
  private String message;
  private byte[] signature;

  private SignMessageEnterPinPanelView enterPinPanelView;
  private SignMessageHardwarePanelView signMessageHardwarePanelView;
  private SignMessageConfirmSignHardwarePanelView confirmSignPanelView;

  /**
   * @param state The state object
   */
  public SignMessageWizardModel(SignMessageState state) {
    super(state);
  }

  @Override
  public void showNext() {

    switch (state) {
      case SIGN_MESSAGE_PASSWORD:
        break;
      case SIGN_MESSAGE_TREZOR_ENTER_PIN:
        state = SignMessageState.SIGN_MESSAGE_HARDWARE;
        break;
      case SIGN_MESSAGE_TREZOR_CONFIRM_SIGN:
        state = SignMessageState.SIGN_MESSAGE_HARDWARE;
        break;
      case SIGN_MESSAGE_HARDWARE:
        break;
      default:
        // Do nothing
    }
  }

  @Override
  public void showPrevious() {
    switch (state) {
      case SIGN_MESSAGE_PASSWORD:
        break;
      case SIGN_MESSAGE_TREZOR_ENTER_PIN:
        state = SignMessageState.SIGN_MESSAGE_HARDWARE;
        break;
      case SIGN_MESSAGE_TREZOR_CONFIRM_SIGN:
        state = SignMessageState.SIGN_MESSAGE_HARDWARE;
        break;
      case SIGN_MESSAGE_HARDWARE:
        break;
      default:
        throw new IllegalStateException("Unexpected state:" + state);
    }
  }

  /**
   * @param addressText The Bitcoin address to use for signing
   * @param finalMessage The message to be signed
   */
  public void requestSignMessage(final String addressText, final String finalMessage) {

    setMessage(finalMessage);

    ListenableFuture<Boolean> signMessageFuture = hardwareWalletRequestService.submit(
      new Callable<Boolean>() {

        @Override
        public Boolean call() {

          final Address signingAddress;
          try {
            signingAddress = new Address(BitcoinNetwork.current().get(), addressText);
          } catch (AddressFormatException e) {
            return false;
          }

          // Convert the address into a signing address
          Wallet wallet = WalletManager.INSTANCE.getCurrentWalletSummary().get().getWallet();
          ECKey signingKey = wallet.findKeyFromPubHash(signingAddress.getHash160());
          if (signingKey == null) {
            log.warn("Could not find ECKey for address '{}'", addressText);
            return false;
          }
          DeterministicKey keyFromPubKey = wallet.getActiveKeychain().findKeyFromPubKey(signingKey.getPubKey());
          if (keyFromPubKey == null) {
            log.warn("Could not find DeterministicKey for address '{}'", addressText);
            return false;
          }
          int index = keyFromPubKey.getChildNumber().getI();

          // Talk to the Trezor and get it to sign the message
          // This call to the Trezor will (sometime later) fire a
          // HardwareWalletEvent containing the encrypted text (or a PIN failure)
          // Expect a MESSAGE_SIGNATURE or SHOW_OPERATION_FAILED
          Optional<HardwareWalletService> hardwareWalletService = CoreServices.getCurrentHardwareWalletService();
          hardwareWalletService.get().signMessage(
            0,
            KeyChain.KeyPurpose.RECEIVE_FUNDS,
            index,
            finalMessage.getBytes(Charsets.UTF_8)
          );

          // Must have successfully send the message to be here
          return true;

        }
      });
    Futures.addCallback(
      signMessageFuture, new FutureCallback<Boolean>() {

        @Override
        public void onSuccess(Boolean result) {

          // Do nothing message was sent to device correctly

        }

        @Override
        public void onFailure(Throwable t) {

          log.error(t.getMessage(), t);
          // Failed to send the message
          SignMessageResult signMessageResult = new SignMessageResult(
            Optional.<String>absent(),
            false,
            CoreMessageKey.SIGN_MESSAGE_FAILURE,
            new String[]{}
          );
          signMessageHardwarePanelView.showSignMessageResult(signMessageResult);
        }
      }
    );

  }

  /**
   * @param pinPositions The PIN positions providing a level of obfuscation to protect the PIN
   */
  public void requestPinCheck(final String pinPositions) {

    ListenableFuture<Boolean> pinCheckFuture = hardwareWalletRequestService.submit(
      new Callable<Boolean>() {

        @Override
        public Boolean call() {

          log.debug("Performing a PIN check");

          // Talk to the Trezor and get it to check the PIN
          // This call to the Trezor will (sometime later) fire a
          // HardwareWalletEvent containing the encrypted text (or a PIN failure)
          // Expect a SHOW_OPERATION_SUCCEEDED or SHOW_OPERATION_FAILED
          Optional<HardwareWalletService> hardwareWalletService = CoreServices.getCurrentHardwareWalletService();
          hardwareWalletService.get().providePIN(pinPositions);

          // Must have successfully send the message to be here
          return true;

        }
      });
    Futures.addCallback(
      pinCheckFuture, new FutureCallback<Boolean>() {

        @Override
        public void onSuccess(Boolean result) {

          // Do nothing message was sent to device correctly

        }

        @Override
        public void onFailure(Throwable t) {

          log.error(t.getMessage(), t);
          // Failed to send the message
          enterPinPanelView.failedPin();
        }
      }
    );

  }

  @Override
  public void showPINEntry(HardwareWalletEvent event) {

    switch (state) {
      case SIGN_MESSAGE_HARDWARE:
        log.debug("Transaction signing is PIN protected");
        state = SignMessageState.SIGN_MESSAGE_TREZOR_ENTER_PIN;
        break;
      default:
        throw new IllegalStateException("Unknown state: " + state.name());
    }
  }

  @Override
  public void showButtonPress(HardwareWalletEvent event) {

    log.debug("Received hardware event: '{}'.{}", event.getEventType().name(), event.getMessage());

    // Ensure we transition to the confirm screen
    state = SignMessageState.SIGN_MESSAGE_TREZOR_CONFIRM_SIGN;

  }

  @Override
  public void receivedMessageSignature(HardwareWalletEvent event) {

    // Successful message signature
    MessageSignature signature = (MessageSignature) event.getMessage().get();

    log.info("Signature:\n{}", Utils.HEX.encode(signature.getSignature()));

    // Ensure we show the Trezor panel view
    state = SignMessageState.SIGN_MESSAGE_HARDWARE;

    // Verify the signature
    String base64Signature = Base64.toBase64String(signature.getSignature());

    SignMessageResult signMessageResult;
    try {
      ECKey key = ECKey.signedMessageToKey(getMessage(), base64Signature);
      Address gotAddress = key.toAddress(MainNetParams.get());

      if (gotAddress.toString().equals(signature.getAddress())) {
        log.info("Verified the signature");
        signMessageResult = new SignMessageResult(
          Optional.of(base64Signature),
          true,
          CoreMessageKey.SIGN_MESSAGE_SUCCESS,
          new String[]{}
        );
      } else {
        log.warn("Failed to verify the signature");
        signMessageResult = new SignMessageResult(
          Optional.<String>absent(),
          false,
          CoreMessageKey.SIGN_MESSAGE_FAILURE,
          new String[]{}
        );
      }
    } catch (SignatureException e) {
      log.warn("Failed to verify the signature");
      signMessageResult = new SignMessageResult(
        Optional.<String>absent(),
        false,
        CoreMessageKey.SIGN_MESSAGE_FAILURE,
        new String[]{}
      );
    }

    // Update the panel view
    signMessageHardwarePanelView.showSignMessageResult(signMessageResult);

    // Must be showing message signing Trezor display

    // Enable Finish button
    ViewEvents.fireWizardButtonEnabledEvent(
      getPanelName(),
      WizardButton.FINISH,
      true
    );

  }

  @Override
  public void showOperationFailed(HardwareWalletEvent event) {

    SignMessageResult signMessageResult = new SignMessageResult(
      Optional.<String>absent(),
      false,
      CoreMessageKey.SIGN_MESSAGE_FAILURE,
      new String[]{}
    );
    signMessageHardwarePanelView.showSignMessageResult(signMessageResult);

    // Ignore device reset messages
    ApplicationEventService.setIgnoreHardwareWalletEventsThreshold(Dates.nowUtc().plusSeconds(1));

  }

  /**
   * @return The message
   */
  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  /**
   * @return The signing address
   */
  public Address getSigningAddress() {
    return signingAddress;
  }

  public void setSigningAddress(Address signingAddress) {
    this.signingAddress = signingAddress;
  }

  /**
   * @return The signature
   */
  public byte[] getSignature() {
    return Arrays.copyOf(signature, signature.length);
  }

  public void setSignature(byte[] signature) {
    this.signature = Arrays.copyOf(signature, signature.length);
  }

  public void setEnterPinPanelView(SignMessageEnterPinPanelView enterPinPanelView) {
    this.enterPinPanelView = enterPinPanelView;
  }

  public SignMessageEnterPinPanelView getEnterPinPanelView() {
    return enterPinPanelView;
  }

  public SignMessageHardwarePanelView getSignMessageHardwarePanelView() {
    return signMessageHardwarePanelView;
  }

  public void setSignMessageHardwarePanelView(SignMessageHardwarePanelView signMessageHardwarePanelView) {
    this.signMessageHardwarePanelView = signMessageHardwarePanelView;
  }

  public void setConfirmSignPanelView(SignMessageConfirmSignHardwarePanelView confirmSignPanelView) {
    this.confirmSignPanelView = confirmSignPanelView;
  }
}
