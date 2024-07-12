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
import inetsoft.report.io.viewsheet.CoordinateHelper;
import inetsoft.report.io.viewsheet.VSSelectionListHelper;
import inetsoft.report.io.viewsheet.excel.ExcelVSUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.util.Tool;
import org.apache.poi.xslf.usermodel.XSLFSlide;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * SelectionList helper for powerpoint.
 *
 * @version 8.5, 15/8/2006
 * @author InetSoft Technology Corp
 */
public class PPTSelectionListHelper extends VSSelectionListHelper {
   /**
    * Constructor.
    */
   public PPTSelectionListHelper(XSLFSlide slide, PPTCoordinateHelper cHelper,
                                 PPTVSExporter exporter)
   {
      this.slide = slide;
      this.cHelper = cHelper;
      this.exporter = exporter;
   }

   /**
    * Write title which inContainer.
    */
   @Override
   protected void writeTitleInContainer(SelectionListVSAssembly assembly,
                                        VSCompositeFormat format, String title, double titleRatio,
                                        Insets padding)
   {
      PPTValueHelper helper = new PPTValueHelper(slide);
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();
      Rectangle2D bounds = cHelper.getBounds(assembly, CoordinateHelper.TITLE);
      PPTVSUtil.writeTitleInContainer(bounds, format, Tool.localize(info.getTitle()),
                                      title, (PPTCoordinateHelper) cHelper, helper, titleRatio,
                                      padding);
   }

   /**
    * Write the object background.
    */
   @Override
   protected void writeObjectBackground(SelectionListVSAssembly assembly,
                                        VSCompositeFormat format) {
      Rectangle2D bounds = cHelper.getBounds(assembly, CoordinateHelper.ALL);
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();
      boolean incs = assembly.getContainer() instanceof CurrentSelectionVSAssembly;

      if(!info.isTitleVisible()) {
         Rectangle2D title = cHelper.getBounds(assembly, CoordinateHelper.TITLE);
         double y = bounds.getY() + (incs ? 0 : title.getHeight());
         double h = bounds.getHeight() - title.getHeight();

         bounds.setRect(bounds.getX(), y, bounds.getWidth(), h);
      }


      PPTValueHelper helper = new PPTValueHelper(slide);
      helper.setBounds(bounds);
      helper.setValue("");
      helper.setFormat(format);
      helper.writeTextBox();
   }

   /**
    * Write the title. There are two type of the title.
    */
   @Override
   protected void writeTitle(SelectionListVSAssemblyInfo info, List<SelectionValue> values) {
      Rectangle2D bounds = (Rectangle2D) boundsList.get(0);
      VSCompositeFormat format = info.getFormat();
      String title = Tool.localize(info.getTitle());
      FormatInfo finfo = info.getFormatInfo();

      if(finfo != null) {
         format = finfo.getFormat(new TableDataPath(-1, TableDataPath.TITLE), false);
      }

      bounds.setFrame(bounds.getX(), bounds.getY(), bounds.getWidth(),
         !info.isTitleVisible() ? 0 : bounds.getHeight());

      PPTValueHelper helper = new PPTValueHelper(slide);
      helper.setBounds(bounds);
      helper.setValue(title);
      helper.setFormat(format);
      helper.setPadding(info.getTitlePadding());
      helper.writeTextBox();
   }

   /**
    * Write the list.
    */
   @Override
   protected void writeList(SelectionListVSAssembly assembly, List<SelectionValue> values) {
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();
      SelectionValue value = null;
      String valueLabel = null;

      VSCompositeFormat pFormat = (VSCompositeFormat) info.getFormat().clone();
      double height = getInvisibleTitleHeight(assembly);

      CompositeSelectionValue root = new CompositeSelectionValue();
      root.setSelectionList(info.getSelectionList());
      boolean hasSelected =
         root.getSelectionValues(-1, SelectionValue.STATE_SELECTED, 0).size() > 0;

      for(int i = 0; i < values.size() && i < boundsList.size(); i++) {
         valueLabel = "";
         value = values.get(i);
         VSCompositeFormat vsformat = value == null ? null : value.getFormat();
         VSCompositeFormat format = (vsformat == null) ?
            new VSCompositeFormat() : vsformat;

         format = VSSelectionListHelper.getValueFormat(value, format, hasSelected);

         if(i < values.size() - 1 && i >= boundsList.size() - 2) {
            // last cell but still got more elements
            valueLabel = catalog.getString("More") + "...";
         }
         else {
            valueLabel = value.getLabel();
         }

         // merge the obj border and cell's border
         if((format.getBorders() == null || format.getBorderColors() == null) &&
            pFormat != null)
         {
            Insets pBorders = new Insets(0, 0, 0, 0);
            format.getUserDefinedFormat().setBorders(pBorders);
         }

         Rectangle2D bounds;

         if(i < boundsList.size() - 1) {
            bounds = (Rectangle2D) boundsList.get(i + 1);

            bounds.setFrame(bounds.getX(), bounds.getY() - height,
               bounds.getWidth(), bounds.getHeight());

            PPTValueHelper helper = new PPTValueHelper(slide);
            helper.setBounds(getValueLabelBounds(info, bounds));
            helper.setValue(valueLabel);
            helper.setFormat(format);
            helper.setPadding(info.getCellPadding());
            helper.writeTextBox();

            paintMeasure(value, bounds, info.getSelectionList(), info);
         }
      }

      // set the default selection list's border.
      Rectangle2D bounds = cHelper.getBounds(assembly, CoordinateHelper.ALL);

      if(!info.isTitleVisible()) {
         Rectangle2D title = cHelper.getBounds(assembly, CoordinateHelper.TITLE);
         double y = bounds.getY() + title.getHeight();
         double h = bounds.getHeight() - title.getHeight();

         bounds.setRect(bounds.getX(), y, bounds.getWidth(), h);
      }

      VSCompositeFormat allformat = info.getFormat();
      PPTVSUtil.paintBorders(bounds, allformat, slide,
                             ExcelVSUtil.CELL_HEADER);
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
   protected void writeText(String txt, Rectangle2D bounds, VSCompositeFormat format) {
      exporter.writeText(txt, bounds, format);
   }

   private XSLFSlide slide = null;
   private PPTVSExporter exporter;
}
