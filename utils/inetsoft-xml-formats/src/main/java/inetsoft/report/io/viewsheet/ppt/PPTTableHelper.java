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

import inetsoft.report.Hyperlink;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.report.io.viewsheet.VSTableHelper;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.report.composition.VSTableLens;
import java.awt.*;
import java.awt.geom.*;
import org.apache.poi.xslf.usermodel.XSLFSlide;

/**
 * Crosstab helper for powerpoint.
 *
 * @version 8.5, 8/18/2006
 * @author InetSoft Technology Corp
 */
public class PPTTableHelper extends VSTableHelper {
   /**
    * Constructor.
    * @param slide the slide the assembly is on.
    * @param coordinationHelper the specific PPTCoordinateHelper with slide.
    * @param vs the viewsheet the assembly is on.
    */
   public PPTTableHelper(XSLFSlide slide,
                         PPTCoordinateHelper coordinationHelper, Viewsheet vs)
   {
      setViewsheet(vs);
      this.slide = slide;
      this.coordinationHelper = coordinationHelper;
      this.vHelper =  new PPTValueHelper(slide);
   }

   /**
    * Constructor.
    * @param slide the slide the assembly is on.
    * @param coordinationHelper the specific PPTCoordinateHelper with slide.
    * @param vs the viewsheet the assembly is on.
    */
   public PPTTableHelper(XSLFSlide slide,
                         PPTCoordinateHelper coordinationHelper,
                         Viewsheet vs, VSAssembly assembly)
   {
      this(slide, coordinationHelper, vs);
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
      pixelbounds = VSUtil.getShrinkTitleWidth(assembly, coordinationHelper,
         pixelbounds, bounds, new Point(startX, startY), columnPixelW);
      PPTVSUtil.writeTableCell(startX, startY, bounds, pixelbounds, format,
                               dispText, dispObj, coordinationHelper, vHelper,
                               (PPTVSExporter) getExporter(), null,
                               getTitlePadding());
   }

   /**
    * Implementation of the abstarct method for writing crosstab cells to ppt.
    * @param startX the display area's left-most X coordinate.
    * @param startY the display Area's top-most Y coordinate.
    * @param bounds the Dimension returned by getSpan().
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
                                 Rectangle rec, Insets padding)
   {
      Rectangle2D rect = (pixelbounds == null) ? null : pixelbounds;
      PPTVSUtil.writeTableCell(startX, startY, bounds, rect, format, dispText,
                               dispObj, coordinationHelper, vHelper,
                               (PPTVSExporter) getExporter(),
                               getAnnotation(row, col), padding);
   }

   /**
    * Write the data for table assembly.
    * @param info the specified TableVSAssemblyInfo.
    */
   @Override
   protected void drawObjectFormat(TableDataVSAssemblyInfo info,
                                   VSTableLens lens, boolean borderOnly)
   {
      if(info == null) {
         return;
      }

      VSCompositeFormat format = info.getFormat();
      Rectangle2D bounds = getObjectPixelBounds(info, lens, coordinationHelper);

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

      vHelper.setValue(null);
      vHelper.setBounds(bounds);
      vHelper.setFormat(format);
      vHelper.writeTextBox();
   }

   /**
    * Get the pixel bounds of a table cell.
    */
   @Override
   protected Rectangle2D getPixelBounds(TableDataVSAssemblyInfo info,
                                   int r, int c, Dimension span,
                                   VSTableLens lens)
   {
      Rectangle2D rect = super.getPixelBounds(info, r, c, span, lens);

      return new Rectangle2D.Double(rect.getX() * PPTVSUtil.PIXEL_TO_POINT,
                                    rect.getY() * PPTVSUtil.PIXEL_TO_POINT,
                                    rect.getWidth() * PPTVSUtil.PIXEL_TO_POINT,
                                    rect.getHeight() * PPTVSUtil.PIXEL_TO_POINT);
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
    * Get the pixel to point ratio.
    */
   @Override
   protected double getPixelToPointRatio() {
      return PPTVSUtil.PIXEL_TO_POINT;
   }

   /**
    * Set exporter.
    */
   @Override
   public void setExporter(VSExporter exporter) {
      super.setExporter(exporter);

      initAnnotation();
   }

   private XSLFSlide slide = null;
   private PPTCoordinateHelper coordinationHelper = null;
   private PPTValueHelper vHelper = null;
}
