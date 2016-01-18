package org.multibit.hd.ui.views.wizards.verify_network;

import com.google.common.base.Optional;
import com.google.common.eventbus.Subscribe;
import net.miginfocom.swing.MigLayout;
import org.multibit.hd.core.dto.BitcoinNetworkSummary;
import org.multibit.hd.core.events.BitcoinNetworkChangedEvent;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.ui.MultiBitUI;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.Labels;
import org.multibit.hd.ui.views.components.Panels;
import org.multibit.hd.ui.views.components.panels.PanelDecorator;
import org.multibit.hd.ui.views.fonts.AwesomeDecorator;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;
import org.multibit.hd.ui.views.wizards.AbstractWizard;
import org.multibit.hd.ui.views.wizards.AbstractWizardPanelView;

import javax.swing.*;

/**
 * <p>View to provide the following to UI:</p>
 * <ul>
 * <li>Verify network: Show report</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class VerifyNetworkPanelView extends AbstractWizardPanelView<VerifyNetworkWizardModel, VerifyNetworkPanelModel> {

  // Panel specific components
  private JLabel peerCountLabel;
  private JLabel peerCountStatusLabel;

  private JLabel blocksLeftLabel;
  private JLabel blocksLeftStatusLabel;

  /**
   * @param wizard The wizard managing the states
   */
  public VerifyNetworkPanelView(AbstractWizard<VerifyNetworkWizardModel> wizard, String panelName) {

    super(wizard, panelName, AwesomeIcon.SITEMAP, MessageKey.VERIFY_NETWORK_TITLE);

  }

  @Override
  public void newPanelModel() {

    // Configure the panel model
    final VerifyNetworkPanelModel panelModel = new VerifyNetworkPanelModel(
            getPanelName());
    setPanelModel(panelModel);

    // Bind it to the wizard model

  }

  @Override
  public void initialiseContent(JPanel contentPanel) {

    contentPanel.setLayout(new MigLayout(
            Panels.migXYLayout(),
            "[shrink]10[][]", // Column constraints
            "[][][50][][][50][]" // Row constraints
    ));

    int currentPeerCount = CoreServices.getOrCreateBitcoinNetworkService().getNumberOfConnectedPeers();
    peerCountLabel = Labels.newValueLabel(String.valueOf(currentPeerCount));
    peerCountStatusLabel = Labels.newPeerCount();
    decoratePeerCountStatusLabel(currentPeerCount);

    blocksLeftLabel = Labels.newValueLabel("");
    blocksLeftStatusLabel = Labels.newBlocksLeft();

    contentPanel.add(peerCountStatusLabel, "shrink 1000");
    contentPanel.add(peerCountLabel, "align left");
    contentPanel.add(Labels.newValueLabel(""), "grow 1000,push,wrap");
    contentPanel.add(Labels.newPeerCountInfo(), "span 3," + MultiBitUI.WIZARD_MAX_WIDTH_MIG + ",wrap");

    contentPanel.add(Panels.newHorizontalDashedSeparator(), "growx, span 3,wrap");

    contentPanel.add(blocksLeftStatusLabel, "shrink 1000");
    contentPanel.add(blocksLeftLabel, "align left");
    contentPanel.add(Labels.newValueLabel(""), "grow 1000, push, wrap");
    contentPanel.add(Labels.newBlockCountInfo(), "span 3," + MultiBitUI.WIZARD_MAX_WIDTH_MIG + ",wrap");

    contentPanel.add(Panels.newHorizontalDashedSeparator(), "growx, span 3,wrap");

    contentPanel.add(Labels.newVerifyNetworkNoteBottom(), "span 3," + MultiBitUI.WIZARD_MAX_WIDTH_MIG + ",wrap");

  }

  @Override
  protected void initialiseButtons(AbstractWizard<VerifyNetworkWizardModel> wizard) {

    PanelDecorator.addFinish(this, wizard);

  }

  @Override
  public void afterShow() {

    // Use the latest values from the event service
    Optional<BitcoinNetworkChangedEvent> event = CoreServices.getApplicationEventService().getLatestBitcoinNetworkChangedEvent();

    if (event.isPresent()) {

      onBitcoinNetworkChangedEvent(event.get());

    }

  }

  @Override
  public void updateFromComponentModels(Optional componentModel) {

    // No need to update the wizard it has the references

  }

  @Subscribe
  public void onBitcoinNetworkChangedEvent(final BitcoinNetworkChangedEvent event) {

    // Avoid NPEs with early events
    if (!isInitialised()) {
      return;
    }

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {

        BitcoinNetworkSummary summary = event.getSummary();

        // Get peer count directly from services so it always matches footer
        int peerCount = CoreServices.getOrCreateBitcoinNetworkService().getNumberOfConnectedPeers();
        peerCountLabel.setText(String.valueOf(peerCount));
        decoratePeerCountStatusLabel(peerCount);

        // Blocks left
        int blocksLeft = event.getSummary().getBlocksLeft();
        if (blocksLeft == 0) {
          // Sync has completed
          AwesomeDecorator.applyIcon(
            AwesomeIcon.CHECK,
            blocksLeftStatusLabel,
            true,
            MultiBitUI.NORMAL_ICON_SIZE
          );
          blocksLeftLabel.setText(String.valueOf(summary.getBlocksLeft()));
        } else if (blocksLeft > 0) {
          // Sync is in progress
          AwesomeDecorator.applyIcon(
            AwesomeIcon.EXCHANGE,
            blocksLeftStatusLabel,
            true,
            MultiBitUI.NORMAL_ICON_SIZE
          );
          blocksLeftLabel.setText(String.valueOf(summary.getBlocksLeft()));
        }
        // blocksLeft can be -1 if no blocks left information is set
      }
    });

  }

  private void decoratePeerCountStatusLabel(int peerCount) {
    if (peerCount > 0) {
      AwesomeDecorator.applyIcon(
              AwesomeIcon.CHECK,
              peerCountStatusLabel,
              true,
              MultiBitUI.NORMAL_ICON_SIZE
      );
    } else {
      AwesomeDecorator.applyIcon(
              AwesomeIcon.TIMES,
              peerCountStatusLabel,
              true,
              MultiBitUI.NORMAL_ICON_SIZE
      );
    }
  }
}