package org.multibit.hd.ui.fest.requirements.trezor;

import com.google.common.collect.Maps;
import org.fest.swing.fixture.FrameFixture;
import org.multibit.hd.testing.hardware_wallet_fixtures.HardwareWalletFixture;
import org.multibit.hd.ui.fest.use_cases.standard.credentials.UnlockReportUseCase;
import org.multibit.hd.ui.fest.use_cases.trezor.TrezorConfirmUnlockUseCase;
import org.multibit.hd.ui.fest.use_cases.trezor.TrezorEnterPinFromMasterPublicKeyUseCase;
import org.multibit.hd.ui.fest.use_cases.trezor.TrezorRequestCipherKeyUseCase;
import org.multibit.hd.ui.fest.use_cases.trezor.TrezorRequestMasterPublicKeyUseCase;

import java.util.Map;

/**
 * <p>FEST Swing UI test to provide:</p>
 * <ul>
 * <li>Exercise the responses to hardware wallet events in the context of
 * unlocking a Trezor wallet under warm start</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class UnlockTrezorHardwareWalletWarmStartRequirements {

  public static void verifyUsing(FrameFixture window, HardwareWalletFixture hardwareWalletFixture) {

    Map<String, Object> parameters = Maps.newHashMap();

    // Request the master public key (refer to mock client for PublicKey responses)
    new TrezorRequestMasterPublicKeyUseCase(window, hardwareWalletFixture).execute(parameters);

    // Verify PIN entry (refer to mock client for PIN entry responses)
    new TrezorEnterPinFromMasterPublicKeyUseCase(window, hardwareWalletFixture).execute(parameters);

    // Request the cipher key (no PIN usually)
    new TrezorRequestCipherKeyUseCase(window, hardwareWalletFixture).execute(parameters);

    // Unlock with cipher key
    new TrezorConfirmUnlockUseCase(window, hardwareWalletFixture).execute(parameters);

    hardwareWalletFixture.fireNextEvent("Confirm unlock");

    // Verify the wallet unlocked
    new UnlockReportUseCase(window).execute(parameters);


  }
}
