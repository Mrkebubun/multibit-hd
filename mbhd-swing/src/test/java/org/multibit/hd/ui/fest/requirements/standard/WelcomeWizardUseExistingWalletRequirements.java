package org.multibit.hd.ui.fest.requirements.standard;

import com.google.common.collect.Maps;
import org.fest.swing.fixture.FrameFixture;
import org.multibit.hd.ui.fest.use_cases.standard.credentials.RestoreButtonUseCase;
import org.multibit.hd.ui.fest.use_cases.standard.environment.CloseDebugEnvironmentPopoverUseCase;
import org.multibit.hd.ui.fest.use_cases.standard.welcome_select.WelcomeSelectExistingWalletUseCase;

import java.util.Map;

/**
 * <p>FEST Swing UI test to provide:</p>
 * <ul>
 * <li>Select an existing wallet from the welcome wizard options</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class WelcomeWizardUseExistingWalletRequirements {

  public static void verifyUsing(FrameFixture window) {

    Map<String, Object> parameters = Maps.newHashMap();

    new CloseDebugEnvironmentPopoverUseCase(window).execute(parameters);

    // Start the restore process to access the welcome wizard
    new RestoreButtonUseCase(window).execute(parameters);

    // Select the "use existing wallet" option to trigger
    // a reset to the unlock screen
    new WelcomeSelectExistingWalletUseCase(window).execute(parameters);

  }
}
