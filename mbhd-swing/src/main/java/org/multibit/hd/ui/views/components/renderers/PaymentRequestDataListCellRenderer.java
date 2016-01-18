package org.multibit.hd.ui.views.components.renderers;

import org.multibit.hd.core.dto.MBHDPaymentRequestData;
import org.multibit.hd.ui.views.themes.Themes;

import javax.swing.*;
import java.awt.*;

/**
 * <p>List cell renderer to provide the following to combo boxes:</p>
 * <ul>
 * <li>Rendering of paymentRequestData</li>
 * </ul>
 *
 * @since 0.0.1
 *
 */
public class PaymentRequestDataListCellRenderer extends JLabel implements ListCellRenderer<MBHDPaymentRequestData> {

  public PaymentRequestDataListCellRenderer() {

    setOpaque(true);
    setVerticalAlignment(CENTER);
  }

  public Component getListCellRendererComponent(
    JList list,
    MBHDPaymentRequestData value,
    int index,
    boolean isSelected,
    boolean cellHasFocus
  ) {

    if (isSelected) {
      setBackground(Themes.currentTheme.tableRowSelectedBackground());
      setForeground(Themes.currentTheme.inverseText());
    } else {
      setBackground(Themes.currentTheme.dataEntryBackground());
      setForeground(Themes.currentTheme.dataEntryText());
    }

    if (value != null) {

      setText(value.getDescription());

    } else {
      // No value/ not a paymentRequestData  means no text or icon
      setIcon(null);
      setText("");
    }

    return this;
  }

}