package org.multibit.hd.ui.fest.test_cases;

import com.google.common.base.Optional;
import org.junit.Ignore;
import org.junit.Test;
import org.multibit.hd.core.dto.WalletMode;
import org.multibit.hd.core.dto.WalletSummary;
import org.multibit.hd.testing.hardware_wallet_fixtures.HardwareWalletFixture;
import org.multibit.hd.testing.hardware_wallet_fixtures.HardwareWalletFixtures;
import org.multibit.hd.ui.fest.requirements.keepkey.*;
import org.multibit.hd.ui.fest.requirements.standard.PaymentsScreenRequirements;
import org.multibit.hd.ui.fest.requirements.standard.QuickUnlockEmptyWalletFixtureRequirements;
import org.multibit.hd.ui.fest.requirements.standard.SendRequestScreenRequirements;
import org.multibit.hd.ui.fest.requirements.standard.WelcomeWizardCreateWallet_ro_RO_Requirements;
import org.multibit.hd.ui.fest.use_cases.keepkey.KeepKeySendBitcoinKeepKeyRequirements;

/**
 * <p>KeepKey hardware wallet functional tests</p>
 *
 * @since 0.0.1
 */
public class KeepKeyFestTest extends AbstractFestTest {

  private static final WalletMode walletMode = WalletMode.KEEP_KEY;

  /**
   * <p>Verify the following:</p>
   * <ul>
   * <li>Start with fresh wallet fixture (cold)</li>
   * <li>Attach a KeepKey when prompted</li>
   * <li>Create a KeepKey hard wallet</li>
   * <li>Unlock the wallet</li>
   * </ul>
   */
  @Test
  public void verifyCreateHardwareWallet_ColdStart() throws Exception {

    // Prepare an empty and attached KeepKey device that will be initialised
    HardwareWalletFixture hardwareWalletFixture = HardwareWalletFixtures.newWipedFixture(walletMode);

    // Start with a completely empty random application directory
    arrangeFresh(Optional.of(hardwareWalletFixture));

    // Verify
    CreateKeepKeyHardwareWalletColdStartRequirements.verifyUsing(window, hardwareWalletFixture);

  }

  /**
   * <p>Verify the following:</p>
   * <ul>
   * <li>Start with empty wallet fixture (warm)</li>
   * <li>Create a new KeepKey hardware wallet</li>
   * <li>Unlock the wallet</li>
   * </ul>
   */
  @Test
  public void verifyCreateHardwareWallet_WarmStart() throws Exception {

    // Prepare an empty and attached KeepKey device that will be initialised
    HardwareWalletFixture hardwareWalletFixture = HardwareWalletFixtures.newWipedFixture(walletMode);

    // Start with the empty hardware wallet fixture
    arrangeEmpty(Optional.of(hardwareWalletFixture));

    // Verify
    CreateKeepKeyHardwareWalletWarmStartRequirements.verifyUsing(window, hardwareWalletFixture);

  }

  /**
   * <p>Verify the following:</p>
   * <ul>
   * <li>Start with empty wallet fixture (warm)</li>
   * <li>Unlock wallet</li>
   * </ul>
   */
  @Test
  public void verifyUnlockHardwareWallet_WarmStart() throws Exception {

    // Prepare an initialised and attached KeepKey device that will be unlocked
    HardwareWalletFixture hardwareWalletFixture = HardwareWalletFixtures.newInitialisedUnlockFixture(walletMode);

    // Start with the empty hardware wallet fixture
    arrangeEmpty(Optional.of(hardwareWalletFixture));

    // Verify
    UnlockKeepKeyHardwareWalletWarmStartRequirements.verifyUsing(window, hardwareWalletFixture);

  }

  /**
   * <p>Verify the following:</p>
   * <ul>
   * <li>Start with fresh wallet fixture (cold)</li>
   * <li>Unlock wallet</li>
   * </ul>
   */
  @Test
  public void verifyUnlockHardwareWallet_ColdStart() throws Exception {

    // Prepare an initialised and attached KeepKey device that will be unlocked
    HardwareWalletFixture hardwareWalletFixture = HardwareWalletFixtures.newInitialisedUnlockFixture(walletMode);

    // Start with a completely empty random application directory
    arrangeFresh(Optional.of(hardwareWalletFixture));

    // Verify
    UnlockKeepKeyHardwareWalletColdStartRequirements.verifyUsing(window, hardwareWalletFixture);

  }

  /**
   * <p>Verify the following:</p>
   * <ul>
   * <li>Start with empty wallet fixture (warm)</li>
   * <li>Detach KeepKey, see password screen, attach KeepKey, see PIN matrix, unlock</li>
   * </ul>
   */
  @Test
  public void verifyReattachHardwareWallet() throws Exception {

    // Prepare an initialised and attached KeepKey device that will be re-attached
    HardwareWalletFixture hardwareWalletFixture = HardwareWalletFixtures.newInitialisedReattachedFixture(walletMode);

    // Start with the empty hardware wallet fixture
    arrangeEmpty(Optional.of(hardwareWalletFixture));

    // Verify
    ReattachKeepKeyHardwareWalletRequirements.verifyUsing(window, hardwareWalletFixture);

  }

