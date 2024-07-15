/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.io.viewsheet.pdf;

import inetsoft.report.io.viewsheet.VSSelectionTreeHelper;
import inetsoft.uql.viewsheet.SelectionValue;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * SelectionTree helper for pdf.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class PDFSelectionTreeHelper extends VSSelectionTreeHelper {
   /**
    * Constructor.
    */
   public PDFSelectionTreeHelper(PDFCoordinateHelper helper) {
      this.cHelper = helper;
   }

   /**
    * Write the tree content.
    * @param info the specified SelectionTreeVSAssemblyInfo.
    * @param dispList SelectionValues for display.
    */
   @Override
   protected void writeTree(SelectionTreeVSAssemblyInfo info, List<SelectionValue> dispList) {
      VSCompositeFormat format = info.getFormat();
      format = format == null ? new VSCompositeFormat() : format.clone();
      format.getDefaultFormat().setBorders(null);
      super.writeTree0(info, dispList);
      Point position = info.getViewsheet().getPixelPosition(info);
      Dimension size = info.getPixelSize();
      // set the default selection list's border.
      Rectangle2D bounds = cHelper.createBounds(position, size);
      VSCompositeFormat allformat = info.getFormat();

      double height = 0;

      if(!info.isTitleVisible()) {
         Rectangle2D rec = (Rectangle2D) boundsList.get(0);
         height = rec.getHeight();
      }

      bounds.setFrame(bounds.getX(), bounds.getY() + height, bounds.getWidth(), totalHeight[0] - height);
      writeText(bounds, allformat, null, false);
   }

   /**
    * Draw the text box.
    */
   @Override
   protected void writeText(Rectangle2D bounds, Rectangle2D textBounds, VSCompositeFormat format,
                            String dispText, boolean paintBackground, int ctype, Insets padding)
   {
      ((PDFCoordinateHelper) cHelper).drawTextBox(bounds, textBounds, format, dispText,
         paintBackground, padding);
   }

   /**
    * Draw the text box.
    */
   @Override
   protected void writeText(Rectangle2D bounds, Rectangle2D textBounds, VSCompositeFormat format,
                            String dispText, boolean paintBackground,
                            int ctype, VSCompositeFormat pfmt, Insets padding)
   {
      boolean bgNeed = PDFVSExporter.isBackgroundNeed(format, pfmt);
      writeText(bounds, textBounds, format, dispText, bgNeed, ctype, padding);
   }

   /**
    * Draw the text box.
    */
   @Override
   protected void writeText(Rectangle2D bounds, VSCompositeFormat format,
                            String dispText, VSCompositeFormat pfmt, Insets padding)
   {
      boolean bgNeed = PDFVSExporter.isBackgroundNeed(format, pfmt);
      writeText(bounds, format, dispText, bgNeed, -1, padding);
   }

   /**
    * Paint an image at the bounds.
    */
   @Override
   protected void writePicture(Image img, Rectangle2D bounds) {
      ((PDFCoordinateHelper) cHelper).drawImage(img, bounds);
   }

   /**
    * Draw a text at the bounds.
    */
   @Override
   protected void writeText(String txt, Rectangle2D bounds,
                            VSCompositeFormat format)
   {
      ((PDFCoordinateHelper) cHelper).drawTextBox(bounds, format, txt);
   }
}
