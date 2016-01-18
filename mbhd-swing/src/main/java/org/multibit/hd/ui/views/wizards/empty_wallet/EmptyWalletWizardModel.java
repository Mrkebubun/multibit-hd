package org.multibit.hd.ui.views.wizards.empty_wallet;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.MainNetParams;
import org.multibit.commons.utils.Dates;
import org.multibit.hd.brit.core.dto.FeeState;
import org.multibit.hd.brit.core.services.FeeService;
import org.multibit.hd.core.config.BitcoinConfiguration;
import org.multibit.hd.core.config.Configurations;
import org.multibit.hd.core.config.LanguageConfiguration;
import org.multibit.hd.core.dto.*;
import org.multibit.hd.core.events.ExchangeRateChangedEvent;
import org.multibit.hd.core.exchanges.ExchangeKey;
import org.multibit.hd.core.managers.WalletManager;
import org.multibit.hd.core.services.ApplicationEventService;
import org.multibit.hd.core.services.BitcoinNetworkService;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.core.utils.BitcoinSymbol;
import org.multibit.hd.hardware.core.HardwareWalletService;
import org.multibit.hd.hardware.core.events.HardwareWalletEvent;
import org.multibit.hd.hardware.core.messages.ButtonRequest;
import org.multibit.hd.ui.events.view.ViewEvents;
import org.multibit.hd.ui.languages.Formats;
import org.multibit.hd.ui.languages.Languages;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.wizards.AbstractHardwareWalletWizardModel;
import org.multibit.hd.ui.views.wizards.WizardButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.math.BigDecimal;
import java.util.concurrent.Callable;

import static org.multibit.hd.ui.views.wizards.empty_wallet.EmptyWalletState.*;


/**
 * <p>Model object to provide the following to "empty wallet wizard":</p>
 * <ul>
 * <li>Storage of panel data</li>
 * <li>State transition management</li>
 * </ul>
 *
 * <p>This is very similar to the <code>SendBitcoinWizardModel</code> but there are subtle
 * differences that mean that they cannot share much code.</p>
 *
 * @since 0.0.1
 */
public class EmptyWalletWizardModel extends AbstractHardwareWalletWizardModel<EmptyWalletState> {

  private static final Logger log = LoggerFactory.getLogger(EmptyWalletWizardModel.class);

  /**
   * The "enter details" panel model
   */
  private EmptyWalletEnterDetailsPanelModel enterDetailsPanelModel;

  /**
   * Keep track of which transaction output is being signed
   * Start with -1 to allow for initial increment
   */
  private int txOutputIndex = -1;

  /**
   * The current wallet balance in coins less any fees
   */
  private Optional<Coin> coinAmount;


  private BitcoinNetworkService bitcoinNetworkService;

  /**
   * The prepared tx
   */
  private SendRequestSummary sendRequestSummary;
  private EmptyWalletConfirmHardwarePanelView emptyWalletConfirmHardwarePanelView;
  private EmptyWalletEnterPinPanelView enterPinPanelView;

  /**
   * @param state The state object
   */
  public EmptyWalletWizardModel(EmptyWalletState state) {
    super(state);

    coinAmount = WalletManager.INSTANCE.getCurrentWalletBalance();

  }

