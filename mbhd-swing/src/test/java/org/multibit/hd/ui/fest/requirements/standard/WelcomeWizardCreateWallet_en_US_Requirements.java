package org.multibit.hd.ui.fest.requirements.standard;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Uninterruptibles;
import org.fest.swing.fixture.FrameFixture;
import org.multibit.hd.ui.fest.use_cases.standard.create_wallet.*;
import org.multibit.hd.ui.fest.use_cases.standard.credentials.QuickUnlockWalletUseCase;
import org.multibit.hd.ui.fest.use_cases.standard.credentials.UnlockReportUseCase;
import org.multibit.hd.ui.fest.use_cases.standard.environment.CloseDebugEnvironmentPopoverUseCase;
import org.multibit.hd.ui.fest.use_cases.standard.sidebar.manage_wallet.ShowManageWalletScreenUseCase;
import org.multibit.hd.ui.fest.use_cases.standard.welcome_select.AcceptLicenceUseCase;
import org.multibit.hd.ui.fest.use_cases.standard.welcome_select.AttachHardwareWalletUseCase;
import org.multibit.hd.ui.fest.use_cases.standard.welcome_select.WelcomeSelectCreateWalletUseCase;
import org.multibit.hd.ui.fest.use_cases.standard.welcome_select.WelcomeSelectLanguage_en_US_UseCase;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>FEST Swing UI test to provide:</p>
 * <ul>
 * <li>Create wallet using welcome wizard using en_US as the base language</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class WelcomeWizardCreateWallet_en_US_Requirements {

  public static void verifyUsing(FrameFixture window) {

    Map<String, Object> parameters = Maps.newHashMap();

    new CloseDebugEnvironmentPopoverUseCase(window).execute(parameters);

    new AcceptLicenceUseCase(window).execute(parameters);

    // Use the en_US language
    new WelcomeSelectLanguage_en_US_UseCase(window).execute(parameters);

    new AttachHardwareWalletUseCase(window).execute(parameters);

    new WelcomeSelectCreateWalletUseCase(window).execute(parameters);

    new CreateWalletPreparationUseCase(window).execute(parameters);

    new CreateWalletSelectBackupLocationWalletUseCase(window).execute(parameters);

    new CreateWalletSeedPhraseUseCase(window).execute(parameters);
    new CreateWalletConfirmSeedPhraseUseCase(window).execute(parameters);

    Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

    new CreateWalletCreatePasswordUseCase(window).execute(parameters);

    Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

    new CreateWalletReportUseCase(window).execute(parameters);

    // Hand over to the credentials wizard

    Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

    new QuickUnlockWalletUseCase(window).execute(parameters);

    new UnlockReportUseCase(window).execute(parameters);

    // Show the manage wallets screen
    new ShowManageWalletScreenUseCase(window).execute(parameters);

    // TODO at this point the close MBHD screen is shown and MBHD is shutting down.
    // Test that the cloud backup was successful
    //new ShowThenCancelEditWalletUseCase(window).execute(parameters);

  }
}
