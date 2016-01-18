package org.multibit.hd.ui.views.wizards.send_bitcoin;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import net.miginfocom.swing.MigLayout;
import org.bitcoinj.core.Coin;
import org.multibit.commons.utils.Dates;
import org.multibit.hd.core.config.Configuration;
import org.multibit.hd.core.config.Configurations;
import org.multibit.hd.core.dto.PaymentRequestData;
import org.multibit.hd.ui.MultiBitUI;
import org.multibit.hd.ui.events.view.ViewEvents;
import org.multibit.hd.ui.languages.Languages;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.*;
import org.multibit.hd.ui.views.components.display_amount.DisplayAmountModel;
import org.multibit.hd.ui.views.components.display_amount.DisplayAmountStyle;
import org.multibit.hd.ui.views.components.display_amount.DisplayAmountView;
import org.multibit.hd.ui.views.components.panels.PanelDecorator;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;
import org.multibit.hd.ui.views.themes.Themes;
import org.multibit.hd.ui.views.wizards.AbstractWizard;
import org.multibit.hd.ui.views.wizards.AbstractWizardPanelView;
import org.multibit.hd.ui.views.wizards.WizardButton;

import javax.swing.*;

/**
 * <p>View to provide the following to UI:</p>
 * <ul>
 * <li>Send bitcoin: Display payment request</li>
 * </ul>
 *
 * @since 0.0.8
 */

public class SendBitcoinDisplayPaymentRequestPanelView extends AbstractWizardPanelView<SendBitcoinWizardModel, SendBitcoinDisplayPaymentRequestPanelModel> {

  // Panel specific components
  private ModelAndView<DisplayAmountModel, DisplayAmountView> paymentRequestAmountMaV;

  private JLabel trustStatusLabel;
  private JLabel memo;
  private JLabel displayName;
  private JLabel date;
  private JLabel expires;

  /**
   * @param wizard    The wizard managing the states
   * @param panelName The panel name
   */
  public SendBitcoinDisplayPaymentRequestPanelView(AbstractWizard<SendBitcoinWizardModel> wizard, String panelName) {

    super(wizard, panelName, AwesomeIcon.MONEY, MessageKey.DISPLAY_PAYMENT_REQUEST_TITLE);

  }

  @Override
  public void newPanelModel() {

    // Configure the panel model
    final SendBitcoinDisplayPaymentRequestPanelModel panelModel = new SendBitcoinDisplayPaymentRequestPanelModel(getPanelName());
    setPanelModel(panelModel);
  }

  @Override
  public void initialiseContent(JPanel contentPanel) {

    contentPanel.setLayout(
      new MigLayout(
        Panels.migXYLayout(),
        "[][]", // Column constraints
        "[][][][][][]" // Row constraints
      ));

    // Apply the theme
    contentPanel.setBackground(Themes.currentTheme.detailPanelBackground());

    // Payment request amount
    paymentRequestAmountMaV = Components.newDisplayAmountMaV(
      DisplayAmountStyle.TRANSACTION_DETAIL_AMOUNT,
      true,
      SendBitcoinState.SEND_DISPLAY_PAYMENT_REQUEST.name() + ".amount"
    );
    // The amount is charged in Bitcoin so ignore local amount
    paymentRequestAmountMaV.getModel().setLocalAmountVisible(false);
    paymentRequestAmountMaV.getView().setVisible(true);

    // Populate value labels
    memo = Labels.newValueLabel(Languages.safeText(MessageKey.NOT_AVAILABLE));
    memo.setName(MessageKey.NOTES.getKey() + ".value");

    date = Labels.newValueLabel(Languages.safeText(MessageKey.NOT_AVAILABLE));
    date.setName(MessageKey.DATE.getKey() + ".value");

    expires = Labels.newValueLabel(Languages.safeText(MessageKey.NOT_AVAILABLE));
    expires.setName(MessageKey.EXPIRES.getKey() + ".value");

    displayName = Labels.newValueLabel(Languages.safeText(MessageKey.NOT_AVAILABLE));
    displayName.setName(MessageKey.NAME.getKey() + ".value");

    trustStatusLabel = Labels.newStatusLabel(Optional.<MessageKey>absent(), null, Optional.<Boolean>absent());
    trustStatusLabel.setName("trust_status");
    contentPanel.add(trustStatusLabel, "span 2,aligny top,wrap");

    contentPanel.add(Labels.newMemoLabel(), "shrink");
    contentPanel.add(memo, "shrink," + MultiBitUI.WIZARD_MAX_WIDTH_MIG + ",wrap");

    contentPanel.add(Labels.newDisplayNameLabel(), "shrink");
    contentPanel.add(displayName, "shrink," + MultiBitUI.WIZARD_MAX_WIDTH_MIG + ",wrap");

    contentPanel.add(Labels.newDateLabel(), "shrink");
    contentPanel.add(date, "shrink," + MultiBitUI.WIZARD_MAX_WIDTH_MIG + ",wrap");

    contentPanel.add(Labels.newExpiresLabel(), "shrink");
    contentPanel.add(expires, "shrink," + MultiBitUI.WIZARD_MAX_WIDTH_MIG + ",wrap");

    contentPanel.add(Labels.newAmount(), "baseline");
    contentPanel.add(paymentRequestAmountMaV.getView().newComponentPanel(), "span 4,wrap");

    // Register components
    registerComponents(paymentRequestAmountMaV);

  }

