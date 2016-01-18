package org.multibit.hd.ui.fest.use_cases.trezor;

import org.fest.swing.fixture.FrameFixture;
import org.fest.swing.timing.Timeout;
import org.multibit.hd.testing.hardware_wallet_fixtures.HardwareWalletFixture;
import org.multibit.hd.ui.fest.use_cases.AbstractHardwareWalletFestUseCase;
import org.multibit.hd.ui.languages.MessageKey;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>Use case to provide the following to FEST testing:</p>
 * <ul>
 * <li>Verify the Trezor "enter wallet details" panel view</li>
 * </ul>
 *
 * @since 0.0.5
 */
public class TrezorEnterWalletDetailsUseCase extends AbstractHardwareWalletFestUseCase {

  /**
   * @param window                The FEST window frame fixture
   * @param hardwareWalletFixture The hardware wallet fixture
   */
  public TrezorEnterWalletDetailsUseCase(FrameFixture window, HardwareWalletFixture hardwareWalletFixture) {
    super(window, hardwareWalletFixture);
  }

  @Override
  public void execute(Map<String, Object> parameters) {

    // Allow time for hardware events to propagate
    pauseForHardwareEvent();

    // Check that the Trezor enter wallet details screen is showing
    window
      .label(MessageKey.CREATE_HARDWARE_WALLET_ENTER_DETAILS_TITLE.getKey())
      .requireVisible();

    window
      .label(MessageKey.ENTER_HARDWARE_LABEL.getKey())
      .requireVisible();

    window
      .label(MessageKey.SEED_SIZE.getKey())
      .requireVisible();

    window
      .textBox(MessageKey.ENTER_HARDWARE_LABEL.getKey())
      .requireVisible();

    window
      .comboBox(MessageKey.SEED_SIZE.getKey())
      .requireVisible();

    // Check the 'Next' button is present and click it
    window
      .button(MessageKey.NEXT.getKey())
      .requireEnabled(Timeout.timeout(3, TimeUnit.SECONDS))
      .click();

  }
}
