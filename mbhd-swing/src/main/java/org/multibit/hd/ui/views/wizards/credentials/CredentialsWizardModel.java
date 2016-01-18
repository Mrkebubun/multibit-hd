package org.multibit.hd.ui.views.wizards.credentials;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.*;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.wallet.KeyChain;
import org.joda.time.DateTime;
import org.multibit.commons.concurrent.SafeExecutors;
import org.multibit.commons.utils.Dates;
import org.multibit.hd.core.config.Configurations;
import org.multibit.hd.core.dto.*;
import org.multibit.hd.core.events.CoreEvents;
import org.multibit.hd.core.events.WalletLoadEvent;
import org.multibit.hd.core.exceptions.ContactsLoadException;
import org.multibit.hd.core.exceptions.WalletLoadException;
import org.multibit.hd.core.managers.InstallationManager;
import org.multibit.hd.core.managers.WalletManager;
import org.multibit.hd.core.services.ApplicationEventService;
import org.multibit.hd.core.services.ContactService;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.hardware.core.HardwareWalletService;
import org.multibit.hd.hardware.core.events.HardwareWalletEvent;
import org.multibit.hd.hardware.core.fsm.HardwareWalletContext;
import org.multibit.hd.hardware.core.messages.*;
import org.multibit.hd.ui.audio.Sounds;
import org.multibit.hd.ui.events.view.ViewEvents;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.ViewKey;
import org.multibit.hd.ui.views.wizards.AbstractHardwareWalletWizardModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import javax.swing.*;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * <p>Model object to provide the following to "credentials wizard":</p>
 * <ul>
 * <li>Storage of panel data</li>
 * <li>State transition management</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class CredentialsWizardModel extends AbstractHardwareWalletWizardModel<CredentialsState> {

  private static final Logger log = LoggerFactory.getLogger(CredentialsWizardModel.class);

  /**
   * The "enter password" panel model
   */
  private CredentialsEnterPasswordPanelModel enterPasswordPanelModel;

  /**
   * The current "enter PIN" panel view (might have one each for master public key and cipher key)
   */
  private CredentialsEnterPinPanelView enterPinPanelView;

  /**
   * The "request cipher key" panel view
   */
  private CredentialsRequestCipherKeyPanelView requestCipherKeyPanelView;

  /**
   * The "confirm cipher key" panel view
   */
  private CredentialsConfirmCipherKeyPanelView confirmCipherKeyPanelView;

  /**
   * The "enter password" panel view
   */
  private CredentialsEnterPasswordPanelView enterPasswordPanelView;

  /**
   * The unlock wallet executor service
   */
  private ListeningExecutorService unlockWalletService = SafeExecutors.newSingleThreadExecutor("unlock-wallet");

  /**
   * The entropy to be used for the wallet ID (result of encryption by the Trezor of fixed text)
   */
  Optional<byte[]> entropy = Optional.absent();

  /**
   * True if a Trezor failure has occurred that necessitates a switch to password entry
   * or if the Trezor has been pulled out of the USB socket
   */
  private boolean switchToPassword;

  /**
   * True if an uninitialised Trezor is present and the user needs to progress to create new wallet wizard
   */
  private boolean createNewTrezorWallet = false;

  /**
   * @param credentialsState The starting state
   */
  public CredentialsWizardModel(CredentialsState credentialsState) {
    super(credentialsState);
  }

  @Override
  public String getPanelName() {
    return state.name();
  }

  /**
   * @param switchToPassword True if there is a need to switch to password entry through showNext()
   */
  public void setSwitchToPassword(boolean switchToPassword) {
    this.switchToPassword = switchToPassword;
  }

  /**
   * @param createNewTrezorWallet True if there is a need to switch to password entry through showNext()
   */
  public void setCreateNewTrezorWallet(boolean createNewTrezorWallet) {
    this.createNewTrezorWallet = createNewTrezorWallet;
  }

  @Override
  public void showPrevious() {
    switch (state) {
      case CREDENTIALS_LOAD_WALLET_REPORT:
        // Show the enter password screen (for when the user has entered an incorrect password
        state = CredentialsState.CREDENTIALS_ENTER_PASSWORD;
        setWalletMode(WalletMode.STANDARD);
        break;
      default:
        throw new IllegalStateException("Cannot showPrevious with a state of " + state);
    }
  }

  @Override
  public void showNext() {

    switch (state) {
      case CREDENTIALS_ENTER_PASSWORD:
        // Show the wallet load report
        state = CredentialsState.CREDENTIALS_LOAD_WALLET_REPORT;

        // Unlock wallet
        unlockWalletWithPassword();

        break;
      case CREDENTIALS_REQUEST_MASTER_PUBLIC_KEY:
      case CREDENTIALS_REQUEST_CIPHER_KEY:
        // User may detach their device at this point
        if (switchToPassword) {
          state = CredentialsState.CREDENTIALS_ENTER_PASSWORD;
          setWalletMode(WalletMode.STANDARD);
        }
        break;
      case CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY:
        break;
      case CREDENTIALS_PRESS_CONFIRM_FOR_UNLOCK:
        // Show the wallet load report
        state = CredentialsState.CREDENTIALS_LOAD_WALLET_REPORT;
        break;
      case CREDENTIALS_RESTORE:
        break;
      case CREDENTIALS_CREATE:
        break;
      case CREDENTIALS_LOAD_WALLET_REPORT:
        break;
      default:
        throw new IllegalStateException("Cannot showNext with a state of " + state);
    }
  }

  @Override
  public void showButtonPress(HardwareWalletEvent event) {

    ButtonRequest buttonRequest = (ButtonRequest) event.getMessage().get();

    switch (state) {
      case CREDENTIALS_REQUEST_MASTER_PUBLIC_KEY:
        switch (buttonRequest.getButtonRequestType()) {
          case OTHER:
            // Device requires confirmation to provide master public key
            state = CredentialsState.CREDENTIALS_PRESS_CONFIRM_FOR_UNLOCK;
            break;
          case FEE_OVER_THRESHOLD:
            break;
          case CONFIRM_OUTPUT:
            break;
          case RESET_DEVICE:
            break;
          case CONFIRM_WORD:
            break;
          case WIPE_DEVICE:
            break;
          case PROTECT_CALL:
            // Device requires PIN before providing master public key
            state = CredentialsState.CREDENTIALS_ENTER_PIN_FROM_MASTER_PUBLIC_KEY;
            break;
          case SIGN_TX:
            break;
          case FIRMWARE_CHECK:
            break;
          case ADDRESS:
            break;
          default:
            throw new IllegalStateException("Unexpected button: " + buttonRequest.getButtonRequestType().name());
        }
        break;
      case CREDENTIALS_ENTER_PIN_FROM_MASTER_PUBLIC_KEY:
        // Proceed to request cipher key
        state = CredentialsState.CREDENTIALS_REQUEST_CIPHER_KEY;
        break;
      case CREDENTIALS_REQUEST_CIPHER_KEY:
        switch (buttonRequest.getButtonRequestType()) {
          case OTHER:
            // Device requires confirmation to provide cipher key
            state = CredentialsState.CREDENTIALS_PRESS_CONFIRM_FOR_UNLOCK;
            break;
          case FEE_OVER_THRESHOLD:
            break;
          case CONFIRM_OUTPUT:
            break;
          case RESET_DEVICE:
            break;
          case CONFIRM_WORD:
            break;
          case WIPE_DEVICE:
            break;
          case PROTECT_CALL:
            // Device requires PIN before providing cipher key
            state = CredentialsState.CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY;
            break;
          case SIGN_TX:
            break;
          case FIRMWARE_CHECK:
            break;
          case ADDRESS:
            break;
          default:
            throw new IllegalStateException("Unexpected button: " + buttonRequest.getButtonRequestType().name());
        }
        break;
      case CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY:
        // Should be catered for by finish
        state = CredentialsState.CREDENTIALS_PRESS_CONFIRM_FOR_UNLOCK;
        break;
      default:
        throw new IllegalStateException("Unknown state: " + state.name());
    }

  }

  @Override
  public void showPINEntry(HardwareWalletEvent event) {

    switch (state) {
      case CREDENTIALS_ENTER_PASSWORD:
        break;
      case CREDENTIALS_REQUEST_MASTER_PUBLIC_KEY:
        log.debug("Master public key is PIN protected");
        state = CredentialsState.CREDENTIALS_ENTER_PIN_FROM_MASTER_PUBLIC_KEY;
        break;
      case CREDENTIALS_ENTER_PIN_FROM_MASTER_PUBLIC_KEY:
        break;
      case CREDENTIALS_REQUEST_CIPHER_KEY:
        log.debug("Cipher key is PIN protected");
        state = CredentialsState.CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY;
        break;
      case CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY:
        break;
      case CREDENTIALS_PRESS_CONFIRM_FOR_UNLOCK:
        break;
      case CREDENTIALS_LOAD_WALLET_REPORT:
        break;
      case CREDENTIALS_RESTORE:
        break;
      default:
        throw new IllegalStateException("Unknown state: " + state.name());
    }
  }

  @Override
  public void showPassphraseEntry(HardwareWalletEvent event) {

    log.warn("Device is passphrase protected (not currently supported)");

    CoreEvents.fireEnvironmentEvent(EnvironmentSummary.newUnsupportedConfigurationPassphrase());

    setSwitchToPassword(true);
    state = CredentialsState.CREDENTIALS_ENTER_PASSWORD;
    setWalletMode(WalletMode.STANDARD);
  }

  // A note is added to the switch to cover this
  @SuppressFBWarnings({"SF_SWITCH_FALLTHROUGH"})
  @Override
  public void showOperationSucceeded(HardwareWalletEvent event) {

    switch (state) {
      case CREDENTIALS_REQUEST_MASTER_PUBLIC_KEY:
        // A successful get master public key has been performed
        log.debug("CREDENTIALS_REQUEST_MASTER_PUBLIC_KEY was successful");
        break;
      case CREDENTIALS_ENTER_PIN_FROM_MASTER_PUBLIC_KEY:
        // Indicate a successful PIN
        getEnterPinPanelView().setPinStatus(true, true);
        break;
      case CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY:
        // Indicate a successful PIN
        getEnterPinPanelView().setPinStatus(true, true);

        // Fall through to "press confirm for unlock"
      case CREDENTIALS_PRESS_CONFIRM_FOR_UNLOCK:

        SwingUtilities.invokeLater(
          new Runnable() {
            @Override
            public void run() {

              confirmCipherKeyPanelView.getHardwareDisplayView().setOperationText(MessageKey.COMMUNICATING_WITH_HARDWARE_OPERATION, getWalletMode().brand());
              confirmCipherKeyPanelView.getHardwareDisplayView().setDisplayVisible(false);
              confirmCipherKeyPanelView.getHardwareDisplayView().setSpinnerVisible(true);

            }
          });

        if (event.getMessage().get() instanceof CipheredKeyValue) {

          // Payload contains entropy so use it directly
          byte[] payload = ((CipheredKeyValue) event.getMessage().get()).getPayload().get();

          // Do not write the payload into the logs since it can unlock a wallet!
          log.info("Payload length: {}", payload == null ? 0 : payload.length);

          log.debug("Using the payload as entropy");
          entropy = Optional.fromNullable(payload);

          // Ready to unlock the device wallet
          log.debug("Calling unlockWalletWithEntropy");
          unlockWalletWithEntropy();
        }
        break;
      default:
        log.info("Message:'Operation succeeded'\n{}", event.getMessage().get());
    }

  }

  @Override
  public void showOperationFailed(HardwareWalletEvent event) {

    Optional<HardwareWalletService> hardwareWalletService = CoreServices.getCurrentHardwareWalletService();

    final Failure failure = (Failure) event.getMessage().orNull();
    if (failure == null) {
      handleRestart(hardwareWalletService);
      return;
    }

    log.debug("A failure event has occurred {}", failure);

    switch (state) {
      case CREDENTIALS_REQUEST_MASTER_PUBLIC_KEY:
        // An unsuccessful get master public key has been performed
        ApplicationEventService.setIgnoreHardwareWalletEventsThreshold(Dates.nowUtc().plusSeconds(1));
        break;
      case CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY:
        // User entered incorrect PIN so should start again

        // Indicate a wrong PIN
        getEnterPinPanelView().setPinStatus(false, true);

        // Try again
        handleRestart(hardwareWalletService);

        break;
      default:

        if (FailureType.ACTION_CANCELLED.equals(failure.getType())) {
          // User is backing out of using their device (switch to password)
          state = CredentialsState.CREDENTIALS_ENTER_PASSWORD;
          setWalletMode(WalletMode.STANDARD);
        } else {
          // Something has gone wrong with the device so start again
          handleRestart(hardwareWalletService);
        }
    }
  }

  private void handleRestart(Optional<HardwareWalletService> hardwareWalletService) {
    state = CredentialsState.CREDENTIALS_REQUEST_MASTER_PUBLIC_KEY;
    // Reset the device and start again
    if (hardwareWalletService.isPresent()) {
      hardwareWalletService.get().requestCancel();
    }
  }

  @Override
  public void showDeviceReady(HardwareWalletEvent event) {

    if (ApplicationEventService.isHardwareWalletEventAllowed()) {
      // User attached an operational device in place of whatever
      // they are currently doing so start again.

      // If a wallet is loading then do not switch to PIN entry
      if (!state.equals(CredentialsState.CREDENTIALS_LOAD_WALLET_REPORT)) {
        state = CredentialsState.CREDENTIALS_REQUEST_MASTER_PUBLIC_KEY;
        setWalletMode(WalletMode.of(event));
      }
    }
  }


  @Override
  public void showDeviceDetached(HardwareWalletEvent event) {
    log.debug("Device is now detached - showing password screen");

    if (ApplicationEventService.isHardwareWalletEventAllowed()) {

      // If the wallet is loading then do not switch to password entry
      if (!state.equals(CredentialsState.CREDENTIALS_LOAD_WALLET_REPORT)) {
        state = CredentialsState.CREDENTIALS_ENTER_PASSWORD;
        setWalletMode(WalletMode.STANDARD);
      }
    }
  }

  @Override
  public void receivedDeterministicHierarchy(HardwareWalletEvent event) {

    switch (state) {
      case CREDENTIALS_ENTER_PIN_FROM_MASTER_PUBLIC_KEY:
        // Fall through since there may have been no PIN request
      case CREDENTIALS_REQUEST_MASTER_PUBLIC_KEY:
        // A successful get master public key has been performed
        log.debug("CREDENTIALS_REQUEST_MASTER_PUBLIC_KEY was successful");

        // Transition to request a cipher key (to provide entropy).
        // This will most likely trigger a PIN request
        state = CredentialsState.CREDENTIALS_REQUEST_CIPHER_KEY;
        break;

      default:
        log.info(
          "Message:'Operation succeeded'\n{}",
          event.getMessage().get()
        );
    }

  }

  /**
   * Request a cipher key from the device
   */
  public void requestCipherKey() {
    // Communicate with the device off the EDT
    ListenableFuture<Boolean> requestCipherKeyFuture = hardwareWalletRequestService.submit(
      new Callable<Boolean>() {
        @Override
        public Boolean call() {
          log.debug("Performing a request cipher key to hardware wallet");

          // Provide a short delay to allow UI to update
          Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);

          // A 'requestCipherKey' is performed in which the user presses the OK button to encrypt a set text
          // (the result of which will be used to decrypt the wallet)
          Optional<HardwareWalletService> hardwareWalletService = CoreServices.getCurrentHardwareWalletService();

          // Check if there is a wallet present
          if (hardwareWalletService.get().isWalletPresent()) {

            // Use this layout to ensure line wrapping occurs on a V1 Trezor
            // DO NOT CHANGE THIS or client wallets will not unlock
            byte[] key = "MultiBit HD     Unlock".getBytes(Charsets.UTF_8);
            byte[] keyValue = "0123456789abcdef".getBytes(Charsets.UTF_8);

            // Request a cipher key against 0'/0/0
            // AbstractHardwareWalletWizard will deal with the responses
            hardwareWalletService.get().requestCipherKey(
              0,
              KeyChain.KeyPurpose.RECEIVE_FUNDS,
              0,
              key,
              keyValue,
              true,
              true,
              true
            );

          }

          // Completed
          return true;

        }
      });
    Futures.addCallback(
      requestCipherKeyFuture, new FutureCallback<Boolean>() {

        @Override
        public void onSuccess(Boolean result) {

          // Do nothing - message was successfully relayed to the device

        }

        @Override
        public void onFailure(Throwable t) {

          // Failed to send the message
          requestCipherKeyPanelView.setOperationText(MessageKey.HARDWARE_FAILURE_OPERATION);
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

          // Do nothing - message was successfully relayed to the device

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

  /**
   * Request the root node for the Trezor HD wallet as a deterministic hierarchy
   */
  public void requestRootNode() {

    // Start the requestRootNode
    ListenableFuture future = hardwareWalletRequestService.submit(
      new Callable<Boolean>() {

        @Override
        public Boolean call() throws Exception {

          Optional<HardwareWalletService> hardwareWalletServiceOptional = CoreServices.getCurrentHardwareWalletService();

          if (hardwareWalletServiceOptional.isPresent()) {

            HardwareWalletService hardwareWalletService = hardwareWalletServiceOptional.get();

            if (hardwareWalletService.isWalletPresent()) {

              log.debug("Request the deterministic hierarchy for the Trezor account");
              hardwareWalletService.requestDeterministicHierarchy(
                Lists.newArrayList(
                  new ChildNumber(44 | ChildNumber.HARDENED_BIT),
                  ChildNumber.ZERO_HARDENED,
                  ChildNumber.ZERO_HARDENED
                ));

              log.debug("Request deterministic hierarchy has been performed");

              // The "receivedDeterministicHierarchy" response is dealt with in the wizard model

            } else {
              log.debug("No wallet present");
            }
          } else {
            log.error("No hardware wallet service");
          }
          return true;

        }

      });

    Futures.addCallback(
      future, new FutureCallback() {
        @Override
        public void onSuccess(@Nullable Object result) {

          // Succeeded in sending the root node message

        }

        @Override
        public void onFailure(Throwable t) {

          // Failed to send the message
          requestCipherKeyPanelView.setOperationText(MessageKey.HARDWARE_FAILURE_OPERATION);
        }

      });
  }

  /**
   * <p>Continue the hide process after user has confirmed entropy and we have a deterministic hierarchy</p>
   */
  private void unlockWalletWithEntropy() {

    // Set the state to move to the load wallet report form
    state = CredentialsState.CREDENTIALS_LOAD_WALLET_REPORT;

    // Hide the header view (switching back on is done in MainController#onBitcoinNetworkChangedEvent
    ViewEvents.fireViewChangedEvent(ViewKey.HEADER, false);

    // Check the password (might take a while so do it asynchronously while showing a spinner)
    ListenableFuture<Optional<WalletSummary>> passwordFuture = unlockWalletService.submit(
      new Callable<Optional<WalletSummary>>() {

        @Override
        public Optional<WalletSummary> call() {

          // Need a very short delay here to allow the UI thread to update
          Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);

          return getOrCreateTrezorWallet();

        }
      });
    Futures.addCallback(
      passwordFuture, new FutureCallback<Optional<WalletSummary>>() {

        @Override
        public void onSuccess(Optional<WalletSummary> result) {
          // Hide the wallet summary in production
          log.trace("Result: {}", result);

          // Check the result
          if (!result.isPresent()) {

            // Provide aural feedback immediately
            Sounds.playBeep(Configurations.currentConfiguration.getSound());

            // Wait just long enough to be annoying (anything below 2 seconds is comfortable)
            Uninterruptibles.sleepUninterruptibly(2, TimeUnit.SECONDS);

            // Ensure the view hides the spinner and enables components
            confirmCipherKeyPanelView.incorrectEntropy();
            confirmCipherKeyPanelView.enableForFailedUnlock();

          }
        }

        @Override
        public void onFailure(Throwable t) {
          // Ensure the view hides the spinner and enables components
          confirmCipherKeyPanelView.incorrectEntropy();
          confirmCipherKeyPanelView.enableForFailedUnlock();
        }
      }
    );

  }

  /**
   * <p>Continue the hide process after user has entered a password and clicked unlock</p>
   */
  public void unlockWalletWithPassword() {

    // Hide the header view (switching back on is done in MainController#onBitcoinNetworkChangedEvent
    ViewEvents.fireViewChangedEvent(ViewKey.HEADER, false);

    // Check the password (might take a while so do it asynchronously)
    // Tar pit (must be in a separate thread to ensure UI updates)
    ListenableFuture<Boolean> passwordFuture = unlockWalletService.submit(
      new Callable<Boolean>() {

        @Override
        public Boolean call() {

          // Need a very short delay here to allow the UI thread to update
          Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);

          return checkPasswordAndLoadWallet();

        }
      });
    Futures.addCallback(
      passwordFuture, new FutureCallback<Boolean>() {

        @Override
        public void onSuccess(Boolean result) {
          // Check the result
          if (!result) {

            // Provide aural feedback immediately
            Sounds.playBeep(Configurations.currentConfiguration.getSound());

            // Wait just long enough to be annoying (anything below 2 seconds is comfortable)
            Uninterruptibles.sleepUninterruptibly(2, TimeUnit.SECONDS);

            // Ensure the view hides the spinner and enables components
            SwingUtilities.invokeLater(
              new Runnable() {
                @Override
                public void run() {

                  enterPasswordPanelView.incorrectPassword();
                  enterPasswordPanelView.enableForFailedUnlock();

                }
              });
          }
        }

        @Override
        public void onFailure(Throwable t) {

          SwingUtilities.invokeLater(
            new Runnable() {
              @Override
              public void run() {
                // Ensure the view hides the spinner and enables components
                enterPasswordPanelView.enableForFailedUnlock();

              }
            });
        }
      }
    );

  }

  /**
   * Check the password and load the wallet
   *
   * @return True if the selected wallet can be opened with the given password
   */
  private boolean checkPasswordAndLoadWallet() {
    CharSequence password = enterPasswordPanelModel.getEnterPasswordModel().getValue();

    if (!"".equals(password)) {
      // Attempt to open the wallet to check the password
      WalletId walletId = enterPasswordPanelModel.getSelectWalletModel().getValue().getWalletId();


      Optional<WalletSummary> currentWalletSummary;
      try {
        // Open the contacts BEFORE the wallet
        // This way if the password is a previous password a rolling backup is not loaded
        // Fail fast

        // Create the contacts service
        ContactService contactService = CoreServices.getOrCreateContactService(new WalletPassword(password, walletId));
        contactService.loadContacts(password);

        currentWalletSummary = WalletManager.INSTANCE.openWalletFromWalletId(InstallationManager.getOrCreateApplicationDataDirectory(), walletId, password);
      } catch (ContactsLoadException | org.bitcoinj.crypto.KeyCrypterException | WalletLoadException wle) {
        // Mostly this will be from a bad password
        log.error(wle.getMessage());
        // Assume bad credentials
        CoreEvents.fireWalletLoadEvent(new WalletLoadEvent(Optional.<WalletId>absent(), false, CoreMessageKey.WALLET_BAD_PASSWORD, null, Optional.<File>absent()));

        return false;
      }

      if (currentWalletSummary != null && currentWalletSummary.isPresent()) {

        // Store this wallet in the current configuration
        String walletRoot = WalletManager.createWalletRoot(walletId);
        Configurations.currentConfiguration.getWallet().setLastSoftWalletRoot(walletRoot);

        // Update the wallet data
        WalletSummary walletSummary = currentWalletSummary.get();
        walletSummary.setWalletPassword(new WalletPassword(password, walletId));

        return true;
      }
    }

    // Must have failed to be here
    log.error("Failed attempt to open wallet");

    return false;

  }

  /**
   * <p>Get or create a Trezor wallet based on the entropy and deterministic hierarchy obtained earlier</p>
   *
   * @return The wallet summary, present if the wallet was created/opened successfully
   */
  private Optional<WalletSummary> getOrCreateTrezorWallet() {

    Optional<HardwareWalletService> hardwareWalletServiceOptional = CoreServices.getCurrentHardwareWalletService();
    if (hardwareWalletServiceOptional.isPresent()) {

      HardwareWalletService hardwareWalletService = hardwareWalletServiceOptional.get();

      if (hardwareWalletService.isWalletPresent()) {
        try {

          HardwareWalletContext hardwareWalletContext = hardwareWalletService.getContext();
          // Parent key should be M/44'/0'/0'
          final DeterministicKey parentKey = hardwareWalletContext.getDeterministicKey().get();
          log.info("Parent key path: {}", parentKey.getPathAsString());

          // Set the creation date of the parent key to be the earliest possible HD wallet date
          long earliestHDWalletCreationTime = new DateTime(WalletManager.EARLIEST_HD_WALLET_DATE).getMillis() / 1000;
          parentKey.setCreationTimeSeconds(earliestHDWalletCreationTime);

          // Verify the deterministic hierarchy can derive child keys
          // In this case 0/0 from a parent of M/44'/0'/0'
          DeterministicHierarchy hierarchy = hardwareWalletContext.getDeterministicHierarchy().get();
          DeterministicKey childKey = hierarchy.deriveChild(
            Lists.newArrayList(
              ChildNumber.ZERO
            ),
            true,
            true,
            ChildNumber.ZERO
          );

          // Calculate the address
          ECKey seedKey = ECKey.fromPublicOnly(childKey.getPubKey());
          Address walletKeyAddress = new Address(MainNetParams.get(), seedKey.getPubKeyHash());

          log.info("Path {}/0/0 has address: '{}'", parentKey.getPathAsString(), walletKeyAddress.toString());

          // Get the label of the Trezor from the features to use as the wallet name
          Optional<Features> features = hardwareWalletContext.getFeatures();
          log.debug("Features: {}", features);
          final String label;
          if (features.isPresent()) {
            label = features.get().getLabel();
          } else {
            label = "";
          }

          if (!entropy.isPresent()) {
            log.error("No entropy from Trezor so cannot create or load a wallet.");
            return Optional.absent();
          }

          // The entropy is used as the password of the Trezor wallet (so the user does not need to remember it
          log.debug("Running decrypt of Trezor wallet with entropy of length {}", entropy.get().length);

          String newWalletPassword = Hex.toHexString(entropy.get());

          // Locate the installation directory
          final File applicationDataDirectory = InstallationManager.getOrCreateApplicationDataDirectory();

          // Work out if the wallet is a brand new Trezor wallet
          // if the label is the same and the data validity time is within a few minutes of now then we use the
          // data validity time as the replay date
          long replayDateInMillis = DateTime.parse(WalletManager.EARLIEST_HD_WALLET_DATE).getMillis();
          String recentWalletLabel = Configurations.currentConfiguration.getWallet().getRecentWalletLabel();
          log.debug("Label of current Trezor wallet: {}, recentWalletLabel: {}", label, recentWalletLabel);

          if (label.equals(recentWalletLabel)) {
            long now = System.currentTimeMillis();
            long dataValidityTime = Configurations.currentConfiguration.getWallet().getRecentWalletDataValidity();
            log.debug("Now: {}, recentWalletDataValidity: {}", label, dataValidityTime);
            if (now - dataValidityTime <= WalletManager.MAXIMUM_WALLET_CREATION_DELTA) {
              replayDateInMillis = dataValidityTime;
              log.debug("Using a replayDate for brand new Trezor of {}", replayDateInMillis);
            }
          }

          // Must be OK to be here

          return Optional.fromNullable(
            WalletManager.INSTANCE.getOrCreateTrezorCloneHardWalletSummaryFromRootNode(
              applicationDataDirectory,
              parentKey,
              // There is no reliable timestamp for a 'new' wallet as it could exist elsewhere
              replayDateInMillis / 1000,
              newWalletPassword,
              label, "", true));

        } catch (Exception e) {
          CoreEvents.fireWalletLoadEvent(new WalletLoadEvent(Optional.<WalletId>absent(), false, CoreMessageKey.WALLET_FAILED_TO_LOAD, e, Optional.<File>absent()));

          log.error(e.getMessage(), e);

        }
      } else {
        log.debug("No wallet present");
      }
    } else {
      CoreEvents.fireWalletLoadEvent(
        new WalletLoadEvent(
          Optional.<WalletId>absent(),
          false,
          CoreMessageKey.WALLET_FAILED_TO_LOAD,
          new IllegalStateException("No hardware wallet service available"),
          Optional.<File>absent()));

      log.error("No hardware wallet service");
    }
    return Optional.absent();
  }

  /**
   * <p>Reduced visibility for panel models only</p>
   *
   * @param enterPasswordPanelModel The "enter credentials" panel model
   */
  void setEnterPasswordPanelModel(CredentialsEnterPasswordPanelModel enterPasswordPanelModel) {
    this.enterPasswordPanelModel = enterPasswordPanelModel;
  }

  /**
   * @return The current "enter PIN" panel view
   */
  public CredentialsEnterPinPanelView getEnterPinPanelView() {
    return enterPinPanelView;
  }

  public void setEnterPinPanelView(CredentialsEnterPinPanelView enterPinPanelView) {
    this.enterPinPanelView = enterPinPanelView;
  }

  public void setRequestCipherKeyPanelView(CredentialsRequestCipherKeyPanelView requestCipherKeyPanelView) {
    this.requestCipherKeyPanelView = requestCipherKeyPanelView;
  }

  public void setConfirmCipherKeyPanelView(CredentialsConfirmCipherKeyPanelView confirmCipherKeyPanelView) {
    this.confirmCipherKeyPanelView = confirmCipherKeyPanelView;
  }

  public void setEnterPasswordPanelView(CredentialsEnterPasswordPanelView enterPasswordPanelView) {
    this.enterPasswordPanelView = enterPasswordPanelView;
  }
}
