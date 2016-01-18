package org.multibit.hd.ui.views.wizards;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.multibit.commons.concurrent.SafeExecutors;
import org.multibit.commons.utils.Dates;
import org.multibit.hd.core.dto.WalletMode;
import org.multibit.hd.core.services.ApplicationEventService;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.hardware.core.HardwareWalletService;
import org.multibit.hd.hardware.core.events.HardwareWalletEvent;
import org.multibit.hd.ui.languages.MessageKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

/**
 * <p>Abstract base class wizard model:</p>
 * <ul>
 * <li>Access to standard implementations of required methods to support hardware wallets</li>
 * </ul>
 *
 * @param <S> The state object type
 *
 * @since 0.0.1
 */
public abstract class AbstractHardwareWalletWizardModel<S> extends AbstractWizardModel<S> {

  private static final Logger log = LoggerFactory.getLogger(AbstractHardwareWalletWizardModel.class);

  /**
   * Hardware wallet requests have their own executor service which is shared across all hardware wizards
   */
  protected static final ListeningExecutorService hardwareWalletRequestService = SafeExecutors.newSingleThreadExecutor("hardware-requests");

  /**
   * The current wallet mode (e.g. TREZOR, KEEP_KEY etc)
   */
  private WalletMode walletMode;

  /**
   * The hardware wallet report message key
   */
  private Optional<MessageKey> reportMessageKey = Optional.absent();
  /**
   * The hardware wallet message status (true if successful, false if failed)
   */
  private boolean reportMessageStatus = false;

  protected AbstractHardwareWalletWizardModel(S state) {
    super(state);

    // Keep track of the current wallet mode
    this.walletMode = WalletMode.of(CoreServices.getCurrentHardwareWalletService());

  }

  /**
   * @return The current wallet mode from when this wizard was created
   */
  public WalletMode getWalletMode() {
    return walletMode;
  }

  /**
   * @param walletMode The new wallet mode (devices may attach/detach during wizard presentation)
   */
  public void setWalletMode(WalletMode walletMode) {
    this.walletMode = walletMode;
  }

  /**
   * Handles state transition to a "device failed" panel
   *
   * Usually this will be an "Report" panel following a "Request" and the
   * panel will show a report indicating that Trezor communication has failed
   *
   * Clicking "Next" or "Finish" will trigger the end of the wizard or a transition
   * to a fallback (e.g. password entry)
   *
   * @param event The hardware wallet event containing payload and context
   */
  public void showDeviceFailed(HardwareWalletEvent event) {
    // Do nothing
  }

  /**
   * Handles state transition to a "device ready" panel
   *
   * Usually this will be a "use hardware wallet" panel which will show a collection
   * of options to determine what happens next
   *
   * Note that an "operation failure" will reset the device back to its
   * initialised state leading to a "device ready" so implementers should be
   * aware of the context
   *
   * Clicking "Next" will trigger the next step
   *
   * @param event The hardware wallet event containing payload and context
   */
  public void showDeviceReady(HardwareWalletEvent event) {
    // Do nothing
  }

  /**
   * Handles state transition to a "device detached" panel
   *
   * Usually this will be an "Report" panel which will show a report
   * indicating that Trezor communication has been detached
   *
   * Clicking "Next" will trigger the next step
   *
   * @param event The hardware wallet event containing payload and context
   */
  public void showDeviceDetached(HardwareWalletEvent event) {
    // Do nothing
  }

  /**
   * Handles state transition to a "device stopped" panel
   *
   * Usually this will be seen during a switch wallet operation where a
   * device has been shut down
   *
   * There may be no user interaction required
   *
   * @param event The hardware wallet event containing payload and context
   */
  public void showDeviceStopped(HardwareWalletEvent event) {
    // Do nothing
  }

  /**
   * Handles state transition to a "PIN entry" panel
   *
   * Usually this will be an "EnterCurrentPin" panel following a "Request" and the
   * panel will show a PIN matrix
   *
   * Clicking "Next" or "Unlock" will trigger the sending of PIN positions to the device
   * and subsequent state transitions
   *
   * @param event The hardware wallet event containing payload and context
   */
  public void showPINEntry(HardwareWalletEvent event) {
    // Do nothing
  }

  /**
   * Handles state transition to a "passphrase entry" panel
   *
   * Usually this will be an "EnterCurrentPassphrase" panel following a "Request" and the
   * panel will show a passphrase dialog (this is currently unsupported see #4 in MultiBit Hardware)
   *
   * Clicking "Next" or "Unlock" will trigger the sending of passphrase to the device
   * and subsequent state transitions
   *
   * @param event The hardware wallet event containing payload and context
   */
  public void showPassphraseEntry(HardwareWalletEvent event) {
    // Do nothing
  }

