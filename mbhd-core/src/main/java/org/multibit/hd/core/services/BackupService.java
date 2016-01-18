package org.multibit.hd.core.services;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Uninterruptibles;
import org.multibit.hd.core.dto.*;
import org.multibit.hd.core.events.CoreEvents;
import org.multibit.hd.core.events.ShutdownEvent;
import org.multibit.hd.core.managers.BackupManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * <p>Service to provide the following to application API:</p>
 * <ul>
 * <li>Access to rolling, local zip and cloud zip backups</li>
 * </ul>
 * <p/>
 * Wallet backup strategy:
 * <p/>
 * <p/>
 * Bitcoinj wallet writes
 * <p/>
 * this is a two stage write
 * wallet.saveNow is done instantly
 * wallet.saveLater - set to a 30 seconds period
 * make a save at MBHD exit
 * <p/>
 * <p/>
 * Rolling backups
 * <p/>
 * make saves every 2 minutes
 * make first save 1 minutes after MBHD start (most likely after initial sync)
 * make a save at MBHD exit
 * <p/>
 * <p/>
 * Local zip backups
 * <p/>
 * make saves every 10 minutes
 * make first save 1 minutes after MBHD start
 * make a save at MBHD exit
 * <p/>
 * <p/>
 * Cloud backups
 * <p/>
 * make saves every 30 minutes
 * make first save 1 minutes after MBHD start
 * make a save at MBHD exit
 * <p/>
 * <p/>
 * Do not bother tracking if the wallet is dirty, this only really affects the rolling backups and
 * is not worth the bother of writing a wallet extension to track it.
 *
 * @since 0.0.1
 */
public class BackupService extends AbstractService {

  private static final Logger log = LoggerFactory.getLogger(BackupService.class);

  /**
   * Initial delay in seconds after startup before making a backup
   * This delay is so that the wallet can sync
   */
  private static final int INITIAL_DELAY = 60;

  /**
   * This is the fastest tick in seconds used for the backup scheduler
   * Everything else is done on a multiple of this
   */
  private static final int TICK_TIME_SECONDS = 12;

  /**
    * The slowdown rate for performing rolling backups
    */
   private static final int ROLLING_BACKUP_MODULO = 10;


  /**
   * The slowdown rate for performing local zip backups
   */
  private static final int LOCAL_ZIP_BACKUP_MODULO = 50;

  /**
   * The slowdown rate for performing cloud zip backups
   */
  private static final int CLOUD_ZIP_BACKUP_MODULO = 150;

  /**
   * The number of times the backup main loop has incremented
   * We expect this service to be a singleton
   */
  private int tickCount;

  /**
   * The wallet summary to use for the next rolling backup
   */
  private Optional<WalletSummary> rememberedWalletSummaryForRollingBackup = Optional.absent();

  /**
   * The credentials to use for the next rolling backup
   */
  private Optional<CharSequence> rememberedPasswordForRollingBackup = Optional.absent();

  /**
   * The wallet id to use for the next local zip backup
   */
  private Optional<WalletId> rememberedWalletIdForLocalBackup = Optional.absent();

  /**
   * The credentials to use for the next local zip backup
   */
  private Optional<CharSequence> rememberedPasswordForLocalBackup = Optional.absent();

  /**
   * The wallet id to use for the next cloud zip backup
   */
  private Optional<WalletId> rememberedWalletIdForCloudBackup = Optional.absent();

  /**
   * The credentials to use for the next cloud zip backup
   */
  private Optional<CharSequence> rememberedPasswordForCloudBackup = Optional.absent();

  /**
   * Whether backups are enabled or not
   */
  private boolean backupsAreEnabled = true;

  /**
   * Whether backups are currently being performed
   */
  private boolean backupsAreRunning = false;

  /**
   * Whether a cloud backup should be performed at the next tick
   */
  private boolean performCloudBackupAtNextTick = false;

  @Override
  protected boolean startInternal() {

    // The first tick (at time INITIAL_DELAY seconds) all of a rolling backup,
    // local backup and a cloud backup
    // The users copy of MBHD will most likely be fully synchronised by then
    tickCount = 0;

    // Use the provided executor service management
    requireSingleThreadScheduledExecutor("backup");

    // Use the provided executor service management
    getScheduledExecutorService().scheduleAtFixedRate(
      new Runnable() {
        public void run() {
          backupsAreRunning = true;
          try {
            // Main backup loop
            //log.debug("The tickCount is {}", tickCount);

            // A rolling backup is performed every ROLLING_BACKUP_MODULO tick
            if (backupsAreEnabled  && tickCount % ROLLING_BACKUP_MODULO == 0) {
              performRollingBackup();
            }

            // Local zip backups are done every LOCAL_ZIP_BACKUP_MODULO number of ticks
            if (backupsAreEnabled && tickCount % LOCAL_ZIP_BACKUP_MODULO == 0) {
              performLocalZipBackup();
            }

            // Check if a cloud zip backup is required
            // Cloud backups are done every CLOUD_ZIP_BACKUP_MODULO number of ticks or if the performCloudBackupAtNextTick is set
            if (backupsAreEnabled && (tickCount % CLOUD_ZIP_BACKUP_MODULO == 0 || performCloudBackupAtNextTick)) {
              performCloudBackupAtNextTick = false;
              performCloudZipBackup();
            }

          } finally {
            tickCount++;
            backupsAreRunning = false;
          }
        }
      }
      , INITIAL_DELAY, TICK_TIME_SECONDS, TimeUnit.SECONDS);

    return true;
  }

