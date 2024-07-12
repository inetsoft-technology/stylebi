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

import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.gui.viewsheet.*;
import inetsoft.report.internal.Common;
import inetsoft.report.io.viewsheet.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.util.css.*;
import org.apache.poi.sl.usermodel.PictureData.PictureType;
import org.apache.poi.xslf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

/**
 * The class is exporting to powerpoint slide.
 *
 * @version 8.5, 8/7/2006
 * @author InetSoft Technology Corp
 */
public class PPTVSExporter extends AbstractVSExporter {
   /**
    * Constructor.
    * @param context the specified PPTContext.
    */
   public PPTVSExporter(PPTContext context, OutputStream stream) {
      this.show = context.getSlideShow();
      this.stream = stream;
   }

   @Override
   public int getFileFormatType() {
      return FileFormatInfo.EXPORT_TYPE_POWERPOINT;
   }

   /**
    * Create a new slide.
    * @param slideName the name of the slide created.
    */
   private void setUpSlide(String slideName) {
      try {
         slide = show.createSlide();
         Color bg = CSSDictionary.getDictionary().getBackground(
            new CSSParameter(CSSConstants.VIEWSHEET, null, null, null));

         if(bg != null) {
            slide.getBackground().setFillColor(bg);
         }

         final TableDataPath dataPath = new TableDataPath(-1, TableDataPath.OBJECT);
         final VSCompositeFormat format = viewsheet.getFormatInfo().getFormat(dataPath);

         if(format != null) {
            final Color background = format.getBackground();

            if(background != null) {
               final XSLFBackground slideBackground = slide.getBackground();

               if(slideBackground != null) {
                  slideBackground.setFillColor(background);
               }
            }
         }
         // title is unvisible.
         //TextBox title = slide.addTitle();
         //RichTextRun rtr = title.getTextRun().getRichTextRuns()[0];
         //title.setText(slideName);
         //rtr.setFontSize(VSFontHelper.getDefaultFont().getSize());

         // Set the title's visible is false.
         //rtr.setFontColor(java.awt.Color.white);
      }
      catch(Exception e) {
         LOG.error("Failed to initialize slide", e);
      }
   }

   /**
    * Write the in-mem document (workbook or show) to OutputStream.
    */
   @Override
   public void write() {
      try {
         show.write(stream);
         stream.close();
      }
      catch(Exception ex) {
         LOG.error("Failed to write presentation", ex);
      }
   }

   /**
    * Specify the size and cell size of sheet.
    * @param vsheet the Viewsheet to be exported.
    * @param sheet the specified sheet name.
    * @param box the specified viewsheet sandbox.
    */
   @Override
   protected void prepareSheet(Viewsheet vsheet, String sheet, ViewsheetSandbox box)
      throws Exception
   {
      super.prepareSheet(vsheet, sheet, box);
      setUpSlide(sheet);

      this.coordinator = new PPTCoordinateHelper();
      coordinator.setViewsheet(vsheet);
      Dimension size = coordinator.getOutputSize(viewsheet.getPreferredSize());
      Dimension pptSize = show.getPageSize();
      // fix bug1374173407396, scale the min ppt default size.
      pptSize.width = (int) (pptSize.width * 0.82);
      pptSize.height = (int) (pptSize.height * 0.82);
      Dimension newSize = new Dimension(Math.max(size.width, pptSize.width),
                                        Math.max(size.height, pptSize.height));

      if(newSize.width > 4000 || newSize.height > 4000) {
         LOG.warn("The width or height of style page is greater than 142 cm");
         newSize.width = Math.min(4000, newSize.width);
         newSize.height = Math.min(4000, newSize.height);
      }

      show.setPageSize(newSize);
   }

   /**
    * This method is called before writing the specified assembly.
    */
   @Override
   protected void prepareAssembly(VSAssembly assembly) {
      super.prepareAssembly(assembly);
      coordinator.setViewsheet(assembly.getViewsheet());
   }

   /**
    * Check need use the region table lens or not.
    */
   @Override
   protected boolean needRegionLens() {
      return true;
   }

