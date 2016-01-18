package org.multibit.hd.ui.views.wizards.welcome;

import com.google.common.base.Optional;
import net.miginfocom.swing.MigLayout;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.hardware.core.HardwareWalletService;
import org.multibit.hd.ui.MultiBitUI;
import org.multibit.hd.ui.events.view.ViewEvents;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.*;
import org.multibit.hd.ui.views.components.display_environment_alert.DisplayEnvironmentAlertModel;
import org.multibit.hd.ui.views.components.display_environment_alert.DisplayEnvironmentAlertView;
import org.multibit.hd.ui.views.components.panels.PanelDecorator;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;
import org.multibit.hd.ui.views.themes.Themes;
import org.multibit.hd.ui.views.wizards.AbstractWizard;
import org.multibit.hd.ui.views.wizards.AbstractWizardPanelView;
import org.multibit.hd.ui.views.wizards.WizardButton;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * <p>Wizard to provide the following to UI:</p>
 * <ul>
 * <li>Inform the user about attaching their hardware wallet</li>
 * </ul>
 *
 * @since 0.1.0
 */

public class WelcomeAttachHardwareWalletPanelView extends AbstractWizardPanelView<WelcomeWizardModel, String> {

  // Display environment popovers
  private ModelAndView<DisplayEnvironmentAlertModel, DisplayEnvironmentAlertView> displayEnvironmentPopoverMaV;

  /**
   * Handles periodic increments of rotation
   */
  private final Timer timer;

  private int timerCount = 1;
  private JLabel note1Icon;
  private JLabel note1Label;

  private JLabel note2Icon;
  private JLabel note2Label;

  private JLabel note3Icon;
  private JLabel note3Label;

  private JLabel note4Icon;
  private JLabel note4Label;

  private JLabel note5Icon;
  private JLabel note5Label;

  private JLabel reportStatusLabel;

