package org.multibit.hd.ui.views;

import com.google.common.eventbus.Subscribe;
import net.miginfocom.swing.MigLayout;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.ui.events.SystemStatusChangedEvent;
import org.multibit.hd.ui.views.components.Panels;
import org.multibit.hd.ui.views.fonts.AwesomeDecorator;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;
import org.multibit.hd.ui.views.themes.Themes;

import javax.swing.*;

/**
 * <p>View to provide the following to application:</p>
 * <ul>
 * <li>Provision of components and layout for the footer display</li>
 * </ul>
 *
 * @since 0.0.1
 *         
 */
public class FooterView {

  private final JPanel contentPanel;
  private final JProgressBar progressBar;
  private final JLabel messageLabel;
  private final JLabel statusLabel;
  private final JLabel statusIcon;


  public FooterView() {

    CoreServices.uiEventBus.register(this);

    contentPanel = Panels.newPanel(new MigLayout(
      "ins 7",
      "[][][]",
      "[]"
    ));

    // Apply the theme
    contentPanel.setBackground(Themes.currentTheme.headerPanelBackground());

    progressBar = new JProgressBar();

    messageLabel = new JLabel();

    // Label text and icon are different colours so must be separated
    statusLabel = new JLabel("");
    statusIcon = AwesomeDecorator.createIconLabel(
      AwesomeIcon.CIRCLE,
      "",
      false
    );

    // Start with no knowledge so assume the worst
    statusIcon.setForeground(Themes.currentTheme.dangerBackground());

    contentPanel.add(progressBar, "shrink,left");
    contentPanel.add(messageLabel, "grow,push");
    contentPanel.add(statusLabel, "split,shrink,right");
    contentPanel.add(statusIcon, "right");

  }

  /**
   * @return The content panel for this View
   */
  public JPanel getContentPanel() {
    return contentPanel;
  }

  /**
   * <p>Handles the representation of a system status change</p>
   *
   * @param event The system status change event
   */
  @Subscribe
  public void onSystemStatusChangeEvent(SystemStatusChangedEvent event) {

    statusLabel.setText(event.getLocalisedMessage());
    switch (event.getSeverity()) {
      case RED:
        statusIcon.setForeground(Themes.currentTheme.dangerBackground());
        break;
      case AMBER:
        statusIcon.setForeground(Themes.currentTheme.warningBackground());
        break;
      case GREEN:
        statusIcon.setForeground(Themes.currentTheme.successBackground());
        break;
      default:
        // Unknown status
        throw new IllegalStateException("Unknown event severity "+event.getSeverity());
    }

  }

}
