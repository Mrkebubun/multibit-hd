package org.multibit.hd.ui.views.wizards.welcome;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListeningExecutorService;
import net.miginfocom.swing.MigLayout;
import org.multibit.hd.brit.core.seed_phrase.Bip39SeedPhraseGenerator;
import org.multibit.hd.brit.core.seed_phrase.SeedPhraseGenerator;
import org.multibit.commons.concurrent.SafeExecutors;
import org.multibit.commons.crypto.AESUtils;
import org.multibit.hd.core.dto.WalletId;
import org.multibit.hd.core.dto.WalletSummary;
import org.multibit.hd.core.managers.InstallationManager;
import org.multibit.hd.core.managers.WalletManager;
import org.multibit.hd.ui.MultiBitUI;
import org.multibit.hd.ui.events.view.ViewEvents;
import org.multibit.hd.ui.languages.Languages;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.AccessibilityDecorator;
import org.multibit.hd.ui.views.components.Labels;
import org.multibit.hd.ui.views.components.Panels;
import org.multibit.hd.ui.views.components.panels.PanelDecorator;
import org.multibit.hd.ui.views.fonts.AwesomeDecorator;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;
import org.multibit.hd.ui.views.themes.Themes;
import org.multibit.hd.ui.views.wizards.AbstractWizard;
import org.multibit.hd.ui.views.wizards.AbstractWizardPanelView;
import org.multibit.hd.ui.views.wizards.WizardButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.swing.*;
import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * <p>View to provide the following to UI:</p>
 * <ul>
 * <li>Show credentials recovery progress report</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class RestorePasswordReportPanelView extends AbstractWizardPanelView<WelcomeWizardModel, Boolean> {

  private static final Logger log = LoggerFactory.getLogger(RestorePasswordReportPanelView.class);

  private JLabel working;
  private JLabel passwordRecoveryStatus;

  private ListeningExecutorService listeningExecutorService;

  /**
   * @param wizard The wizard managing the states
   */
  public RestorePasswordReportPanelView(AbstractWizard<WelcomeWizardModel> wizard, String panelName) {

    super(wizard, panelName, AwesomeIcon.MAGIC, MessageKey.RESTORE_PASSWORD_REPORT_TITLE);

  }

  @Override
  public void newPanelModel() {

    // Nothing to bind

  }

  @Override
  public void initialiseContent(JPanel contentPanel) {

    contentPanel.setLayout(
      new MigLayout(
        Panels.migXYLayout()+",hidemode 3", // Ensure result is on same line as working
        "[][][]", // Column constraints
        "[]10[]10[]" // Row constraints
      ));

    // Apply the theme
    contentPanel.setBackground(Themes.currentTheme.detailPanelBackground());

    working = Labels.newWorking();
    AwesomeDecorator.applyIcon(AwesomeIcon.REFRESH, working, true, MultiBitUI.NORMAL_ICON_SIZE);

    passwordRecoveryStatus = Labels.newStatusLabel(Optional.<MessageKey>absent(), null, Optional.<Boolean>absent());

    contentPanel.add(working, "wrap");
    contentPanel.add(passwordRecoveryStatus, "wrap");

    listeningExecutorService = SafeExecutors.newSingleThreadExecutor("restore-password");

  }

  @Override
  protected void initialiseButtons(AbstractWizard<WelcomeWizardModel> wizard) {

    PanelDecorator.addExitCancelPreviousFinish(this, wizard);

  }

  @Override
  public void afterShow() {
    // Run the decryption on a different thread
    listeningExecutorService.submit(
      new Runnable() {
        @Override
        public void run() {

          recoverPassword();

          // Enable the Finish button when it's done
          ViewEvents.fireWizardButtonEnabledEvent(getPanelName(), WizardButton.FINISH, true);
        }
      });
  }

  @Override
  public void updateFromComponentModels(Optional componentModel) {
    // Do nothing - panel model is updated via an action and wizard model is not applicable
  }

  /**
   * Attempt to recover the credentials and display it to the user
   */
  private void recoverPassword() {

    WelcomeWizardModel model = getWizardModel();

    // Locate the installation directory
    File applicationDataDirectory = InstallationManager.getOrCreateApplicationDataDirectory();

    // Work out the seed, wallet id and wallet directory
    log.debug("Converting seed phrase to seed...");
    List<String> seedPhrase = model.getRestorePasswordEnterSeedPhraseModel().getSeedPhrase();
    SeedPhraseGenerator seedPhraseGenerator = new Bip39SeedPhraseGenerator();
    byte[] seed = seedPhraseGenerator.convertToSeed(seedPhrase);

    // Trezor soft wallets use a different salt in creating wallet ids
    boolean restoreAsTrezor = model.getRestorePasswordEnterSeedPhraseModel().isRestoreAsTrezorSoftWallet();
    log.debug("Restoring password for Trezor soft wallet: {}", restoreAsTrezor);
    WalletId walletId;
    if (restoreAsTrezor) {
      walletId = new WalletId(seed, WalletId.getWalletIdSaltUsedInScryptForTrezorSoftWallets());
    } else {
      walletId = new WalletId(seed);
    }

    String walletRoot = applicationDataDirectory.getAbsolutePath() + File.separator + WalletManager.createWalletRoot(walletId);
    File walletDirectory = new File(walletRoot);

    WalletSummary walletSummary;
    if (walletDirectory.isDirectory()) {
      walletSummary = WalletManager.getOrCreateWalletSummary(walletDirectory, walletId);
    } else {
      log.debug("Restore password failed (no wallet summary directory)");
      SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            // Failed
            working.setVisible(false);
            passwordRecoveryStatus.setText(Languages.safeText(MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL));
            AccessibilityDecorator.apply(passwordRecoveryStatus, MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL);
            AwesomeDecorator.applyIcon(AwesomeIcon.TIMES, passwordRecoveryStatus, true, MultiBitUI.NORMAL_ICON_SIZE);
            passwordRecoveryStatus.setVisible(true);
          }
        });
      return;
    }

    // Read the encrypted wallet credentials and decrypt with an AES key derived from the seed
    KeyParameter backupAESKey;
    try {
      backupAESKey = AESUtils.createAESKey(seed, WalletManager.scryptSalt());
    } catch (final NoSuchAlgorithmException e) {
      log.error("Restore password failed (no such algorithm)", e);
      SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            // Failed
            working.setVisible(false);
            passwordRecoveryStatus.setText(Languages.safeText(MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL));
            AccessibilityDecorator.apply(passwordRecoveryStatus, MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL);
            AwesomeDecorator.applyIcon(AwesomeIcon.TIMES, passwordRecoveryStatus, true, MultiBitUI.NORMAL_ICON_SIZE);
            passwordRecoveryStatus.setVisible(true);
          }
        });
      return;
    }

    byte[] decryptedPaddedWalletPasswordBytes = org.multibit.commons.crypto.AESUtils.decrypt(
      // Get the padded credentials out of the wallet summary. This is put in when a wallet is created.
      walletSummary.getEncryptedPassword(),
      backupAESKey,
      WalletManager.aesInitialisationVector()
    );

    try {
      final byte[] decryptedWalletPasswordBytes = WalletManager.unpadPasswordBytes(decryptedPaddedWalletPasswordBytes);

      // Check the result
      if (decryptedWalletPasswordBytes.length == 0) {
        log.debug("Restore password failed (decrypt)");
        SwingUtilities.invokeLater(
          new Runnable() {
            @Override
            public void run() {
              // Failed
              working.setVisible(false);
              passwordRecoveryStatus.setText(Languages.safeText(MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL));
              AccessibilityDecorator.apply(passwordRecoveryStatus, MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL);
              AwesomeDecorator.applyIcon(AwesomeIcon.TIMES, passwordRecoveryStatus, true, MultiBitUI.NORMAL_ICON_SIZE);
              passwordRecoveryStatus.setVisible(true);
            }
          });
        return;
      }

      // Must be OK to be here
      log.debug("Restore password succeeded");
      SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            working.setVisible(false);
            String decryptedWalletPassword = new String(decryptedWalletPasswordBytes, Charsets.UTF_8);
            passwordRecoveryStatus.setText(Languages.safeText(MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_SUCCESS, decryptedWalletPassword));
            AccessibilityDecorator.apply(passwordRecoveryStatus, MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_SUCCESS);
            AwesomeDecorator.applyIcon(AwesomeIcon.CHECK, passwordRecoveryStatus, true, MultiBitUI.NORMAL_ICON_SIZE);
            passwordRecoveryStatus.setVisible(true);
          }
        });

    } catch (IllegalStateException ise) {
      // Probably the unpad failed
      log.debug("Restore password failed (unpad failed)");
      SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            working.setVisible(false);
            passwordRecoveryStatus.setText(Languages.safeText(MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL));
            AccessibilityDecorator.apply(passwordRecoveryStatus, MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL);
            AwesomeDecorator.applyIcon(AwesomeIcon.TIMES, passwordRecoveryStatus, true, MultiBitUI.NORMAL_ICON_SIZE);
          }
        });
    }
  }
}
