package org.multibit.hd.ui.views.components;

import org.multibit.hd.ui.views.components.display_qrcode.DisplayQRCodeModel;
import org.multibit.hd.ui.views.components.display_qrcode.DisplayQRCodeView;
import org.multibit.hd.ui.views.components.display_environment_alert.DisplayEnvironmentAlertModel;
import org.multibit.hd.ui.views.components.display_environment_alert.DisplayEnvironmentAlertView;
import org.multibit.hd.ui.views.components.enter_yes_no.EnterYesNoModel;
import org.multibit.hd.ui.views.components.enter_yes_no.EnterYesNoView;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;

/**
 * <p>Factory to provide the following to UI:</p>
 * <ul>
 * <li>Creation of complex components requiring a model and view suitable for use as popovers</li>
 * </ul>
 *
 * @since 0.0.1
 *
 */
public class Popovers {

  /**
   * <p>A "discard Yes/No" model and view displays a popover with the following features:</p>
   * <ul>
   * <li>Button to close the light box popover</li>
   * <li>Label field indicating that a Discard operation will occur if Yes is clicked</li>
   * <li>No will return to safety</li>
   * </ul>
   *
   * @param panelName The underlying panel name for this popover
   *
   * @return A new "discard yes/no" model and view
   */
  public static ModelAndView<EnterYesNoModel, EnterYesNoView> newDiscardYesNoPopoverMaV(String panelName) {

    EnterYesNoModel model = new EnterYesNoModel(panelName);
    EnterYesNoView view = new EnterYesNoView(model, AwesomeIcon.TRASH, true);

    return new ModelAndView<>(model, view);

  }

  /**
   * <p>A "display QR" model and view displays a QR code with the following features:</p>
   * <ul>
   * <li>Image field showing a QR code</li>
   * <li>Button to copy the QR code image to the Clipboard</li>
   * <li>Button to close the light box popover</li>
   * <li></li>
   * </ul>
   *
   * @param panelName The underlying panel name for this popover
   *
   * @return A new "display QR code" model and view
   */
  public static ModelAndView<DisplayQRCodeModel, DisplayQRCodeView> newDisplayQRCodePopoverMaV(String panelName) {

    DisplayQRCodeModel model = new DisplayQRCodeModel(panelName);
    DisplayQRCodeView view = new DisplayQRCodeView(model);

    return new ModelAndView<>(model, view);

  }

  /**
   * <p>A "display environment alert" model and view displays an alert with the following features:</p>
   * <ul>
   * <li>Appropriately themed message panel</li>
   * <li>Button to close the light box popover</li>
   * </ul>
   *
   * @param panelName The underlying panel name for this popover
   *
   * @return A new "display environment alert" model and view
   */
  public static ModelAndView<DisplayEnvironmentAlertModel, DisplayEnvironmentAlertView> newDisplayEnvironmentPopoverMaV(String panelName) {

    DisplayEnvironmentAlertModel model = new DisplayEnvironmentAlertModel(panelName);
    DisplayEnvironmentAlertView view = new DisplayEnvironmentAlertView(model);

    return new ModelAndView<>(model, view);

  }

}
