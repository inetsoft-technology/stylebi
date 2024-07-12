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

import inetsoft.report.Hyperlink;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.io.viewsheet.VSCrosstabHelper;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Crosstab helper for pdf.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class PDFCrosstabHelper extends VSCrosstabHelper {
   /**
    * Constructor.
    * @param helper the PDFCoordinateHelper provides bounds info and drawing.
    * @param vs the viewsheet the assembly is on.
    */
   public PDFCrosstabHelper(PDFCoordinateHelper helper, Viewsheet vs) {
      setViewsheet(vs);
      this.vHelper =  helper;
   }

   /**
    * Constructor.
    * @param helper the PDFCoordinateHelper provides bounds info and drawing.
    * @param vs the viewsheet the assembly is on.
    */
   public PDFCrosstabHelper(PDFCoordinateHelper helper, Viewsheet vs,
                            VSAssembly assembly)
   {
      this(helper, vs);
      this.assembly = assembly;
   }

   /**
    * Implementation of the abstarct method for writing crosstab cells to Excel.
    * @param startX the Crosstab's X coordinate in cells.
    * @param startY the Crosstab's Y coordinate in cells.
    * @param bounds the Dimension returned by getSpan().
    * @param irow the cell's row.
    * @param icol the cell's col.
    * @param format the VSCompositeFormat for the cell.
    * @param dispText the cell's text to be displayed.
    */
   @Override
   protected void writeTitleCell(int startX, int startY, Dimension bounds,
                                 Rectangle2D pixelbounds, int irow, int icol,
                                 VSCompositeFormat format, String dispText,
                                 Object dispObj, Hyperlink.Ref hyperlink,
                                 VSCompositeFormat parentformat, Rectangle rec)
   {
      boolean bgNeed = PDFVSExporter.isBackgroundNeed(parentformat, format);
      pixelbounds = VSUtil.getShrinkTitleWidth(assembly, vHelper,
         pixelbounds, bounds, new Point(startX, startY), columnPixelW);
      vHelper.writeTableCell(startX, startY, bounds, pixelbounds, format,
                             dispText, dispObj, hyperlink, bgNeed, null, null,
                             getTitlePadding());
   }

   /**
    * Implementation of the abstarct method for writing crosstab cells to PDF.
    * @param startX the display area's left-most X coordinate.
    * @param startY the display Area's top-most Y coordinate.
    * @param bounds the Dimension returned by getSpan().
    * @param pixelbounds pixel position and size.
    * @param row the cell's row.
    * @param col the cell's col.
    * @param format the VSCompositeFormat for the cell.
    * @param dispText the cell's text to be displayed.
    */
   @Override
   protected void writeTableCell(int startX, int startY, Dimension bounds,
                                 Rectangle2D pixelbounds, int row, int col,
                                 VSCompositeFormat format, String dispText,
                                 Object dispObj, Hyperlink.Ref hyperlink,
                                 VSCompositeFormat parentformat,
                                 Rectangle rec,
                                 Insets padding)
   {
      boolean bgNeed = PDFVSExporter.isBackgroundNeed(parentformat, format);
      vHelper.writeTableCell(startX, startY, bounds, pixelbounds, format,
                             dispText, dispObj, hyperlink, bgNeed,
                             (PDFVSExporter) getExporter(),
                             getAnnotation(row, col), padding);
   }

   /**
    * Write the data for crosstab assembly.
    * @param info the specified CrossTableVSAssemblyInfo.
    */
   @Override
   protected void drawObjectFormat(TableDataVSAssemblyInfo info, VSTableLens lens,
                                   boolean borderOnly)
   {
      if(info == null) {
         return;
      }

      VSCompositeFormat format = info.getFormat();
      Rectangle2D bounds = getObjectPixelBounds(info, lens, vHelper);

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

   /**
    * Distribute the remaining column to the column list.
    */
   @Override
   protected void distributeColumn(TableDataVSAssemblyInfo info,
                                   VSTableLens lens, int[] columnStarts,
                                   int[] columnWidths, int n)
   {
      // ignore
   }

   /**
    * Get the table range for export.
    */
   @Override
   protected Rectangle getTableRectangle(TableDataVSAssemblyInfo info,
                                         VSTableLens lens)
   {
      Rectangle rec = super.getTableRectangle(info, lens);

      if(rec.height > 500 * AssetUtil.defh) {
         LOG.warn(
            "The length of the crosstab has been truncated to 500 rows");
         rec.height = 500 * AssetUtil.defh;
      }

      return rec;
   }

   @Override
   protected int getWrappingCellHeight(TableDataVSAssemblyInfo info, VSTableLens lens,
                                       int row, int lines)
   {
      int[] heights = lens.getRowHeights();

      if(row >= heights.length) {
         return AssetUtil.defh;
      }

      return lens.getWrappedHeight(row, true);
   }

   /**
    * Set exporter.
    */
   @Override
   public void setExporter(VSExporter exporter) {
      super.setExporter(exporter);

      initAnnotation();
   }

   private PDFCoordinateHelper vHelper = null;
   private static final Logger LOG =
      LoggerFactory.getLogger(PDFCrosstabHelper.class);
}
