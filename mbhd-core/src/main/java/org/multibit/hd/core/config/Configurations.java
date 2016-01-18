package org.multibit.hd.core.config;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.multibit.hd.core.events.CoreEvents;
import org.multibit.hd.core.managers.InstallationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * <p>Utility to provide the following to configuration:</p>
 * <ul>
 * <li>Default configuration</li>
 * <li>Switch configuration</li>
 * <li>Read/write configuration files</li>
 * </ul>
 *
 * @since 0.0.1
 *
 */
// This arises from the global nature of the configuration
@SuppressFBWarnings({"MS_CANNOT_BE_FINAL"})
public class Configurations {

  private static final Logger log = LoggerFactory.getLogger(Configurations.class);

  /**
   * The current runtime configuration (preserved across soft restarts)
   */
  public static Configuration currentConfiguration;

  /**
   * The previous configuration (preserved across soft restarts)
   */
  public static Configuration previousConfiguration;

  /**
   * Utilities have private constructors
   */
  private Configurations() {
  }

  /**
   * <p>The default configuration has the following characteristics:</p>
   * <ul>
   *   <li>Based on Locale.US since it is the dominant locale on the Internet</li>
   *   <li>Licence not accepted</li>
   *   <li>Licence not accepted</li>
   * </ul>
   * @return A new default configuration
   */
  public static Configuration newDefaultConfiguration() {

    return new Configuration();

  }

  /**
   * <p>Handle the process of switching to a new configuration</p>
   *
   * <p>Provides locale and event notification of the change</p>
   *
   * @param newConfiguration The new configuration
   */
  public static synchronized void switchConfiguration(Configuration newConfiguration) {

    log.debug("Switching configuration");

    // Keep track of the previous configuration
    previousConfiguration = currentConfiguration;

    // Ensure the new configuration has the same frame bounds since MainView won't have updated it
    newConfiguration.getAppearance().setLastFrameBounds(currentConfiguration.getAppearance().getLastFrameBounds());

    // Set the replacement
    currentConfiguration = newConfiguration;

    // Persist the changes
    persistCurrentConfiguration();

    // Update any JVM classes
    Locale.setDefault(currentConfiguration.getLocale());

    // Notify interested parties
    CoreEvents.fireConfigurationChangedEvent();

  }

  /**
   * <p>Persist the current configuration</p>
   *
   * <p>No locale change or event takes place (see {@link #switchConfiguration(Configuration)})</p>
   */
  public static synchronized void persistCurrentConfiguration() {

    log.debug("Persisting current configuration");

    // Get the configuration file
    // This approach allows for easy debugging of persisting over live configuration
    // in unit tests (see BitcoinURIListeningServiceTest for an example)
    File configurationFile = InstallationManager.getConfigurationFile();

    // Persist the new configuration
    try (FileOutputStream fos = new FileOutputStream(configurationFile)) {

      Yaml.writeYaml(fos, Configurations.currentConfiguration);

    } catch (IOException e) {
      // Nothing the user can do here so ignore it (logging for advanced users)
      log.warn("Could not persist current configuration", e);
    }
  }

}
