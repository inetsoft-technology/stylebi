/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.report.gui.viewsheet;

import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.SpinnerVSAssemblyInfo;
import inetsoft.util.CoreTool;
import inetsoft.util.ThreadContext;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.text.Format;

/**
 * VSSpinner component for view sheet.
 *
 * @version 8.5, 07/26/2006
 * @author InetSoft Technology Corp
 */
public class VSSpinner extends VSFloatable {
   /**
    * Constructor.
    */
   public VSSpinner(Viewsheet vs) {
      super(vs);
   }

   /**
    * Paint the component.
    */
   @Override
   public void paintComponent(Graphics2D g) {
      SpinnerVSAssemblyInfo info = (SpinnerVSAssemblyInfo) getAssemblyInfo();

      if(info == null) {
         return;
      }

      VSCompositeFormat format = info.getFormat() == null ?
         new VSCompositeFormat() : info.getFormat();
      int w = getContentWidth();
      int h = getContentHeight();

      Graphics2D g2 = (Graphics2D) g.create(getContentX(), getContentY(),
                                            w + 1, h + 1);

      Number data = (Number) info.getSelectedObject();
      double v = data == null ? 0 : data.doubleValue();
      Format fmt = TableFormat.getFormat(format.getFormat(), format.getFormatExtent(),
                                         ThreadContext.getLocale());
      v = v > info.getMax() ? info.getMax() : v;
      v = v < info.getMin() ? info.getMin() : v;
      String label = fmt != null ? fmt.format(v) : CoreTool.toString(v);

      int upH = (h + 1) / 2;
      int downH = h + 1 - upH; // make sure no rounding error
      FlexTheme theme = getTheme();
      int roundCorner = format.getRoundCorner();

      Image upImg = theme != null
         ? theme.getImage("s|NumericStepper", "upArrowUpSkin", -1, upH) : null;
      Image dnImg = theme != null
         ? theme.getImage("s|NumericStepper", "downArrowUpSkin", -1, downH) : null;

      if(upImg != null && dnImg != null) {
         int imgW = upImg.getWidth(null);
         drawString(g2, 0, 0, w - imgW, h, label, format);
         // clip after the text is drawn so only the box and the stepper images
         // are trimmed to the rounded shape
         clipRoundCorner(g2, roundCorner);
         // right edge should be covered by the images
         drawWidgetBox(g2, w - imgW / 2, h, roundCorner);
         g2.drawImage(upImg, w - imgW, 0, null);
         g2.drawImage(dnImg, w - imgW, upH, null);
      }
      else {
         // Fallback when theme images are unavailable: draw a simple bordered text box
         int arrowW = Math.max(12, h / 2);
         drawString(g2, 0, 0, Math.max(0, w - arrowW), h, label, format);
         clipRoundCorner(g2, roundCorner);
         drawWidgetBox(g2, Math.max(0, w - arrowW / 2), h, roundCorner);
         // Vertical divider between text and arrow column
         g2.setColor(Color.lightGray);
         g2.drawLine(w - arrowW, 1, w - arrowW, h - 1);
         // Up triangle — center apex on the arrow column (round to nearest pixel)
         int cx = w - (arrowW + 1) / 2;
         int midY = h / 2;
         g2.setColor(new Color(80, 80, 80));
         g2.fillPolygon(
            new int[]{cx, w - arrowW + 2, w - 2},
            new int[]{2, midY - 1, midY - 1},
            3);
         // Down triangle
         g2.fillPolygon(
            new int[]{cx, w - arrowW + 2, w - 2},
            new int[]{h - 3, midY + 1, midY + 1},
            3);
      }

      g2.dispose();
   }

   /**
    * Draw the outline of the widget box, whose right edge is covered by the stepper
    * arrows. The box is skipped when the assembly format defines its own borders: the
    * assembly border already delimits the widget, and a second outline shows up as a
    * doubled line - misaligned at the corners, since the two rectangles have different
    * origins and sizes - which does not match the browser, where only the input's own
    * border is drawn.
    *
    * @param bw the box width.
    * @param h  the widget height.
    */
   private void drawWidgetBox(Graphics2D g, int bw, int h, int roundCorner) {
      if(hasBorders()) {
         return;
      }

      g.setColor(Color.lightGray);

      //Bug #31348. if start with (0,0), the top and left border will be hidden when export png.
      if(roundCorner > 0) {
         int arc = roundCorner * 2;
         g.drawRoundRect(1, 1, bw, h - 2, arc, arc);
      }
      else {
         g.drawRect(1, 1, bw, h - 2);
      }
   }

   /**
    * Check if the assembly format defines a border on any side.
    */
   private boolean hasBorders() {
      return getBW(TOP) > 0 || getBW(BOTTOM) > 0 || getBW(LEFT) > 0 || getBW(RIGHT) > 0;
   }

   /**
    * Clip the graphics to the round corner shape of the assembly border, so the widget
    * box and the stepper arrows are not squared off at the corners. This matches the
    * browser, which renders the spinner as a single input with border-radius. No-op
    * when round corner is not set.
    */
   private void clipRoundCorner(Graphics2D g, int roundCorner) {
      if(roundCorner <= 0) {
         return;
      }

      // The clip must line up with the border rectangle drawn by VSObject.drawBorders,
      // which is inset by the right/bottom border width. paintComponent is translated
      // by the top/left border width, so shift the clip back by that gap.
      Point gap = getBorderGap();
      Dimension size = getSize();
      double w = size.width - getBW(RIGHT);
      double h = size.height - getBW(BOTTOM);
      double arc = roundCorner * 2d;

      g.clip(new RoundRectangle2D.Double(-gap.x, -gap.y, w, h, arc, arc));
   }
}
