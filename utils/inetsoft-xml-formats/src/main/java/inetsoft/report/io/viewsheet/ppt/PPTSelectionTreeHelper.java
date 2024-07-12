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
