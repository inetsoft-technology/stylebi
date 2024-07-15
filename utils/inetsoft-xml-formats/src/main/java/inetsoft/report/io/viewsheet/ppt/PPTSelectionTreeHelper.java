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
package inetsoft.report.io.viewsheet.ppt;

import inetsoft.report.io.viewsheet.VSSelectionTreeHelper;
import inetsoft.report.io.viewsheet.excel.ExcelVSUtil;
import inetsoft.uql.viewsheet.SelectionValue;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import org.apache.poi.xslf.usermodel.XSLFSlide;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * SelectionList helper for powerpoint.
 *
 * @author InetSoft Technology Corp
 * @version 8.5
 */
public class PPTSelectionTreeHelper extends VSSelectionTreeHelper {
   /**
    * Constructor.
    */
   public PPTSelectionTreeHelper(PPTCoordinateHelper scHelper,
                                 XSLFSlide slide, PPTVSExporter exporter)
   {
      this.cHelper = scHelper;
      this.slide = slide;
      this.exporter = exporter;
   }

   /**
    * Write the tree content.
    *
    * @param info     the specified SelectionTreeVSAssemblyInfo.
    * @param dispList SelectionValues for display.
    */
   @Override
   protected void writeTree(SelectionTreeVSAssemblyInfo info, List<SelectionValue> dispList) {
      super.writeTree0(info, dispList);
      Point position = info.getViewsheet().getPixelPosition(info);
      Dimension size = info.getPixelSize();
      // set the default selection list's border.
      Rectangle2D bounds = cHelper.createBounds(position, size);
      VSCompositeFormat allformat = info.getFormat();

      bounds.setFrame(bounds.getX(), bounds.getY(), bounds.getWidth(), totalHeight[0]);
      PPTVSUtil.paintBorders(bounds, allformat, slide, ExcelVSUtil.CELL_HEADER);
   }

   /**
    * Draw the text box.
    */
   @Override
   protected void writeText(Rectangle2D bounds, VSCompositeFormat format,
                            String dispText, boolean paintBackground, int ctype, Insets padding)
   {
      writeText(null, bounds, format, dispText, paintBackground, ctype, padding);
   }

   @Override
   protected void writeText(Rectangle2D bounds, Rectangle2D textBounds, VSCompositeFormat format,
                            String dispText, boolean paintBackground, int ctype, Insets padding)
   {
      PPTValueHelper helper = new PPTValueHelper(slide);
      helper.setBounds(textBounds);
      helper.setFormat(format);
      helper.setValue(dispText);
      helper.setPadding(padding);

      if(ctype != -1) {
         helper.setCellType(ctype);
      }

      helper.writeTextBox();
   }

   /**
    * Paint an image at the bounds.
    */
   @Override
   protected void writePicture(Image img, Rectangle2D bounds) {
      try {
         exporter.writePicture((BufferedImage) img, bounds);
      }
      catch(Exception ex) {
         throw new RuntimeException(ex.getMessage());
      }
   }

   /**
    * Draw a text at the bounds.
    */
   @Override
   protected void writeText(String txt, Rectangle2D bounds,
                            VSCompositeFormat format)
   {
      exporter.writeText(txt, bounds, format);
   }

   private final XSLFSlide slide;
   private final PPTVSExporter exporter;
}
