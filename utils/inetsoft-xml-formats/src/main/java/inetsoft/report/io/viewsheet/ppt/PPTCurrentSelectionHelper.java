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
