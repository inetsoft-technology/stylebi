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

import inetsoft.report.TableDataPath;
import inetsoft.report.io.viewsheet.VSCurrentSelectionHelper;
import inetsoft.report.io.viewsheet.excel.ExcelVSUtil;
import inetsoft.uql.viewsheet.FormatInfo;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.CurrentSelectionVSAssemblyInfo;
import inetsoft.util.Tool;
import org.apache.poi.xslf.usermodel.XSLFSlide;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Current selection helper for powerpoint.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class PPTCurrentSelectionHelper extends VSCurrentSelectionHelper {
   /**
    * Constructor.
    */
   public PPTCurrentSelectionHelper(XSLFSlide slide,
      PPTCoordinateHelper coordinater)
   {
      this.slide = slide;
      this.cHelper = coordinater;
   }

   /**
    * Write the title. There are two type of the title.
    * @param info the current selection assembly info.
    */
   @Override
   protected void writeTitle(CurrentSelectionVSAssemblyInfo info) {
      Point position = info.getViewsheet().getPixelPosition(info);
      Dimension titleSize = new Dimension(info.getPixelSize().width, info.getTitleHeight());
      Rectangle2D bounds = null;

      // set the default selection list's border.
      bounds = cHelper.createBounds(position, titleSize);
      VSCompositeFormat format = info.getFormat();
      PPTVSUtil.paintBorders(bounds, format, slide, ExcelVSUtil.CELL_HEADER);

      String title = Tool.localize(info.getTitle());
      FormatInfo finfo = info.getFormatInfo();

      if(finfo != null) {
         format = finfo.getFormat(
            new TableDataPath(-1, TableDataPath.TITLE), false);
      }

      drawTextBox(title, bounds, format, info.getTitlePadding());
   }

   /**
    * Draw text box.
    */
   @Override
   protected void drawTextBox(String value, Rectangle2D bounds,
                              VSCompositeFormat format, Insets padding)
   {
      PPTValueHelper helper = new PPTValueHelper(slide);
      helper.setBounds(bounds);
      helper.setValue(value);
      helper.setFormat(format);
      helper.setPadding(padding);
      helper.writeTextBox();
   }

   /**
    * Write out selection title.
    */
   @Override
   protected void writeOutTitle(String title, String value, Rectangle2D bounds,
                                VSCompositeFormat format, double titleRatio,
                                Insets padding)
   {
      PPTValueHelper helper = new PPTValueHelper(slide);
      PPTVSUtil.writeTitleInContainer(bounds, format, Tool.localize(title),
         value, (PPTCoordinateHelper) cHelper, helper, titleRatio, padding);
   }

   private XSLFSlide slide = null;
}