  /**
   * @param wizard    The wizard managing the states
   * @param panelName The panel name to filter events from components
   */
  public WelcomeAttachHardwareWalletPanelView(AbstractWizard<WelcomeWizardModel> wizard, String panelName) {

    super(wizard, panelName, AwesomeIcon.SHIELD, MessageKey.ATTACH_HARDWARE_WALLET_TITLE, new Object[]{wizard.getWizardModel().getWalletMode().brand()});

    // Timer needs to be fairly fast to appear responsive
    timer = new Timer(
      500, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {

        // Guaranteed to be on the EDT
        updateFromComponentModels(Optional.absent());

      }
    });

  }

  @Override
  public void newPanelModel() {

    displayEnvironmentPopoverMaV = Popovers.newDisplayEnvironmentPopoverMaV(getPanelName());

    // Allow wizard model to make callbacks for hardware wallet status
    getWizardModel().setAttachHardwareWalletPanelView(this);

  }

  @Override
  public void initialiseContent(JPanel contentPanel) {

    contentPanel.setLayout(
      new MigLayout(
        Panels.migXYLayout(),
        "[]20[]", // Column constraints
        "10[40]10[40]10[40]10[40]10[40]40[40]10" // Row constraints
      ));

    // Apply the theme
    contentPanel.setBackground(Themes.currentTheme.detailPanelBackground());

    // Note 1
    note1Icon = Labels.newIconLabel(AwesomeIcon.SHIELD, Optional.<MessageKey>absent(), null);
    contentPanel.add(note1Icon, "shrink");
    note1Label = Labels.newNoteLabel(MessageKey.ATTACH_HARDWARE_WALLET_NOTE_1, new Object[]{getWizardModel().getWalletMode().brand()});
    contentPanel.add(note1Label, MultiBitUI.WIZARD_MAX_WIDTH_MIG + ",wrap");

    // Note 2
    note2Icon = Labels.newIconLabel(AwesomeIcon.PLUS_CIRCLE, Optional.<MessageKey>absent(), null);
    contentPanel.add(note2Icon, "shrink");
    note2Label = Labels.newNoteLabel(MessageKey.ATTACH_HARDWARE_WALLET_NOTE_2, new Object[]{getWizardModel().getWalletMode().brand()});
    contentPanel.add(note2Label, MultiBitUI.WIZARD_MAX_WIDTH_MIG + ",wrap");
    note2Icon.setVisible(false);
    note2Label.setVisible(false);

    // Note 3
    note3Icon = Labels.newIconLabel(AwesomeIcon.EXTERNAL_LINK, Optional.<MessageKey>absent(), null);
    contentPanel.add(note3Icon, "shrink");
    note3Label = Labels.newNoteLabel(MessageKey.ATTACH_HARDWARE_WALLET_NOTE_3, new Object[]{getWizardModel().getWalletMode().brand()});
    contentPanel.add(note3Label, MultiBitUI.WIZARD_MAX_WIDTH_MIG + ",wrap");
    note3Icon.setVisible(false);
    note3Label.setVisible(false);

    // Note 4
    note4Icon = Labels.newIconLabel(AwesomeIcon.PLUG, Optional.<MessageKey>absent(), null);
    contentPanel.add(note4Icon, "shrink");
    note4Label = Labels.newNoteLabel(MessageKey.ATTACH_HARDWARE_WALLET_NOTE_4, new Object[]{getWizardModel().getWalletMode().brand()});
    contentPanel.add(note4Label, MultiBitUI.WIZARD_MAX_WIDTH_MIG + ",wrap");
    note4Icon.setVisible(false);
    note4Label.setVisible(false);

    // Note 5
    note5Icon = Labels.newIconLabel(AwesomeIcon.PLUS_SQUARE, Optional.<MessageKey>absent(), null);
    contentPanel.add(note5Icon, "shrink");
    note5Label = Labels.newNoteLabel(MessageKey.ATTACH_HARDWARE_WALLET_NOTE_5, new Object[]{getWizardModel().getWalletMode().brand()});
    contentPanel.add(note5Label, MultiBitUI.WIZARD_MAX_WIDTH_MIG + ",wrap");
    note5Icon.setVisible(false);
    note5Label.setVisible(false);

    // Provide an empty status label (populated after show)
    reportStatusLabel = Labels.newStatusLabel(Optional.of(MessageKey.HARDWARE_FOUND), new Object[]{getWizardModel().getWalletMode().brand()}, Optional.<Boolean>absent());
    reportStatusLabel.setVisible(false);

    contentPanel.add(reportStatusLabel, "span 2,aligny top,wrap");

  }

  @Override
  protected void initialiseButtons(AbstractWizard<WelcomeWizardModel> wizard) {

    PanelDecorator.addExitCancelPreviousNext(this, wizard);

  }

  @Override
  public void fireInitialStateViewEvents() {

    // Disable the next button
    ViewEvents.fireWizardButtonEnabledEvent(getPanelName(), WizardButton.NEXT, false);

  }

  @Override
  public void updateFromComponentModels(Optional componentModel) {

    switch (timerCount) {
      case 0:
        // Note 1
        note1Icon.setVisible(true);
        note1Label.setVisible(true);
        break;
      case 1:
        // Note 2
        note2Icon.setVisible(true);
        note2Label.setVisible(true);
        break;
      case 2:
        // Note 3
        note3Icon.setVisible(true);
        note3Label.setVisible(true);
        break;
      case 3:
        // Note 4
        note4Icon.setVisible(true);
        note4Label.setVisible(true);
        break;
      case 4:
        // Note 5
        note5Icon.setVisible(true);
        note5Label.setVisible(true);
        break;
      case 5:
        // Configure the initial state (the wizard may not have been created when the DEVICE_READY was issued)
        final Optional<HardwareWalletService> hardwareWalletService = CoreServices.getCurrentHardwareWalletService();
        if (hardwareWalletService.isPresent() && hardwareWalletService.get().isDeviceReady()) {
          setHardwareWalletStatus(Optional.of(MessageKey.HARDWARE_FOUND), new Object[] {getWizardModel().getWalletMode().brand()}, true);
        }
        break;
      default:

        // Use the timer to continue to check for possible problems
        // with the hardware wallet
        SwingUtilities.invokeLater(
          new Runnable() {
            @Override
            public void run() {
              checkForEnvironmentEventPopover(displayEnvironmentPopoverMaV);
            }
          });

        // Enable the Next button
        ViewEvents.fireWizardButtonEnabledEvent(getPanelName(), WizardButton.NEXT, true);

    }

    if (timerCount < 7) {
      timerCount++;
    }
  }

  @Override
  public void afterShow() {

    getNextButton().requestFocusInWindow();

    // Run continuously
    timer.setRepeats(true);
    timer.start();

  }

  @Override
  public boolean beforeHide(boolean isExitCancel) {

    // Prevent popovers triggering continuously when finished
    timer.stop();

    return true;
  }

  /**
   * @param messageKey The message key (absent implies not visible)
   * @param values     The values for the message key
   * @param status     True for a check mark
   */
  public void setHardwareWalletStatus(final Optional<MessageKey> messageKey, final Object[] values, final boolean status) {

    SwingUtilities.invokeLater(
      new Runnable() {
        @Override
        public void run() {
          // Check for report message from hardware wallet
          LabelDecorator.applyReportMessage(reportStatusLabel, messageKey, values, status);
        }
      });

  }
}
