package org.multibit.hd.ui.fest.use_cases.standard.sidebar.settings.exchange;

import org.fest.swing.fixture.FrameFixture;
import org.multibit.hd.ui.fest.use_cases.AbstractFestUseCase;
import org.multibit.hd.ui.languages.MessageKey;

import java.util.Map;

/**
 * <p>Use case to provide the following to FEST testing:</p>
 * <ul>
 * <li>Verify the "settings" screen exchange wizard shows</li>
 * </ul>
 * <p>Requires the "settings" screen to be showing</p>
 *
 * @since 0.0.1
 *
 */
public class VerifyExchangeNoneUseCase extends AbstractFestUseCase {

  public VerifyExchangeNoneUseCase(FrameFixture window) {
    super(window);
  }

  @Override
  public void execute(Map<String, Object> parameters) {

    // Click on "exchange"
    window
      .button(MessageKey.SHOW_EXCHANGE_WIZARD.getKey())
      .click();

    // Allow time for the exchange rate provider to render
    pauseForViewReset();

    // Verify the "exchange" wizard appears
    assertLabelText(MessageKey.EXCHANGE_SETTINGS_TITLE);

    // Verify cancel is present
    window
      .button(MessageKey.CANCEL.getKey())
      .requireVisible()
      .requireEnabled();

    // Verify that a local amount will show (Bitstamp)
    verifyLocalAmountShows();

    // Click on "exchange"
    window
      .button(MessageKey.SHOW_EXCHANGE_WIZARD.getKey())
      .click();

    // Verify that a local amount will hide (None)
    verifyLocalAmountHides();

  }

  /**
   * Select an exchange and verify that a local amount shows
   */
  private void verifyLocalAmountShows() {

    window
      .comboBox(MessageKey.EXCHANGE_RATE_PROVIDER.getKey())
      .selectItem("Bitstamp");

    // Verify that various components are available
    window
      .button(MessageKey.BROWSE.getKey())
      .requireEnabled();

    window
      .textBox(newNotShowingJTextBoxFixture(MessageKey.ENTER_ACCESS_CODE.getKey()))
      .requireNotVisible();

    window
      .comboBox(MessageKey.SELECT_LOCAL_CURRENCY.getKey())
      .requireEnabled();

    // Select the first currency
    window
      .comboBox(MessageKey.SELECT_LOCAL_CURRENCY.getKey())
      .selectItem(0);

    // Allow time for currency to verify (can be slow)
    pauseForPreparationDisplay();

    // Click Apply
    window
      .button(MessageKey.APPLY.getKey())
      .requireEnabled()
      .click();

    // Allow time for component to change
    pauseForComponentReset();

    // Verify the underlying screen is back
    window
      .button(MessageKey.SHOW_LANGUAGE_WIZARD.getKey())
      .requireVisible()
      .requireEnabled();

    // Verify that balance with a local currency is showing in the header
    // Commented out as there is no guarantee wallet sync has completed (showing the header)
    //assertDisplayAmount("header", "balance", true, true);

  }

  /**
   * Select "None" and verify the local amount hides
   */
  private void verifyLocalAmountHides() {

    // Select "None" from list - always first
    window
      .comboBox(MessageKey.EXCHANGE_RATE_PROVIDER.getKey())
      .selectItem(0);

    // Verify that various components are not available
    window
      .button(MessageKey.BROWSE.getKey())
      .requireDisabled();

    window
      .textBox(newNotShowingJTextBoxFixture(MessageKey.ENTER_ACCESS_CODE.getKey()))
      .requireNotVisible();

    // Click Apply
    window
      .button(MessageKey.APPLY.getKey())
      .requireEnabled()
      .click();

    // Allow time for component to change
    pauseForComponentReset();

    // Verify the underlying screen is back
    window
      .button(MessageKey.SHOW_LANGUAGE_WIZARD.getKey())
      .requireVisible()
      .requireEnabled();

    // Verify that balance with no local currency is showing in the header
    // Commented out as there is no guarantee about sync status
    // assertDisplayAmount("header","balance", true, false);
  }
}
