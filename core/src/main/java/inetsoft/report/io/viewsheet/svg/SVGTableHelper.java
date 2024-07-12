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
package inetsoft.report.io.viewsheet.svg;

import inetsoft.report.Hyperlink;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.report.io.viewsheet.VSTableHelper;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Table helper used when exporting to SVG.
 */
public class SVGTableHelper extends VSTableHelper {
   /**
    * Creates a new instance of <tt>SVGTableHelper</tt>.
    *
    * @param helper the coordinate helper.
    * @param vs     the viewsheet being exported.
    */
   public SVGTableHelper(SVGCoordinateHelper helper, Viewsheet vs){
      setViewsheet(vs);
      this.vHelper =  helper;
   }

   /**
    * Creates a new instance of <tt>SVGTableHelper</tt>.
    *
    * @param helper   the coordinate helper.
    * @param vs       the viewsheet being exported.
    * @param assembly the assembly being written.
    */
   public SVGTableHelper(SVGCoordinateHelper helper, Viewsheet vs,
                         VSAssembly assembly)
   {
      this(helper, vs);
      this.assembly = assembly;
   }

   @Override
   protected void writeTitleCell(int startX, int startY, Dimension bounds,
                                 Rectangle2D pixelbounds, int irow, int icol,
                                 VSCompositeFormat format, String dispText,
                                 Object dispObj, Hyperlink.Ref hyperlink,
                                 VSCompositeFormat parentformat, Rectangle rec)
   {
      boolean needBG = SVGVSExporter.isBackgroundNeed(parentformat, format);
      pixelbounds = VSUtil.getShrinkTitleWidth(
         assembly, vHelper, pixelbounds, bounds, new Point(startX, startY),
         columnPixelW);
      vHelper.writeTableCell(startX, startY, bounds, pixelbounds, format,
                             dispText, dispObj, needBG, null, null,
                             getTitlePadding());
   }

   @Override
   protected void writeTableCell(int startX, int startY, Dimension bounds,
                                 Rectangle2D pixelbounds, int row, int col,
                                 VSCompositeFormat format, String dispText,
                                 Object dispObj, Hyperlink.Ref hyperlink,
                                 VSCompositeFormat parentformat,
                                 Rectangle rec, Insets padding)
   {
      boolean needBG = SVGVSExporter.isBackgroundNeed(parentformat, format);
      vHelper.writeTableCell(startX, startY, bounds, pixelbounds, format,
                             dispText, dispObj, needBG,
                             (SVGVSExporter) getExporter(),
                             getAnnotation(row, col), padding);
   }

   @Override
   protected void drawObjectFormat(TableDataVSAssemblyInfo info, VSTableLens lens,
                                   boolean borderOnly)
   {
      if(info == null) {
         return;
      }

      Rectangle2D bounds = getObjectPixelBounds(info, lens, vHelper);
      VSCompositeFormat format = info.getFormat();

      if(borderOnly) {
         VSCompositeFormat format2 = new VSCompositeFormat();
         format2.getUserDefinedFormat().setBorders(format.getBorders());
         format2.getUserDefinedFormat().setBorderColors(format.getBorderColors());
         format2.getUserDefinedFormat().setBackground(null);

         format = format2;
      }
      else {
         format = (VSCompositeFormat) format.clone();
         format.getUserDefinedFormat().setBorders(new Insets(0, 0, 0, 0));
      }

      vHelper.drawTextBox(bounds, format, null);
   }

   @Override
   protected void distributeColumn(TableDataVSAssemblyInfo info,
                                   VSTableLens lens, int[] columnStarts,
                                   int[] columnWidths, int n)
   {
      // ignore
   }

   /**
    * Set exporter.
    */
   @Override
   public void setExporter(VSExporter exporter) {
      super.setExporter(exporter);

      initAnnotation();
   }

   private SVGCoordinateHelper vHelper = null;
}
