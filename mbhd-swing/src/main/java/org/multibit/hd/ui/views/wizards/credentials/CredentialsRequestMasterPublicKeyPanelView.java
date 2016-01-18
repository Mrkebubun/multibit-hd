package org.multibit.hd.ui.views.wizards.credentials;

import com.google.common.base.Optional;
import net.miginfocom.swing.MigLayout;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.hardware.core.messages.Features;
import org.multibit.hd.ui.events.view.ViewEvents;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.Panels;
import org.multibit.hd.ui.views.components.panels.PanelDecorator;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;
import org.multibit.hd.ui.views.wizards.AbstractHardwareWalletWizard;
import org.multibit.hd.ui.views.wizards.AbstractHardwareWalletWizardPanelView;
import org.multibit.hd.ui.views.wizards.AbstractWizard;
import org.multibit.hd.ui.views.wizards.WizardButton;

import javax.swing.*;

/**
 * <p>View to provide the following to UI:</p>
 * <ul>
 * <li>Credentials: Request master public key</li>
 * </ul>
 * <p>This is the first step in getting the extended public key from a Trezor device</p>
 *
 * @since 0.0.1
 *  
 */
public class CredentialsRequestMasterPublicKeyPanelView extends AbstractHardwareWalletWizardPanelView<CredentialsWizardModel, String> {

  /**
   * @param wizard The wizard managing the states
   */
  public CredentialsRequestMasterPublicKeyPanelView(AbstractHardwareWalletWizard<CredentialsWizardModel> wizard, String panelName) {

    super(wizard, panelName, AwesomeIcon.LOCK, MessageKey.HARDWARE_UNLOCK_TITLE, wizard.getWizardModel().getWalletMode().brand());

  }

  @Override
  public void newPanelModel() {

    // Do nothing
  }

  @Override
  public void initialiseContent(JPanel contentPanel) {

    contentPanel.setLayout(
      new MigLayout(
        Panels.migXLayout(),
        "[]", // Column constraints
        "[]" // Row constraints
      ));

    addCurrentHardwareDisplay(contentPanel);

  }

  @Override
  protected void initialiseButtons(AbstractWizard<CredentialsWizardModel> wizard) {

    // If "next" is clicked on this screen we want to trigger a hide
    // the subsequent event will trigger the handover to the welcome wizard
    // in MainController
    PanelDecorator.addExitCancelNextAsFinish(this, wizard);

  }

  @Override
  public void fireInitialStateViewEvents() {

    // Initialise with "Next" disabled to force users to work with Trezor
    ViewEvents.fireWizardButtonEnabledEvent(
      getPanelName(),
      WizardButton.NEXT,
      false
    );

  }

  @Override
  public void afterShow() {

    // Check if the attached hardware is initialised (the hardware wallet service must be OK to be here)
    Optional<Features> features = CoreServices.getCurrentHardwareWalletService().get().getContext().getFeatures();

    final MessageKey operationKey;
    final boolean nextEnabled;
    final boolean createNewTrezorWallet;
    if (!features.isPresent()) {
      operationKey = MessageKey.HARDWARE_FAILURE_OPERATION;
      nextEnabled = true;
      createNewTrezorWallet = false;
    } else {
      if (features.get().isInitialized()) {
        operationKey = MessageKey.COMMUNICATING_WITH_HARDWARE_OPERATION;
        // May take some time
        nextEnabled = false;
        createNewTrezorWallet = false;
      } else {
        operationKey = MessageKey.HARDWARE_NO_WALLET_OPERATION;
        nextEnabled = true;

        // Tell user that there is no wallet on the device and that they can create a wallet by clicking next
        createNewTrezorWallet = true;
      }
    }

    // Set the communication message
    hardwareDisplayMaV.getView().setOperationText(operationKey, getWizardModel().getWalletMode().brand());

    if (nextEnabled) {
      if (createNewTrezorWallet) {
        hardwareDisplayMaV.getView().setRecoveryText(MessageKey.HARDWARE_NO_WALLET_RECOVERY, getWizardModel().getWalletMode().brand());
      } else {
        hardwareDisplayMaV.getView().setRecoveryText(MessageKey.HARDWARE_FAILURE_RECOVERY, getWizardModel().getWalletMode().brand());
      }
    }

    // No spinner on a failure
    hardwareDisplayMaV.getView().setSpinnerVisible(!nextEnabled);

    // Override the earlier button enable setting so we can perform "next as finish"
    ViewEvents.fireWizardButtonEnabledEvent(
      getPanelName(),
      WizardButton.NEXT,
      nextEnabled
    );

    // Update the wizard model so we can change state
    getWizardModel().setSwitchToPassword(nextEnabled && !createNewTrezorWallet);
    getWizardModel().setCreateNewTrezorWallet(createNewTrezorWallet);

    if (!nextEnabled) {

      // Start the wallet access process by requesting a cipher key
      // to get a deterministic wallet ID
      //
      // This is done as a transitional panel to allow for a device
      // failure at each stage with the user having the option to
      // easily escape
      getWizardModel().requestRootNode();
    }
  }

  @Override
  public void updateFromComponentModels(Optional componentModel) {

    // Do nothing we are a transitional view

  }

  /**
   * @param key The key to the operation text
   */
  public void setOperationText(MessageKey key) {
    this.hardwareDisplayMaV.getView().setOperationText(key, getWizardModel().getWalletMode().brand());
  }

}