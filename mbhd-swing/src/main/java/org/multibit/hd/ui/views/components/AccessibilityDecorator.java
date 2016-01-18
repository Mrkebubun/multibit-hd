package org.multibit.hd.ui.views.components;

import org.multibit.hd.core.dto.CoreMessageKey;
import org.multibit.hd.ui.languages.Languages;
import org.multibit.hd.ui.languages.MessageKey;

import javax.swing.*;

/**
 * <p>Decorator to provide the following to application:</p>
 * <ul>
 * <li>Standard technique for applying FEST and Accessibility API information</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class AccessibilityDecorator {

  /**
   * <p>Full FEST and Accessibility support (tooltip and description)</p>
   *
   * @param component  The Swing component to decorate
   * @param nameKey    The component name (used directly for FEST and with lookup for accessible name)
   * @param tooltipKey The component tooltip and accessible description
   * @param values     The values to apply to tooltip message key
   */
  public static void apply(JComponent component, MessageKey nameKey, MessageKey tooltipKey, Object... values) {

    // Ensure FEST can find it
    component.setName(nameKey.getKey());

    // Ensure we have a suitable tooltip
    component.setToolTipText(Languages.safeText(tooltipKey, values));

    // Ensure Accessibility API can find it
    component.getAccessibleContext().setAccessibleName(Languages.safeText(nameKey));
    component.getAccessibleContext().setAccessibleDescription(Languages.safeText(tooltipKey));

  }

  /**
   * <p>Basic FEST and Accessibility support (no tooltip or description)</p>
   *
   * @param component The Swing component to decorate
   * @param nameKey   The component name (used directly for FEST and with lookup for accessible name)
   * @param values    The values to apply to the accessible name
   */
  public static void apply(JComponent component, MessageKey nameKey, Object... values) {

    // Ensure FEST can find it
    component.setName(nameKey.getKey());

    // Ensure Accessibility API can find it
    component.getAccessibleContext().setAccessibleName(Languages.safeText(nameKey, values));

  }

  /**
   * <p>Full FEST and Accessibility support (tooltip and description)</p>
   *
   * @param component  The Swing component to decorate
   * @param nameKey    The component name (used directly for FEST and with lookup for accessible name)
   * @param tooltipKey The component tooltip and accessible description
   * @param values     The values to the apply to the accessible name and tooltip text
   */
  public static void apply(JComponent component, CoreMessageKey nameKey, CoreMessageKey tooltipKey, Object... values) {

    // Ensure FEST can find it
    component.setName(nameKey.getKey());

    // Ensure we have a suitable tooltip
    component.setToolTipText(Languages.safeText(tooltipKey, values));

    // Ensure Accessibility API can find it
    component.getAccessibleContext().setAccessibleName(Languages.safeText(nameKey, values));
    component.getAccessibleContext().setAccessibleDescription(Languages.safeText(tooltipKey, values));

  }

  /**
   * <p>Basic FEST and Accessibility support (no tooltip or description)</p>
   *
   * @param component The Swing component to decorate
   * @param nameKey   The component name (used directly for FEST and with lookup for accessible name)
   * @param values    The values to apply to the accessible name
   */
  public static void apply(JComponent component, CoreMessageKey nameKey, Object... values) {

    // Ensure FEST can find it
    component.setName(nameKey.getKey());

    // Ensure Accessibility API can find it
    component.getAccessibleContext().setAccessibleName(Languages.safeText(nameKey, values));

  }

}