   /**
    * Get the number of rows to display in a table.
    */
   @Override
   protected int getRegionColCount(TableDataVSAssembly table, TableLens data) {
      int width = super.getRegionColCount(table, data);
      int max = Math.max(0, PPT_MAX_COL - table.getPixelOffset().x / AssetUtil.defw);

      if(width > max) {
         LOG.warn(
            "Table " + table + " data will be truncated because number of " +
            "columns exceeds maximum (" + PPT_MAX_COL + ")");
         return max;
      }
      else {
         return width;
      }
   }

   /**
    * Get the number of rows to display in a table.
    */
   @Override
   protected int getRegionRowCount(TableDataVSAssembly table, TableLens data) {
      int height = super.getRegionRowCount(table, data);
      int rows = data.getRowCount(); // The number of rows includes the header rows
      int height0 = 0;
      int max_height = isMatchLayout() ?
         table.getVSAssemblyInfo().getPixelSize().height : PPT_MAX_ROW;
      int[] rowHeights = data instanceof VSTableLens ? ((VSTableLens) data).getRowHeights() : null;

      for(int i = 0; i < rows; i++) {
         height0 += rowHeights == null || i >= rowHeights.length ? AssetUtil.defh : rowHeights[i];

         if(i > height) {
            return height;
         }

         if(height0 > max_height) {
            setMaxRows(i);

            if(LOG.isDebugEnabled()) {
               LOG.debug("Table " + table + " data is truncated to " + i + " rows.");
            }

            return i;
         }
      }

      return height;
   }