  @Override
  public void showNext() {

    switch (state) {
      case EMPTY_WALLET_ENTER_DETAILS:

        // See if the user has entered a recipient that is in the current wallet
        Optional<Recipient> recipientOptional = enterDetailsPanelModel.getEnterRecipientModel().getRecipient();
        if (recipientOptional.isPresent()) {
          boolean isAddressMine = WalletManager.INSTANCE.isAddressMine(recipientOptional.get().getBitcoinAddress());

          // Update model so that status note is shown
          enterDetailsPanelModel.setAddressMine(isAddressMine);
          if (isAddressMine) {
            log.debug("The address being emptied to is in the wallet !");
            // Do not traverse to next page

            state = EmptyWalletState.EMPTY_WALLET_ENTER_DETAILS;
            break;
          }
        }

        if (prepareTransaction()) {
          state = EMPTY_WALLET_CONFIRM;
        } else {
          // Transaction did not prepare correctly
          state = EMPTY_WALLET_REPORT;
        }
        break;
      case EMPTY_WALLET_CONFIRM:

        // The user has confirmed the send details and pressed the next button
        // For a non-Trezor wallet navigate directly to the send screen
        // Get the current wallet
        Optional<WalletSummary> currentWalletSummary = WalletManager.INSTANCE.getCurrentWalletSummary();
        if (currentWalletSummary.isPresent()) {

          // Determine how to send the bitcoin
          switch (getWalletMode()) {

            case STANDARD:
              log.debug("Sending using soft wallet");
              emptyWallet();
              state = EMPTY_WALLET_REPORT;
              break;
            case TREZOR:
              log.debug("Sending using a Trezor hard wallet");
              state = EMPTY_WALLET_CONFIRM_HARDWARE;
              emptyWallet();
              break;
            case KEEP_KEY:
              log.debug("Sending using a KeepKey hard wallet");
              state = EMPTY_WALLET_CONFIRM_HARDWARE;
              emptyWallet();
              break;
            default:
              throw new IllegalStateException("Unknown hardware wallet: " + getWalletMode().name());
          }
        } else {
          log.debug("No wallet summary - cannot send");
        }

        break;

      case EMPTY_WALLET_ENTER_PIN_FROM_CONFIRM_HARDWARE:
        // Do nothing
        break;

      case EMPTY_WALLET_CONFIRM_HARDWARE:
        // Move to report
        state = EMPTY_WALLET_REPORT;
        break;

      default:
        // Do nothing

    }
  }

  @Override
  public void showPrevious() {

    switch (state) {
      case EMPTY_WALLET_ENTER_DETAILS:
        state = EMPTY_WALLET_ENTER_DETAILS;
        break;
      case EMPTY_WALLET_CONFIRM:
        state = EMPTY_WALLET_ENTER_DETAILS;
        break;
      default:
        throw new IllegalStateException("Unexpected state:" + state);
    }

  }

  @Override
  public String getPanelName() {
    return state.name();
  }

  /**
   * @return The recipient the user identified
   */
  public Recipient getRecipient() {
    return enterDetailsPanelModel
      .getEnterRecipientModel()
      .getRecipient().get();
  }

  /**
   * @return The credentials the user entered
   */
  public String getPassword() {
    return enterDetailsPanelModel.getEnterPasswordModel().getValue();
  }

  /**
   * @return the SendRequestSummary with the payment info in it
   */
  public SendRequestSummary getSendRequestSummary() {
    return sendRequestSummary;
  }

  /**
   * <p>Reduced visibility for panel models only</p>
   *
   * @param enterDetailsPanelModel The "enter details" panel model
   */
  void setEnterDetailsPanelModel(EmptyWalletEnterDetailsPanelModel enterDetailsPanelModel) {
    this.enterDetailsPanelModel = enterDetailsPanelModel;
  }

  /**
   * @return The current wallet balance in coins
   */
  public Optional<Coin> getCoinAmount() {
    return coinAmount;
  }

  /**
   * Prepare the transaction for sending - this does everything but sign the tx
   */
  private boolean prepareTransaction() {
    // Prepare the transaction for sending
    Preconditions.checkNotNull(enterDetailsPanelModel);

    // Ensure Bitcoin network service is started
    bitcoinNetworkService = CoreServices.getOrCreateBitcoinNetworkService();
    Preconditions.checkState(bitcoinNetworkService.isStartedOk(), "'bitcoinNetworkService' should be started");

    Address changeAddress = bitcoinNetworkService.getNextChangeAddress();

    Address bitcoinAddress = enterDetailsPanelModel
      .getEnterRecipientModel()
      .getRecipient()
      .get()
      .getBitcoinAddress();

    String password = enterDetailsPanelModel.getEnterPasswordModel().getValue();

    Optional<FeeState> feeState = WalletManager.INSTANCE.calculateBRITFeeState(true);
    log.debug("FeeState after initial calculation: {}", feeState);

    // Create the fiat payment - note that the fiat amount is not populated, only the exchange rate data.
    // This is because the client and transaction fee is only worked out at point of sending, and the fiat equivalent is computed from that
    Optional<FiatPayment> fiatPayment;
    Optional<ExchangeRateChangedEvent> exchangeRateChangedEvent = CoreServices.getApplicationEventService().getLatestExchangeRateChangedEvent();
    if (exchangeRateChangedEvent.isPresent()) {
      fiatPayment = Optional.of(new FiatPayment());
      fiatPayment.get().setRate(Optional.of(exchangeRateChangedEvent.get().getRate().toString()));
      // A send is denoted with a negative fiat amount
      fiatPayment.get().setAmount(Optional.<BigDecimal>absent());
      fiatPayment.get().setCurrency(Optional.of(exchangeRateChangedEvent.get().getCurrency()));
      fiatPayment.get().setExchangeName(Optional.of(ExchangeKey.current().getExchangeName()));
    } else {
      fiatPayment = Optional.absent();
    }

    // Configure for an empty wallet send request
    sendRequestSummary = new SendRequestSummary(
      bitcoinAddress,
      coinAmount.or(Coin.ZERO),
      fiatPayment,
      changeAddress,
      FeeService.normaliseRawFeePerKB(Configurations.currentConfiguration.getWallet().getFeePerKB()),
      password,
      feeState,
      true);

    // Set a tx description of 'Empty Wallet' localised
    sendRequestSummary.setNotes(Optional.of(Languages.safeText(MessageKey.EMPTY_WALLET_TITLE)));

    log.debug("Just about to prepare empty wallet transaction for sendRequestSummary: {}", sendRequestSummary);
    boolean preparedOk = bitcoinNetworkService.prepareTransaction(sendRequestSummary);
    log.debug("sendRequestSummary after prepareTransaction: {}", sendRequestSummary);

    // The amount to pay is now corrected for fees
    log.debug("Correcting amount to pay to cater for fees from {} to {}", coinAmount, sendRequestSummary.getAmount());
    coinAmount = Optional.of(sendRequestSummary.getAmount());
    return preparedOk;
  }