  @Override
  protected boolean shutdownNowInternal(ShutdownEvent.ShutdownType shutdownType) {

    switch (shutdownType) {

      case HARD:
        // A hard shutdown does not give enough time to wait gracefully
        break;
      case SOFT:
        // A soft shutdown occurs during FEST testing so the backups may not be running
        if (isBackupsAreRunning()) {
          log.debug("Performing backups at shutdown");

          // Disable any new backups
          this.setBackupsAreEnabled(false);

          getScheduledExecutorService().schedule(
            new Runnable() {
              public void run() {
                // Wait for any current backups to complete
                while (isBackupsAreRunning()) {
                  Uninterruptibles.sleepUninterruptibly(200, TimeUnit.MILLISECONDS);
                }

                performRollingBackup();

                performLocalZipBackup();

                performCloudZipBackup();

              }

            }, 0, TimeUnit.MILLISECONDS);
        }
        break;
      case SWITCH:
        break;
    }

    // Backup service is tied to a wallet so should be completely shutdown
    return true;

  }

  /**
   * Remember a wallet summary and credentials.
   * This will be used at the next rolling backup.
   */
  public void rememberWalletSummaryAndPasswordForRollingBackup(WalletSummary walletSummary, CharSequence password) {
    rememberedWalletSummaryForRollingBackup = Optional.of(walletSummary);
    rememberedPasswordForRollingBackup = Optional.of(password);
  }

  /**
   * Perform a rolling backup using the last remembered wallet summary and credentials
   */
  private void performRollingBackup() {
    if (rememberedWalletSummaryForRollingBackup.isPresent() && rememberedPasswordForRollingBackup.isPresent()) {
      log.debug("Performing a rolling backup");

      try {
        BackupManager.INSTANCE.createRollingBackup(rememberedWalletSummaryForRollingBackup.get(), rememberedPasswordForRollingBackup.get());

        // Don't use anything remembered in the past at this point again
        // (This will miss anything newly remembered whilst the backup is taking place
        rememberedWalletSummaryForRollingBackup = Optional.absent();
        rememberedPasswordForRollingBackup = Optional.absent();
      } catch (IOException ioe) {
        log.error("Failed to perform rolling backup", ioe);
        // TODO handle exception (which is thrown inside the main runnable)
      }
    }
  }

  /**
   * Remember a wallet id and credentials.
   * This will be used at the next local zip backup.
   */
  public void rememberWalletIdAndPasswordForLocalZipBackup(WalletId walletId, CharSequence password) {
    rememberedWalletIdForLocalBackup = Optional.of(walletId);
    rememberedPasswordForLocalBackup = Optional.of(password);
  }

  /**
   * Perform a local zip backup
   */
  private void performLocalZipBackup() {
    if (rememberedWalletIdForLocalBackup.isPresent() && rememberedPasswordForLocalBackup.isPresent()) {
      log.debug("Performing a local zip backup");

      try {
        BackupManager.INSTANCE.createLocalBackup(rememberedWalletIdForLocalBackup.get(), rememberedPasswordForLocalBackup.get());

        // Don't use anything remembered in the past at this point again
        // (This will miss anything newly remembered whilst the backup is taking place
        rememberedWalletIdForLocalBackup = Optional.absent();
        rememberedPasswordForLocalBackup = Optional.absent();
      } catch (IOException ioe) {
        log.error("Failed to perform local backup", ioe);
        // TODO handle exception (which is thrown inside the main runnable)
      }
    }
  }

  /**
   * Remember a wallet id and credentials.
   * This will be used at the next cloud zip backup.
   */
  public void rememberWalletIdAndPasswordForCloudZipBackup(WalletId walletId, CharSequence password) {
    rememberedWalletIdForCloudBackup = Optional.of(walletId);
    rememberedPasswordForCloudBackup = Optional.of(password);
  }

  /**
   * Perform a cloud zip backup
   */
  private void performCloudZipBackup() {
    if (rememberedWalletIdForCloudBackup.isPresent() && rememberedPasswordForCloudBackup.isPresent()) {
      log.debug("Performing a cloud zip backup");

      try {
        BackupManager.INSTANCE.createCloudBackup(rememberedWalletIdForCloudBackup.get(), rememberedPasswordForCloudBackup.get());

        // Don't use anything remembered in the past at this point again
        // (This will miss anything newly remembered whilst the backup is taking place
        rememberedWalletIdForCloudBackup = Optional.absent();
        rememberedPasswordForCloudBackup = Optional.absent();
      } catch (IOException ioe) {
        log.error("Failed to perform cloud backup", ioe);
        CoreEvents.fireEnvironmentEvent(EnvironmentSummary.newBackupFailed());
      }
    } else {
      log.debug("Cannot perform cloud backup as no remembered wallet id or password is available");
    }
  }

  /**
   * Set whether backups are enabled.
   * Can be called on any thread.
   * Only affects backups starting subsequently
   *
   * @param backupsAreEnabled whether backups should be performed (true) or not (false)
   */
  public void setBackupsAreEnabled(boolean backupsAreEnabled) {
    this.backupsAreEnabled = backupsAreEnabled;
  }

  /**
   * Indicates whether backups are currently running in the main scheduled loop
   *
   * @return true if a backup is running in the main scheduled loop
   */
  public boolean isBackupsAreRunning() {
    return backupsAreRunning;
  }

  public void setPerformCloudBackupAtNextTick(boolean performCloudBackupAtNextTick) {
    this.performCloudBackupAtNextTick = performCloudBackupAtNextTick;
  }
}