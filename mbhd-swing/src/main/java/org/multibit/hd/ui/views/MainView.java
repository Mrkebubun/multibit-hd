package org.multibit.hd.ui.views;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.miginfocom.swing.MigLayout;
import org.multibit.hd.core.config.Configurations;
import org.multibit.hd.core.events.CoreEvents;
import org.multibit.hd.core.events.ShutdownEvent;
import org.multibit.hd.core.managers.WalletManager;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.core.utils.OSUtils;
import org.multibit.hd.hardware.core.HardwareWalletService;
import org.multibit.hd.ui.MultiBitUI;
import org.multibit.hd.ui.events.view.ViewEvents;
import org.multibit.hd.ui.languages.Languages;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.Images;
import org.multibit.hd.ui.views.components.Panels;
import org.multibit.hd.ui.views.fonts.TitleFontDecorator;
import org.multibit.hd.ui.views.themes.Themes;
import org.multibit.hd.ui.views.wizards.Wizards;
import org.multibit.hd.ui.views.wizards.credentials.CredentialsRequestType;
import org.multibit.hd.core.dto.WalletMode;
import org.multibit.hd.ui.views.wizards.welcome.WelcomeWizardState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Locale;

/**
 * <p>View to provide the following to application:</p>
 * <ul>
 * <li>Provision of components and layout for the main frame</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class MainView extends JFrame {

  private static final Logger log = LoggerFactory.getLogger(MainView.class);

  private HeaderView headerView;
  private SidebarView sidebarView;
  private DetailView detailView;
  private FooterView footerView;

  // Need to track if a wizard was showing before a refresh occurred
  private boolean showExitingWelcomeWizard = false;
  private boolean showExitingCredentialsWizard = false;
  private boolean isCentered = false;

  /**
   * The credentials type to show when starting the credentials wizard
   */
  private CredentialsRequestType credentialsRequestType = CredentialsRequestType.PASSWORD;
  private boolean repeatLatestEvents = true;

  // The Panel.applicationFrame is a global singleton in nature
  @SuppressFBWarnings({"ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD"})
  public MainView() {

    // Define the minimum size for the frame
    setMinimumSize(new Dimension(MultiBitUI.UI_MIN_WIDTH, MultiBitUI.UI_MIN_HEIGHT));

    // Set the starting size
    setSize(new Dimension(MultiBitUI.UI_MIN_WIDTH, MultiBitUI.UI_MIN_HEIGHT));

    // Ensure app does not have Java coffee up icon on Windows
    if (OSUtils.isWindows()) {
      setIconImage(Images.newLogoIconImage());
    }

    // Provide all panels with a reference to the main frame
    Panels.setApplicationFrame(this);

    // Add a glass pane which dims the whole screen - used for switch (MainController#handleSwitchWallet)
    // It also absorbs keystrokes and mouse events
    JComponent glassPane = new JComponent() {
      public void paintComponent(Graphics g) {
        g.setColor(new Color(0, 0, 0, 50));
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
      }
    };
    glassPane.addKeyListener(new KeyListener() {
      @Override
      public void keyTyped(KeyEvent e) {

      }

      @Override
      public void keyPressed(KeyEvent e) {

      }

      @Override
      public void keyReleased(KeyEvent e) {

      }
    });
    glassPane.addMouseListener(new MouseListener() {
      @Override
      public void mouseClicked(MouseEvent e) {

      }

      @Override
      public void mousePressed(MouseEvent e) {

      }

      @Override
      public void mouseReleased(MouseEvent e) {

      }

      @Override
      public void mouseEntered(MouseEvent e) {

      }

      @Override
      public void mouseExited(MouseEvent e) {

      }
    });
    getRootPane().setGlassPane(glassPane);

    addWindowListener(
      new WindowAdapter() {
        @Override
        public void windowClosing(java.awt.event.WindowEvent windowEvent) {

          log.info("Hard shutdown from 'window closing' event");
          CoreEvents.fireShutdownEvent(ShutdownEvent.ShutdownType.HARD);
        }
      });

    addComponentListener(
      new ComponentAdapter() {

        @Override
        public void componentMoved(ComponentEvent e) {
          updateConfiguration();
        }

        @Override
        public void componentResized(ComponentEvent e) {
          updateConfiguration();
        }

        /**
         * Keep the current configuration updated
         */
        private void updateConfiguration() {

          Rectangle bounds = getBounds();
          String lastFrameBounds = String.format("%d,%d,%d,%d", bounds.x, bounds.y, bounds.width, bounds.height);

          Configurations.currentConfiguration.getAppearance().setLastFrameBounds(lastFrameBounds);

        }
      });

  }

  /**
   * <p>Rebuild the contents of the main view based on the current configuration and theme</p>
   *
   * @param isLanguageChange True if this refresh is because of a language change
   */
  public void refresh(final boolean isLanguageChange) {
    if (SwingUtilities.isEventDispatchThread()) {
      refreshOnEventThread(isLanguageChange);
    } else {
      // Start the main view refresh on the EDT
      SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            refreshOnEventThread(isLanguageChange);
          }
        });
    }
  }

  /**
   * <p>Rebuild the contents of the main view based on the current configuration and theme on the Swing Event thread</p>
   *
   * @param isLanguageChange True if this refresh is because of a language change
   */
  private void refreshOnEventThread(boolean isLanguageChange) {

    Preconditions.checkState(SwingUtilities.isEventDispatchThread(), "Must be in the EDT. Check MainController.");

    Locale locale = Configurations.currentConfiguration.getLocale();

    log.debug("Refreshing MainView with locale '{}'", locale);

    // Ensure the title font is updated depending on the new locale
    TitleFontDecorator.refresh(locale);

    // Ensure the frame title matches the new language and wallet name
    if (WalletManager.INSTANCE.getCurrentWalletSummary().isPresent()) {
      setTitle(
        Languages.safeText(MessageKey.MULTIBIT_HD_TITLE)
          + " - "
          + WalletManager.INSTANCE.getCurrentWalletSummary().get().getName());

    } else {
      // Do not have a wallet yet
      setTitle(Languages.safeText(MessageKey.MULTIBIT_HD_TITLE));
    }

    // Clear out all the old content
    getContentPane().removeAll();

    // Rebuild the main content
    getContentPane().add(createMainContent());

    // Usually the latest events need to be repeated after a configuration change
    // Switch wallet should ignore them
    if (repeatLatestEvents) {
      log.debug("Repeating earlier events...");

      // Ensure the wallet balance is propagated out
      if (WalletManager.INSTANCE.getCurrentWalletBalance().isPresent()) {
        ViewEvents.fireBalanceChangedEvent(
          WalletManager.INSTANCE.getCurrentWalletBalance().get(),
          WalletManager.INSTANCE.getCurrentWalletBalanceWithUnconfirmed().get(),
          null,
          Optional.<String>absent());
      }

      // Catch up on recent events
      CoreServices.getApplicationEventService().repeatLatestEvents();
    }

    // Check for any wizards that were showing before the refresh occurred
    if (showExitingWelcomeWizard) {

      // This section must come after a deferred hide has completed

      // Select the appropriate wallet mode
      final WalletMode walletMode;
      if (CredentialsRequestType.HARDWARE.equals(credentialsRequestType)) {
        Optional<HardwareWalletService> currentHardwareWalletService = CoreServices.getCurrentHardwareWalletService();
        walletMode = WalletMode.of(currentHardwareWalletService);
      } else {
        walletMode = WalletMode.STANDARD;
      }

      // Determine the appropriate starting screen for the welcome wizard
      if (Configurations.currentConfiguration.isLicenceAccepted()) {

        // Must have run before so perform some additional checks
        if ((WalletMode.TREZOR == walletMode || WalletMode.KEEP_KEY == walletMode)
          && !isLanguageChange) {
          // Starting with an uninitialised hardware wallet
          log.debug("Showing exiting welcome wizard (select hardware wallet)");
          Panels.showLightBox(Wizards.newExitingWelcomeWizard(WelcomeWizardState.WELCOME_SELECT_WALLET, walletMode).getWizardScreenHolder());
        } else {
          log.debug("Showing exiting welcome wizard (select language)");
          Panels.showLightBox(Wizards.newExitingWelcomeWizard(WelcomeWizardState.WELCOME_SELECT_LANGUAGE, walletMode).getWizardScreenHolder());
        }
      } else {
        log.debug("Showing exiting welcome wizard (licence agreement)");
        Panels.showLightBox(Wizards.newExitingWelcomeWizard(WelcomeWizardState.WELCOME_LICENCE, walletMode).getWizardScreenHolder());
      }

    } else if (showExitingCredentialsWizard) {

      // This section must come after a deferred hide has completed

      log.debug("Showing exiting credentials wizard");

      // Force an exit if the user can't get through
      Panels.showLightBox(Wizards.newExitingCredentialsWizard(credentialsRequestType).getWizardScreenHolder());

    } else {

      log.debug("Showing detail view");

      // No wizards so this reset is a wallet unlock or settings change
      // The AbstractWizard.handleHide credentials unlock thread will close the wizard later
      // to get the effect of everything happening behind the wizard

      // Clear out all the cached screens
      if (detailView != null) {
        detailView.clearScreenCache();
      }
    }

    // Use Configuration to get the last frame bounds
    resizeToLastFrameBounds();

    if (isCentered) {
      GraphicsDevice defaultScreen = getGraphicsDevices().get(0);

      GraphicsConfiguration defaultConfiguration = defaultScreen.getDefaultConfiguration();

      Rectangle sb = defaultConfiguration.getBounds();
      setBounds(
        sb.x + sb.width / 2 - getWidth() / 2,
        sb.y + sb.height / 2 - getHeight() / 2,
        getWidth(),
        getHeight()
      );
    }

    log.debug("Show UI");
    setVisible(true);

    // Repeat the the last frame bounds to overcome bug in Swing setting x=0
    resizeToLastFrameBounds();

    log.debug("Refresh complete");
  }

  /**
   * @return True if the exiting welcome wizard will be shown on a reset
   */
  public boolean isShowExitingWelcomeWizard() {
    return showExitingWelcomeWizard;
  }

  /**
   * @param show True if the exiting welcome wizard should be shown during the next refresh
   */
  public void setShowExitingWelcomeWizard(boolean show) {

    showExitingWelcomeWizard = show;

  }

  /**
   * @param show True if the exiting credentials wizard should be shown during the next refresh
   */
  public void setShowExitingCredentialsWizard(boolean show) {

    showExitingCredentialsWizard = show;

  }

  /**
   * Attempt to get focus to the sidebar
   */
  public void sidebarRequestFocus() {

    sidebarView.requestFocus();
  }

  /**
   * Update the sidebar wallet name tree node
   *
   * @param walletName The wallet name
   */
  public void sidebarWalletName(String walletName) {

    sidebarView.updateWalletTreeNode(walletName);

  }

  /**
   * @return The contents of the main panel (header, body and footer)
   */
  private JPanel createMainContent() {

    Preconditions.checkState(SwingUtilities.isEventDispatchThread(), "Must execute on the EDT");

    // Create the main panel and place it in this frame
    MigLayout layout = new MigLayout(
      Panels.migXYLayout(),
      "[]", // Columns
      "0[]0[]0[33:33:33]"  // Rows
    );
    JPanel mainPanel = Panels.newPanel(layout);

    // Require opaque to ensure the color is shown
    mainPanel.setOpaque(true);

    // Unsubscribe from events
    if (headerView != null) {

      log.debug("Unsubscribe existing views");
      unsubscribe();

    }

    log.debug("Creating fresh views under MainView...");
    // Create supporting views (rebuild every time for language support)
    headerView = new HeaderView();
    // At present we are always in single wallet mode
    sidebarView = new SidebarView(false);
    detailView = new DetailView();
    footerView = new FooterView();

    // Create a splitter pane
    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

    // Set the divider width (3 is about right for a clean look)
    splitPane.setDividerSize(3);

    int sidebarWidth = MultiBitUI.SIDEBAR_LHS_PREF_WIDTH;
    try {
      sidebarWidth = Integer.parseInt(Configurations.currentConfiguration.getAppearance().getSidebarWidth());
    } catch (NumberFormatException e) {
      log.warn("Sidebar width configuration is not a number - using default");
    }

    if (Languages.isLeftToRight()) {
      splitPane.setLeftComponent(sidebarView.getContentPanel());
      splitPane.setRightComponent(detailView.getContentPanel());
      splitPane.setDividerLocation(sidebarWidth);
    } else {
      splitPane.setLeftComponent(detailView.getContentPanel());
      splitPane.setRightComponent(sidebarView.getContentPanel());
      splitPane.setDividerLocation(Panels.getApplicationFrame().getWidth() - sidebarWidth);
    }

    // Sets the colouring for divider and borders
    splitPane.setBackground(Themes.currentTheme.text());
    splitPane.setBorder(
      BorderFactory.createMatteBorder(
        1, 0, 1, 0,
        Themes.currentTheme.text()
      ));

    splitPane.applyComponentOrientation(Languages.currentComponentOrientation());

    splitPane.addPropertyChangeListener(
      JSplitPane.DIVIDER_LOCATION_PROPERTY,
      new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent pce) {

          // Keep the current configuration up to date
          Configurations.currentConfiguration.getAppearance().setSidebarWidth(String.valueOf(pce.getNewValue()));

        }
      }
    );

    // Add the supporting panels
    mainPanel.add(headerView.getContentPanel(), "growx,shrink,wrap"); // Ensure header size remains fixed
    mainPanel.add(splitPane, "grow,push,wrap");
    mainPanel.add(footerView.getContentPanel(), "growx, growy"); // Ensure footer size remains fixed using row height sizing

    return mainPanel;
  }

  /**
   * This view is about to close so all child views should unsubscribe from events
   */
  public void unsubscribe() {

    if (headerView != null) {
      headerView.unregister();
    }
    if (sidebarView != null) {
      sidebarView.unregister();
    }
    if (detailView != null) {
      detailView.unregister();
    }
    if (footerView != null) {
      footerView.unregister();
    }

  }

  /**
   * <p>Resize the frame to the last bounds</p>
   */
  private void resizeToLastFrameBounds() {

    String frameDimension = Configurations.currentConfiguration.getAppearance().getLastFrameBounds();

    if (frameDimension != null) {

      String[] lastFrameDimension = frameDimension.split(",");
      if (lastFrameDimension.length == 4) {

        try {
          int x = Integer.parseInt(lastFrameDimension[0]);
          int y = Integer.parseInt(lastFrameDimension[1]);
          int w = Integer.parseInt(lastFrameDimension[2]);
          int h = Integer.parseInt(lastFrameDimension[3]);
          Rectangle newBounds = new Rectangle(x, y, w, h);

          // Not centered
          isCentered = false;

          // Place the frame in the desired position (setBounds() does not work)
          setPreferredSize(new Dimension(newBounds.width, newBounds.height));
          setSize(new Dimension(newBounds.width, newBounds.height));
          setLocation(newBounds.x, newBounds.y);

          return;

        } catch (NumberFormatException e) {
          log.error("Incorrect format in configuration - using defaults", e);
        }

      } else if (lastFrameDimension.length == 2) {

        log.debug("Using partial coordinates");

        try {
          int w = Integer.parseInt(lastFrameDimension[0]);
          int h = Integer.parseInt(lastFrameDimension[1]);
          Dimension newBounds = new Dimension(w, h);

          // Center in main screen
          isCentered = true;

          // Place the frame in the desired position (setBounds() does not work)
          setPreferredSize(new Dimension(newBounds.width, newBounds.height));
          setSize(new Dimension(newBounds.width, newBounds.height));

          return;

        } catch (NumberFormatException e) {
          log.error("Incorrect format in configuration - using defaults", e);
        }

      }

    }

    log.debug("Using default coordinates");

    // By default center in main screen
    isCentered = true;

    // Set preferred size based on internal defaults
    setPreferredSize(new Dimension(MultiBitUI.UI_MIN_WIDTH, MultiBitUI.UI_MIN_HEIGHT));

  }

  /**
   * @return The available graphics devices with the default in position 0
   */
  private List<GraphicsDevice> getGraphicsDevices() {

    List<GraphicsDevice> devices = Lists.newArrayList();

    // Get the default screen device
    GraphicsDevice defaultScreenDevice = GraphicsEnvironment
      .getLocalGraphicsEnvironment()
      .getDefaultScreenDevice();

    for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {

      if (GraphicsDevice.TYPE_RASTER_SCREEN == gd.getType()) {

        if (defaultScreenDevice == gd) {
          devices.add(0, gd);
        } else {
          devices.add(gd);
        }
      }
    }

    Preconditions.checkState(!devices.isEmpty(), "'devices' must not be empty. Is machine in headless mode?");

    return devices;
  }

  public void setCredentialsRequestType(CredentialsRequestType credentialsRequestType) {
    this.credentialsRequestType = credentialsRequestType;
  }

  public CredentialsRequestType getCredentialsRequestType() {
    return credentialsRequestType;
  }

  public void setRepeatLatestEvents(boolean repeatLatestEvents) {
    this.repeatLatestEvents = repeatLatestEvents;
  }

  public boolean isRepeatLatestEvents() {
    return repeatLatestEvents;
  }
}