  /**
   * Handles state transition to a "button press" panel
   *
   * Usually this will be a "Confirm" panel following a "Request" and the
   * panel will show text mirroring the Trezor
   *
   * Clicking a button on the device will trigger further state transitions
   *
   * @param event The hardware wallet event containing payload and context
   */
  public void showButtonPress(HardwareWalletEvent event) {
    // Do nothing
  }

  /**
   * Handles state transition to an "operation succeeded" panel
   *
   * @param event The hardware wallet event containing payload and context
   */
  public void showOperationSucceeded(HardwareWalletEvent event) {
    // Do nothing
  }

  /**
   * Handles state transition to an "operation failed" panel
   *
   * Typically a wizard would restart with fresh state since the Trezor will fall back
   * to its initialised state
   *
   * @param event The hardware wallet event containing payload and context
   */
  public void showOperationFailed(HardwareWalletEvent event) {
    // Do nothing
  }

  /**
   * Handles state transition to a "word entry" panel
   *
   * @param event The hardware wallet event containing payload and context
   */
  public void showWordEntry(HardwareWalletEvent event) {
    // Do nothing
  }

  /**
   * Handles state transition to a "provide entropy" panel
   *
   * @param event The hardware wallet event containing payload and context
   */
  public void showProvideEntropy(HardwareWalletEvent event) {
    // Do nothing

  }

  /**
   * Handles provision of an Address from the device
   *
   * @param event The hardware wallet event containing payload and context
   */
  public void receivedAddress(HardwareWalletEvent event) {
    // Do nothing
  }

  /**
   * Handles provision of a public key from the device
   *
   * @param event The hardware wallet event containing payload and context
   */
  public void receivedPublicKey(HardwareWalletEvent event) {
    // Do nothing
  }

  /**
   * Handles provision of a deterministic hierarchy from the device
   *
   * @param event The hardware wallet event containing payload and context
   */
  public void receivedDeterministicHierarchy(HardwareWalletEvent event) {
    // Do nothing
  }

  /**
   * Handles provision of a message signature from the device
   *
   * @param event The hardware wallet event containing payload and context
   */
  public void receivedMessageSignature(HardwareWalletEvent event) {
    // Do nothing
  }

  /**
   * @return The report panel view message key for the hardware wallet operation
   */
  public Optional<MessageKey> getReportMessageKey() {
    return reportMessageKey;
  }

  /**
   * @param reportMessageKey The report panel view message key for the hardware wallet operation
   */
  public void setReportMessageKey(MessageKey reportMessageKey) {
    this.reportMessageKey = Optional.fromNullable(reportMessageKey);
  }

  /**
   * @return True if the operation was successful
   */
  public boolean getReportMessageStatus() {
    return reportMessageStatus;
  }

  /**
   * @param reportMessageStatus True if the operation was successful
   */
  public void setReportMessageStatus(boolean reportMessageStatus) {
    this.reportMessageStatus = reportMessageStatus;
  }

  /**
   * Request cancellation of current operation
   * Only sent if the hardware wallet is present
   */
  public void requestCancel() {

    // Attempt the cancellation operation on a separate thread to avoid UI lockup
    // if a hardware wallet is not present

    // Start the request
    ListenableFuture future = hardwareWalletRequestService.submit(
      new Callable<Boolean>() {

        @Override
        public Boolean call() throws Exception {

          // See if the attached trezor is initialised - no need to perform a cancel if there is no wallet
          final Optional<HardwareWalletService> hardwareWalletService = CoreServices.getCurrentHardwareWalletService();
          if (hardwareWalletService.isPresent()) {

            // Cancel the current Trezor operation

            // The Trezor should respond quickly to a cancel
            ApplicationEventService.setIgnoreHardwareWalletEventsThreshold(Dates.nowUtc().plusMillis(100));

            log.debug("Sending 'request cancel'");

            // Cancel the operation
            hardwareWalletService.get().requestCancel();

            log.debug("Request was successful");

          }

          // Must have successfully sent the message to be here
          return true;

        }

      });
    Futures.addCallback(
      future, new FutureCallback() {
        @Override
        public void onSuccess(@Nullable Object result) {

          // We successfully made the request so wait for the result

        }

        @Override
        public void onFailure(Throwable t) {

          log.warn("Hardware wallet cancel failed", t);

        }

      });
  }

}
