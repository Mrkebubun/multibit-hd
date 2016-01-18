package org.multibit.hd.ui.views.wizards.repair_wallet;

import com.google.common.base.Optional;
import com.google.common.eventbus.Subscribe;
import net.miginfocom.swing.MigLayout;
import org.multibit.hd.core.dto.BitcoinNetworkSummary;
import org.multibit.hd.core.events.BitcoinNetworkChangedEvent;
import org.multibit.hd.ui.MultiBitUI;
import org.multibit.hd.ui.events.view.ComponentChangedEvent;
import org.multibit.hd.ui.events.view.ViewEvents;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.ViewKey;
import org.multibit.hd.ui.views.components.AccessibilityDecorator;
import org.multibit.hd.ui.views.components.Labels;
import org.multibit.hd.ui.views.components.Panels;
import org.multibit.hd.ui.views.components.panels.PanelDecorator;
import org.multibit.hd.ui.views.fonts.AwesomeDecorator;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;
import org.multibit.hd.ui.views.themes.Themes;
import org.multibit.hd.ui.views.wizards.AbstractWizard;
import org.multibit.hd.ui.views.wizards.AbstractWizardPanelView;
import org.multibit.hd.ui.views.wizards.WizardButton;

import javax.swing.*;

/**
 * <p>View to provide the following to UI:</p>
 * <ul>
 * <li>Show report wallet progress report</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class RepairWalletReportPanelView extends AbstractWizardPanelView<RepairWalletWizardModel, RepairWalletReportPanelModel> {

  private JLabel cacertsRepairedStatusLabel;

  private JLabel walletRepairedStatusLabel;

  private JLabel blocksLeftLabel;
  private JLabel blocksLeftStatusLabel;

  private boolean initialised = false;

  /**
   * @param wizard The wizard managing the states
   */
  public RepairWalletReportPanelView(AbstractWizard<RepairWalletWizardModel> wizard, String panelName) {

    super(wizard, panelName, AwesomeIcon.MEDKIT, MessageKey.REPAIR_WALLET_PROGRESS_TITLE);

  }

  @Override
  public void newPanelModel() {

    // Configure the panel model
    RepairWalletReportPanelModel panelModel = new RepairWalletReportPanelModel(
            getPanelName()
    );
    setPanelModel(panelModel);

  }

  @Override
  public void initialiseContent(JPanel contentPanel) {

    contentPanel.setLayout(new MigLayout(
            Panels.migXYLayout(),
            "[][][]", // Column constraints
            "[]10[]10[]10[]" // Row constraints
    ));

    // Apply the theme
    contentPanel.setBackground(Themes.currentTheme.detailPanelBackground());

    cacertsRepairedStatusLabel = Labels.newCACertsInstalledStatus(false);
    AwesomeDecorator.applyIcon(
            AwesomeIcon.EXCHANGE,
            cacertsRepairedStatusLabel,
            true,
            MultiBitUI.NORMAL_ICON_SIZE
    );
    AccessibilityDecorator.apply(cacertsRepairedStatusLabel, MessageKey.CACERTS_INSTALLED_STATUS);

    // Start invisible (activates after CA certs completes)
    blocksLeftLabel = Labels.newValueLabel("0");
    blocksLeftLabel.setVisible(false);
    blocksLeftStatusLabel = Labels.newBlocksLeft();
    blocksLeftStatusLabel.setVisible(false);

    // Start invisible (activates after synchronization completes)
    walletRepairedStatusLabel = Labels.newStatusLabel(
            Optional.of(MessageKey.WALLET_REPAIRED_STATUS),
            null,
            Optional.<Boolean>absent());
    AccessibilityDecorator.apply(cacertsRepairedStatusLabel, MessageKey.CACERTS_INSTALLED_STATUS);
    walletRepairedStatusLabel.setVisible(false);

    contentPanel.add(cacertsRepairedStatusLabel, "wrap");

    contentPanel.add(blocksLeftStatusLabel, "");
    contentPanel.add(blocksLeftLabel, "wrap");

    contentPanel.add(walletRepairedStatusLabel, "wrap");

    initialised = true;
  }

  @Override
  protected void initialiseButtons(AbstractWizard<RepairWalletWizardModel> wizard) {

    PanelDecorator.addFinish(this, wizard);

  }

  @Override
  public void afterShow() {

    // Ensure the Finish button is disabled to avoid complex side effects during repair
    ViewEvents.fireWizardButtonEnabledEvent(getPanelName(), WizardButton.FINISH, false);

    // Hide the header balance - switching back on is dealt with in MainController#onBitcoinNetworkChangedEvent
    ViewEvents.fireViewChangedEvent(ViewKey.HEADER, false);

    // Start the CA certs update process in a new thread
    getWizardModel().installCACertificates();

    // Start the repair wallet process in a new thread
    getWizardModel().repairWallet();

  }

  @Override
  public void updateFromComponentModels(Optional componentModel) {
    // Do nothing - panel model is updated via an action and wizard model is not applicable
  }

  @Subscribe
  public void onBitcoinNetworkChangedEvent(final BitcoinNetworkChangedEvent event) {

    // The event may be fired before the UI has initialised
    if (!initialised) {
      return;
    }

    BitcoinNetworkSummary summary = event.getSummary();
    switch (summary.getStatus()) {
      case NOT_CONNECTED:
        blocksLeftLabel.setVisible(false);
        blocksLeftStatusLabel.setVisible(false);
        break;

      case CONNECTING:
        blocksLeftLabel.setVisible(false);
        blocksLeftStatusLabel.setVisible(false);
        break;

      case CONNECTED:
        break;

      case DOWNLOADING_BLOCKCHAIN:
        blocksLeftLabel.setVisible(true);
        blocksLeftStatusLabel.setVisible(true);
        blocksLeftLabel.setText(String.valueOf(summary.getBlocksLeft()));
        AwesomeDecorator.applyIcon(
                AwesomeIcon.EXCHANGE,
                blocksLeftStatusLabel,
                true,
                MultiBitUI.NORMAL_ICON_SIZE
        );
        break;
      case SYNCHRONIZED:
        blocksLeftLabel.setVisible(true);
        blocksLeftStatusLabel.setVisible(true);
        blocksLeftLabel.setText("0");

        AwesomeDecorator.applyIcon(
                AwesomeIcon.CHECK,
                blocksLeftStatusLabel,
                true,
                MultiBitUI.NORMAL_ICON_SIZE
        );

        // Put the report screen into a finished state
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            // Enable the Finish button
            ViewEvents.fireWizardButtonEnabledEvent(getPanelName(), WizardButton.FINISH, true);

            // Wallet replayed without errors so it must be repaired
            walletRepairedStatusLabel.setVisible(true);
            AwesomeDecorator.bindIcon(
                    AwesomeIcon.CHECK,
                    walletRepairedStatusLabel,
                    true,
                    MultiBitUI.NORMAL_ICON_SIZE
            );

          }
        });
        break;
      default:
    }
  }


  @Subscribe
  public void onComponentChangedEvent(final ComponentChangedEvent event) {

    // The event may be fired before the UI has initialised
    if (!initialised) {
      return;
    }

    if (getPanelName().equals(event.getPanelName())) {

      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {

          Optional<Boolean> cacertsRepaired = getWizardModel().isCacertsRepaired();
          Optional<Boolean> walletRepaired = getWizardModel().isWalletRepaired();

          if (cacertsRepaired.isPresent()) {
            cacertsRepairedStatusLabel.setVisible(true);
            AwesomeDecorator.bindIcon(
                    cacertsRepaired.get() ? AwesomeIcon.CHECK : AwesomeIcon.TIMES,
                    cacertsRepairedStatusLabel,
                    true,
                    MultiBitUI.NORMAL_ICON_SIZE
            );

          }

          if (walletRepaired.isPresent()) {
            walletRepairedStatusLabel.setVisible(true);
            AwesomeDecorator.bindIcon(
                    walletRepaired.get() ? AwesomeIcon.CHECK : AwesomeIcon.TIMES,
                    walletRepairedStatusLabel,
                    true,
                    MultiBitUI.NORMAL_ICON_SIZE
            );
          }
        }
      });
    }
  }
}
