package org.multibit.hd.ui.views.wizards.send_bitcoin;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import net.miginfocom.swing.MigLayout;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.uri.BitcoinURI;
import org.multibit.hd.brit.core.dto.FeeState;
import org.multibit.hd.brit.core.services.FeeService;
import org.multibit.hd.core.config.Configuration;
import org.multibit.hd.core.config.Configurations;
import org.multibit.hd.core.dto.PaymentRequestData;
import org.multibit.hd.core.dto.WalletType;
import org.multibit.hd.core.managers.WalletManager;
import org.multibit.hd.ui.events.view.ViewEvents;
import org.multibit.hd.ui.languages.Languages;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.*;
import org.multibit.hd.ui.views.components.display_amount.DisplayAmountModel;
import org.multibit.hd.ui.views.components.display_amount.DisplayAmountStyle;
import org.multibit.hd.ui.views.components.display_amount.DisplayAmountView;
import org.multibit.hd.ui.views.components.enter_password.EnterPasswordModel;
import org.multibit.hd.ui.views.components.enter_password.EnterPasswordView;
import org.multibit.hd.ui.views.components.panels.PanelDecorator;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;
import org.multibit.hd.ui.views.wizards.AbstractWizard;
import org.multibit.hd.ui.views.wizards.AbstractWizardPanelView;
import org.multibit.hd.ui.views.wizards.WizardButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * <p>View to provide the following to UI:</p>
 * <ul>
 * <li>Send bitcoin: Confirm</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class SendBitcoinConfirmPanelView extends AbstractWizardPanelView<SendBitcoinWizardModel, SendBitcoinConfirmPanelModel> {

  private static final Logger log = LoggerFactory.getLogger(SendBitcoinConfirmPanelView.class);

  // View components
  private JTextArea notesTextArea;

  private ModelAndView<DisplayAmountModel, DisplayAmountView> transactionDisplayAmountMaV;
  private ModelAndView<DisplayAmountModel, DisplayAmountView> transactionFeeDisplayAmountMaV;
  private ModelAndView<DisplayAmountModel, DisplayAmountView> clientFeeDisplayAmountMaV;
  private ModelAndView<DisplayAmountModel, DisplayAmountView> runningTotalClientFeeDisplayAmountMaV;
  private ModelAndView<EnterPasswordModel, EnterPasswordView> enterPasswordMaV;

  private JLabel recipientSummaryLabel;

  private JLabel clientFeeInfoLabel;
  private JLabel runningTotalClientFeeInfoLabel;

  private SendBitcoinConfirmPanelModel panelModel;

  /**
   * @param wizard    The wizard managing the states
   * @param panelName The panel name for filtering component events
   */
  public SendBitcoinConfirmPanelView(AbstractWizard<SendBitcoinWizardModel> wizard, String panelName) {
    super(wizard, panelName, AwesomeIcon.CLOUD_UPLOAD, MessageKey.CONFIRM_SEND_TITLE);
  }

  @Override
  public void newPanelModel() {
    // Require a reference for the model
    enterPasswordMaV = Components.newEnterPasswordMaV(getPanelName());

    // Configure the panel model
    panelModel = new SendBitcoinConfirmPanelModel(
      getPanelName(),
      enterPasswordMaV.getModel()
    );
    setPanelModel(panelModel);

    // Bind it to the wizard model
    getWizardModel().setConfirmPanelModel(panelModel);

    // Register components
    registerComponents(enterPasswordMaV);
  }

  @Override
  public void initialiseContent(JPanel contentPanel) {

    // Transaction information
    transactionDisplayAmountMaV = Components.newDisplayAmountMaV(
      DisplayAmountStyle.TRANSACTION_DETAIL_AMOUNT,
      true,
      SendBitcoinState.SEND_CONFIRM_AMOUNT.name() + ".transaction"
    );
    // Ensure local amount on main amount is visible
    transactionDisplayAmountMaV.getModel().setLocalAmountVisible(true);

    transactionFeeDisplayAmountMaV = Components.newDisplayAmountMaV(
      DisplayAmountStyle.FEE_AMOUNT,
      true,
      SendBitcoinState.SEND_CONFIRM_AMOUNT.name() + ".transaction_fee"
    );
    clientFeeDisplayAmountMaV = Components.newDisplayAmountMaV(
      DisplayAmountStyle.FEE_AMOUNT,
      true,
      SendBitcoinState.SEND_CONFIRM_AMOUNT.name() + ".client_fee"
    );
    runningTotalClientFeeDisplayAmountMaV = Components.newDisplayAmountMaV(
      DisplayAmountStyle.PLAIN,
      true,
      SendBitcoinState.SEND_CONFIRM_AMOUNT.name() + ".running_total_client_fee"
    );
    // Ensure visibility
    transactionDisplayAmountMaV.getView().setVisible(true);
    transactionFeeDisplayAmountMaV.getView().setVisible(true);
    clientFeeDisplayAmountMaV.getView().setVisible(true);
    runningTotalClientFeeDisplayAmountMaV.getView().setVisible(true);

    // User entered text
    notesTextArea = TextBoxes.newEnterPrivateNotes(getWizardModel());

    // Apply any Payment Request parameters
    if (getWizardModel().getPaymentRequestData().isPresent()) {
      PaymentRequestData paymentRequestData = getWizardModel().getPaymentRequestData().get();

      if (paymentRequestData.getIdentityDisplayName().isEmpty()) {
        // Unknown recipient
        recipientSummaryLabel = Labels.newValueLabel(Languages.safeText(MessageKey.NOT_AVAILABLE));
      } else {
        recipientSummaryLabel = Labels.newValueLabel(paymentRequestData.getIdentityDisplayName());
      }

      // Fill in the memo
      notesTextArea.setText(paymentRequestData.getNote());
    } else {
      recipientSummaryLabel = Labels.newRecipientSummary(getWizardModel().getRecipient());
    }

    // Apply any Bitcoin URI parameters
    if (getWizardModel().getBitcoinURI().isPresent()) {
      BitcoinURI uri = getWizardModel().getBitcoinURI().get();
      String notes = "";
      if (!Strings.isNullOrEmpty(uri.getLabel())) {
        // We have a label
        notes += uri.getLabel();
      }
      if (!Strings.isNullOrEmpty(uri.getMessage())) {
        // We have a message
        if (!Strings.isNullOrEmpty(notes)) {
          notes += "\n";
        }
        notes += uri.getMessage();
      }
      notesTextArea.setText(notes);
    }

    contentPanel.setLayout(
      new MigLayout(
        Panels.migXYLayout(),
        "[]10[]4[]0[200]10[120]10[][]", // Column constraints
        "[]10[]10[][][][][][]10[][]" // Row constraints
      ));

    clientFeeInfoLabel = Labels.newBlankLabel();
    AccessibilityDecorator.apply(clientFeeInfoLabel, MessageKey.CLIENT_FEE);

    runningTotalClientFeeInfoLabel = Labels.newClientFeeRunningTotal();

    contentPanel.add(Labels.newConfirmSendAmount(), "span 7,push,wrap");

    contentPanel.add(Labels.newRecipient());
    contentPanel.add(recipientSummaryLabel, "span 6,wrap");

    contentPanel.add(Labels.newAmount(), "baseline");
    contentPanel.add(transactionDisplayAmountMaV.getView().newComponentPanel(), "span 6,wrap");

    contentPanel.add(Labels.newTransactionFee(), "top");
    contentPanel.add(transactionFeeDisplayAmountMaV.getView().newComponentPanel(), "span 6,wrap");

    contentPanel.add(Labels.newBlankLabel(), "top");
    contentPanel.add(Panels.newHorizontalDashedSeparator(), "push, growx, span 6,wrap");

    contentPanel.add(Labels.newClientFee(), "top");
    contentPanel.add(clientFeeDisplayAmountMaV.getView().newComponentPanel(), "top, shrink");

    contentPanel.add(Labels.newBlankLabel(), "top, growx, span 2, push");
    contentPanel.add(clientFeeInfoLabel, "top, span 3, wrap");

    contentPanel.add(Labels.newBlankLabel(), "top, span 4");
    contentPanel.add(runningTotalClientFeeInfoLabel, "top, shrink");
    contentPanel.add(runningTotalClientFeeDisplayAmountMaV.getView().newComponentPanel(), "top, shrink, align left");
    contentPanel.add(Labels.newBlankLabel(), "top, growx, push, wrap");

    contentPanel.add(Labels.newNotes());
    contentPanel.add(notesTextArea, "span 6,growx,push,wrap");

    if (!isHardwareWallet()) {
      contentPanel.add(enterPasswordMaV.getView().newComponentPanel(), "span 7,align right,wrap");
    }

    // Register components
    registerComponents(transactionDisplayAmountMaV, transactionFeeDisplayAmountMaV, clientFeeDisplayAmountMaV, runningTotalClientFeeDisplayAmountMaV);
  }

  @Override
  protected void initialiseButtons(AbstractWizard<SendBitcoinWizardModel> wizard) {
    PanelDecorator.addCancelPreviousSend(this, wizard);
  }

  @Override
  public void fireInitialStateViewEvents() {
    // Send button starts off disabled for regular wallets, enabled for Trezor hard wallets
    ViewEvents.fireWizardButtonEnabledEvent(getPanelName(), WizardButton.NEXT, isHardwareWallet());
  }

  /**
   * @return True if this is a hard wallet
   */
  private boolean isHardwareWallet() {

    return WalletManager.INSTANCE.getCurrentWalletSummary().isPresent() &&
            WalletType.TREZOR_HARD_WALLET.equals(WalletManager.INSTANCE.getCurrentWalletSummary().get().getWalletType())
            ;
  }

  @Override
  public boolean beforeShow() {
    Configuration configuration = Configurations.currentConfiguration;

    final Coin amount;
    if (getWizardModel().getPaymentRequestData().isPresent()) {
      // User has received a BIP70 Payment Request
      PaymentRequestData paymentRequestData = getWizardModel().getPaymentRequestData().get();
      if (paymentRequestData.getIdentityDisplayName().isEmpty()) {
        // Unknown recipient
        recipientSummaryLabel = Labels.newValueLabel(Languages.safeText(MessageKey.NOT_AVAILABLE));
      } else {
        recipientSummaryLabel = Labels.newValueLabel(paymentRequestData.getIdentityDisplayName());
      }
      amount = paymentRequestData.getAmountCoin().or(Coin.ZERO);


      // TODO check PKI
//      try {
//        PaymentSession paymentSession = new PaymentSession(paymentRequestData.getPaymentRequest(), false);
//        Wallet.SendRequest sendRequest = paymentSession.getSendRequest();
//
//        transactionFeeDisplayAmountMaV.getModel().setCoinAmount(sendRequest.fee);
//      } catch (PaymentProtocolException e) {
//        e.printStackTrace();
//      }
    } else {
      // Regular send with data entered by the user
      amount = getWizardModel().getCoinAmount().or(Coin.ZERO);

      // Update the model and view for the recipient
      recipientSummaryLabel.setText(
        getWizardModel()
          .getRecipient()
          .getSummary()
      );
    }

    // Update the model and view for the transaction fee - by this point the prepareTransaction will have been called by either:
    // (for regular sends) the SendBitcoinWizardModel#showNext
    // (for BIP70 payment requests)or the SendBitcoinWizardModel#prepareWhenBIP70
    Optional<Wallet.SendRequest> sendRequest = getWizardModel().getSendRequestSummary().getSendRequest();
    if (sendRequest.isPresent()) {
      transactionFeeDisplayAmountMaV.getModel().setCoinAmount(sendRequest.get().fee);
    } else {
      log.error("No send request - hence cannot set a tx fee");
    }
    if (getWizardModel().getLocalAmount().isPresent()) {
      transactionDisplayAmountMaV.getModel().setLocalAmount(getWizardModel().getLocalAmount().get());
    } else {
      transactionDisplayAmountMaV.getModel().setLocalAmount(null);
    }

    // Update the model and view for the amount
    Preconditions.checkNotNull(amount);
    transactionDisplayAmountMaV.getModel().setCoinAmount(amount);
    transactionDisplayAmountMaV.getView().updateView(configuration);

    transactionFeeDisplayAmountMaV.getModel().setLocalAmountVisible(false);
    transactionFeeDisplayAmountMaV.getView().updateView(configuration);

    // Update the model and view for the client fee
    Optional<FeeState> feeStateOptional = WalletManager.INSTANCE.calculateBRITFeeState(true);
    String feeText;
    if (feeStateOptional.isPresent()) {
      FeeState feeState = feeStateOptional.get();

      // The amount of fee to show - if paid later the singular client fee, if paid now, the total payable
      Coin feeToShow = FeeService.FEE_PER_SEND;
      boolean showRunningTotal = false;

      if (feeState.getCurrentNumberOfSends() == feeState.getNextFeeSendCount()) {
        // The fee is due at the next send e.g. current number of sends = 20, nextFeeSendCount = 20 (the 21st send i.e. the coming one)
        feeText = Languages.safeText(MessageKey.CLIENT_FEE_NOW);
        feeToShow = feeState.getFeeOwed();
      } else if (feeState.getFeeOwed().compareTo(Coin.ZERO) < 0) {
        // The user has overpaid
        feeText = Languages.safeText(MessageKey.CLIENT_FEE_OVERPAID);
      } else {
        // It is due later
        int dueLater = feeState.getNextFeeSendCount() - feeState.getCurrentNumberOfSends();
        if (dueLater == 1) {
          feeText = Languages.safeText(MessageKey.CLIENT_FEE_LATER_SINGULAR);
        } else {
          feeText = Languages.safeText(MessageKey.CLIENT_FEE_LATER_PLURAL, dueLater);
        }
        runningTotalClientFeeDisplayAmountMaV.getModel().setCoinAmount(feeState.getFeeOwed());
        runningTotalClientFeeDisplayAmountMaV.getModel().setLocalAmountVisible(false);
        runningTotalClientFeeDisplayAmountMaV.getView().updateView(configuration);
        showRunningTotal = true;
      }

      clientFeeDisplayAmountMaV.getModel().setCoinAmount(feeToShow);
      clientFeeDisplayAmountMaV.getModel().setLocalAmountVisible(false);
      clientFeeDisplayAmountMaV.getView().updateView(configuration);

      runningTotalClientFeeDisplayAmountMaV.getView().setVisible(showRunningTotal);
      runningTotalClientFeeInfoLabel.setVisible(showRunningTotal);

    } else {
      // Possibly no wallet loaded
      feeText = "";
    }

    clientFeeInfoLabel.setText(feeText);

    return true;
  }

  @Override
  public void afterShow() {
    notesTextArea.requestFocusInWindow();
  }

  @Override
  public void updateFromComponentModels(Optional componentModel) {
    panelModel.setNotes(notesTextArea.getText());

    // Determine any events
    ViewEvents.fireWizardButtonEnabledEvent(
      getPanelName(),
      WizardButton.NEXT,
      isNextEnabled()
    );
  }

  /**
   * @return True if the "next" button should be enabled
   */
  private boolean isNextEnabled() {
    return !Strings.isNullOrEmpty(getPanelModel().get().getPasswordModel().getValue());
  }
}