  /**
   * <p>Verify the following:</p>
   * <ul>
   * <li>Start with standard application directory</li>
   * <li>Show the PIN entry, unlock screen and restore from there. This FEST test creates a local backup</li>
   * </ul>
   */
  @Test
  public void verifyRestoreWithLocalBackup() throws Exception {

    // Prepare an initialised and attached KeepKey device that will be restored then unlocked
    HardwareWalletFixture hardwareWalletFixture = HardwareWalletFixtures.newInitialisedRestoreFixture(walletMode);

    // Start with the empty hardware wallet fixture
    WalletSummary walletSummary = arrangeEmpty(Optional.of(hardwareWalletFixture));

    // Verify up to the restore
    RestoreKeepKeyWarmStartRequirements.verifyUsing(window, hardwareWalletFixture);

    // Create a local backup so that there is something to load
    // (this is done after the initial keepKey dialog so that the
    // master public key has been returned)
    createLocalBackup(walletSummary);

    // Do the restore with the local backup available
    RestoreKeepKeyRestoreWithLocalBackupRequirements.verifyUsing(window, hardwareWalletFixture);

  }

  /**
   * <p>Verify the following:</p>
   * <ul>
   * <li>Start with empty wallet fixture (warm)</li>
   * <li>Unlock the wallet</li>
   * <li>Send and force a PIN request</li>
   * </ul>
   */
  @Test
  public void verifySendScreen() throws Exception {

    // Prepare an initialised and attached KeepKey device that will be restored then unlocked
    HardwareWalletFixture hardwareWalletFixture = HardwareWalletFixtures.newInitialisedUnlockFixture(walletMode);

    // Start with the empty hardware wallet fixture
    arrangeStandard(Optional.of(hardwareWalletFixture));

    // Verify up to unlock
    UnlockKeepKeyHardwareWalletWarmStartRequirements.verifyUsing(window, hardwareWalletFixture);

    // Verify send workflow
    KeepKeySendBitcoinKeepKeyRequirements.verifyUsing(window, hardwareWalletFixture);

  }

  /**
   * <p>Verify the following:</p>
   * <ul>
   * <li>Start with standard wallet fixture</li>
   * <li>Unlock wallet</li>
   * <li>Exercise the Send/Request screen</li>
   * </ul>
   */
  @Test
  public void verifySendRequestScreen() throws Exception {

    // Start with the standard hardware wallet fixture
    arrangeStandard(Optional.<HardwareWalletFixture>absent());

    // Unlock the wallet
    QuickUnlockEmptyWalletFixtureRequirements.verifyUsing(window);

    // Verify
    SendRequestScreenRequirements.verifyUsing(window);

  }

  /**
   * <p>Verify the following:</p>
   * <ul>
   * <li>Start with standard application directory</li>
   * <li>Show the unsupported firmware popover</li>
   * </ul>
   */
  @Test
  public void verifyUnsupportedFirmware() throws Exception {

    // Prepare an initialised and attached KeepKey device that will be restored then unlocked
    HardwareWalletFixture hardwareWalletFixture = HardwareWalletFixtures.newInitialisedUnsupportedFirmwareFixture(walletMode);

    // Start with the empty hardware wallet fixture
    arrangeStandard(Optional.of(hardwareWalletFixture));

    // Verify up to unlock
    UnlockKeepKeyHardwareWalletUnsupportedFirmwareRequirements.verifyUsing(window, hardwareWalletFixture);

  }

  /**
   * <p>Verify the following:</p>
   * <ul>
   * <li>Start with standard application directory</li>
   * <li>Show the deprecated firmware popover</li>
   * </ul>
   *
   * Currently there are no deprecated firmware versions
   */
  @Ignore
  public void verifyDeprecatedFirmware() throws Exception {

    // Prepare an initialised and attached KeepKey device that will be restored then unlocked
    HardwareWalletFixture hardwareWalletFixture = HardwareWalletFixtures.newInitialisedDeprecatedFirmwareFixture(walletMode);

    // Start with the empty hardware wallet fixture
    arrangeStandard(Optional.of(hardwareWalletFixture));

    // Verify up to unlock
    UnlockKeepKeyHardwareWalletDeprecatedFirmwareRequirements.verifyUsing(window, hardwareWalletFixture);

  }


  /**
   * <p>Verify the following:</p>
   * <ul>
   * <li>Start with standard wallet fixture</li>
   * <li>Unlock wallet</li>
   * <li>Exercise the Payments screen</li>
   * </ul>
   */
  @Test
  public void verifyPaymentsScreen() throws Exception {

    // Start with the standard hardware wallet fixture
    arrangeStandard(Optional.<HardwareWalletFixture>absent());

    // Unlock the wallet
    QuickUnlockEmptyWalletFixtureRequirements.verifyUsing(window);

    // Verify
    PaymentsScreenRequirements.verifyUsing(window);

  }

  /**
   * <p>Verify the following:</p>
   * <ul>
   * <li>Start with standard application directory</li>
   * <li>Use an initialised KeepKey with an unsupported configuration (e.g. passphrase)</li>
   * <li>Show the unsupported configuration "passphrase" popover</li>
   * </ul>
   */
  @Test
  public void verifyUnsupportedConfiguration_Passphrase() throws Exception {

    // Prepare an initialised and attached KeepKey device that will be attached
    HardwareWalletFixture hardwareWalletFixture = HardwareWalletFixtures.newInitialisedUnsupportedConfigurationPassphraseFixture(walletMode);

    // Start with the standard hardware wallet fixture
    arrangeStandard(Optional.of(hardwareWalletFixture));

    // Verify up to unlock
    UnlockKeepKeyHardwareWalletUnsupportedConfigurationPassphraseRequirements.verifyUsing(window, hardwareWalletFixture);

  }

  /**
   * <p>Verify the following:</p>
   * <ul>
   * <li>Start with fresh application directory</li>
   * <li>Create a wallet</li>
   * </ul>
   */
  @Test
  public void verifyCreateWallet_ro_RO_ColdStart() throws Exception {

    // Start with a completely empty random application directory
    arrangeFresh(Optional.<HardwareWalletFixture>absent());

    // Create a wallet through the welcome wizard
    WelcomeWizardCreateWallet_ro_RO_Requirements.verifyUsing(window);

  }

}
