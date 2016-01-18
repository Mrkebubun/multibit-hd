package org.multibit.hd.ui.fest.use_cases.keepkey;

import org.fest.swing.fixture.FrameFixture;
import org.multibit.hd.testing.hardware_wallet_fixtures.HardwareWalletFixture;
import org.multibit.hd.ui.fest.use_cases.AbstractHardwareWalletFestUseCase;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.wizards.credentials.CredentialsState;

import java.util.Map;

/**
 * <p>Use case to provide the following to FEST testing:</p>
 * <ul>
 * <li>Verify the Keepkey create wallet enter PIN panel (unsupported firmware)</li>
 * </ul>
 *
 * @since 0.0.5
 */
public class KeepKeyEnterPinUnsupportedFirmwareUseCase extends AbstractHardwareWalletFestUseCase {

  /**
   * @param window                The FEST window frame fixture
   * @param hardwareWalletFixture The hardware wallet fixture
   */
  public KeepKeyEnterPinUnsupportedFirmwareUseCase(FrameFixture window, HardwareWalletFixture hardwareWalletFixture) {
    super(window, hardwareWalletFixture);
  }

  @Override
  public void execute(Map<String, Object> parameters) {

    // Allow time for UI to catch up with events
    pauseForViewReset();

    // Check that the Keepkey enter new PIN panel view is showing
    window
      .label(MessageKey.PIN_TITLE.getKey())
      .requireVisible();

    // Check that all buttons are disabled
    window
      .button(CredentialsState.CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY.name() + ".button_1")
      .requireDisabled();
    window
      .button(CredentialsState.CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY.name() + ".button_2")
      .requireDisabled();
    window
      .button(CredentialsState.CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY.name() + ".button_3")
      .requireDisabled();
    window
      .button(CredentialsState.CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY.name() + ".button_4")
      .requireDisabled();
    window
      .button(CredentialsState.CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY.name() + ".button_5")
      .requireDisabled();
    window
      .button(CredentialsState.CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY.name() + ".button_6")
      .requireDisabled();
    window
      .button(CredentialsState.CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY.name() + ".button_7")
      .requireDisabled();
    window
      .button(CredentialsState.CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY.name() + ".button_8")
      .requireDisabled();
    window
      .button(CredentialsState.CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY.name() + ".button_9")
      .requireDisabled();
    window
      .button(CredentialsState.CREDENTIALS_ENTER_PIN_FROM_CIPHER_KEY.name() + ".button_delete")
      .requireDisabled();

    // 'Next' button
    window
      .button(MessageKey.PASSWORD_UNLOCK.getKey())
      .requireDisabled();

    // 'Exit' button
    window
      .button(MessageKey.EXIT.getKey())
      .requireEnabled();

  }
}
