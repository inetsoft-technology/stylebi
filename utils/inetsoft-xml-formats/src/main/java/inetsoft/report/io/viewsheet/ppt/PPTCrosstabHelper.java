/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */
package inetsoft.report.io.viewsheet.ppt;

import inetsoft.report.Hyperlink;
import inetsoft.report.io.viewsheet.VSCrosstabHelper;
import inetsoft.report.io.viewsheet.VSExporter;
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
public class PPTCrosstabHelper extends VSCrosstabHelper {
   /**
    * Constructor.
    * @param slide the slide the assembly is on.
    * @param coordinater the specific PPTCoordinateHelper with slide.
    * @param vs the viewsheet the assembly is on.
    */
   public PPTCrosstabHelper(XSLFSlide slide, PPTCoordinateHelper coordinater,
                            Viewsheet vs) {
      setViewsheet(vs);
      this.slide = slide;
      this.coordinationHelper = coordinater;
      this.vHelper =  new PPTValueHelper(slide);
   }

   /**
    * Constructor.
    * @param slide the slide the assembly is on.
    * @param coordinater the specific PPTCoordinateHelper with slide.
    * @param vs the viewsheet the assembly is on.
    */
   public PPTCrosstabHelper(XSLFSlide slide, PPTCoordinateHelper coordinater,
                            Viewsheet vs, VSAssembly assembly) {
      this(slide, coordinater, vs);
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

      Rectangle2D bounds = getObjectPixelBounds(info, lens, coordinationHelper);
      VSCompositeFormat format = info.getFormat();
      vHelper.setBounds(bounds);

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
