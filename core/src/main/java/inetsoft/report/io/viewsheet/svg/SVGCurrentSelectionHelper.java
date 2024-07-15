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
package inetsoft.report.io.viewsheet.svg;

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
 * Current selection helper used when exporting to SVG.
 */
public class SVGCurrentSelectionHelper extends VSCurrentSelectionHelper {
   /**
    * Creates a new instance of <tt>SVGCurrentSelectionHelper</tt>.
    *
    * @param helper the coordinate helper.
    */
   public SVGCurrentSelectionHelper(SVGCoordinateHelper helper) {
      this.cHelper = helper;
   }

   @Override
   protected void writeTitle(CurrentSelectionVSAssemblyInfo info) {
      Point position = info.getViewsheet().getPixelPosition(info);
      Dimension titleSize = new Dimension(info.getPixelSize().width, info.getTitleHeight());
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
      Rectangle2D bounds = cHelper.createBounds(position, titleSize);
      VSCompositeFormat format = info.getFormat();
      drawTextBox(null, bounds, format, null);
      String title = Tool.localize(info.getTitle());
      boolean needBG = SVGVSExporter.isBackgroundNeed(titleFormat, info.getFormat());
      drawTextBox(title, bounds, titleFormat, needBG, info.getTitlePadding());
   }

   protected void drawTextBox(String value, Rectangle2D bounds,
                              VSCompositeFormat format, boolean needBG,
                              Insets padding)
   {
      getHelper().drawTextBox(bounds, bounds, format, value, needBG, padding);
   }

   @Override
   protected void drawTextBox(String value, Rectangle2D bounds,
                              VSCompositeFormat format, Insets padding)
   {
      getHelper().drawTextBox(bounds, format, value);
   }

   @Override
   protected void writeOutTitle(String title, String value, Rectangle2D bounds,
                                VSCompositeFormat format, double titleRatio,
                                Insets padding)
   {
      String text = ExportUtil.getTextInTitleCell(Tool.localize(title), value,
                                                  (int) bounds.getWidth(),
                                                  format.getFont(), titleRatio);
      // wrapping may cause value to be missing (on next line which is not visible).
      format = format.clone();
      format.getUserDefinedFormat().setWrapping(false);
      drawTextBox(text, bounds, format, false, padding);
   }

   private SVGCoordinateHelper getHelper() {
      return (SVGCoordinateHelper) cHelper;
   }
}