   /**
    * Write image assembly.
    * @ImageVSAssembly the ImageVSAssembly to be written.
    */
   @Override
   protected void writeImageAssembly(ImageVSAssembly assembly, XPortalHelper helper) {
      ImageVSAssemblyInfo info = (ImageVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      Rectangle2D bounds = coordinator.getBounds(info);

      try {
         VSImage image = new VSImage(info.getViewsheet());
         image.setTheme(theme);
         image.setAssemblyInfo(info);
         Image rimg = VSUtil.getVSImage(info.getRawImage(), info.getImage(),
                                        assembly.getViewsheet(),
                                        image.getContentWidth(),
                                        image.getContentHeight(),
                                        image.getAssemblyInfo().getFormat(),
                                        helper);

         image.setRawImage(rimg);
         BufferedImage img = (BufferedImage) image.getImage(true);
         writePicture(img, bounds, info.getImageAlpha());
      }
      catch(Exception ex) {
         LOG.error("Failed to write image assembly: " + assembly.getAbsoluteName(), ex);
      }
   }

   /**
    * Write submit assembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeSubmit(SubmitVSAssembly assembly) {
      // it is unnecessray to export submit assembly
      return;
   }

   /**
    * Write gauge assembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeGauge(GaugeVSAssembly assembly) {
      GaugeVSAssemblyInfo info = (GaugeVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      Rectangle2D bounds = coordinator.getBounds(info);

      try {
         writePicture(getImage(assembly), bounds);
      }
      catch(Exception e) {
         LOG.error("Failed to write gauge: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write thermometer assembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeThermometer(ThermometerVSAssembly assembly) {
      ThermometerVSAssemblyInfo info = (ThermometerVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      Rectangle2D bounds = coordinator.getBounds(info);

      try {
         writePicture(getImage(assembly), bounds);
      }
      catch(Exception e) {
         LOG.error("Failed to write thermometer: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write cylinder assembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeCylinder(CylinderVSAssembly assembly) {
      CylinderVSAssemblyInfo info = (CylinderVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      Rectangle2D bounds = coordinator.getBounds(info);

      try {
         writePicture(getImage(assembly), bounds);
      }
      catch(Exception e) {
         LOG.error("Failed to write cylinder: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write SlidingScale assembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeSlidingScale(SlidingScaleVSAssembly assembly) {
      SlidingScaleVSAssemblyInfo info = (SlidingScaleVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      Rectangle2D bounds = coordinator.getBounds(info);

      try {
         writePicture(getImage(assembly), bounds);
      }
      catch(Exception e) {
         LOG.error("Failed to write sliding scale: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write RadioButton VSAssembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeRadioButton(RadioButtonVSAssembly assembly) {
      try {
         RadioButtonVSAssemblyInfo info = (RadioButtonVSAssemblyInfo) assembly.getVSAssemblyInfo();

         if(info == null) {
            return;
         }

         Rectangle2D bounds = coordinator.getBounds(info);
         writePicture(getImage(assembly), bounds);
      }
      catch(Exception e) {
         LOG.error("Failed to write radio button: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write CheckBox VSAssembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeCheckBox(CheckBoxVSAssembly assembly) {
      try {
         CheckBoxVSAssemblyInfo info = (CheckBoxVSAssemblyInfo) assembly.getVSAssemblyInfo();

         if(info == null) {
            return;
         }

         Rectangle2D bounds = coordinator.getBounds(info);
         writePicture(getImage(assembly), bounds);
      }
      catch(Exception e) {
         LOG.error("Failed to write checkbox: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write slider assembly.
    * @param assembly the SliderVSAssembly.
    */
   @Override
   protected void writeSlider(SliderVSAssembly assembly) {
      SliderVSAssemblyInfo info = (SliderVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      Rectangle2D bounds = coordinator.getBounds(info);

      try {
         writePicture(getImage(assembly), bounds);
      }
      catch(Exception e) {
         LOG.error("Failed to write slider: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write spinner assembly.
    * @param assembly the spinnerVSAssembly.
    */
   @Override
   protected void writeSpinner(SpinnerVSAssembly assembly) {
      SpinnerVSAssemblyInfo info = (SpinnerVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      Rectangle2D bounds = coordinator.getBounds(info);

      try {
         writePicture(getImage(assembly), bounds);
      }
      catch(Exception e) {
         LOG.error("Failed to write spinner: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write comboBox assembly.
    * @param assembly the specified ComboBoxVSAssembly.
    */
   @Override
   protected void writeComboBox(ComboBoxVSAssembly assembly) {
      ComboBoxVSAssemblyInfo info = (ComboBoxVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      Rectangle2D bounds = coordinator.getBounds(info);

      try {
         writePicture(getImage(assembly), bounds);
      }
      catch(Exception e) {
         LOG.error("Failed to write combo box: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write selection list assembly.
    * @param assembly the specified SelectionListVSAssembly.
    */
   @Override
   protected void writeSelectionList(SelectionListVSAssembly assembly) {
      try {
         PPTSelectionListHelper helper = new PPTSelectionListHelper(slide, coordinator, this);
         helper.write(assembly);
      }
      catch(Exception e) {
         LOG.error("Failed to write selection list: " + assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write selection list assembly.
    * @param assembly the specified SelectionListVSAssembly.
    */
   @Override
   protected void writeSelectionTree(SelectionTreeVSAssembly assembly) {
      try {
         PPTSelectionTreeHelper helper = new PPTSelectionTreeHelper(
            coordinator, slide, this);
         helper.write(assembly);
      }
      catch(Exception e) {
         LOG.error("Failed to write selection tree: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write textinput with format.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeTextInput(TextInputVSAssembly assembly) {
      Object value = assembly.getSelectedObject() != null ?
         assembly.getSelectedObject() : null;
      writeText(assembly, value == null ? "" : Tool.getDataString(value, assembly.getDataType()));
   }

   /**
    * Write text assembly.
    * @param text the specified VSAssembly.
    */
   @Override
   protected void writeText(TextVSAssembly text) {
      TextVSAssemblyInfo info = (TextVSAssemblyInfo) text.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      VSCompositeFormat fmt = info.getFormat();

      if(fmt != null && fmt.getPresenter() != null) {
         try {
            Rectangle2D bounds = coordinator.getBounds(info);
            writePicture(getImage(text), bounds);
            return;
         }
         catch(Exception ex) {
            LOG.error("Failed to write text presenter image: " +
               text.getAbsoluteName(), ex);
         }
      }

      writeText(text.getText(), coordinator.getBounds(info),
         getTextFormat(info), info.isShadow(), info.getPadding());
   }

   /**
    * Write text with format.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeText(VSAssembly assembly, String txt) {
      try {
         VSAssemblyInfo info = assembly.getVSAssemblyInfo();

         if(info == null) {
            return;
         }

         boolean shadowed = false;

         if(info instanceof TextVSAssemblyInfo) {
            shadowed = ((TextVSAssemblyInfo) info).getShadowValue();
         }

         writeText(txt, coordinator.getBounds(info), getTextFormat(info), shadowed);
      }
      catch(Exception e) {
         LOG.error("Failed to write text \"" + txt +
            "\" for assembly: " + assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write text with format.
    * @param text the specified text.
    * @param pos the specified position.
    * @param size the specified text size.
    * @param format the specified text format.
    */
   @Override
   protected void writeText(String text, Point pos,
                            Dimension size, VSCompositeFormat format) {
      try {
         writeText(text, coordinator.getBounds(pos, size), format);
      }
      catch(Exception e) {
         LOG.error("Failed to write text: " + text, e);
      }
   }

   /**
    * Write text with format.
    */
   public void writeText(String text, Rectangle2D bounds,
                         VSCompositeFormat format)
   {
       writeText(text, bounds, format, false, null);
   }

   /**
    * Write text with format.
    */
   public void writeText(String text, Rectangle2D bounds,
                         VSCompositeFormat format, boolean shadowed)
   {
      writeText(text, bounds, format, shadowed, null);
   }

   /**
    * Write text with format.
    */
   public void writeText(String text, Rectangle2D bounds,
                         VSCompositeFormat format, boolean shadowed,
                         Insets padding)
   {
      try {
         PPTValueHelper helper = new PPTValueHelper(slide);
         helper.setBounds(bounds);
         helper.setValue(text);
         helper.setFormat(format);
         helper.setShadowed(shadowed);
         helper.setPadding(padding);
         helper.writeTextBox();
      }
      catch(Exception e) {
         LOG.error("Failed to write text box: " + text, e);
      }
   }

   /**
    * Write text with rich text format
    * @param richTexts
    * @param bounds
    * @param format
    * @param shadowed
    */
   public void writeRichText(java.util.List richTexts, Rectangle2D bounds,
                             VSCompositeFormat format, int textAlignment, boolean shadowed)
   {
      try {
         PPTValueHelper helper = new PPTValueHelper(slide);
         helper.setBounds(bounds);
         helper.setShadowed(shadowed);
         helper.setFormat(format);
         helper.writeRichTextContent(richTexts, textAlignment);
      }
      catch(Exception e) {
         LOG.error("Failed to write rich text box", e);
      }
   }

   /**
    * Write a range slider.
    */
   @Override
   protected void writeTimeSlider(TimeSliderVSAssembly assm) {
      try {
         TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) assm.getVSAssemblyInfo();

         if(info == null) {
            return;
         }

         if(assm.getContainer() instanceof CurrentSelectionVSAssembly) {
            FormatInfo finfo = info.getFormatInfo();
            VSCompositeFormat format = new VSCompositeFormat();

            if(finfo != null) {
               format = finfo.getFormat(
                  new TableDataPath(-1, TableDataPath.TITLE), false);
            }

            format.getUserDefinedFormat().setBackground(
               ExportUtil.getBackGroundColor(format, info.getFormat()));

            if(info.isTitleVisible()) {
               CurrentSelectionVSAssemblyInfo cinfo = (CurrentSelectionVSAssemblyInfo)
                  assm.getContainer().getInfo();
               PPTVSUtil.writeTitleInContainer(
                  coordinator.getBounds(assm, CoordinateHelper.TITLE), format,
                  Tool.localize(info.getTitle()),
                  assm.getDisplayValue(true, true), coordinator,
                  new PPTValueHelper(slide), cinfo.getTitleRatio(), info.getTitlePadding());
            }

            if(assm.getPixelSize().height > info.getTitleHeight()) {
               double h = 0;

               if(!info.isTitleVisible()) {
                  h = coordinator.getBounds(assm, CoordinateHelper.TITLE).getHeight();
               }

               Rectangle2D abounds = coordinator.getBounds(assm, CoordinateHelper.ALL);
               Rectangle2D bounds = coordinator.getBounds(assm, CoordinateHelper.DATA);
               bounds = new Rectangle2D.Double(
                  bounds.getX(), bounds.getY() - h, bounds.getWidth(),
                  (CoordinateHelper.getAssemblySize(assm, null).height - info.getTitleHeight()) *
                  coordinator.getScale());

               if(bounds.getY() + bounds.getHeight() > abounds.getY() + abounds.getHeight()) {
                  return;
               }

               fixBounds(bounds);
               writePicture(getImage(assm), bounds);
            }
         }
         else {
            Rectangle2D bounds = coordinator.getBounds(info);
            writePicture(getImage(assm), bounds);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to write time slider: " + assm.getAbsoluteName(), e);
      }
   }

   /**
    * Fix the Bounds. The bounds is defined with Rectangle2D. However, the
    * picture.setAnchor() in writePicture needs a bounds defined with Rectangle.
    * So there may be a pixel error when write the picture. This method is to
    * fix this error.
    * @param bounds the bounds to be fixed.
    */
   private void fixBounds(Rectangle2D bounds) {
      double width = bounds.getWidth();
      double height = bounds.getHeight();

      if(((int) (bounds.getX() + width)) -
         ((int) bounds.getX() + (int) width) == 1)
      {
         width = Math.ceil(bounds.getWidth());
      }

      if(((int) (bounds.getY() + height)) -
         ((int) bounds.getY() + (int) height) == 1)
      {
         height = Math.ceil(bounds.getHeight());
      }

      bounds.setRect(new Rectangle2D.Double(bounds.getX(), bounds.getY(),
         width, height));
   }

   /**
    * Write text with format.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeCalendar(CalendarVSAssembly assembly) {
      if(assembly.getShowType() == CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE) {
         writeCalendarTitle(assembly);
         return;
      }

      try {
         CalendarVSAssemblyInfo info =
            (CalendarVSAssemblyInfo) assembly.getVSAssemblyInfo();

         if(info == null) {
            return;
         }

         Rectangle2D bounds = coordinator.getBounds(info);
         writePicture(getImage(assembly), bounds);
      }
      catch(Exception e) {
         LOG.error("Failed to write calendar: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write the single calendar title cell.
    */
   private void writeCalendarTitle(CalendarVSAssembly assembly) {
      try {
         CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo)
            assembly.getVSAssemblyInfo();
         String txt = info.getDisplayValue();

         if(info == null) {
            return;
         }

         VSCompositeFormat format = getCalendarTitleFormat(info);
         Dimension size = info.getPixelSize();

         // calculate the area the String will cover
         Dimension pixsize = calculateSizeInPixel(info);
         Font styleFont = format.getFont() == null ?
            VSFontHelper.getDefaultFont() : format.getFont();
         FontMetrics fontMetrics = Common.getFontMetrics(styleFont);
         int totalLength = fontMetrics.stringWidth(txt) == 0 ?
            (int) pixsize.getWidth() : fontMetrics.stringWidth(txt);
         double height = Math.ceil(totalLength / pixsize.getWidth()) * AssetUtil.defh;
         height = (height >= size.getHeight()) ? size.getHeight() : height;
         pixsize = new Dimension((int) info.getPixelSize().getWidth(), (int) height);
         format.getUserDefinedFormat().setWrapping(true);
         Rectangle2D bounds =
            coordinator.createBounds(info.getViewsheet().getPixelPosition(info), pixsize);

         PPTValueHelper helper = new PPTValueHelper(slide);
         helper.setBounds(bounds);
         helper.setValue(txt);
         helper.setFormat(format);
         helper.writeTextBox();
      }
      catch(Exception e) {
         LOG.error("Failed to write calendar title: " + assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write table assembly.
    * @param assembly the specified TableVSAssembly.
    * @param lens the specified VSTableLens.
    */
   @Override
   protected void writeTable(TableVSAssembly assembly, VSTableLens lens) {
      try {
         PPTTableHelper helper = new PPTTableHelper(slide, coordinator,
            assembly.getViewsheet(), assembly);
         helper.setExporter(this);
         helper.write(assembly, lens);
      }
      catch(Exception e) {
         LOG.error("Failed to write table: " + assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write crosstab assembly.
    * @param assembly the specified CrosstabVSAssembly.
    * @param lens the specified VSTableLens.
    */
   @Override
   protected void writeCrosstab(CrosstabVSAssembly assembly, VSTableLens lens) {
      try {
         PPTCrosstabHelper helper = new PPTCrosstabHelper(slide, coordinator,
            assembly.getViewsheet(), assembly);
         helper.setExporter(this);
         helper.write(assembly, lens);
      }
      catch(Exception e) {
         LOG.error("Failed to write crosstab table: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write calctable assembly.
    * @param assembly the specified CalcTableVSAssembly.
    */
   @Override
   protected void writeCalcTable(CalcTableVSAssembly assembly,
                                 VSTableLens lens)
   {
      try {
         PPTCrosstabHelper helper = new PPTCrosstabHelper(slide, coordinator,
            assembly.getViewsheet(), assembly);
         helper.setExporter(this);
         helper.write(assembly, lens);
      }
      catch(Exception e) {
         LOG.error("Failed to write formula table: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write chart aggregate -- temporary empty for ppt.
    * @param chartAsm the specified VSAssembly.
    */
   @Override
   protected void writeChart(ChartVSAssembly chartAsm, VGraph vgraph,
                             DataSet data, boolean imgOnly)
   {
      try {
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chartAsm.getVSAssemblyInfo();

         if(info == null) {
            return;
         }

         final BufferedImage chartImage = getChartImage(chartAsm, vgraph);
         Rectangle2D bounds = coordinator.getBounds(info);
         writePicture(chartImage, bounds);
      }
      catch(Exception ex) {
         LOG.error("Failed to write chart image: " +
            chartAsm.getAbsoluteName(), ex);
      }
   }

   /**
    * @param chartAsm the chart assembly.
    * @param vgraph the chart's vgraph.
    *
    * @return the chart image.
    */
   private BufferedImage getChartImage(ChartVSAssembly chartAsm, VGraph vgraph) {
      final int scale = 2;
      final Dimension size = chartAsm.getPixelSize();
      final BufferedImage img = new BufferedImage(size.width * scale,
         size.height * scale, BufferedImage.TYPE_4BYTE_ABGR);
      final Graphics2D g2 = ((Graphics2D) img.getGraphics());

      writeChart(g2, chartAsm, vgraph, scale);
      g2.dispose();

      return img;
   }

   /**
    * Write the slice chart.
    * @param originalAsm the original chart vs assembly.
    * @param asm the slice chart vs assembly.
    * @param graph the vgraph to paint chart.
    * @param data the data set.
    * @param img the buffered image of the slice chart.
    * @param firstTime the first to write a slice chart of the whole chart.
    */
   @Override
   protected void writeChart(ChartVSAssembly originalAsm,
                             ChartVSAssembly asm, VGraph graph,
                             DataSet data, BufferedImage img,
                             boolean firstTime, boolean imgOnly)
   {
      try {
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) asm.getVSAssemblyInfo();

         if(info == null) {
            return;
         }

         Rectangle2D bounds = coordinator.getBounds(info);
         writePicture(img, bounds);
      }
      catch(Exception ex) {
         LOG.error("Failed to write chart image: " + asm.getAbsoluteName(), ex);
      }
   }

   /**
    * Write given BufferedImage to slide.
    * @param img the specified BufferedImage.
    * @param anchor the specified anchor.
    */
   public void writePicture(BufferedImage img, Rectangle2D anchor) throws Exception {
      writePicture(img, anchor, null);
   }

   /**
    * Write given BufferedImage to slide.
    * @param img the specified BufferedImage.
    * @param anchor the specified anchor.
    * @param alpha the alpha of the image.
    */
   public void writePicture(BufferedImage img, Rectangle2D anchor, String alpha)
      throws Exception
   {
      // This is poi bug, change image data to make the image on different slide
      // have the different uid, to fix bug1205749332140 for ppt2007
      if(index > 0) {
         Color c = new Color(img.getRGB(0, 0));
         c = PPTVSUtil.changeColor(c, index);
         img.setRGB(0, 0, c.getRGB());
      }

      if(alpha != null) {
         float alphaVal = 1.0f - Integer.parseInt(alpha) / 100.0f;
         Graphics g = img.getGraphics();
         g.setColor(new Color(1.0f, 1.0f, 1.0f, alphaVal));
         g.fillRect(0, 0, img.getWidth(), img.getHeight());
         g.dispose();
      }

      OutputStream out = new ByteArrayOutputStream();
      writeSheetImage(img, out);
      byte[] tempByte = ((ByteArrayOutputStream) out).toByteArray();
      XSLFPictureData pdata = show.addPicture(tempByte, PictureType.PNG);
      XSLFPictureShape picture = slide.createPicture(pdata);
      picture.setAnchor(new Rectangle((int) anchor.getX(), (int) anchor.getY(),
         (int) anchor.getWidth(),
         (int) anchor.getHeight()));
   }

   /**
    * Write VSTab assembly.
    * @param assembly the VSTabAssembly.
    */
   @Override
   protected void writeVSTab(TabVSAssembly assembly) {
      TabVSAssemblyInfo info =
         (TabVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      Rectangle2D bounds = coordinator.getBounds(info);

      try {
         writePicture(getImage(assembly), bounds);
      }
      catch(Exception e) {
         LOG.error("Failed to write tab: " + assembly.getAbsoluteName(), e);
      }
   }

   /**
    * implements the AbstractVSExporter to writeText warning.
    * @param assemblies the Assembly array.
    * @param warning the warning message.
    * @param format the VSCompositeFormat.
    */
   @Override
   protected void writeWarningText(Assembly[] assemblies, String warning,
                                   VSCompositeFormat format) {
      Point textPos = new Point(0, 0);

      for(int i = 0; i < assemblies.length; i++) {
         VSAssembly assembly = (VSAssembly) assemblies[i];
         prepareAssembly(assembly);
         Rectangle2D bounds = coordinator.getBounds(assembly.getVSAssemblyInfo());
         textPos.x = (int) Math.max(bounds.getX() + bounds.getWidth(), textPos.x);
         textPos.y = (int) Math.max(bounds.getY() + bounds.getHeight(), textPos.y);
      }

      Dimension size = new Dimension(220, 18);
      textPos.x -= Math.min(size.width, textPos.x);
      Rectangle2D bounds = new Rectangle2D.Double(textPos.x, textPos.y,
                                                  size.width, size.height);
      writeText(warning, bounds, format);
   }

   /**
    * Write VSGroupContainer assembly.
    * @param assembly the GroupContainerAssembly.
    */
   @Override
   protected void writeGroupContainer(GroupContainerVSAssembly assembly,
                                      XPortalHelper phelper) {
      GroupContainerVSAssemblyInfo info =
         (GroupContainerVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      Rectangle2D bounds = coordinator.getBounds(info);

      try {
         String path = info.getBackgroundImage();

         if(path == null) {
            writePicture(getImage(assembly), bounds);
            return;
         }

         VSGroupContainer container = new VSGroupContainer(info.getViewsheet());
         container.setTheme(theme);
         container.setAssemblyInfo(info);
         Image rimg = VSUtil.getVSImage(null, path, assembly.getViewsheet(),
                                        container.getContentWidth(),
                                        container.getContentHeight(),
                                        container.getAssemblyInfo().getFormat(),
                                        phelper);
         container.setRawImage(rimg);
         writePicture((BufferedImage) container.getImage(true), bounds);
      }
      catch(Exception ex) {
         LOG.error("Failed to write group container: " +
            assembly.getAbsoluteName(), ex);
      }
   }

   /**
    * Write shape assembly.
    * @param assembly the specified ShapeVSAssembly.
    */
   @Override
   protected void writeShape(ShapeVSAssembly assembly) {
      ShapeVSAssemblyInfo info =
         (ShapeVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      Rectangle2D bounds = coordinator.getBounds(info);

      try {
         writePicture(getImage(assembly), bounds);
      }
      catch(Exception e) {
         LOG.error("Failed to write shape: " + assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write given VSCurrentSelection to worksheet.
    * @param assembly the specified VSCurrentSelection.
    */
   @Override
   protected void writeCurrentSelection(CurrentSelectionVSAssembly assembly) {
      try {
         PPTCurrentSelectionHelper helper =
            new PPTCurrentSelectionHelper(slide, coordinator);
         helper.write(assembly);
      }
      catch(Exception e) {
         LOG.error("Failed to write current selection: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write annotation assembly.
    * @param assembly the specified AnnotationVSAssembly.
    */
   @Override
   protected void writeAnnotation(AnnotationVSAssembly assembly) {
      super.writeAnnotation(assembly);

      AnnotationVSAssemblyInfo info =
         (AnnotationVSAssemblyInfo) assembly.getVSAssemblyInfo();
      writeAnnotation0(info);
   }

   /**
    * Write annotation assembly.
    * @param ainfo the specified AnnotationVSAssemblyInfo.
    */
   public void writeAnnotation0(VSAssemblyInfo ainfo) {
      if(!(ainfo instanceof AnnotationVSAssemblyInfo)) {
         return;
      }

      AnnotationVSAssemblyInfo info = (AnnotationVSAssemblyInfo) ainfo;
      String line = info.getLine();

      if(line != null) {
         AnnotationLineVSAssembly lineAss =
            (AnnotationLineVSAssembly) viewsheet.getAssembly(line);

         if(lineAss != null && "show".equals(((VSAssemblyInfo)
            lineAss.getInfo()).getVisibleValue()))
         {
            writeShape(lineAss);
         }
      }

      String rectangle = info.getRectangle();

      if(rectangle != null) {
         AnnotationRectangleVSAssembly recAss =
            (AnnotationRectangleVSAssembly) viewsheet.getAssembly(rectangle).clone();

         if(recAss != null) {
            writeShape(recAss);

            AnnotationRectangleVSAssemblyInfo recInfo =
               (AnnotationRectangleVSAssemblyInfo) recAss.getVSAssemblyInfo().clone();

            if(recInfo != null) {
               Rectangle2D bounds = coordinator.getBounds(recInfo);

               recInfo.setContent(recInfo.getContent().replaceAll("<br>", "%3Cbr%3E"));
               java.util.List list = AnnotationVSUtil.getAnnotationContent(
                  viewsheet, recInfo, bounds);
               int textAlign = AnnotationVSUtil.getAnnotationTextAlignment(recInfo.getContent());
               VSCompositeFormat format = getTextFormat(recInfo);
               format.getUserDefinedFormat().setWrapping(true);
               bounds = new Rectangle2D.Double(bounds.getX() + 2,
                  bounds.getY() + 2, bounds.getWidth() - 4,
                  bounds.getHeight() - 4);
               writeRichText(list, bounds, format, textAlign, false);
            }
         }
      }
   }

   @Override
   protected void writeChartBackgroundShape(ChartVSAssembly chart, ChartVSAssemblyInfo info,
                                            Point pos, Dimension size)
   {
      // no-op
   }

   private XMLSlideShow show;
   private XSLFSlide slide;
   private PPTCoordinateHelper coordinator;
   private OutputStream stream;

   private static final int PPT_MAX_ROW = 3600;
   private static final int PPT_MAX_COL = 50;
   private static final Logger LOG = LoggerFactory.getLogger(PPTVSExporter.class);
}
