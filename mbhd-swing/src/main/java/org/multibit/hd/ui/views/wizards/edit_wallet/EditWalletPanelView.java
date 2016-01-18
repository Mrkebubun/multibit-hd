package org.multibit.hd.ui.views.wizards.edit_wallet;

import com.google.common.base.Optional;
import net.miginfocom.swing.MigLayout;
import org.multibit.hd.core.config.Configurations;
import org.multibit.hd.core.dto.WalletSummary;
import org.multibit.hd.core.managers.BackupManager;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.ui.MultiBitUI;
import org.multibit.hd.ui.events.view.ViewEvents;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.*;
import org.multibit.hd.ui.views.components.panels.PanelDecorator;
import org.multibit.hd.ui.views.components.select_file.SelectFileModel;
import org.multibit.hd.ui.views.components.select_file.SelectFileView;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;
import org.multibit.hd.ui.views.wizards.AbstractWizard;
import org.multibit.hd.ui.views.wizards.AbstractWizardPanelView;
import org.multibit.hd.ui.views.wizards.WizardButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.File;

/**
 * <p>Wizard to provide the following to UI:</p>
 * <ul>
 * <li>Edit Wallet: Enter details</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class EditWalletPanelView extends AbstractWizardPanelView<EditWalletWizardModel, SelectFileModel> {

  private static final Logger log = LoggerFactory.getLogger(EditWalletPanelView.class);

  // View components
  JTextField name;
  JTextArea notes;

  private ModelAndView<SelectFileModel, SelectFileView> selectFileMaV;

  /**
   * @param wizard    The wizard managing the states
   * @param panelName The panel name to allow event filtering
   */
  public EditWalletPanelView(AbstractWizard<EditWalletWizardModel> wizard, String panelName) {

    super(wizard, panelName, AwesomeIcon.EDIT, MessageKey.EDIT_WALLET_TITLE);
  }

  @Override
  public void newPanelModel() {

    selectFileMaV = Components.newSelectFileMaV(getPanelName());

    // Register components
    registerComponents(selectFileMaV);
  }

  @Override
  public void initialiseContent(JPanel contentPanel) {

    contentPanel.setLayout(new MigLayout(
      Panels.migXYLayout(),
      "[][]", // Column constraints
      "[]5[]5[]10[]" // Row constraints
    ));

    // Name
    contentPanel.add(Labels.newLabel(MessageKey.NAME));
    name = TextBoxes.newEnterName(getWizardModel(), false);
    contentPanel.add(name, "push,wrap");
    name.setText(getWizardModel().getWalletSummary().getName());

    // Public notes can become quite long
    notes = TextBoxes.newEnterNotes(getWizardModel());
    JScrollPane scrollPane = ScrollPanes.newDataEntryScrollPane(notes);

    notes.setText(getWizardModel().getWalletSummary().getNotes());
    notes.setCaretPosition(0);

    // Cloud backup location
    setPanelModel(selectFileMaV.getModel());
    if (Configurations.currentConfiguration != null) {
      selectFileMaV.getModel().setValue(Configurations.currentConfiguration.getAppearance().getCloudBackupLocation());
    }

    contentPanel.add(Labels.newLabel(MessageKey.NOTES));
    contentPanel.add(scrollPane, "push," + MultiBitUI.WIZARD_MAX_WIDTH_MIG + ",wrap");

    contentPanel.add(Labels.newLabel(MessageKey.CLOUD_BACKUP_LOCATION));
    contentPanel.add(selectFileMaV.getView().newComponentPanel(), "span 2,wrap");

  }

  @Override
  protected void initialiseButtons(AbstractWizard<EditWalletWizardModel> wizard) {

    PanelDecorator.addCancelApply(this, wizard);
  }

  @Override
  public void fireInitialStateViewEvents() {

    // Apply button starts off enabled
    ViewEvents.fireWizardButtonEnabledEvent(getPanelName(), WizardButton.APPLY, true);
  }

  @Override
  public void afterShow() {

    name.requestFocusInWindow();
    name.selectAll();
  }

  @Override
  public boolean beforeHide(boolean isExitCancel) {

    if (!isExitCancel) {

      // Ensure the wizard model correctly reflects the contents of the components
      updateFromComponentModels(Optional.absent());

    }

    // Must be OK to proceed
    return true;
  }


  @Override
  public void updateFromComponentModels(Optional componentModel) {

    WalletSummary walletSummary = getWizardModel().getWalletSummary();

    if (walletSummary != null) {
      if (name != null) {
        walletSummary.setName(name.getText());
      }
      if (notes != null) {
        walletSummary.setNotes(notes.getText());
      }
    }

    log.debug("selectFileMaV.getModel().getValue() = '" + selectFileMaV.getModel().getValue() + "', isSelected = " + selectFileMaV.getModel().isSelected());
    if (Configurations.currentConfiguration != null) {
      if (selectFileMaV.getModel().isSelected()) {
        // The user has selected a cloud backup location - set it into the configuration and perform a cloud backup at the next backup tick
        Configurations.currentConfiguration.getAppearance().setCloudBackupLocation(selectFileMaV.getModel().getValue());
        log.debug("Performing cloud backup at next backup tick as user has just set cloud backup location");
        BackupManager.INSTANCE.setCloudBackupDirectory(Optional.of(new File(selectFileMaV.getModel().getValue())));
        CoreServices.getOrCreateBackupService().setPerformCloudBackupAtNextTick(true);
      }
      log.debug("Cloud backup location:\n{}", Configurations.currentConfiguration.getAppearance().getCloudBackupLocation());
    }

  }
}