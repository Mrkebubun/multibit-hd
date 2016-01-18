package org.multibit.hd.ui.views.components;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import org.bitcoinj.core.Coin;
import org.multibit.hd.core.dto.CoreMessageKey;
import org.multibit.hd.core.dto.Recipient;
import org.multibit.hd.core.dto.WalletMode;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.hardware.core.HardwareWalletService;
import org.multibit.hd.ui.MultiBitUI;
import org.multibit.hd.ui.languages.Languages;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.utils.HtmlUtils;
import org.multibit.hd.ui.views.animations.RotatingIcon;
import org.multibit.hd.ui.views.components.display_amount.DisplayAmountStyle;
import org.multibit.hd.ui.views.components.labels.TitleLabel;
import org.multibit.hd.ui.views.fonts.AwesomeDecorator;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;
import org.multibit.hd.ui.views.fonts.TitleFontDecorator;
import org.multibit.hd.ui.views.themes.Themes;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * <p>Utility to provide the following to UI:</p>
 * <ul>
 * <li>Provision of localised buttons</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class Labels {

  /**
   * Utilities have no public constructor
   */
  private Labels() {
  }


  /**
   * @param key    The resource key for the language message text
   * @param values The data values for token replacement in the message text
   *
   * @return A new label with default styling
   */
  public static JLabel newLabel(MessageKey key, Object... values) {

    JLabel label = new JLabel(Languages.safeText(key, values));

    // Ensure it is accessible
    AccessibilityDecorator.apply(label, key);

    // Apply theme
    label.setForeground(Themes.currentTheme.text());

    return label;

  }

  /**
   * @param key    The resource key for the language message text
   * @param values The data values for token replacement in the message text
   *
   * @return A new label with default styling
   */
  public static JLabel newLabel(CoreMessageKey key, Object... values) {

    JLabel label = new JLabel(Languages.safeText(key, values));

    // Ensure it is accessible
    AccessibilityDecorator.apply(label, key);

    // Apply theme
    label.setForeground(Themes.currentTheme.text());

    return label;

  }

  /**
   * @return A new blank label with default styling
   */
  public static JLabel newBlankLabel() {

    JLabel label = new JLabel("");

    // Apply theme
    label.setForeground(Themes.currentTheme.text());

    return label;

  }

  /**
   * <p>A convenience method for creating a themed label with direct text. This is not internationalised.</p>
   *
   * @return A new wrapping value label with default styling for placing direct text
   */
  public static JLabel newValueLabel(String value) {

    String htmlText = HtmlUtils.localiseWithLineBreaks(new String[]{value});
    JLabel label = new JLabel(htmlText);

    // Apply theme
    label.setForeground(Themes.currentTheme.text());

    return label;

  }

  /**
   * @param key    The message key
   * @param values The substitution values if applicable
   *
   * @return A new label with appropriate font, theme and alignment for a wizard panel view title
   */
  public static TitleLabel newTitleLabel(MessageKey key, Object... values) {

    String[] titleText = new String[]{Languages.safeText(key, values)};

    String htmlText = HtmlUtils.localiseWithCenteredLinedBreaks(titleText);

    TitleLabel label = new TitleLabel(htmlText);

    // Ensure it is accessible
    AccessibilityDecorator.apply(label, key);

    // Font
    TitleFontDecorator.apply(label, MultiBitUI.WIZARD_TITLE_FONT_SIZE);

    // Theme
    label.setForeground(Themes.currentTheme.text());

    return label;

  }

  /**
   * <p>Create a new label with appropriate font/theme for a note. Interpret the contents of the text as Markdown for HTML translation.</p>
   *
   * @param keys   The message keys for each line referencing simple HTML (standard wrapping/breaking elements like {@literal <html></html>} and {@literal <br/>} will be provided)
   * @param values The substitution values for each line if applicable
   *
   * @return A new label with HTML formatting to correctly render the line break and contents
   */
  static JLabel newNoteLabel(CoreMessageKey[] keys, Object[][] values) {

    String[] lines = new String[keys.length];
    for (int i = 0; i < keys.length; i++) {
      if (values.length > 0) {
        // Substitution is required
        lines[i] = Languages.safeText(keys[i], values[i]);
      } else {
        // Key only
        lines[i] = Languages.safeText(keys[i]);
      }
    }

    // Wrap in HTML to ensure LTR/RTL and line breaks are respected
    JLabel label = new JLabel(HtmlUtils.localiseWithLineBreaks(lines));

    // Ensure it is accessible
    AccessibilityDecorator.apply(label, keys[0]);

    // Theme
    label.setForeground(Themes.currentTheme.text());

    return label;

  }

  /**
   * <p>Create a new label with appropriate font/theme for a note. Interpret the contents of the text as Markdown for HTML translation.</p>
   *
   * @param key    The message key referencing simple HTML (standard wrapping/breaking elements like {@literal <html></html>} and {@literal <br/>} will be provided)
   * @param values The substitution values if applicable
   *
   * @return A new label with HTML formatting to correctly render the line break and contents
   */
  public static JLabel newNoteLabel(MessageKey key, @Nullable Object[] values) {

    String line;
    if (values != null && values.length > 0) {
      // Substitution is required
      line = Languages.safeText(key, values);
    } else {
      // Key only
      line = Languages.safeText(key);
    }

    // Wrap in HTML to ensure LTR/RTL and line breaks are respected
    JLabel label = new JLabel(HtmlUtils.localiseWithLineBreaks(new String[]{line}));

    // Ensure it is accessible
    AccessibilityDecorator.apply(label, key);

    // Theme
    label.setForeground(Themes.currentTheme.text());

    return label;

  }

  /**
   * <p>Create a new label with appropriate font/theme for a note. Interpret the contents of the text as Markdown for HTML translation.</p>
   *
   * @param keys   The message keys for each line referencing simple HTML (standard wrapping/breaking elements like {@literal <html></html>} and {@literal <br/>} will be provided)
   * @param values The substitution values for each line if applicable
   *
   * @return A new label with HTML formatting to correctly render the line break and contents
   */
  public static JLabel newNoteLabel(MessageKey[] keys, Object[][] values) {

    String[] lines = new String[keys.length];
    for (int i = 0; i < keys.length; i++) {
      if (values.length > 0) {
        // Substitution is required
        lines[i] = Languages.safeText(keys[i], values[i]);
      } else {
        // Key only
        lines[i] = Languages.safeText(keys[i]);
      }
    }

    // Wrap in HTML to ensure LTR/RTL and line breaks are respected
    JLabel label = new JLabel(HtmlUtils.localiseWithLineBreaks(lines));

    // Ensure it is accessible
    AccessibilityDecorator.apply(label, keys[0]);

    // Theme
    label.setForeground(Themes.currentTheme.text());

    return label;

  }

  /**
   * <p>A "status" label sets a label with a check or cross icon</p>
   *
   * @param key    The message key
   * @param values The substitution values
   * @param status True if a check icon is required, false for a cross
   *
   * @return A new label with icon binding to allow the AwesomeDecorator to update it
   */
  public static JLabel newStatusLabel(MessageKey key, Object[] values, boolean status) {
    return newStatusLabel(Optional.of(key), values, Optional.of(status));
  }

  /**
   * <p>A "status" label sets a label with no icon, a check or cross icon</p>
   *
   * @param key    The message key - if not present then empty text is put on the label
   * @param values The substitution values
   * @param status True if a check icon is required, false for a cross
   *
   * @return A new label with icon binding to allow the AwesomeDecorator to update it
   */
  public static JLabel newStatusLabel(Optional<MessageKey> key, Object[] values, Optional<Boolean> status) {

    JLabel label;

    if (key.isPresent()) {
      label = newLabel(key.get(), values);
    } else {
      label = newBlankLabel();
    }

    LabelDecorator.applyStatusLabel(label, status);

    return label;
  }

  /**
   * <p>A "status" label sets a label with no icon, a check or cross icon</p>
   *
   * @param key    The message key - if not present then empty text is put on the label
   * @param values The substitution values
   * @param status True if a check icon is required, false for a cross
   *
   * @return A new label with icon binding to allow the AwesomeDecorator to update it
   */
  public static JLabel newCoreStatusLabel(Optional<CoreMessageKey> key, Object[] values, Optional<Boolean> status) {

    JLabel label;

    if (key.isPresent()) {
      label = newLabel(key.get(), values);
    } else {
      label = newBlankLabel();
    }

    LabelDecorator.applyStatusLabel(label, status);

    return label;
  }

  /**
   * <p>An "icon" label sets a label with an icon in the leading position. Useful for lists of notes.</p>
   *
   * @param icon   The icon to place in the leading position
   * @param key    The message key - if not present then empty text is put on the label
   * @param values The substitution values
   *
   * @return A new label with icon binding to allow the AwesomeDecorator to update it
   */
  public static JLabel newIconLabel(AwesomeIcon icon, Optional<MessageKey> key, Object[] values) {

    JLabel label;

    if (key.isPresent()) {
      label = newLabel(key.get(), values);
    } else {
      label = newBlankLabel();
    }

    AwesomeDecorator.bindIcon(icon, label, false, MultiBitUI.NORMAL_ICON_SIZE);

    return label;
  }


  /**
   * @param image The optional image
   *
   * @return A new label with the image or a placeholder if not present
   */
  public static JLabel newImageLabel(Optional<BufferedImage> image) {

    if (image.isPresent()) {
      JLabel label = new JLabel(new ImageIcon(image.get()));

      // Apply theme
      label.setForeground(Themes.currentTheme.text());

      return label;
    }

    // Fall back to a default image
    JLabel label = newBlankLabel();

    AwesomeDecorator.applyIcon(AwesomeIcon.USER, label, true, MultiBitUI.LARGE_ICON_SIZE);

    return label;

  }

  /**
   * @param panelName The panel name (used as the basis of the unique FEST name)
   * @param status    True if the status is "good"
   *
   * @return A new "verification" status label (confirms user has done something right)
   */
  public static JLabel newVerificationStatus(String panelName, boolean status) {

    JLabel label = newStatusLabel(MessageKey.VERIFICATION_STATUS, null, status);

    // There could be many verification labels on a single panel so provide a unique name
    // See AbstractFestUseCase for more details
    label.setName(panelName + "." + MessageKey.VERIFICATION_STATUS.getKey());

    return label;

  }

  /**
   * @param status True if the status is "good"
   *
   * @return A new "validity" status label (confirms user has made a valid combination)
   */
  public static JLabel newErrorStatus(boolean status) {

    return newStatusLabel(MessageKey.ERROR, null, status);
  }

  /**
   * @param status True if the status is "good"
   *
   * @return A new "seed phrase created" status label
   */
  public static JLabel newSeedPhraseCreatedStatus(boolean status) {
    return newStatusLabel(MessageKey.SEED_PHRASE_CREATED_STATUS, null, status);
  }

  /**
   * @param status True if the address is acceptable (i.e. not mine)
   *
   * @return A new "address is mine" status label
   */
  public static JLabel newAddressIsMineStatusLabel(boolean status) {
    return newStatusLabel(MessageKey.ADDRESS_IS_MINE_STATUS, null, status);
  }

  /**
   * @param status True if the status is "good"
   *
   * @return A new "wallet credentials created" status label
   */
  public static JLabel newWalletPasswordCreatedStatus(boolean status) {
    return newStatusLabel(MessageKey.WALLET_PASSWORD_CREATED_STATUS, null, status);
  }

  /**
   * @param status True if the status is "good"
   *
   * @return A new "wallet created" status label
   */
  public static JLabel newWalletCreatedStatus(boolean status) {
    return newStatusLabel(MessageKey.WALLET_CREATED_STATUS, null, status);
  }

  /**
   * @return A new "passwordChanged" status label
   */
  public static JLabel newPasswordChangedStatus() {
    return newStatusLabel(Optional.<MessageKey>absent(), new Object[]{}, Optional.<Boolean>absent());
  }

  /**
   * @param status True if the status is "good"
   *
   * @return A new "backup location" status label
   */
  public static JLabel newBackupLocationStatus(boolean status) {
    return newStatusLabel(MessageKey.BACKUP_LOCATION_STATUS, null, status);
  }

  /**
   * @param status True if the status is "good"
   *
   * @return A new "CA certs installed" status label
   */
  public static JLabel newCACertsInstalledStatus(boolean status) {
    return newStatusLabel(MessageKey.CACERTS_INSTALLED_STATUS, null, status);
  }

  /**
   * @param status True if the status is "good"
   *
   * @return A new "synchronizing" status label
   */
  public static JLabel newSynchronizingStatus(boolean status) {
    return newStatusLabel(MessageKey.SYNCHRONIZING_STATUS, null, status);
  }

  /**
   * @param color The spinner color
   * @param size  The size in pixels of the target component
   *
   * @return A new "spinner" label (indicates that something is happening asynchronously)
   */
  public static JLabel newSpinner(Color color, int size) {

    // The container label
    final JLabel label = newBlankLabel();

    final RotatingIcon rotatingIcon = new RotatingIcon(AwesomeDecorator.createIcon(
      AwesomeIcon.REFRESH,
      color,
      size
    ), label);

    label.setIcon(rotatingIcon);

    // Require a small border when placing in a central position
    label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    return label;
  }

  /**
   * @return A new "select language" label
   */
  public static JLabel newSelectLanguageLabel() {

    JLabel label = Labels.newLabel(MessageKey.DISPLAY_LANGUAGE);

    AwesomeDecorator.applyIcon(
      AwesomeIcon.GLOBE,
      label,
      true,
      MultiBitUI.NORMAL_PLUS_ICON_SIZE
    );

    return label;
  }

  /**
   * @return A new "show balance" label
   */
  public static JLabel newShowBalance() {

    return Labels.newLabel(MessageKey.SHOW_BALANCE);

  }

  /**
   * @return A new "Block explorer" label
   */
  public static JLabel newBlockExplorer() {

    return Labels.newLabel(MessageKey.BLOCK_EXPLORER);

  }

  /**
   * @return A new "select theme" label
   */
  public static JLabel newSelectTheme() {

    return Labels.newLabel(MessageKey.SELECT_THEME);

  }

  /**
   * @return A new "select decimal separator" label
   */
  public static JLabel newSelectDecimal() {

    return Labels.newLabel(MessageKey.SELECT_DECIMAL_SEPARATOR);
  }

  /**
   * @return A new "select grouping separator" label
   */
  public static JLabel newSelectGrouping() {

    return Labels.newLabel(MessageKey.SELECT_GROUPING_SEPARATOR);
  }

  /**
   * @return A new "select local currency symbol" label
   */
  public static JLabel newLocalSymbol() {

    return Labels.newLabel(MessageKey.SELECT_LOCAL_SYMBOL);
  }

  /**
   * @return A new "select local currency code" label
   */
  public static JLabel newLocalCurrency() {

    return Labels.newLabel(MessageKey.SELECT_LOCAL_CURRENCY);
  }

  /**
   * @return A new "enter access code" label (for API keys)
   */
  public static JLabel newApiKey() {

    JLabel label = Labels.newLabel(MessageKey.ENTER_ACCESS_CODE);
    label.setName("exchange_" + MessageKey.ENTER_ACCESS_CODE.getKey());
    return label;
  }

  /**
   * @return A new "select local Bitcoin symbol" label
   */
  public static JLabel newBitcoinSymbol() {

    return Labels.newLabel(MessageKey.SELECT_BITCOIN_SYMBOL);
  }

  /**
   * @return A new "select placement" label
   */
  public static JLabel newPlacement() {

    return Labels.newLabel(MessageKey.SELECT_PLACEMENT);
  }

  /**
   * @return A new "example" label
   */
  public static JLabel newExample() {

    return Labels.newLabel(MessageKey.EXAMPLE);
  }

  /**
   * @return A new "plus unconfirmed" label
   */
  public static JLabel newPlusUnconfirmed() {
    JLabel label = Labels.newLabel(MessageKey.PLUS_UNCONFIRMED);
    label.setForeground(Themes.currentTheme.headerPanelText());
    label.setFont(label.getFont().deriveFont(MultiBitUI.BALANCE_HEADER_SMALL_FONT_SIZE));
    return label;
  }

  /**
   * @return A new "version" label
   */
  public static JLabel newVersion() {

    return Labels.newLabel(MessageKey.VERSION);
  }

  /**
   * @return A new "select exchange rate provider" label
   */
  public static JLabel newSelectExchangeRateProvider() {

    return Labels.newLabel(MessageKey.SELECT_EXCHANGE_RATE_PROVIDER);
  }

  /**
   * <p>The balance labels</p>
   * <ul>
   * <li>[0]: Primary value, possibly decorated with leading symbol/code, to 2dp</li>
   * <li>[1]: Secondary value covering remaining decimal places</li>
   * <li>[2]: Placeholder for trailing symbol/code</li>
   * <li>[3]: Localised exchange rate display</li>
   * </ul>
   *
   * @param style    The display style to use depending on the context
   * @param festName The FEST name to use when adding accessibility
   *
   * @return A new collection of labels that together form a balance display
   */
  public static JLabel[] newBalanceLabels(DisplayAmountStyle style, String festName) {

    Preconditions.checkNotNull(style, "'style' must be present");

    JLabel leadingBalanceLabel = newBlankLabel();
    JLabel primaryBalanceLabel = newBlankLabel();
    JLabel secondaryBalanceLabel = newBlankLabel();
    JLabel trailingSymbolLabel = newBlankLabel();
    JLabel exchangeLabel = newBlankLabel();

    // Add FEST information (accessibility is covered at the overall panel level)
    leadingBalanceLabel.setName(festName + ".leading_balance");
    primaryBalanceLabel.setName(festName + ".primary_balance");
    secondaryBalanceLabel.setName(festName + ".secondary_balance");
    trailingSymbolLabel.setName(festName + ".trailing_symbol");
    exchangeLabel.setName(festName + ".exchange");

    // Font
    final Font largeFont;
    final Font normalFont;

    final Color textColor;

    switch (style) {
      case HEADER:
        largeFont = primaryBalanceLabel.getFont().deriveFont(MultiBitUI.BALANCE_HEADER_LARGE_FONT_SIZE);
        normalFont = primaryBalanceLabel.getFont().deriveFont(MultiBitUI.BALANCE_HEADER_NORMAL_FONT_SIZE);
        textColor = Themes.currentTheme.headerPanelText();
        break;
      case HEADER_SMALL:
        largeFont = primaryBalanceLabel.getFont().deriveFont(MultiBitUI.BALANCE_HEADER_SMALL_FONT_SIZE);
        normalFont = primaryBalanceLabel.getFont().deriveFont(MultiBitUI.BALANCE_HEADER_SMALL_FONT_SIZE);
        textColor = Themes.currentTheme.headerPanelText();
        break;
      case TRANSACTION_DETAIL_AMOUNT:
        largeFont = primaryBalanceLabel.getFont().deriveFont(Font.BOLD, MultiBitUI.BALANCE_TRANSACTION_LARGE_FONT_SIZE);
        normalFont = primaryBalanceLabel.getFont().deriveFont(Font.BOLD, MultiBitUI.BALANCE_TRANSACTION_NORMAL_FONT_SIZE);
        textColor = Themes.currentTheme.text();
        break;
      case FEE_AMOUNT:
        largeFont = primaryBalanceLabel.getFont().deriveFont(Font.BOLD, MultiBitUI.BALANCE_FEE_NORMAL_FONT_SIZE);
        normalFont = primaryBalanceLabel.getFont().deriveFont(Font.BOLD, MultiBitUI.BALANCE_FEE_NORMAL_FONT_SIZE);
        textColor = Themes.currentTheme.text();
        break;
      case PLAIN:
        largeFont = primaryBalanceLabel.getFont().deriveFont(Font.PLAIN, MultiBitUI.BALANCE_FEE_NORMAL_FONT_SIZE);
        normalFont = primaryBalanceLabel.getFont().deriveFont(Font.PLAIN, MultiBitUI.BALANCE_FEE_NORMAL_FONT_SIZE);
        textColor = Themes.currentTheme.text();
        break;
      default:
        throw new IllegalStateException("Unknown style:" + style.name());
    }

    leadingBalanceLabel.setFont(largeFont);
    leadingBalanceLabel.setForeground(textColor);

    primaryBalanceLabel.setFont(largeFont);
    primaryBalanceLabel.setForeground(textColor);

    secondaryBalanceLabel.setFont(normalFont);
    secondaryBalanceLabel.setForeground(textColor);

    trailingSymbolLabel.setFont(largeFont);
    trailingSymbolLabel.setForeground(textColor);

    exchangeLabel.setFont(normalFont);
    exchangeLabel.setForeground(textColor);

    // Theme
    if (style != DisplayAmountStyle.PLAIN) {
      secondaryBalanceLabel.setForeground(Themes.currentTheme.fadedText());
    }

    return new JLabel[]{

      leadingBalanceLabel,
      primaryBalanceLabel,
      secondaryBalanceLabel,
      trailingSymbolLabel,
      exchangeLabel
    };

  }

  /**
   * @return A new "Amount" label
   */
  public static JLabel newAmount() {
    return newLabel(MessageKey.LOCAL_AMOUNT);
  }

  /**
   * @return A new "approximately" symbol
   */
  public static JLabel newApproximately() {

    JLabel label = newLabel(MessageKey.APPROXIMATELY);

    Font font = label.getFont().deriveFont(Font.BOLD, (float) MultiBitUI.NORMAL_ICON_SIZE);
    label.setFont(font);

    return label;
  }

  /**
   * @return A new "select folder" label
   */
  public static JLabel newSelectFolder() {

    return newLabel(MessageKey.SELECT_FOLDER);
  }

  /**
   * @param values The message key values
   *
   * @return A new "enter hardware label" label
   */
  public static JLabel newEnterHardwareLabel(Object... values) {
    return newLabel(MessageKey.ENTER_HARDWARE_LABEL, values);
  }

  /**
   * @return A new "enter current PIN"
   */
  public static JLabel newEnterCurrentPin() {

    return newLabel(MessageKey.ENTER_CURRENT_PIN);

  }

  /**
   * @return A new "enter new PIN"
   */
  public static JLabel newEnterNewPin() {

    return newLabel(MessageKey.ENTER_NEW_PIN);

  }

  /**
   * @return A new "confirm new PIN"
   */
  public static JLabel newConfirmNewPin() {

    return newLabel(MessageKey.CONFIRM_NEW_PIN);

  }

  /**
   * @return A new "enter PIN look at device"
   */
  public static JLabel newEnterPinLookAtDevice() {

    return newLabel(MessageKey.ENTER_PIN_LOOK_AT_DEVICE);

  }

  /**
   * @return A new "Enter credentials" label
   */
  public static JLabel newEnterPassword() {

    return newLabel(MessageKey.ENTER_PASSWORD);
  }

  /**
   * @return A new "Enter new credentials" label
   */
  public static JLabel newEnterNewPassword() {

    return newLabel(MessageKey.ENTER_NEW_PASSWORD);
  }

  /**
   * @return A new "Retype new credentials" label
   */
  public static JLabel newRetypeNewPassword() {

    return newLabel(MessageKey.RETYPE_NEW_PASSWORD);
  }

  /**
   * @return A new "You are about to send" message
   */
  public static JLabel newConfirmSendAmount() {

    return newLabel(MessageKey.CONFIRM_SEND_MESSAGE);
  }

  /**
   * @return A new "recipient" message
   */
  public static JLabel newRecipient() {
    return newLabel(MessageKey.RECIPIENT);
  }

  /**
   * @return A new "recipient summary" label
   */
  public static JLabel newRecipientSummary(Recipient recipient) {

    return newLabel(MessageKey.RECIPIENT_SUMMARY, recipient.getSummary());

  }

  /**
   * @return A new "transaction fee" message
   */
  public static JLabel newTransactionFee() {
    return newLabel(MessageKey.TRANSACTION_FEE);
  }

  /**
   * @return A new "Adjust transaction fee" message
   */
  public static JLabel newAdjustTransactionFee() {
    return newLabel(MessageKey.ADJUST_TRANSACTION_FEE);
  }

  /**
   * @return A new "Explain transaction fee 1" message
   */
  public static JLabel newExplainTransactionFee1() {
    return newNoteLabel(MessageKey.EXPLAIN_TRANSACTION_FEE1, null);
  }

  /**
   * @return A new "Explain transaction fee 2" message
   */
  public static JLabel newExplainTransactionFee2() {
    return newNoteLabel(MessageKey.EXPLAIN_TRANSACTION_FEE2, null);
  }

  /**
   * @return A new "Explain client fee 1" message
   */
  public static JLabel newExplainClientFee1(Coin clientFee) {
    return newNoteLabel(MessageKey.EXPLAIN_CLIENT_FEE1, new Object[]{String.valueOf(clientFee.longValue())});
  }

  /**
   * @return A new "Explain client fee 2" message
   */
  public static JLabel newExplainClientFee2() {
    return newNoteLabel(MessageKey.EXPLAIN_CLIENT_FEE2, null);
  }

  /**
   * @return A new "transaction hash" label
   */
  public static JLabel newTransactionHash() {
    return newValueLabel(Languages.safeText(MessageKey.TRANSACTION_HASH));
  }

  /**
   * @return A new "raw transaction" label
   */
  public static JLabel newRawTransaction() {
    return Labels.newValueLabel(Languages.safeText(MessageKey.RAW_TRANSACTION));
  }

  /**
   * @return A new "client fee" message
   */
  public static JLabel newClientFee() {
    return newLabel(MessageKey.CLIENT_FEE);
  }

  /**
   * @return A new "running total" message
   */
  public static JLabel newClientFeeRunningTotal() {
    return newLabel(MessageKey.CLIENT_FEE_RUNNING_TOTAL);
  }

  /**
   * @return A new "seed size" message
   */
  public static JLabel newSeedSize() {
    return newLabel(MessageKey.SEED_SIZE);
  }

  /**
   * @return A new "timestamp" message
   */
  public static JLabel newTimestamp() {
    return newLabel(MessageKey.TIMESTAMP);
  }

  /**
   * @return A new "seed phrase" message
   */
  public static JLabel newSeedPhrase() {
    return newLabel(MessageKey.SEED_PHRASE);
  }

  /**
   * @return A new "contact name" label
   */
  public static JLabel newName() {
    return newLabel(MessageKey.NAME);
  }

  /**
   * @return A new "contact email" label
   */
  public static JLabel newEmailAddress() {
    return newLabel(MessageKey.EMAIL_ADDRESS);
  }

  /**
   * @return A new "contact Bitcoin address" label
   */
  public static JLabel newBitcoinAddress() {
    return newLabel(MessageKey.BITCOIN_ADDRESS);
  }

  /**
   * @return A new "names" label
   */
  public static JLabel newNames() {
    return newLabel(MessageKey.NAMES);
  }

  /**
   * @return A new "tags" label
   */
  public static JLabel newTags() {
    return newLabel(MessageKey.TAGS);
  }

  /**
   * @return A new "QR code label" message for use with receiving addresses
   */
  public static JLabel newQRCodeLabel() {
    return newLabel(MessageKey.QR_CODE_LABEL);
  }

  /**
   * @return a new "select alert sound" for sound settings
   */
  public static JLabel newSelectAlertSound() {
    return newLabel(MessageKey.ALERT_SOUND);
  }

  /**
   * @return a new "select receive sound" for sound settings
   */
  public static JLabel newSelectReceiveSound() {
    return newLabel(MessageKey.RECEIVE_SOUND);
  }

  /**
   * @return a new "select hardware wallet" for lab settings
   */
  public static JLabel newSelectHardware() {
    return newLabel(MessageKey.SELECT_HARDWARE_WALLET);
  }

  /**
   * @return a new "select Trezor" for lab settings
   */
  public static JLabel newSelectShowRestoreBeta7Wallets() {
    return newLabel(MessageKey.SELECT_SHOW_RESTORE_BETA7_WALLETS);
  }

  /**
   * @return a new "peer count" for verifying network
   */
  public static JLabel newPeerCount() {
    return newLabel(MessageKey.PEER_COUNT);
  }

  /**
   * @return a new "blocks left" for verifying network
   */
  public static JLabel newBlocksLeft() {
    return newLabel(MessageKey.BLOCKS_LEFT);
  }

  /**
   * @return A new "peer count info" label
   */
  public static JLabel newPeerCountInfo() {

    // Provide the number of peers required to be "healthy"
    return newLabel(MessageKey.VERIFY_NETWORK_PEER_COUNT, 10);
  }

  /**
   * @return A new "peer count info" label
   */
  public static JLabel newBlockCountInfo() {

    return newLabel(MessageKey.VERIFY_NETWORK_BLOCK_COUNT);
  }

  /**
   * @return A new "notes" label
   */
  public static JLabel newNotes() {
    return newLabel(MessageKey.PRIVATE_NOTES);
  }

  /**
   * @return A new "message" label
   */
  public static JLabel newMessage() {
    return newLabel(MessageKey.MESSAGE);
  }

  /**
   * @return A new "signature" label
   */
  public static JLabel newSignature() {
    return newLabel(MessageKey.SIGNATURE);
  }

  /**
   * @return A new "communicating with hardware" operation label
   */
  public static JLabel newCommunicatingWithHardware() {

    Optional<HardwareWalletService> currentHardwareWalletService = CoreServices.getCurrentHardwareWalletService();
    WalletMode walletMode = WalletMode.of(currentHardwareWalletService);

    return newLabel(MessageKey.COMMUNICATING_WITH_HARDWARE_OPERATION, walletMode.brand());
  }

  /**
   * @return A new "multi edit note" label
   */
  public static JLabel newMultiEditNote() {
    return newLabel(MessageKey.MULTI_EDIT_NOTE);
  }

  /**
   * @return a new "memo" label (notes)
   */
  public static JLabel newMemoLabel() {
    return newLabel(MessageKey.NOTES);
  }

  /**
   * @return a new "date" label
   */
  public static JLabel newDateLabel() {
    return newLabel(MessageKey.DATE);
  }

  /**
   * @return a new "expires" label
   */
  public static JLabel newExpiresLabel() {
    return newLabel(MessageKey.EXPIRES);
  }

  /**
   * @return a new "display name" label (name)
   */
  public static JLabel newDisplayNameLabel() {
    return newLabel(MessageKey.NAME);
  }

  /**
   * @return A new "Working" label
   */
  public static JLabel newWorking() {
    return newLabel(MessageKey.WORKING);
  }

  /**
   * @return A new "licence note" note
   */
  public static JLabel newLicenceNote() {

    return newNoteLabel(
      new MessageKey[]{
        MessageKey.LICENCE_NOTE_1,
      }, new Object[][]{});
  }

  /**
   * @return A new "about" note
   */
  public static JLabel newAboutNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.ABOUT_NOTE_1,
      MessageKey.ABOUT_NOTE_2,
      MessageKey.ABOUT_NOTE_3
    }, new Object[][]{});
  }

  /**
   * @return A new "default" note for use on the Fee slider
   */
  public static JLabel newDefaultNote() {
    // Wrap in HTML to ensure LTR/RTL and line breaks are respected
    String[] lines = new String[2];
    lines[0] = "\u25B2"; // 25B2 =up black triangle
    lines[1] = Languages.toCapitalCase(Languages.safeText(MessageKey.DEFAULT));
    JLabel label = new JLabel(HtmlUtils.localiseCenteredWithLineBreaks(lines));
    label.setHorizontalAlignment(SwingConstants.CENTER);

    // Ensure it is accessible
    AccessibilityDecorator.apply(label, MessageKey.DEFAULT);

    // Theme
    label.setForeground(Themes.currentTheme.text());

    return label;
  }

  /**
   * @return A new "wallet credentials" note
   */
  public static JLabel newWalletPasswordNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.WALLET_PASSWORD_NOTE_1,
      MessageKey.WALLET_PASSWORD_NOTE_2,
      MessageKey.WALLET_PASSWORD_NOTE_3
    }, new Object[][]{});

  }

  /**
   * @param brand The brand to apply to the message key
   *
   * @return A new "buy hardware" note
   */
  public static JLabel newBuyHardwareCommentNote(String brand) {

    return newNoteLabel(new MessageKey[]{
      MessageKey.BUY_HARDWARE_COMMENT
    }, new Object[][]{{brand}});

  }

  /**
   * @return A new "debugger warning" note
   */
  public static JLabel newDebuggerWarningNote() {

    JLabel label = newNoteLabel(new CoreMessageKey[]{
      CoreMessageKey.DEBUGGER_ATTACHED
    }, new Object[][]{});

    // Allow for danger theme
    label.setForeground(Themes.currentTheme.dangerAlertText());

    return label;

  }

  /**
   * @return A new "unsupported firmware" note
   */
  public static JLabel newUnsupportedFirmwareNote() {

    JLabel label = newNoteLabel(new CoreMessageKey[]{
      CoreMessageKey.UNSUPPORTED_FIRMWARE_ATTACHED
    }, new Object[][]{});

    // Allow for warning theme
    label.setForeground(Themes.currentTheme.warningAlertText());

    return label;

  }

  /**
   * @return A new "deprecated firmware" note
   */
  public static JLabel newDeprecatedFirmwareNote() {

    JLabel label = newNoteLabel(new CoreMessageKey[]{
      CoreMessageKey.DEPRECATED_FIRMWARE_ATTACHED
    }, new Object[][]{});

    // Allow for warning theme
    label.setForeground(Themes.currentTheme.warningAlertText());

    return label;

  }

  /**
   * @return A new "unsupported configuration passphrase" note
   */
  public static JLabel newUnsupportedConfigurationPassphrase() {

    JLabel label = newNoteLabel(new CoreMessageKey[]{
      CoreMessageKey.UNSUPPORTED_CONFIGURATION_PASSPHRASE
    }, new Object[][]{});

    // Allow for warning theme
    label.setForeground(Themes.currentTheme.warningAlertText());

    return label;

  }

  /**
   * @return A new "language change" note
   */
  public static JLabel newLanguageChangeNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.LANGUAGE_CHANGE_NOTE_1
    }, new Object[][]{});

  }

  /**
   * @return A new "MultiBit HD is localised by volunteers" note
   */
  public static JLabel newLocalisationByVolunteersNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.LOCALISATION_IS_BY_VOLUNTEERS
    }, new Object[][]{});

  }

  /**
   * @return A new "sound change" note
   */
  public static JLabel newSoundChangeNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.SOUND_CHANGE_NOTE_1
    }, new Object[][]{});

  }

  /**
   * @return A new "lab change" note
   */
  public static JLabel newLabChangeNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.LAB_CHANGE_NOTE_1
    }, new Object[][]{});

  }

  /**
   * @return A new "confirm seed phrase" note
   */
  public static JLabel newConfirmSeedPhraseNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.CONFIRM_SEED_PHRASE_NOTE_1,
      MessageKey.CONFIRM_SEED_PHRASE_NOTE_2,
      MessageKey.CONFIRM_SEED_PHRASE_NOTE_3
    }, new Object[][]{});
  }

  /**
   * @return A new "restore from seed phrase" note
   */
  public static JLabel newRestoreFromSeedPhraseNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.RESTORE_FROM_SEED_PHRASE_NOTE_1,
      MessageKey.RESTORE_FROM_SEED_PHRASE_NOTE_2,
      MessageKey.RESTORE_FROM_SEED_PHRASE_NOTE_3
    }, new Object[][]{});
  }

  /**
   * @return A new "timestamp" note
   */
  public static JLabel newTimestampNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.TIMESTAMP_NOTE_1,
      MessageKey.TIMESTAMP_NOTE_2
    }, new Object[][]{});
  }

  /**
   * @return A new "restore from timestamp" note
   */
  public static JLabel newRestoreFromTimestampNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.RESTORE_TIMESTAMP_NOTE_1,
      MessageKey.RESTORE_TIMESTAMP_NOTE_2,
      MessageKey.RESTORE_TIMESTAMP_NOTE_3
    }, new Object[][]{});
  }

  /**
   * @return A new "restore password" note
   */
  public static JLabel newRestorePasswordNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.RESTORE_PASSWORD_NOTE_1
    }, new Object[][]{});
  }

  /**
   * @return A new "select backup location" note (create wizard)
   */
  public static JLabel newSelectBackupLocationNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.SELECT_BACKUP_LOCATION_NOTE_1,
      MessageKey.SELECT_BACKUP_LOCATION_NOTE_2,
      MessageKey.SELECT_BACKUP_LOCATION_NOTE_3,
      MessageKey.SELECT_BACKUP_LOCATION_NOTE_4,
    }, new Object[][]{});

  }

  /**
   * @return A new "export payments location" status label
   */
  public static JLabel newSelectExportPaymentsLocationNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.SELECT_EXPORT_PAYMENTS_LOCATION_NOTE_1,
      MessageKey.SELECT_EXPORT_PAYMENTS_LOCATION_NOTE_2,
      MessageKey.SELECT_EXPORT_PAYMENTS_LOCATION_NOTE_3,
      MessageKey.SELECT_EXPORT_PAYMENTS_LOCATION_NOTE_4,
    }, new Object[][]{});
  }

  /**
   * @return A new "restore from backup" note
   */
  public static JLabel newRestoreFromBackupNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.RESTORE_BACKUP_NOTE_1,
      MessageKey.RESTORE_BACKUP_NOTE_2,
      MessageKey.RESTORE_BACKUP_NOTE_3
    }, new Object[][]{});
  }

  /**
   * @return A new "select backup" note (restore wizard)
   */
  public static JLabel newSelectBackupNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.SELECT_BACKUP_NOTE_1,
      MessageKey.SELECT_BACKUP_NOTE_2
    }, new Object[][]{});

  }

  /**
   * @return A new "password" note (credentials wizard)
   */
  public static JLabel newPasswordNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.PASSWORD_NOTE_1
    }, new Object[][]{});

  }

  /**
   * @return A new "PIN introduction" note (credentials wizard)
   */
  public static JLabel newPinIntroductionNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.PIN_INTRODUCTION
    }, new Object[][]{});

  }

  /**
   * @return A new "select wallet" note (credentials wizard)
   */
  public static JLabel newSelectWalletNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.SELECT_WALLET_NOTE_1
    }, new Object[][]{});

  }

  /**
   * @return A new "restore wallet" note (credentials wizard)
   */
  public static JLabel newRestoreWalletNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.RESTORE_WALLET_NOTE_1,
      MessageKey.RESTORE_WALLET_NOTE_2
    }, new Object[][]{});

  }

  /**
   * @return A new "change credentials note 1" (change credentials wizard)
   */
  public static JLabel newChangePasswordNote1() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.CHANGE_PASSWORD_NOTE_1
    }, new Object[][]{});

  }

  /**
   * @return A new "change credentials note 2" (change credentials wizard)
   */
  public static JLabel newChangePasswordNote2() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.CHANGE_PASSWORD_NOTE_2
    }, new Object[][]{});

  }

  /**
   * @return A new "verify network" note
   */
  public static JLabel newVerifyNetworkNoteBottom() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.VERIFY_NETWORK_NOTE_2,
      MessageKey.VERIFY_NETWORK_NOTE_3
    }, new Object[][]{});

  }

  /**
   * @return A new "Units settings" note
   */
  public static JLabel newUnitsSettingsNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.UNITS_SETTINGS_NOTE_1
    }, new Object[][]{});

  }

  /**
   * @return A new "exchange settings" note
   */
  public static JLabel newExchangeSettingsNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.EXCHANGE_SETTINGS_NOTE_1
    }, new Object[][]{});

  }

  /**
   * @return A new "data entered" note
   */
  public static JLabel newDataEnteredNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.DATA_ENTERED_NOTE_1,
      MessageKey.DATA_ENTERED_NOTE_2
    }, new Object[][]{});

  }

  /**
   * @return A new "verify message" note
   */
  public static JLabel newVerifyMessageNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.VERIFY_MESSAGE_NOTE_2
    }, new Object[][]{});

  }

  /**
   * @return A new "repair wallet" note
   */
  public static JLabel newRepairWalletNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.REPAIR_WALLET_NOTE_1,
      MessageKey.REPAIR_WALLET_NOTE_2,
      MessageKey.REPAIR_WALLET_NOTE_3,
      MessageKey.CLICK_NEXT_TO_CONTINUE
    }, new Object[][]{});

  }

  /**
   * @param isCopyAvailable True if the additional "copy QR image" note should be included
   *
   * @return A new "QR popover" note
   */
  public static JLabel newQRCodePopoverNote(boolean isCopyAvailable) {

    if (isCopyAvailable) {
      return newNoteLabel(new MessageKey[]{
        MessageKey.QR_CODE_NOTE_1,
        MessageKey.QR_CODE_NOTE_2,
      }, new Object[][]{});
    }

    return newNoteLabel(new MessageKey[]{
      MessageKey.QR_CODE_NOTE_1,
    }, new Object[][]{});

  }

  /*

   */

  /**
   * @return A new "create wallet report" note (makes transition to credentials less jarring)
   */
  public static JLabel newCreateWalletReportNote() {

    return newNoteLabel(new MessageKey[]{
      MessageKey.CREATE_WALLET_REPORT_NOTE_1,
    }, new Object[][]{});

  }

}
