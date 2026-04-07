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
      Image upImg = getTheme() != null
         ? getTheme().getImage("s|NumericStepper", "upArrowUpSkin", -1, upH) : null;
      Image dnImg = getTheme() != null
         ? getTheme().getImage("s|NumericStepper", "downArrowUpSkin", -1, downH) : null;

      if(upImg != null && dnImg != null) {
         int imgW = upImg.getWidth(null);
         drawString(g2, 0, 0, w - imgW, h, label, format);
         g2.setColor(Color.lightGray);
         // right edge should be covered by the images
         //Bug #31348. if start with (0,0), the top and left border will be hidden when export png.
         g2.drawRect(1, 1, w - imgW / 2, h - 2);
         g2.drawImage(upImg, w - imgW, 0, null);
         g2.drawImage(dnImg, w - imgW, upH, null);
      }
      else {
         // Fallback when theme images are unavailable: draw a simple bordered text box
         int arrowW = Math.max(12, h / 2);
         drawString(g2, 0, 0, w - arrowW, h, label, format);
         g2.setColor(Color.lightGray);
         g2.drawRect(1, 1, w - arrowW / 2, h - 2);
         // Vertical divider between text and arrow column
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
}
