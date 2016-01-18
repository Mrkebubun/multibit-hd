package org.multibit.hd.ui.views.components.renderers;

import com.google.common.base.Optional;
import org.multibit.hd.ui.views.components.Labels;
import org.multibit.hd.ui.views.themes.Themes;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Render the background of an image icon correctly
 */
public class ImageIconTableCellRenderer extends DefaultTableCellRenderer {

  JLabel label = Labels.newImageLabel(Optional.<BufferedImage>absent());


  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row,
                                                 int column) {

    label.setHorizontalAlignment(SwingConstants.CENTER);

    if (isSelected) {
      setBackground(Themes.currentTheme.tableRowSelectedBackground());
      setForeground(Themes.currentTheme.inverseText());
    } else {
      setBackground(Themes.currentTheme.detailPanelBackground());
      setForeground(Themes.currentTheme.text());
    }

    if (value != null && value instanceof ImageIcon) {
      label.setIcon((ImageIcon)value);
    }

    setBorder(noFocusBorder);

    if (isSelected) {
      label.setBackground(Themes.currentTheme.tableRowSelectedBackground());
    } else {
      if (row % 2 != 0) {
        label.setBackground(Themes.currentTheme.tableRowAltBackground());
      } else {
        label.setBackground(Themes.currentTheme.tableRowBackground());
      }
    }

    return label;
  }
}
