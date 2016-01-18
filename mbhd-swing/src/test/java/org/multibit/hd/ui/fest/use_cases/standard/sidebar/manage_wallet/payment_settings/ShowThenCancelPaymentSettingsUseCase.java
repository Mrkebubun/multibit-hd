package org.multibit.hd.ui.fest.use_cases.standard.sidebar.manage_wallet.payment_settings;

import org.fest.swing.fixture.FrameFixture;
import org.multibit.hd.ui.fest.use_cases.AbstractFestUseCase;
import org.multibit.hd.ui.languages.MessageKey;

import java.util.Map;

/**
 * <p>Use case to provide the following to FEST testing:</p>
 * <ul>
 * <li>Verify the "manage wallet" screen payment settings wizard shows</li>
 * </ul>
 * <p>Requires the "manage wallet" screen to be showing</p>
 *
 * @since 0.0.1
 *
 */
public class ShowThenCancelPaymentSettingsUseCase extends AbstractFestUseCase {

  public ShowThenCancelPaymentSettingsUseCase(FrameFixture window) {
    super(window);
  }

  @Override
  public void execute(Map<String, Object> parameters) {

    // Click on "payment settings"
    window
      .button(MessageKey.SHOW_PAYMENT_SETTINGS_WIZARD.getKey())
      .click();

    // Verify the "payment settings" wizard appears
    assertLabelText(MessageKey.PAYMENT_SETTINGS_TITLE);

    // Verify cancel is present
    window
      .button(MessageKey.CANCEL.getKey())
      .requireVisible()
      .requireEnabled();

    // Verify next is present
    window
      .button(MessageKey.APPLY.getKey())
      .requireVisible()
      .requireEnabled();

    // Click Cancel
    window
      .button(MessageKey.CANCEL.getKey())
      .click();

    // Verify the underlying screen is back
    window
      .button(MessageKey.SHOW_EDIT_WALLET_WIZARD.getKey())
      .requireVisible()
      .requireEnabled();

  }

}