  /**
   * Actually send the transaction
   */
  private void emptyWallet() {
    log.debug("Emptying wallet with: {}", sendRequestSummary);
    Preconditions.checkState(bitcoinNetworkService.isStartedOk(), "'bitcoinNetworkService' should be started");
    bitcoinNetworkService.send(sendRequestSummary);

    // The send throws TransactionCreationEvents and BitcoinSentEvents to which you subscribe to to work out success and failure.
  }

  public void setEmptyWalletConfirmHardwarePanelView(EmptyWalletConfirmHardwarePanelView emptyWalletConfirmHardwarePanelView) {
    this.emptyWalletConfirmHardwarePanelView = emptyWalletConfirmHardwarePanelView;
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
      case EMPTY_WALLET_CONFIRM_HARDWARE:
        log.debug("Transaction signing is PIN protected");
        state = EmptyWalletState.EMPTY_WALLET_ENTER_PIN_FROM_CONFIRM_HARDWARE;
        break;
      default:
        throw new IllegalStateException("Unknown state: " + state.name());
    }
  }


  @Override
  public void showButtonPress(HardwareWalletEvent event) {

    log.debug("Received hardware event: '{}'.{}", event.getEventType().name(), event.getMessage());

    // Successful PIN entry or not required so transition to Trezor signing display view
    state = EMPTY_WALLET_CONFIRM_HARDWARE;

    BitcoinNetworkService bitcoinNetworkService = CoreServices.getOrCreateBitcoinNetworkService();

    // Update label with descriptive text matching what the Trezor is showing
    ButtonRequest buttonRequest = (ButtonRequest) event.getMessage().get();

    // General message is nothing
    MessageKey key = null;
    Object[] values = null;

    if (bitcoinNetworkService.getLastSendRequestSummaryOptional().isPresent() && bitcoinNetworkService.getLastWalletOptional().isPresent()) {

      // We have a send request and a wallet

      Wallet wallet = bitcoinNetworkService.getLastWalletOptional().get();

      BitcoinConfiguration bitcoinConfiguration = Configurations.currentConfiguration.getBitcoin();
      LanguageConfiguration languageConfiguration = Configurations.currentConfiguration.getLanguage();

      Optional<Transaction> currentTransactionOptional = CoreServices.getCurrentHardwareWalletService().get().getContext().getTransaction();
      if (currentTransactionOptional.isPresent()) {

        Transaction currentTransaction = currentTransactionOptional.get();
        // Substitute mBTC for MICON
        String bitcoinSymbolText = bitcoinConfiguration.getBitcoinSymbol();
        if (BitcoinSymbol.MICON.toString().equals(bitcoinSymbolText)) {
          bitcoinSymbolText = BitcoinSymbol.MBTC.getSymbol();
        }

        String[] transactionAmountFormatted;
        String[] feeAmount;

        switch (buttonRequest.getButtonRequestType()) {
          case FEE_OVER_THRESHOLD:
            // Avoid an accidental high fee by detecting > 10,000 satoshi fee rate
            feeAmount = Formats.formatCoinAsSymbolic(currentTransaction.getFee(), languageConfiguration, bitcoinConfiguration);

            // Select the display message
            switch (getWalletMode()) {
              case TREZOR:
                key = MessageKey.TREZOR_HIGH_FEE_CONFIRM_DISPLAY;
                break;
              case KEEP_KEY:
                key = MessageKey.KEEP_KEY_HIGH_FEE_CONFIRM_DISPLAY;
                break;
              default:
                throw new IllegalStateException("Unknown hardware wallet: " + getWalletMode().name());
            }

            values = new String[]{feeAmount[0] + feeAmount[1] + " " + bitcoinSymbolText};
            break;
          case CONFIRM_OUTPUT:

            // Work out which output we're confirming (will be in same order as tx but wallet addresses will be ignored)

            Optional<TransactionOutput> confirmingOutput = Optional.absent();
            do {
              // Always increment from starting position (first button request is then 0 index)
              txOutputIndex++;

              if (!currentTransaction.getOutput(txOutputIndex).isMine(wallet)) {
                // Not owned by us so Trezor will show it on the display
                confirmingOutput = Optional.of(currentTransaction.getOutput(txOutputIndex));
                break;
              }
            } while (txOutputIndex < currentTransaction.getOutputs().size());

            if (confirmingOutput.isPresent()) {

              // Trezor will be displaying this output
              TransactionOutput output = confirmingOutput.get();

              String[] transactionOutputAmount = Formats.formatCoinAsSymbolic(output.getValue(), languageConfiguration, bitcoinConfiguration);

              // P2PKH are the most common addresses so try that first
              Address transactionOutputAddress = output.getAddressFromP2PKHScript(MainNetParams.get());
              if (transactionOutputAddress == null) {
                  // Fall back to P2SH
                  transactionOutputAddress = output.getAddressFromP2SH(MainNetParams.get());
              }

              // Select the display message
              switch (getWalletMode()) {
                case TREZOR:
                  key = MessageKey.TREZOR_TRANSACTION_OUTPUT_CONFIRM_DISPLAY;
                  break;
                case KEEP_KEY:
                  key = MessageKey.KEEP_KEY_TRANSACTION_OUTPUT_CONFIRM_DISPLAY;
                  break;
                default:
                  throw new IllegalStateException("Unknown hardware wallet: " + getWalletMode().name());
              }

              // Amount, address
              values = new String[]{
                transactionOutputAmount[0] + transactionOutputAmount[1] + " " + bitcoinSymbolText,
                transactionOutputAddress == null ? "" : transactionOutputAddress.toString()
              };

            } else {
              throw new IllegalStateException("Trezor is confirming an output outside of the transaction. Have change addresses been ignored?");
            }
            break;
          case SIGN_TX:
            // Transaction#getValue() provides the net amount leaving the wallet which includes the fee
            // See #499: Trezor firmware below 1.3.3 displays the sum of all external outputs (including fee) and the fee separately
            // From 1.3.3+ the display is the net amount leaving the wallet with fees shown separately
            Coin transactionAmount = currentTransaction.getValue(wallet).negate();
            transactionAmountFormatted = Formats.formatCoinAsSymbolic(transactionAmount, languageConfiguration, bitcoinConfiguration);
            feeAmount = Formats.formatCoinAsSymbolic(currentTransaction.getFee(), languageConfiguration, bitcoinConfiguration);

            // Select the display message
            switch (getWalletMode()) {
              case TREZOR:
                key = MessageKey.TREZOR_SIGN_CONFIRM_DISPLAY;
                break;
              case KEEP_KEY:
                key = MessageKey.KEEP_KEY_SIGN_CONFIRM_DISPLAY;
                break;
              default:
                throw new IllegalStateException("Unknown hardware wallet: " + getWalletMode().name());
            }

            values = new String[]{
              transactionAmountFormatted[0] + transactionAmountFormatted[1] + " " + bitcoinSymbolText,
              feeAmount[0] + feeAmount[1] + " " + bitcoinSymbolText
            };
            break;
          default:

        }
      }
    }

    emptyWalletConfirmHardwarePanelView.setDisplayText(key, values);

  }

  @Override
  public void showOperationSucceeded(HardwareWalletEvent event) {

    if (state == EMPTY_WALLET_ENTER_PIN_FROM_CONFIRM_HARDWARE) {
      // Indicate a successful PIN
      getEnterPinPanelView().setPinStatus(true, true);
      return;
    }

    // Must be showing signing Trezor display

    // Enable next button
    ViewEvents.fireWizardButtonEnabledEvent(
      getPanelName(),
      WizardButton.NEXT,
      true
    );

    SwingUtilities.invokeLater(
      new Runnable() {
        @Override
        public void run() {

          // The tx is now complete so commit and broadcast it
          // Trezor will provide a signed serialized transaction
          byte[] deviceTxPayload = CoreServices.getCurrentHardwareWalletService().get().getContext().getSerializedTx().toByteArray();

          log.info("DeviceTx payload:\n{}", Utils.HEX.encode(deviceTxPayload));

          // Load deviceTx
          Transaction deviceTx = new Transaction(MainNetParams.get(), deviceTxPayload);

          log.info("deviceTx:\n{}", deviceTx.toString());

          // Check the signatures are canonical
          for (TransactionInput txInput : deviceTx.getInputs()) {
            byte[] signature = txInput.getScriptSig().getChunks().get(0).data;
            if (signature != null) {
              log.debug(
                "Is signature canonical test result '{}' for txInput '{}', signature '{}'",
                TransactionSignature.isEncodingCanonical(signature),
                txInput.toString(),
                Utils.HEX.encode(signature));
            } else {
              log.warn("No signature data");
            }
          }

          log.debug("Committing and broadcasting the last tx");

          BitcoinNetworkService bitcoinNetworkService = CoreServices.getOrCreateBitcoinNetworkService();

          if (bitcoinNetworkService.getLastSendRequestSummaryOptional().isPresent() && bitcoinNetworkService.getLastWalletOptional().isPresent()) {

            SendRequestSummary sendRequestSummary = bitcoinNetworkService.getLastSendRequestSummaryOptional().get();

            // Substitute the signed tx from the trezor
            log.debug("Substituting the Trezor signed tx '{}' for the unsigned version {}", deviceTx.toString(), sendRequestSummary.getSendRequest().get().tx.toString());
            sendRequestSummary.getSendRequest().get().tx = deviceTx;
            log.debug("The transaction fee was {}", sendRequestSummary.getSendRequest().get().fee);

            // Get the last wallet
            Wallet wallet = bitcoinNetworkService.getLastWalletOptional().get();

            // Clear the previous remembered tx so that it is not committed twice
            bitcoinNetworkService.setLastSendRequestSummaryOptional(Optional.<SendRequestSummary>absent());
            bitcoinNetworkService.setLastWalletOptional(Optional.<Wallet>absent());

            emptyWalletConfirmHardwarePanelView.setOperationText(MessageKey.HARDWARE_TRANSACTION_CREATED_OPERATION);
            emptyWalletConfirmHardwarePanelView.setRecoveryText(MessageKey.CLICK_NEXT_TO_CONTINUE);
            emptyWalletConfirmHardwarePanelView.setDisplayVisible(false);

            bitcoinNetworkService.commitAndBroadcast(sendRequestSummary, wallet, Optional.<PaymentRequestData>absent());

          } else {
            log.debug("Cannot commit and broadcast the last send as it is not present in bitcoinNetworkService");
          }

        }
      });

  }

  @Override
  public void showOperationFailed(HardwareWalletEvent event) {
    switch (state) {
      case EMPTY_WALLET_ENTER_PIN_FROM_CONFIRM_HARDWARE:
        state = EmptyWalletState.EMPTY_WALLET_REPORT;
        setReportMessageKey(MessageKey.HARDWARE_INCORRECT_PIN_FAILURE);
        setReportMessageStatus(false);
        requestCancel();
        break;
      default:
        state = EmptyWalletState.EMPTY_WALLET_REPORT;
        setReportMessageKey(MessageKey.HARDWARE_SIGN_FAILURE);
        setReportMessageStatus(false);
        requestCancel();
        break;
    }

    // Ignore device reset messages
    ApplicationEventService.setIgnoreHardwareWalletEventsThreshold(Dates.nowUtc().plusSeconds(1));

  }

  public void setEnterPinPanelView(EmptyWalletEnterPinPanelView enterPinPanelView) {
    this.enterPinPanelView = enterPinPanelView;
  }

  public EmptyWalletEnterPinPanelView getEnterPinPanelView() {
    return enterPinPanelView;
  }

}
