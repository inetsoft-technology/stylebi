/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.io.viewsheet.pdf;

import inetsoft.graph.internal.GTool;
import inetsoft.report.TableDataPath;
import inetsoft.report.io.viewsheet.ExportUtil;
import inetsoft.report.io.viewsheet.VSCurrentSelectionHelper;
import inetsoft.uql.viewsheet.FormatInfo;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.CurrentSelectionVSAssemblyInfo;
import inetsoft.util.Tool;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Current selection helper for pdf.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class PDFCurrentSelectionHelper extends VSCurrentSelectionHelper {
   /**
    * Constructor.
    */
   public PDFCurrentSelectionHelper(PDFCoordinateHelper helper) {
      this.cHelper = helper;
   }

   /**
    * Write the title. There are two type of the title.
    * @param info the specified assembly info.
    */
   @Override
   protected void writeTitle(CurrentSelectionVSAssemblyInfo info) {
      Point position = info.getViewsheet().getPixelPosition(info);
      Dimension size = info.getPixelSize();
      Dimension titleSize = new Dimension(size.width, info.getTitleHeight());
      VSCompositeFormat titleFormat = null;
      FormatInfo finfo = info.getFormatInfo();

      if(finfo != null) {
         titleFormat = finfo.getFormat(
            new TableDataPath(-1, TableDataPath.TITLE), false);
      }

      if(titleFormat != null) {
         Insets insets = titleFormat.getBorders();
         float borderH = GTool.getLineWidth(insets.bottom);
         titleSize = new Dimension(titleSize.width, (int) (titleSize.height - borderH));
      }

      // set the default selection list's border.
      Rectangle2D bounds = cHelper.createBounds(position, size);
      VSCompositeFormat format = info.getFormat();
      drawTextBox(null, bounds, format, null);
      bounds = cHelper.createBounds(position, titleSize);
      String title = Tool.localize(info.getTitle());
      boolean needBG = PDFVSExporter.isBackgroundNeed(titleFormat, info.getFormat());
      drawTextBox(title, bounds, titleFormat, needBG, info.getTitlePadding());
   }

   /**
    * Draw text box.
    */
   protected void drawTextBox(String value, Rectangle2D bounds,
                              VSCompositeFormat format, boolean needBG, Insets padding)
   {
      getHelper().drawTextBox(bounds, bounds, format, value, needBG, padding);
   }

   /**
    * Draw text box.
    */
   @Override
   protected void drawTextBox(String value, Rectangle2D bounds,
                              VSCompositeFormat format, Insets padding)
   {
      getHelper().drawTextBox(bounds, format, value);
   }

   /**
    * Write out selection title.
    */
   @Override
   protected void writeOutTitle(String title, String value, Rectangle2D bounds,
                                VSCompositeFormat format, double titleRatio,
                                Insets padding)
   {
      String text = ExportUtil.getTextInTitleCell(Tool.localize(title),
         value, (int) bounds.getWidth(), format.getFont(), titleRatio);
      drawTextBox(text, bounds, format, false, padding);
   }

   /**
    * Convert CoordinateHelper as PDFCoordinateHelper.
    */
   private PDFCoordinateHelper getHelper() {
      return (PDFCoordinateHelper) cHelper;
   }
}