  @Override
  protected void initialiseButtons(AbstractWizard<SendBitcoinWizardModel> wizard) {
    PanelDecorator.addExitCancelNext(this, wizard);
  }

  @Override
  public void afterShow() {

    // Fail fast
    Preconditions.checkState(getWizardModel().getPaymentRequestData().isPresent(), "'paymentRequestData' must be present");

    PaymentRequestData paymentRequestData = getWizardModel().getPaymentRequestData().get();

    switch (paymentRequestData.getTrustStatus()) {
      case TRUSTED:
        LabelDecorator.applyPaymentSessionStatusIcon(
          paymentRequestData.getTrustStatus(),
          trustStatusLabel,
          MessageKey.PAYMENT_PROTOCOL_TRUSTED_NOTE,
          MultiBitUI.NORMAL_ICON_SIZE);
        break;
      case UNTRUSTED:
        LabelDecorator.applyPaymentSessionStatusIcon(
          paymentRequestData.getTrustStatus(),
          trustStatusLabel,
          MessageKey.PAYMENT_PROTOCOL_UNTRUSTED_NOTE,
          MultiBitUI.NORMAL_ICON_SIZE);
        break;
      case DOWN:
      case ERROR:
        // Provide more details on the failure
        LabelDecorator.applyPaymentSessionStatusIcon(
          paymentRequestData.getTrustStatus(),
          trustStatusLabel,
          MessageKey.PAYMENT_PROTOCOL_ERROR_NOTE,
          MultiBitUI.NORMAL_ICON_SIZE);
        memo.setText(paymentRequestData.getTrustErrorMessage());
        displayName.setVisible(false);
        date.setVisible(false);
        expires.setVisible(false);
        paymentRequestAmountMaV.getView().setVisible(false);
        return;
      default:
        throw new IllegalStateException("Unknown trust status: " + paymentRequestData.getTrustStatus());
    }

    memo.setText(paymentRequestData.getNote());

    date.setText(Dates.formatTransactionDateLocal(paymentRequestData.getDate()));

    if (paymentRequestData.getExpirationDate() == null) {
      expires.setText(Languages.safeText(MessageKey.NOT_AVAILABLE));
    } else {
      expires.setText(Dates.formatTransactionDateLocal(paymentRequestData.getExpirationDate()));
      // TODO Handle display of expiry and button control
//            if (expiresDate.isBeforeNow()) {
//              // This payment request has expired
//            } else {
//            }
    }

    // Update the model and view for the amount
    // (no local in case of different exchange rates causing confusion)
    Configuration configuration = Configurations.currentConfiguration;
    paymentRequestAmountMaV.getModel().setCoinAmount(paymentRequestData.getAmountCoin().or(Coin.ZERO));
    paymentRequestAmountMaV.getModel().setLocalAmount(null);
    paymentRequestAmountMaV.getView().updateView(configuration);

    if (paymentRequestData.getIdentityDisplayName() != null) {
      displayName.setText(paymentRequestData.getIdentityDisplayName());
    } else {
      displayName.setText(Languages.safeText(MessageKey.NOT_AVAILABLE));
    }

    // Ensure the next button is enabled
    ViewEvents.fireWizardButtonEnabledEvent(getPanelName(), WizardButton.NEXT, true);

  }

  @Override
  public void updateFromComponentModels(Optional componentModel) {
    // Do nothing - panel model is updated via an action and wizard model is not applicable
  }

}

