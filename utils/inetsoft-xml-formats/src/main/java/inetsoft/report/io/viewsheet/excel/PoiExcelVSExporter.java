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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.graph.VGraph;
import inetsoft.graph.Visualizable;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.GraphVO;
import inetsoft.report.Hyperlink;
import inetsoft.report.*;
import inetsoft.report.composition.RegionTableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.DCMergeDatesCell;
import inetsoft.report.gui.viewsheet.*;
import inetsoft.report.internal.Common;
import inetsoft.report.io.excel.*;
import inetsoft.report.io.viewsheet.*;
import inetsoft.report.io.viewsheet.excel.chart.XSSFChartElement;
import inetsoft.report.io.viewsheet.pdf.LinkArea;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.util.XPortalHelper;
import inetsoft.web.viewsheet.service.ExcelVSExporter;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openxmlformats.schemas.drawingml.x2006.main.*;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTPicture;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Shape;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.poi.util.Units.EMU_PER_PIXEL;

/**
 * The class is exporting to excel worksheet.
 *
 * @version 8.5, 7/19/2006
 * @author InetSoft Technology Corp
 */
public class PoiExcelVSExporter extends ExcelVSExporter {
   /**
    * Constructor.
    * @param ec the specified ExcelContext.
    */
   public PoiExcelVSExporter(ExcelContext ec, OutputStream stream) {
      this.book = ec.getWorkbook();
      this.ec = ec;
      this.stream = stream;
   }

   /**
    * Create a workbook and a sheet, set the format of sheet.
    * @param sheetName the specified sheet name.
    */
   private void setUpSheet(String sheetName) {
      try {
         // this may be called twice from OfflineExcelVSExporter
         if(sheetName.equals(this.sheetName)) {
            return;
         }

         this.sheetName = sheetName;

         if(onlyDataComponents) {
            return;
         }

         sheet = book.createSheet(getSheetName(sheetName));
         patriarch = (XSSFDrawing) sheet.createDrawingPatriarch();
         boolean showGrid = "true".equals(SreeEnv.getProperty("excel.vs.export.grid.show"));
         sheet.setDisplayGridlines(showGrid);
         chartList = new Vector();
      }
      catch(Exception ex) {
         LOG.error("Failed to initialize sheet", ex);
      }
   }

   @Override
   public void fixFileError() {
      if(this.book.getNumberOfSheets() < 1) {
         this.book.createSheet();
      }
   }

   /**
    * Write the in-mem document (workbook or show) to OutputStream.
    */
   @Override
   public void write() throws IOException {
      // write out
      book.write(stream);
      // stream should be closed here because all have been written already.
      stream.close();
   }

   /**
    * Specify the size and cell size of sheet.
    * @param vsheet the Viewsheet to be exported.
    * @param sheetName the specified sheet name.
    * @param box the specified viewsheet sandbox.
    */
   @Override
   protected void prepareSheet(Viewsheet vsheet, String sheetName, ViewsheetSandbox box)
      throws Exception
   {
      PoiExcelVSUtil.processOverlap(vsheet);

      super.prepareSheet(vsheet, sheetName, box);
      setUpSheet(sheetName);

      if(sheet == null) {
         return;
      }

      TreeSet<Integer> rowsSet = new TreeSet<>();
      TreeSet<Integer> colsSet = new TreeSet<>();
      List<VSAssembly> cellBasedAssemblies = new ArrayList<>();
      split(viewsheet, rowsSet, colsSet, cellBasedAssemblies);
      colsSet.add(0);
      cellBasedAssemblies.sort(rightComparator);
      cellBasedAssemblies.forEach(assembly -> splitCellBasedVSAssembly(assembly, colsSet));

      // Split tables columns after splitting all cell based assemblies
      cellBasedAssemblies.forEach(assembly -> {
         if(assembly instanceof TableDataVSAssembly) {
            splitTableVSAssembly(viewsheet, (TableDataVSAssembly) assembly, colsSet, box);
         }
      });

      initColsForExcel(viewsheet, colsSet);
      cols = new Integer[colsSet.size()];
      colsSet.toArray(cols);

      int colLength = Math.min(ExcelConstants.EXCEL_MAX_COL, cols.length - 1);
      int rowLength = Math.min(ExcelConstants.EXCEL_MAX_ROW, rowsSet.size() - 1);

      rows = new int[rowLength + 1];
      Iterator<Integer> iter = rowsSet.iterator();

      for(int i = 0; i < rows.length; i++) {
         rows[i] = iter.next();
      }

      for(int i = 0; i < colLength; i++) {
         sheet.setColumnWidth((short) i, PoiExcelVSUtil.getValidColumnWidth(
            PoiExcelVSUtil.pixelToWidthUnits((cols[i + 1] - cols[i]))));
      }

      for(int i = 0; i < rowLength; i++) {
         Row row = sheet.createRow(i);
         row.setHeight((short) ((rows[i + 1] - rows[i])
                               * ExcelVSUtil.EXCEL_PIXEL_HEIGHT_FACTOR));
      }
   }

   private void initColsForExcel(Viewsheet vs, TreeSet<Integer> cols) {
      int last = cols.last();
      int lastPoint = (int) Math.ceil((double) last / AssetUtil.defw);
      int colCount = (int) Math.ceil((double) vs.getPixelSize().width / AssetUtil.defw);

      if(!vs.isEmbedded()) {
         for(int i = lastPoint; i <= colCount; i++) {
            cols.add(i *  AssetUtil.defw);
         }
      }
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
      int max =
         Math.max(0, ExcelConstants.EXCEL_MAX_COL - (table.getPixelOffset().x / AssetUtil.defw));

      if(width > max) {
         LOG.warn(
            "Table " + table + " data will be truncated because number of " +
            "columns exceeds maximum (" + ExcelConstants.EXCEL_MAX_COL + ")");
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
      int max =
         Math.max(0, ExcelConstants.EXCEL_MAX_ROW - (table.getPixelOffset().y / AssetUtil.defh));

      if(height > max) {
         LOG.warn(
            "Table " + table + " data will be truncated because number of " +
            "rows exceeds maximum (" + ExcelConstants.EXCEL_MAX_ROW + ")");
         return max;
      }
      else {
         return height;
      }
   }

   /**
    * Get the table assembly max row count.
    */
   @Override
   protected int getMaxRow(int start) {
      return Math.max(0, ExcelConstants.EXCEL_MAX_ROW - start);
   }

   /**
    * Get the table assembly max col count.
    */
   @Override
   protected int getMaxCol(int start) {
      return Math.max(0, ExcelConstants.EXCEL_MAX_COL - start);
   }

   /**
    * This method is called before writing the specified assembly.
    */
   @Override
   protected void prepareAssembly(VSAssembly assembly) {
      super.prepareAssembly(assembly);
      vs = assembly.getViewsheet();
   }

   private void split(Viewsheet vs, TreeSet<Integer> rows, TreeSet<Integer> cols,
                      List<VSAssembly> cellBasedAssemblies)
   {
      int rowCount = (int) Math.ceil((double) vs.getPixelSize().height / AssetUtil.defh);

      // without grid it doesn't help to split the rows/cols further for embedded vs
      if(!vs.isEmbedded()) {
         //cols.add((colCount - 1) *  AssetUtil.defw);

         for(int i = 0; i <= rowCount; i++) {
            rows.add(i * AssetUtil.defh);
         }
      }

      for(Assembly assembly : vs.getAssemblies()) {
         if(!needExport((VSAssembly) assembly)) {
            continue;
         }

         if(assembly instanceof TableDataVSAssembly ||
            assembly instanceof CurrentSelectionVSAssembly ||
            assembly instanceof SelectionListVSAssembly ||
            assembly instanceof SelectionTreeVSAssembly ||
            assembly instanceof CalendarVSAssembly &&
            ((CalendarVSAssembly) assembly).getShowType() == CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE)
         {
            cellBasedAssemblies.add((VSAssembly) assembly);
         }
         else if(assembly instanceof Viewsheet) {
            split((Viewsheet) assembly, rows, cols, cellBasedAssemblies);
         }
      }
   }

   private void splitTableVSAssembly(Viewsheet vs, TableDataVSAssembly assembly,
      TreeSet<Integer> cols, ViewsheetSandbox box)
   {
      try {
         String name = assembly.getAbsoluteName();
         VSTableLens lens = box.getVSTableLens(name, false);
         lens = getRegionTableLens(lens, assembly, box);
         TableDataVSAssemblyInfo info =
            (TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo();
         int[] columnsWidth = PoiExcelVSUtil.calculateColumnWidths(vs, info, lens,
                                                                   false, isMatchLayout(), false);
         Point pos = vs.getPixelPosition(info);
         Dimension pixelSize = vs.getPixelSize(info);
         int colWidthSum = 0;
         int colCount = lens.getColCount();

         if(info.isEmbedded() &&
            info.getViewsheet().getInfo() instanceof ViewsheetVSAssemblyInfo)
         {
            ViewsheetVSAssemblyInfo vsInfo =
               (ViewsheetVSAssemblyInfo) info.getViewsheet().getInfo();
            Rectangle vsBounds = vsInfo.getAssemblyBounds();
            Point vsPos = vs.getPixelPosition(vsInfo);

            if(vsBounds != null) {
               pos.x -= vsBounds.x;
               pos.y -= vsBounds.y;
            }

            if(vsPos != null) {
               pos.x += vsPos.x;
               pos.y += vsPos.y;
            }
         }

         cols.add(pos.x);

         for(int i = 0; i < colCount; i++) {
            colWidthSum += columnsWidth[i];

            if(colWidthSum > pixelSize.width) {
               break;
            }

            cols.add(pos.x + colWidthSum);

            if(colWidthSum == pixelSize.width) {
               break;
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to split table assembly: " +
            assembly.getAbsoluteName(), ex);
      }
   }

   /**
    * Split the excel sheet by the selection assembly position.
    * @param assembly   the cell based assembly
    * @param gridAxis   the Excel sheet axis
    */
   private void splitCellBasedVSAssembly(VSAssembly assembly, TreeSet<Integer> gridAxis) {
      Viewsheet vs = assembly.getViewsheet();
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      Point pos = vs.getPixelPosition(info);
      Dimension size = vs.getPixelSize(info);
      List<Integer> coordinates = new ArrayList<>();
      coordinates.add(pos.x);

      // multi-column selection list
      if(info instanceof SelectionListVSAssemblyInfo) {
         int ncol = ((SelectionListVSAssemblyInfo) info).getColumnCount();

         for(int i = 1; i < ncol; i++) {
            coordinates.add(pos.x + size.width * i / ncol);
         }
      }

      coordinates.add(pos.x + size.width);

      for(Integer coordinate : coordinates) {
         if(gridAxis.add(coordinate)) {
            Integer lower = gridAxis.lower(coordinate);
            int difference = coordinate - (lower == null ? 0 : lower);
            SortedSet<Integer> axisTailSet = gridAxis.tailSet(coordinate, false);
            TreeSet<Integer> newAxisTailSet = new TreeSet<>();

            axisTailSet.forEach(position -> newAxisTailSet.add(position + difference));

            axisTailSet.clear();
            gridAxis.addAll(newAxisTailSet);
         }
      }
   }

   @Override
   protected boolean isExportedAsText(Assembly assembly) {
      return super.isExportedAsText(assembly)
          || assembly instanceof TableDataVSAssembly
          || assembly instanceof SelectionListVSAssembly
          || assembly instanceof SelectionTreeVSAssembly
          || assembly instanceof ListInputVSAssembly;
   }

   protected int getExpandTableHeight(TableDataVSAssembly obj, XTable table) {
      if(isMatchLayout()) {
         return super.getExpandTableHeight(obj, table);
      }

      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) obj.getVSAssemblyInfo();
      int ypos = obj.getPixelOffset().y;
      int titleRow = 0;
      int rowCount = Math.min(table.getRowCount(), getMaxRow(ypos));

      if(info instanceof TitledVSAssemblyInfo) {
         titleRow = ((TitledVSAssemblyInfo) info).isTitleVisible() ? 0 :
            (int)Math.round((double) ((TitledVSAssemblyInfo) info).getTitleHeight() /
            AssetUtil.defh);
         titleRow = Math.max(1, titleRow);
      }

      int rows = titleRow;
      int i = 0;
      VSTableLens lens = (VSTableLens)table;

      while(i < rowCount) {
         int h = lens.getWrappedHeight(i, true);
         int count = (int) Math.round(((double)h) / AssetUtil.defh);
         count = Math.max(1, count);
         rows += count;
         i++;
      }

      return rows * AssetUtil.defh;
   }

   protected double getSelectionHeight(SelectionBaseVSAssemblyInfo info, List<Double> rowHeights,
                                       boolean isInContainer)
   {
      double newHeight = 0;

      // Add title height if it is visible
      if(info.isTitleVisible()) {
         // For selection in container, round title to small to avoid overlap.
         // For selection in vs, round title to large.
         newHeight = isInContainer ? PoiExcelVSUtil.getSelectionTitleHeight(info) :
            PoiExcelVSUtil.getExcelTitleHeight(info);
      }

      for(int i = 0; i < rowHeights.size(); i++) {
         newHeight += PoiExcelVSUtil.getSelectionCellHeight(rowHeights.get(i).intValue());
      }

      return newHeight;
   }

   /**
    * Write image assembly.
    * @ImageVSAssembly the ImageVSAssembly to be written.
    */
   @Override
   protected void writeImageAssembly(ImageVSAssembly assembly,
                                     XPortalHelper helper)
   {
      ImageVSAssemblyInfo info =
         (ImageVSAssemblyInfo) assembly.getVSAssemblyInfo();

      //@by yanie:bug1412708029832
      //if a shape or image is overlap to another assembly like table,
      //don't export image since in excel the floatable will always on top,
      //which will cover the important data cells.
      if(info == null || isOverlaps(assembly, vs, true)) {
         return;
      }

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

         if(rimg == null) {
            return;
         }

         image.setRawImage(rimg);
         BufferedImage img = (BufferedImage) image.getImage(true);

         if(info.getImageAlpha() != null) {
            float alphaVal = 1.0f - Integer.parseInt(info.getImageAlpha()) / 100.0f;
            Graphics g = img.getGraphics();
            g.setColor(new Color(1.0f, 1.0f, 1.0f, alphaVal));
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.dispose();
         }


         XSSFPicture picture = writePicture(img, getAnchorPosition(info));
         PoiExcelUtil.addLinkToImage(picture, info.getHyperlinkRef());
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception ex) {
         LOG.error("Failed to write image assembly: " +
            assembly.getAbsoluteName(), ex);
      }
   }

   /**
    * Write submit assembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeSubmit(SubmitVSAssembly assembly) {
      // it is unnecessray to export submit assembly
   }

   /**
    * Write gauge assembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeGauge(GaugeVSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      try {
         XSSFPicture picture = writePicture(getImage(assembly), getAnchorPosition(info));
         PoiExcelUtil.addLinkToImage(picture, info.getHyperlinkRef());
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception ex) {
         LOG.error("Failed to write gauge assembly: " +
            assembly.getAbsoluteName(), ex);
      }
   }

   /**
    * Write thermometer assembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeThermometer(ThermometerVSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      try {
         writePicture(getImage(assembly), getAnchorPosition(info));
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception ex) {
         LOG.error("Failed to write thermometer assembly: " +
            assembly.getAbsoluteName(), ex);
      }
   }

   /**
    * Write cylinder assembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeCylinder(CylinderVSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      try {
         writePicture(getImage(assembly), getAnchorPosition(info));
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception ex) {
         LOG.error("Failed to write cylinder assembly: " +
            assembly.getAbsoluteName(), ex);
      }
   }

   /**
    * Write SlidingScale assembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeSlidingScale(SlidingScaleVSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      try {
         writePicture(getImage(assembly), getAnchorPosition(info));
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception ex) {
         LOG.error("Failed to write sliding scale assembly: " +
            assembly.getAbsoluteName(), ex);
      }
   }

   /**
    * Write RadioButton VSAssembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeRadioButton(RadioButtonVSAssembly assembly) {
      try {
         RadioButtonVSAssemblyInfo info =
            (RadioButtonVSAssemblyInfo) assembly.getInfo();

         if(info == null) {
            return;
         }

         writePicture(getImage(assembly), getAnchorPosition(info));
      }
      catch(SheetMaxRowsException e) {
         throw e;
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
         CheckBoxVSAssemblyInfo info =
            (CheckBoxVSAssemblyInfo) assembly.getInfo();

         if(info == null) {
            return;
         }

         writePicture(getImage(assembly), getAnchorPosition(info));
      }
      catch(SheetMaxRowsException e) {
         throw e;
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
      SliderVSAssemblyInfo info = (SliderVSAssemblyInfo)
         assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      try {
         writePicture(getImage(assembly), getAnchorPosition(info));
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception ex) {
         LOG.error("Failed to write slider: " +
            assembly.getAbsoluteName(), ex);
      }
   }

   /**
    * Write spinner assembly.
    * @param assembly the spinnerVSAssembly.
    */
   @Override
   protected void writeSpinner(SpinnerVSAssembly assembly) {
      try {
         SpinnerVSAssemblyInfo info = (SpinnerVSAssemblyInfo)
            assembly.getVSAssemblyInfo();

         if(info == null) {
            return;
         }

         writePicture(getImage(assembly), getAnchorPosition(info));
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception ex) {
         LOG.error("Failed to write spinner: " +
            assembly.getAbsoluteName(), ex);
      }
   }

   /**
    * Write comboBox assembly.
    * @param assembly the specified ComboBoxVSAssembly.
    */
   @Override
   protected void writeComboBox(ComboBoxVSAssembly assembly) {
      try {
         ComboBoxVSAssemblyInfo info =
            (ComboBoxVSAssemblyInfo) assembly.getInfo();

         if(info == null) {
            return;
         }

         writePicture(getImage(assembly), getAnchorPosition(info));
      }
      catch(SheetMaxRowsException e) {
         throw e;
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
         ExcelSelectionListHelper helper = new ExcelSelectionListHelper(book);
         helper.setExporter(this);
         helper.write(sheet, assembly);
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.error("Failed to write selection list: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write selection list assembly.
    * @param assembly the specified SelectionListVSAssembly.
    */
   @Override
   protected void writeSelectionTree(SelectionTreeVSAssembly assembly) {
      try {
         ExcelSelectionTreeHelper helper = new ExcelSelectionTreeHelper(book);
         helper.setExporter(this);
         helper.write(sheet, assembly);
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.error("Failed to write selection tree: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write textinput assembly.
    * @param assembly the specified TextInputVSAssembly.
    */
   @Override
   protected void writeTextInput(TextInputVSAssembly assembly) {
      Object value = assembly.getSelectedObject();
      writeText(assembly, value == null ? "" : Tool.getDataString(value, assembly.getDataType()));
   }

   /**
    * Write text with format.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeText(VSAssembly assembly, String stext) {
      writeTextAsImage(assembly, stext);
   }

   /**
    * Write text assembly.
    */
   @Override
   protected void writeText(TextVSAssembly assembly) {
      if(!onlyDataComponents) {
         writeText(assembly, assembly.getText());
      }
   }

   @Override
   protected boolean needExport(VSAssembly assembly) {
      if(onlyDataComponents && !(assembly instanceof DataVSAssembly)) {
         if(isExportAllTabbedTables() && assembly instanceof TabVSAssembly) {
            return true;
         }
         else {
            return false;
         }
      }

      return super.needExport(assembly);
   }

   /**
    * Set the text assembly as an image.
    * @return true if the text is drawn to the output.
    */
   private boolean writeTextAsImage(VSAssembly assembly, String stext) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info == null) {
         return false;
      }

      Dimension size = null;
      PresenterPainter painter = null;
      boolean shadowed = false;

      if(info instanceof TextVSAssemblyInfo) {
         size = info.getPixelSize();
         painter = VSUtil.createPainter((TextVSAssembly) assembly);
         shadowed = ((TextVSAssemblyInfo) info).getShadowValue();
      }
      else if(info instanceof TextInputVSAssemblyInfo) {
         size = info.getPixelSize();
      }

      if(size == null) {
         size = info.getPixelSize();
      }

      try {
         BufferedImage img;

         if(painter != null) {
            img = ExportUtil.getPainterImage(painter, size.width, size.height,
                                             info.getFormat());
            writePicture(img, getAnchorPosition(info));
         }
         else {
            VSCompositeFormat format = getTextFormat(info);
            /*
            img = new BufferedImage(size.width, size.height,
                                    BufferedImage.TYPE_INT_ARGB);
            Graphics g = img.getGraphics();

            ExportUtil.drawTextBox(
               g, new Rectangle2D.Double(0, 0, size.width, size.height), format,
               stext, true, false, null);
            g.dispose();
            */
            XSSFClientAnchor anchor =
               (XSSFClientAnchor) getAnchorPosition(info);
            XSSFTextBox tb = patriarch.createTextbox(anchor);
            XSSFRichTextString rts = (XSSFRichTextString)
               PoiExcelVSUtil.createRichTextString(book, stext);
            tb.setText(rts);

            Insets padding = info.getPadding();

            if(padding != null) {
               tb.setLeftInset(padding.left);
               tb.setRightInset(padding.right);
               tb.setTopInset(padding.top);
               tb.setBottomInset(padding.bottom);
            }

            applyFormat(tb, rts, format, shadowed);
         }
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception ex) {
         LOG.error("Failed to create image of text \"" + stext +
            "\" for assembly: " + assembly.getAbsoluteName(), ex);
      }

      return true;
   }

   /**
    * Apply the format setting.
    */
   private void applyFormat(XSSFTextBox tb, RichTextString rts,
                            VSCompositeFormat format, boolean shadowed)
   {
      if(format == null) {
         return;
      }

      int align = format.getAlignment();
      java.awt.Color bg = format.getBackground();
      Insets borders = format.getBorders();
      BorderColors bcolors = format.getBorderColors();
      java.awt.Font font = format.getFont();
      int alpha = format.getAlpha();

      if(font != null) {
         rts.applyFont(PoiExcelVSUtil.getPOIFont(format, book, true));
         tb.setText((XSSFRichTextString) rts);
      }

      fixAlignment(tb, align);

      if(bg != null) {
         tb.setFillColor(bg.getRed(), bg.getGreen(), bg.getBlue());
      }

      if(borders != null) {
         int lineStyle = PoiExcelVSUtil.getLineStyle(borders.top);

         if(lineStyle != ExcelVSUtil.EXCEL_NO_BORDER) {
            if(lineStyle != ExcelVSUtil.EXCEL_SOLID_BORDER) {
               tb.setLineStyle(lineStyle);
            }

            if(bcolors != null) {
               tb.setLineStyleColor(bcolors.topColor.getRed(),
                                    bcolors.topColor.getGreen(),
                                    bcolors.topColor.getBlue());
            }
            else {
               tb.setLineStyleColor(0,0,0);
            }
         }
      }

      if(alpha == 0 || bg == null) {
         tb.setNoFill(true);
      }

      if(shadowed) {
         List<XSSFTextParagraph> paragraphs = tb.getTextParagraphs();

         for (XSSFTextParagraph paragraph : paragraphs) {
            List<XSSFTextRun> textRuns = paragraph.getTextRuns();

            for (XSSFTextRun textRun : textRuns) {
               CTRegularTextRun run = textRun.getXmlObject();
               PoiExportUtil.drowTextShadow(run);
            }
         }
      }
   }

   private void fixAlignment(XSSFTextBox tb, int align) {
      fixHAlignment(tb, align);
      fixVAlignment(tb, align);
   }

   private void fixHAlignment(XSSFTextBox tb, int align) {
      List<XSSFTextParagraph> textParas = tb.getTextParagraphs();

      if(textParas.size() == 0) {
         return;
      }

      XSSFTextParagraph textPara = textParas.get(0);

      if((align & StyleConstants.H_LEFT) != 0) {
         textPara.setTextAlign(TextAlign.LEFT);
      }
      else if((align & StyleConstants.H_CENTER) != 0) {
         textPara.setTextAlign(TextAlign.CENTER);
      }
      else if((align & StyleConstants.H_RIGHT) != 0) {
         textPara.setTextAlign(TextAlign.RIGHT);
      }
   }

   private void fixVAlignment(XSSFTextBox tb, int align) {
      if((align & StyleConstants.V_TOP) != 0) {
         tb.setVerticalAlignment(VerticalAlignment.TOP);
      }
      else if((align & StyleConstants.V_CENTER) != 0) {
         tb.setVerticalAlignment(VerticalAlignment.CENTER);
      }
      else if((align & StyleConstants.V_BOTTOM) != 0) {
         tb.setVerticalAlignment(VerticalAlignment.BOTTOM);
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
   protected void writeText(String text, Point pos, Dimension size, VSCompositeFormat format) {
      writeText(text, pos, size, format, null);
   }

   /**
    * Write text with format.
    * @param text the specified text.
    * @param pos the specified position.
    * @param size the specified text size.
    * @param format the specified text format.
    * @param hyperlink the hyperlink to link to.
    */
   protected void writeText(String text, Point pos, Dimension size,
                            VSCompositeFormat format, Hyperlink.Ref hyperlink)
   {
      try {
         CellRangeAddress cellRange = PoiExcelVSUtil.mergeRegion(pos, size, sheet);

         Row row = PoiExcelVSUtil.getRow(pos.y, sheet);
         Cell mcell = PoiExcelVSUtil.getCell((short) (pos.x), row);

         RichTextString hrText = PoiExcelVSUtil.createRichTextString(book,
                                                                     Tool.convertHTMLSymbol(text));
         Font ft = PoiExcelVSUtil.getPOIFont(format, book, true);
         hrText.applyFont(ft);

         mcell.setCellValue(hrText);

         PoiExcelVSUtil.setCellStyles(book, sheet, format, null,
                                      cellRange, null, ft, format.getFont(),
                                      format.getForeground(), false,
                                      ExcelVSUtil.CELL_CONTENT,
                                      ExcelVSUtil.CELL_CONTENT, stylecache);
         PoiExcelVSUtil.setHyperlinkToCell(book, mcell, hyperlink);
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.error("Failed to write text: " + text, e);
      }
   }

   /**
    * Write a range slider.
    */
   @Override
   protected void writeTimeSlider(TimeSliderVSAssembly assm) {
      try {
         TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) assm.getInfo();

         if(info == null) {
            return;
         }

         VSAssembly containerAssembly = assm.getContainer();

         if(containerAssembly instanceof CurrentSelectionVSAssembly) {
            FormatInfo finfo = info.getFormatInfo();
            VSCompositeFormat format = new VSCompositeFormat();

            if(finfo != null) {
               format = finfo.getFormat(new TableDataPath(-1, TableDataPath.TITLE), false);
            }

            Dimension size = info.getPixelSize();
            Point pos = info.getViewsheet().getPixelPosition(info);

            if(info.isTitleVisible()) {
               CurrentSelectionVSAssemblyInfo cinfo =
                (CurrentSelectionVSAssemblyInfo) containerAssembly.getInfo();

               PoiExcelVSUtil.writeTitleInContainer(pos, size, 0,
                                                    Tool.localize(info.getTitle()),
                                                    assm.getDisplayValue(true, true), format,
                                                    sheet, book, this, null, info.getFormat(),
                                                    info.getTitleHeight(), true,
                                                    cinfo.getTitleRatio());
            }

            CoordinateHelper coordinator = new CoordinateHelper();
            coordinator.setViewsheet(vs);
            double h = 0;

            if(!info.isTitleVisible()) {
               h = coordinator.getBounds(assm, CoordinateHelper.TITLE).getHeight();
            }

            Rectangle2D abounds = coordinator.getBounds(assm, CoordinateHelper.ALL);
            Rectangle2D bounds = coordinator.getBounds(assm, CoordinateHelper.DATA);
            bounds = new Rectangle2D.Double(bounds.getX(), bounds.getY() - h,
                                            bounds.getWidth(), bounds.getHeight());

            if(isMatchLayout() &&
               bounds.getY() + bounds.getHeight() > abounds.getY() + abounds.getHeight())
            {
               return;
            }

            ClientAnchor anchor = getAnchorPosition(info);
            // selection container forced on the grid, so don't draw outside
            anchor.setDx1(0);
            anchor.setDy1(0);
            anchor.setDx2(0);
            anchor.setDy2(0);
            writePicture(getImage(assm), anchor);
         }
         else {
            writePicture(getImage(assm), getAnchorPosition(info));
         }
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.error("Failed to write time slider: " + assm.getAbsoluteName(), e);
      }
   }

   /**
    * Write table assembly.
    * @param assembly the specified CalendarVSAssembly.
    */
   @Override
   protected void writeCalendar(CalendarVSAssembly assembly) {
      if(assembly.getShowType() == CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE) {
         writeCalendarTitle(assembly);
         return;
      }

      try {
         CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo)
            assembly.getInfo();

         if(info == null) {
            return;
         }

         writePicture(getImage(assembly), getAnchorPosition(info));
      }
      catch(SheetMaxRowsException e) {
         throw e;
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
         String stext = info.getDisplayValue();

         if(info == null) {
            return;
         }

         VSCompositeFormat format = getCalendarTitleFormat(info);
         Dimension size = info.getPixelSize();

         // calculate the area the String will cover
         Dimension pixsize = calculateSizeInPixel(info);
         java.awt.Font font = format.getFont() == null ?
            VSFontHelper.getDefaultFont() : format.getFont();
         FontMetrics fontMetrics = Common.getFontMetrics(font);
         int totalLength = fontMetrics.stringWidth(stext) == 0 ?
            (int) pixsize.getWidth() : fontMetrics.stringWidth(stext);
         double height = Math.ceil(totalLength / pixsize.getWidth()) * AssetUtil.defh;
         height = (height >= size.getHeight()) ? size.getHeight() : height;
         pixsize = new Dimension((int) size.getWidth(), (int) height);
         format.getUserDefinedFormat().setWrapping(true);

         Point p1 = getRowCol(info.getPixelOffset());
         Point p2 = getRowCol(new Point(info.getPixelOffset().x + pixsize.width,
            info.getPixelOffset().y + pixsize.height));

         CellRangeAddress cellRange = PoiExcelVSUtil.mergeRegion(p1,
                                                                 new Dimension(p2.x - p1.x, p2.y - p1.y), sheet);
         Row row = PoiExcelVSUtil.getRow(p1.y, sheet);
         Cell mcell = PoiExcelVSUtil.getCell((short) (p1.x), row);

         RichTextString hrText = PoiExcelVSUtil.createRichTextString(book,
                                                                     Tool.convertHTMLSymbol(stext));
         Font ft = PoiExcelVSUtil.getPOIFont(format, book, true);
         hrText.applyFont(ft);

         mcell.setCellValue(hrText);

         PoiExcelVSUtil.setCellStyles(book, sheet, format, null,
                                      cellRange, null, ft,
                                      format.getFont(), format.getForeground(),
                                      false, ExcelVSUtil.CELL_HEADER,
                                      ExcelVSUtil.CELL_CONTENT, stylecache);
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.error("Failed to write calendar title: " +
            assembly.getAbsoluteName(), e);
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
         if(!onlyDataComponents) {
            ExcelTableHelper helper = new ExcelTableHelper(book, sheet, assembly);
            helper.setExcelToCSV(excelToCSV);
            helper.setExporter(this);
            helper.write(assembly, lens);
         }

         if(exportTableExpandTheTableData(assembly)) {
            writeExpandTableToSheet(assembly, lens);
         }
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.error("Failed to write table: " + assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write crosstab assembly.
    * @param assembly the specified CrosstabVSAssembly.
    */
   @Override
   protected void writeCrosstab(CrosstabVSAssembly assembly, VSTableLens lens) {
      try {
         if(!onlyDataComponents) {
            ExcelCrosstabHelper helper =
               new ExcelCrosstabHelper(book, sheet, assembly);
            helper.setExcelToCSV(excelToCSV);
            helper.setExporter(this);
            helper.write(assembly, lens);
         }

         if(exportTableExpandTheTableData(assembly)) {
            writeExpandTableToSheet(assembly, lens);
         }
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.error("Failed to write crosstab table: " +
            assembly.getAbsoluteName(), e);
      }
   }

   protected boolean exportTableExpandTheTableData(TableDataVSAssembly assembly) {
      VSAssembly container = assembly.getContainer();

      return !(container instanceof TabVSAssembly) || !isExportAllTabbedTables();
   }

   /**
    * Write calctable assembly.
    * @param assembly the specified CrosstabVSAssembly.
    */
   @Override
   protected void writeCalcTable(CalcTableVSAssembly assembly, VSTableLens lens) {
      try {
         if(!onlyDataComponents) {
            ExcelCrosstabHelper helper =
               new ExcelCrosstabHelper(book, sheet, assembly);
            helper.setExporter(this);
            helper.write(assembly, lens);
         }

         if(exportTableExpandTheTableData(assembly)) {
            writeExpandTableToSheet(assembly, lens);
         }
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.error("Failed to write formula table: " +
            assembly.getAbsoluteName(), e);
      }
   }

   protected void writeExpandTableToSheet(VSAssembly assembly, VSTableLens lens) {
      if("false".equals(SreeEnv.getProperty("excel.table.datatab"))) {
         return;
      }

      if(excelToCSV) {
         return;
      }

      String name = sheetName + "." + assembly.getAbsoluteName();
      Sheet newSheet = book.createSheet(getSheetName(name));
      VSTableLens table = lens;

      XSSFCellStyle style = (XSSFCellStyle) book.createCellStyle();
      style.setTopBorderColor(HSSFColor.HSSFColorPredefined.BLACK.getIndex());
      style.setBottomBorderColor(HSSFColor.HSSFColorPredefined.BLACK.getIndex());
      style.setLeftBorderColor(HSSFColor.HSSFColorPredefined.BLACK.getIndex());
      style.setRightBorderColor(HSSFColor.HSSFColorPredefined.BLACK.getIndex());

      style.setBorderTop(BorderStyle.THIN);
      style.setBorderBottom(BorderStyle.THIN);
      style.setBorderLeft(BorderStyle.THIN);
      style.setBorderRight(BorderStyle.THIN);

      if(lens instanceof RegionTableLens) {
         RegionTableLens region = (RegionTableLens) lens;
         table = (VSTableLens) region.getTable();
      }

      fixMergeCells(newSheet, table);
      fixCellContent(newSheet, table, style);
   }

   private void fixMergeCells(Sheet newSheet, VSTableLens table) {
      int rowCount = table.getRowCount();
      int colCount = table.getColCount();

      for(int r = 0; r < rowCount; r++) {
         for(int c = 0; c < colCount; c++) {
            Dimension span = table.getSpan(r, c);

            if(span != null && (span.width != 1 || span.height != 1)) {
               try {
                  newSheet.addMergedRegion(
                     new CellRangeAddress(r, r + span.height - 1, c, c + span.width - 1));
               }
               catch(Exception ex) {
                  // ignore
               }
            }
         }
      }
   }

   private void fixCellContent(Sheet newSheet, VSTableLens table, XSSFCellStyle style) {
      int rowCount = table.getRowCount();
      int colCount = table.getColCount();

      for(int r = 0; r < rowCount; r++) {
         Row row = newSheet.createRow(r);

         for(int c = 0; c < colCount; c++) {
            Cell cell = row.createCell(c);
            Object obj = table.getObject(r, c);

            if(obj instanceof DCMergeDatesCell) {
               obj = ((DCMergeDatesCell) obj).getFormatedOriginalDate();
            }

            if(obj != null) {
               // this is the limit by excel
               final int MAXLEN = 32767;
               String text = obj.toString();

               if(text.length() > MAXLEN) {
                  text = text.substring(0, MAXLEN);
               }

               cell.setCellValue(text);
            }

            cell.setCellStyle(style);
         }
      }
   }

   /**
    * Write chart aggregate.
    * @param chartAsm the specified VSAssembly.
    */
   @Override
   protected void writeChart(ChartVSAssembly chartAsm, VGraph vgraph,
                             DataSet data, boolean imgOnly)
   {
      if(!onlyDataComponents) {
         writeChartAsset(chartAsm, vgraph);
      }

      if(!imgOnly) {
         writeChart(chartAsm, vgraph, vgraph, data,
                    sheetName + "." + chartAsm.getAbsoluteName());
      }
   }

   /**
    * If excel chart don't support the special binding,
    * then only export as image.
    */
   protected boolean needExportChartToImage(ChartVSAssembly assembly) {
      // change to always export chart as image since native excel chart is not editable and
      // doesn't have much advantage
      /*
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) assembly.getInfo();
      ChartInfo cinfo = info.getVSChartInfo();
      ChartDescriptor desc = assembly.getChartDescriptor();

      if(cinfo == null) {
         return true;
      }

      // spark line without any axis is not supported by excel
      if(desc.isSparkline()) {
         return true;
      }

      int xmeasure = 0;
      int ymeasure = 0;

      for(ChartRef ref : cinfo.getRTXFields()) {
         if(!GraphUtil.isDimension(ref)) {
            xmeasure++;
         }
      }

      for(ChartRef ref : cinfo.getRTYFields()) {
         if(!GraphUtil.isDimension(ref)) {
            ymeasure++;
         }
      }

      // scatter plot matrix not supported in native chart
      if(xmeasure > 1 && ymeasure > 1) {
         return true;
      }

      int style = cinfo.getRTChartType();

      if(xmeasure == 0 && ymeasure == 0 && !GraphTypes.isPoint(style) &&
         !GraphTypes.isLine(style) && !GraphTypes.isStock(style) &&
         !GraphTypes.isCandle(style))
      {
         return true;
      }

      if(GraphTypes.isBoxplot(style)) {
         return true;
      }

      return false;
       */
      return true;
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
      if(firstTime && !imgOnly) {
         writeChart(originalAsm, graph, graph, data,
                    sheetName + "." + originalAsm.getAbsoluteName());
      }

      try {
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) asm.getVSAssemblyInfo();

         if(info == null) {
            return;
         }

         writePicture(img, getAnchorPosition(info));
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.error("Failed to write chart image: " + asm.getAbsoluteName(), e);
      }
   }

   /**
    * Write chart asset.
    */
   private void writeChartAsset(ChartVSAssembly chartAsm, BufferedImage img) {
      try {
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chartAsm.getVSAssemblyInfo();

         if(info == null) {
            return;
         }

         BufferedImage outer = getImageWithPadding(info, img);
         ClientAnchor anchor = getAnchorPosition(info);

         writePicture(getImage(chartAsm, outer), anchor);
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.error("Failed to write chart image: " + chartAsm.getAbsoluteName(), e);
      }
   }

   /**
    * Write chart asset.
    */
   private void writeChartAsset(ChartVSAssembly chartAsm, VGraph vgraph) {
      final BufferedImage chartImage = getChartImage(chartAsm, vgraph);
      final ClientAnchor anchor = getAnchorPosition(chartAsm.getVSAssemblyInfo());

      try {
         writePicture(chartImage, anchor);
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.error("Failed to write chart image: " + chartAsm.getAbsoluteName(), e);
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
    * Set the hyperlink into the helper.
    */
   protected void setHyperlink(VGraph vgraph, Rectangle2D bounds, Hyperlink.Ref hyperlink,
                               Shape shape)
   {
      if(hyperlink == null || hyperlink.getLinkType() != Hyperlink.WEB_LINK) {
         return;
      }

      String url = ExcelVSUtil.getURL(hyperlink);
      LinkArea area = new LinkArea(shape, GTool.getFlipYTransform(vgraph), bounds.getX(),
                                   bounds.getY(), 0);
      Rectangle2D location = area.getBounds();

      Point top = new Point();
      Point bottom = new Point();
      Point p1 = getRowCol((int) location.getX(), (int) location.getY(), top);
      Point p2 = getRowCol((int) location.getMaxX(), (int) location.getMaxY(), bottom);

      XSSFClientAnchor anchor =
         (XSSFClientAnchor) PoiExcelVSUtil.createClientAnchor(book, top.x, top.y, bottom.x,
                                                              bottom.y, p1.x, p1.y, p2.x, p2.y);
      XSSFSimpleShape shape2 = patriarch.createSimpleShape(anchor);

      PackageRelationship relationship =
         patriarch.getPackagePart()
            .addExternalRelationship(url, PackageRelationshipTypes.HYPERLINK_PART);
      String relationshipId = relationship.getId();
      CTHyperlink link = shape2.getCTShape().getNvSpPr().getCNvPr().addNewHlinkClick();
      link.setId(relationshipId);
      shape2.getCTShape().getNvSpPr().getCNvPr().setHlinkClick(link);
   }

   /**
    * Write chart aggregate.
    * @param chart the specified VSAssembly.
    */
   private void writeChart(ChartVSAssembly chart, VGraph vgraph, VGraph root,
                           DataSet data, String name)
   {
      if(needExportChartToImage(chart)) {
         return;
      }

      boolean isContainer = false;

      try {
         for(int i = 0; i < vgraph.getVisualCount(); i++) {
            Visualizable vi = vgraph.getVisual(i);

            if(vi instanceof GraphVO) {
               isContainer = true;
               VGraph vgraph0 = ((GraphVO) vi).getVGraph();

               writeChart(chart, vgraph0, vgraph,
                          vgraph0.getCoordinate().getDataSet(),
                          name + "-" + i);
            }
         }

         if(isContainer) {
            return;
         }

         XSSFChartElement chartElem = new XSSFChartElement(chart, data, vgraph,
                                                           root, ec);
         chartElem.generateChart(getSheetName(name));
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.error("Failed to write chart: " +
                      chart.getAbsoluteName(), e);
      }
   }

   /**
    * Write given VSTab to worksheet.
    * @param assembly the specified VSTab.
    */
   @Override
   protected void writeVSTab(TabVSAssembly assembly) {
      try {
         TabVSAssemblyInfo info = (TabVSAssemblyInfo) assembly.getInfo();

         if(info == null) {
            return;
         }

         if(!onlyDataComponents) {
            writePicture(getImage(assembly), getAnchorPosition(info));
         }

         if(isExportAllTabbedTables()) {
            String[] assemblies = assembly.getAssemblies();

            for(String cassemblyName : assemblies) {
               writeAllExpandTable(vs.getAssembly(cassemblyName));
            }
         }
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.error("Failed to write tab: " +
            assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Expand the table to the excel, also export the children table if the assembly is a container.
    *
    * @param cassembly a specific vs Assembly.
    * @throws Exception
    */
   private void writeAllExpandTable(VSAssembly cassembly) throws Exception {
      if(cassembly instanceof TableDataVSAssembly) {
         VSTableLens lens = box.getVSTableLens(cassembly.getAbsoluteName(), false, 1);
         lens = getRegionTableLens(lens, (TableDataVSAssembly) cassembly, box);
         writeExpandTableToSheet(cassembly, lens);
      }
      else if(cassembly instanceof ContainerVSAssembly) {
         String[] assemblies = ((ContainerVSAssembly) cassembly).getAssemblies();

         if(assemblies == null) {
            return;
         }

         for(String child : assemblies) {
            VSAssembly childAssembly = vs.getAssembly(child);
            writeAllExpandTable(childAssembly);
         }
      }
   }

   /**
    * Get the line start/end position anchor.
    * @param info        the line assembly info.
    * @param basePos     the annotation base assembly position.
    * @return
    */
   public ClientAnchor getLineAnchorPosition(LineVSAssemblyInfo info, Point basePos) {
      Point top = new Point();
      Point bottom = new Point();

      Object[] newInfo = VSUtil.refreshLineInfo(vs, info);
      Point start = (Point) newInfo[2];
      Point end = (Point) newInfo[3];
      Point position = (Point) newInfo[0];

      if(basePos != null && (position.getY() - basePos.getY() <= VSLine.ARROW_GAP)) {
         position.y = basePos.y + VSLine.ARROW_GAP + 1;
      }

      Point p1 = getRowCol(position.x + start.x, position.y + start.y, top);
      Point p2 = getRowCol(position.x + end.x, position.y + end.y, bottom);

      ClientAnchor anchor = PoiExcelVSUtil.createClientAnchor(book, top.x, top.y,
                                                              bottom.x, bottom.y, p1.x, p1.y, p2.x, p2.y);
      anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);

      return anchor;
   }

   /**
    * Get the assembly's anchor.
    * @param info the specified VSAssemblyInfo.
    * @return the specified ClientAnchor.
    */
   public ClientAnchor getAnchorPosition(VSAssemblyInfo info, boolean isPlainText) {
      Point position = vs.getPixelPosition(info);
      Dimension size = vs.getPixelSize(info);

      if(info instanceof TitledVSAssemblyInfo) {
         final int titleH = getTitleHeight(info);

         if(info instanceof TimeSliderVSAssemblyInfo) {
            position = new Point(position.x, position.y + titleH);
         }
         else if(info instanceof CurrentSelectionVSAssemblyInfo) {
            size = new Dimension(size.width, titleH);
         }
      }

      if(info instanceof LineVSAssemblyInfo) {
         Object[] newInfo = VSUtil.refreshLineInfo(vs, (LineVSAssemblyInfo) info);
         position = (Point) newInfo[0];
         size = (Dimension) newInfo[1];
      }

      Point top = new Point();
      Point bottom = new Point();
      position.y = Math.max(position.y, 0);
      position.x = Math.max(position.x, 0);

      Point p1 = isPlainText ? getClosestRowCol(position.x, position.y, top)
         : getRowCol(position.x, position.y, top);
      Point p2;

      if(isPlainText) {
         // for text, it's placed in grid so we use the grid position to calculate
         // the bottom/right since it's shifted onto the grid
         p2 = getClosestRowCol(position.x + size.width, position.y + size.height, bottom);
         p2.x = Math.max(p2.x, p1.x + 1);
         p2.y = Math.max(p2.y, p1.y + 1);
      }
      else {
         p2 = getRowCol(position.x + size.width, position.y + size.height, bottom);
      }

      ClientAnchor anchor = PoiExcelVSUtil.createClientAnchor(book, top.x, top.y,
                                                              bottom.x, bottom.y, p1.x, p1.y, p2.x, p2.y);
      anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);

      return anchor;
   }

   @Override
   protected void writeChartBackgroundShape(ChartVSAssembly chart, ChartVSAssemblyInfo info,
                                            Point pos, Dimension size)
   {
      // no-op
   }

   public ClientAnchor getAnchorPosition(VSAssemblyInfo info) {
      return getAnchorPosition(info, false);
   }

   /**
    * Get row and col index by grid position.
    */
   public Point getRowCol(Point position) {
      Point res = vs.getPixelPosition(position);
      return getRowCol(res.x, res.y, new Point());
   }

   /**
    * Get row and col index by pixel position
    */
   private Point _getRowCol(int x, int y, Point coordinate, boolean closest) {
      // optimize, use binary search to reduce time
      Point p = new Point(0, 0);

      if(cols == null) {
         return p;
      }

      int c = Arrays.binarySearch(cols, x);

      if(c < 0) {
         c = (-c) - 2;
         int xmin = cols[c];

         if(closest) {
            int diff1 = Math.abs(cols[c] - x);
            int diff2 = (c + 1 < cols.length) ? Math.abs(cols[c + 1] - x) : Integer.MAX_VALUE;

            if(diff1 > diff2) {
               c++;
            }
         }

         coordinate.x = (x - xmin) * EMU_PER_PIXEL;
      }

      p.x = c;
      int r = Arrays.binarySearch(rows, y);

      if(r < 0) {
         r = (-r) - 2;
         int ymin = rows[r];
         coordinate.y = (y - ymin) * EMU_PER_PIXEL;

         if(closest) {
            int diff1 = Math.abs(rows[r] - y);
            int diff2 = (r + 1 < rows.length) ? Math.abs(rows[r + 1] - y) : Integer.MAX_VALUE;

            if(diff1 > diff2) {
               r++;
            }
         }
      }

      p.y = r;
      return p;
   }

   /**
    * Get row and col index by pixel position.
    */
   public Point getRowCol(int x, int y, Point coordinate) {
      return _getRowCol(x, y, coordinate, false);
   }

   /**
    * Get row and col index of the last cell assembly resides in
    * by pixel position.
    */
   public Point getClosestRowCol(int x, int y, Point coordinate) {
      return _getRowCol(x,  y, coordinate, true);
   }

   /**
    * Get row index in excel by position and offset.
    */
   public int getRow(Point position, int offset) {
      Point res = vs.getPixelPosition(position);
      return getRowCol(res.x, res.y + offset, new Point()).y;
   }

   /**
    * Get col index in excel by position and offset.
    */
   public int getCol(Point position, int offset) {
      Point res = vs.getPixelPosition(position);
      return getRowCol(res.x + offset, res.y, new Point()).x;
   }

   /**
    * Get pixel size of the specified position and dimension.
    * @param pos the specified position.
    * @param size the specified size.
    */
   public Dimension getPixelSize(Point pos, Dimension size) {
      return new Dimension(AssetUtil.defw, AssetUtil.defh);
   }

   /**
    * Write given BufferedImage to worksheet.
    * @param img the specified BufferedImage.
    * @param anchor the specified client anchor.
    */
   public XSSFPicture writePicture(BufferedImage img, ClientAnchor anchor)
      throws Exception
   {
      XSSFPicture pic = patriarch.createPicture(anchor, addImage(img));
      CTPicture ctPicture = pic.getCTPicture();
      CTShapeProperties spPr = ctPicture != null ? ctPicture.getSpPr() : null;
      CTTransform2D xfrm = spPr != null ? spPr.getXfrm() : null;
      CTPositiveSize2D ext = xfrm != null ? xfrm.getExt() : null;

      if(ext != null && ext.getCx() < 0) {
         ext.setCx(0);
      }

      return pic;
   }

   /**
    * Get the picture index in the book.
    */
   private int addImage(BufferedImage img) throws Exception {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      writeSheetImage(img, out);
      byte[] tempByte = out.toByteArray();
      return book.addPicture(tempByte, Workbook.PICTURE_TYPE_PNG);
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
      Point textPos = new Point(1, 0);

      for(int i = 0; i < assemblies.length; i++) {
         VSAssembly assembly = (VSAssembly) assemblies[i];
         prepareAssembly(assembly);
         Dimension size = assembly.getPixelSize();
         Point pos = assembly.getPixelOffset();
         Point position = assembly instanceof ShapeVSAssembly ?
            getRowCol(pos.x + size.width, pos.y + size.height, new Point()):
            getRowCol(new Point(pos.x + size.width, pos.y + size.height));

         textPos.y = Math.max(position.y, textPos.y);
      }

      Dimension size = new Dimension(8, 1);
      int maxrows = PoiExcelVSUtil.getSheetMaxRows();

      if(maxrows > 0 && maxrows < rows.length) {
         Point pos = getRowCol(0, rows[maxrows] + 40, new Point());
         textPos.y = Math.min(textPos.y, pos.y);
      }

      writeText(warning, textPos, size, format);
   }

   /**
    * Write given GroupContainer to worksheet.
    * @param assembly the specified GroupContainer.
    */
   @Override
   protected void writeGroupContainer(GroupContainerVSAssembly assembly,
                                      XPortalHelper phelper) {
      try {
         GroupContainerVSAssemblyInfo info =
            (GroupContainerVSAssemblyInfo) assembly.getInfo();

         if(info == null) {
            return;
         }

         Viewsheet vs = assembly.getViewsheet();
         String[] assemblie = assembly.getAssemblies();

         // do not write the GroupContainer(background image, format, etc) out
         // when there is some un-floatable assembly to export,
         // to avoid covering other assemblies
         if(vs != null && assemblie != null) {
            for(int i = 0; i < assemblie.length; i++) {
               VSAssembly ass = (VSAssembly) vs.getAssembly(assemblie[i]);

               if(!isFloatAssembly(ass) && needExport(ass)) {
                  return;
               }
            }
         }

         VSGroupContainer container = new VSGroupContainer(info.getViewsheet());
         container.setTheme(theme);
         container.setAssemblyInfo(info);

         String path = info.getBackgroundImage();

         if(path == null) {
            writePicture(getImage(assembly), getAnchorPosition(info));
            return;
         }

         Image rimg = VSUtil.getVSImage(null, path, assembly.getViewsheet(),
                                        container.getContentWidth(),
                                        container.getContentHeight(),
                                        container.getAssemblyInfo().getFormat(),
                                        phelper);
         container.setRawImage(rimg);

         writePicture((BufferedImage) container.getImage(true),
            getAnchorPosition(info));
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.error("Failed to write group container: " +
            assembly.getAbsoluteName(), e);
      }
   }

   private boolean isFloatAssembly(VSAssembly assembly) {
      if(assembly == null) {
         return false;
      }

      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info instanceof ChartVSAssemblyInfo || info instanceof ImageVSAssemblyInfo ||
         info instanceof NumericRangeVSAssemblyInfo ||
         info instanceof RangeOutputVSAssemblyInfo ||
         info instanceof SubmitVSAssemblyInfo || info instanceof TabVSAssemblyInfo ||
         info instanceof TextInputVSAssemblyInfo || info instanceof TextVSAssemblyInfo ||
         info instanceof UploadVSAssemblyInfo)
      {
         return true;
      }

      return false;
   }

   /**
    * Write shape assembly.
    * @param assembly the specified ShapeVSAssembly.
    */
   @Override
   protected void writeShape(ShapeVSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      //@by yanie:bug1412708029832
      //if a shape or image is overlap to another assembly like table,
      //don't export image since in excel the floatable will always on top
      //which will cover the important data cells.
      if(info == null || !(assembly instanceof LineVSAssembly)
         && isOverlaps(assembly, vs, true))
      {
         return;
      }

      try {
         writePicture(getImage(assembly), getAnchorPosition(info));
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }
   }

   /**
    * Write given VSCurrentSelection to worksheet.
    * @param assembly the specified VSCurrentSelection.
    */
   @Override
   protected void writeCurrentSelection(CurrentSelectionVSAssembly assembly) {
      try {
         ExcelCurrentSelectionHelper helper =
            new ExcelCurrentSelectionHelper(book, sheet);
         helper.setExporter(this);
         helper.write(assembly);
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception ex) {
         LOG.error("Failed to write current selection: " +
            assembly.getAbsoluteName(), ex);
      }
   }

   protected String getSheetName(String name) {
      if(name != null) {
         // @by ChrisSpagnoli bug1428646365402 2015-4-10
         // Filter disallowed characters out from Excel sheet name
         final Set<String> replaceValues = new LinkedHashSet<>(
            Arrays.asList("[5b]", "[27]", "[2f]",
               ":", "*", "[", "]", "\\", "'", "/", "?", "__"));
         StringBuilder sb = new StringBuilder(name);

         for(String rv:replaceValues) {
            int start = sb.indexOf(rv);

            while(start != -1) {
               sb.replace(start, start + rv.length(), "_");
               start = sb.indexOf(rv);
            }
         }

         if(sb.length() > 31) {
            name = sb.substring(sb.length() - 31, sb.length());
         }
         else {
            name = sb.toString();
         }

      }

      return name;
   }

   /**
    * Write annotation assembly.
    * @param assembly the specified AnnotationVSAssembly.
    */
   @Override
   protected void writeAnnotation(AnnotationVSAssembly assembly) {
      super.writeAnnotation(assembly);

      AnnotationVSAssemblyInfo annotationInfo =
         (AnnotationVSAssemblyInfo) assembly.getVSAssemblyInfo();
      VSAssemblyInfo baseInfo = getBaseAssemblyInfo(annotationInfo);

      if(annotationInfo.getType() == AnnotationVSAssemblyInfo.DATA &&
         baseInfo instanceof TableDataVSAssemblyInfo)
      {
         return;
      }

      String lineName = annotationInfo.getLine();
      String recName = annotationInfo.getRectangle();

      AnnotationLineVSAssembly lineAssem = (AnnotationLineVSAssembly) vs.getAssembly(lineName);
      AnnotationLineVSAssemblyInfo lineInfo = lineAssem == null ? null :
         (AnnotationLineVSAssemblyInfo) lineAssem.getVSAssemblyInfo();
      AnnotationRectangleVSAssembly recAssem =
         (AnnotationRectangleVSAssembly) vs.getAssembly(recName);
      AnnotationRectangleVSAssemblyInfo recInfo = recAssem == null ? null :
         (AnnotationRectangleVSAssemblyInfo) recAssem.getVSAssemblyInfo();
      Point basePos = null;

      if(baseInfo != null && annotationInfo.getType() == AnnotationVSAssemblyInfo.ASSEMBLY) {
         basePos = baseInfo.getLayoutPosition() != null ?
            baseInfo.getLayoutPosition() : vs.getPixelPosition(baseInfo);
         basePos.y = PoiExcelVSUtil.ceilY(basePos.y);
      }

      drawLine(lineInfo, basePos);
      drawRectangle(recInfo);
   }

   /**
    * Get base assemblyinfo for target annotation.
    */
   private VSAssemblyInfo getBaseAssemblyInfo(AnnotationVSAssemblyInfo info) {
      if(info == null || info.getType() == AnnotationVSAssemblyInfo.VIEWSHEET) {
         return null;
      }

      Assembly[] assemblies = vs.getAssemblies(true);

      for(Assembly assem : assemblies) {
         if(!(assem.getInfo() instanceof BaseAnnotationVSAssemblyInfo)) {
            continue;
         }

         BaseAnnotationVSAssemblyInfo binfo = (BaseAnnotationVSAssemblyInfo) assem.getInfo();
         List annotations = binfo.getAnnotations();

         for(Object name : annotations) {
            if(Tool.equals(info.getName(), name)) {
               return binfo instanceof VSAssemblyInfo ? (VSAssemblyInfo) binfo : null;
            }
         }
      }

      return null;
   }

   /**
    * Draw annotation line part.
    */
   private void drawLine(AnnotationLineVSAssemblyInfo info, Point basePos) {
      if(info == null || !"show".equals(info.getVisibleValue())) {
         return;
      }

      XSSFClientAnchor anchor = (XSSFClientAnchor) getLineAnchorPosition(info, basePos);
      XSSFSimpleShape lineShape = createLineShape(patriarch, anchor);
      lineShape.setLineStyle(PoiExcelVSUtil.getLineStyle(info.getLineStyle()));
      final Color lineColor = info.getFormat().getForeground();

      if(lineColor != null) {
         lineShape.setLineStyleColor(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue());
      }
   }

   /**
    * Create XSSF line shape, anchor need to transform.
    */
   private XSSFSimpleShape createLineShape(XSSFDrawing patriarch,
                                           XSSFClientAnchor anchor)
   {
      int ocol1 = anchor.getCol1();
      int orow1 = anchor.getRow1();
      int ocol2 = anchor.getCol2();
      int orow2 = anchor.getRow2();
      int odx1 = anchor.getDx1();
      int ody1 = anchor.getDy1();
      int odx2 = anchor.getDx2();
      int ody2 = anchor.getDy2();

      int ncol1 = Math.min(ocol1, ocol2);
      int nrow1 = Math.min(orow1, orow2);
      int ncol2 = Math.max(ocol1, ocol2);
      int nrow2 = Math.max(orow1, orow2);
      int ndx1 =
         ocol1 < ocol2 ? odx1 : (ocol1 == ocol2 ? Math.min(odx1, odx2) : odx2);
      int ndy1 =
         orow1 < orow2 ? ody1 : (orow1 == orow2 ? Math.min(ody1, ody2) : ody2);
      int ndx2 = ndx1 == odx1 ? odx2 : odx1;
      int ndy2 = ndy1 == ody1 ? ody2 : ody1;

      anchor = (XSSFClientAnchor) PoiExcelVSUtil.createClientAnchor(book, ndx1,
                                                                    ndy1, ndx2, ndy2, ncol1, nrow1, ncol2, nrow2);
      XSSFSimpleShape lineShape = patriarch.createSimpleShape(anchor);
      lineShape.setShapeType(ShapeTypes.LINE);
      lineShape.setLineStyleColor(0, 0, 0);

      CTShape ctsp = lineShape.getCTShape();
      CTShapeProperties ctspp = ctsp.getSpPr();
      CTTransform2D transform = ctspp.getXfrm();

      if(ncol1 == ocol2 && ndx1 == odx2) {
         transform.setFlipH(true);
      }

      if(nrow1 == orow2 && ndy1 == ody2) {
         transform.setFlipV(true);
      }

      return lineShape;
   }

   /**
    * Draw annotation rectangle part.
    */
   private void drawRectangle(AnnotationRectangleVSAssemblyInfo info) {
      if(info == null) {
         return;
      }

      Rectangle2D bounds = new Rectangle(0, 0, 100, 100);
      String htmlContent = AnnotationVSUtil.getAnnotationHTMLContent(viewsheet, info, bounds);

      if(StringUtils.isBlank(htmlContent)) {
         htmlContent = htmlContent.replaceAll("<h[1-3]?>", "<p>");
         htmlContent = htmlContent.replaceAll("</h[1-3]?>", "</p>");

         if(!htmlContent.startsWith("<p")) {
            htmlContent = "<p>" + htmlContent + "</p>";
         }
      }

      final Document doc = Jsoup.parse(htmlContent);
      final Elements paragraphs = doc.getElementsByTag("p");
      final Map<Integer, TextAlign> textAligns = new HashMap<>();
      XSSFClientAnchor anchor = (XSSFClientAnchor) getAnchorPosition(info);
      XSSFTextBox tb = patriarch.createTextbox(anchor);
      tb.getTextParagraphs().clear();

      for(int i = 0; i < paragraphs.size(); i++) {
         final Element paragraph = paragraphs.get(i);
         final String text = paragraph.text();
         XSSFRichTextString rts = (XSSFRichTextString) PoiExcelVSUtil.createRichTextString(book, text);
         final XSSFFont font = (XSSFFont) book.createFont();
         final String paragraphHTML = paragraph.outerHtml();
         final String fontSize = getFontAttr(paragraphHTML, "font-size:");

         if(fontSize != null) {
            font.setFontHeight(Integer.parseInt(fontSize));
         }
         else {
            font.setFontHeight(9);
         }

         final String fontFamily = getFontAttr(paragraphHTML, "font-family:");

         if(fontFamily != null) {
            font.setFontName(fontFamily);
         }

         final String fontColor = getFontAttr(paragraphHTML, "color:");

         if(fontColor != null) {
            final Pattern pattern = Pattern.compile("rgb\\((\\d+), (\\d+), (\\d+)\\)");
            final Matcher matcher = pattern.matcher(fontColor);

            if(matcher.matches()) {
               final int r = Integer.parseInt(matcher.group(1));
               final int g = Integer.parseInt(matcher.group(2));
               final int b = Integer.parseInt(matcher.group(3));
               final Color color = new Color(r, g, b);
               font.setColor(new XSSFColor(color, null));
            }
         }

         if("underline".equals(getFontAttr(paragraphHTML, "text-decoration:"))) {
            font.setUnderline(FontUnderline.SINGLE);
         }

         final String align = getFontAttr(paragraphHTML, "text-align:");

         if(align != null) {
            switch(align) {
               case "right":
                  textAligns.put(i, TextAlign.RIGHT);
                  break;
               case "left":
                  textAligns.put(i, TextAlign.LEFT);
                  break;
               case "center":
                  textAligns.put(i, TextAlign.CENTER);
                  break;
               case "justify":
                  textAligns.put(i, TextAlign.JUSTIFY);
                  break;
               default:
                  textAligns.put(i, null);
                  break;
            }
         }

         rts.applyFont(font);

         if(i == 0) {
            tb.setText(rts);
         }
         else {
            tb.addNewTextParagraph(rts);
         }
      }

      final List<XSSFTextParagraph> textParagraphs = tb.getTextParagraphs();

      for(int i = 0; i < textParagraphs.size() && textAligns.size() > 0; i++) {
         final XSSFTextParagraph textParagraph = textParagraphs.get(i);
         final TextAlign textAlign = textAligns.get(i);

         if(textAlign != null) {
            textParagraph.setTextAlign(textAlign);
         }
      }

      // fix the rectangle position with line
      /* this appears to be an effort to align rect with line, which shouldn't be
      necessary anymore since both a on pixel positions. it causes the annotation box
      size wrong and covers the line
      if(lineAnchor != null && anchor != null) {
         int pos = lineInfo.getStartAnchorPos();
         Point start = lineInfo.getStartPos();
         Point end = lineInfo.getEndPos();
         int lineRow1 = lineAnchor.getRow1();
         int lineRow2 = lineAnchor.getRow2();

         if(start.x > end.x) {
            if((pos & LineVSAssemblyInfo.NORTH) != 0) {
               anchor.setRow1(lineRow2);
               anchor.setDy1(lineAnchor.getDy2());
            }
            else if((pos & LineVSAssemblyInfo.SOUTH) != 0) {
               anchor.setRow2(lineRow2);
               anchor.setDy2(lineAnchor.getDy2());
            }
         }
         else if(start.x < end.x) {
            if((pos & LineVSAssemblyInfo.NORTH) != 0) {
               anchor.setRow1(lineRow1);
               anchor.setDy1(lineAnchor.getDy1());
            }
            else if((pos & LineVSAssemblyInfo.SOUTH) != 0) {
               anchor.setRow2(lineRow1);
               anchor.setDy2(lineAnchor.getDy1());
            }
         }
      }
      */
      VSCompositeFormat rectangleFormat = info.getFormat();
      Color border = rectangleFormat.getForeground();
      Color fill = PoiExcelVSUtil.getColorByAlpha(rectangleFormat.getBackground());
      tb.setWordWrap(true);
      tb.setLineStyle(PoiExcelVSUtil.getTextBoxBorderStyle(info.getLineStyle()));
      tb.setTextHorizontalOverflow(TextHorizontalOverflow.CLIP);
      tb.setTextVerticalOverflow(TextVerticalOverflow.CLIP);

      if(fill != null) {
         tb.setFillColor(fill.getRed(), fill.getGreen(), fill.getBlue());
      }
      else {
         tb.setFillColor(255, 255, 255);
      }

      if(border != null) {
         tb.setLineStyleColor(border.getRed(), border.getGreen(), border.getBlue());
      }
      else {
         tb.setLineStyleColor(0, 0, 0);
      }

      tb.setLeftInset(0.1);
      tb.setRightInset(0.1);
      tb.setTopInset(0.1);
      tb.setBottomInset(0.1);
   }

   private String getFontAttr(String htmlContent, String attrName) {
      if(htmlContent.indexOf(attrName) > 0) {
         int start = htmlContent.indexOf(attrName);

         if(start > 0) {
            int end1 = htmlContent.indexOf(";", start);
            int end2 = htmlContent.indexOf('"', start);
            int end;

            if(end1 > 0 && end1 < end2) {
               end = end1;
            }
            else if(end2 > 0) {
               end = end2;
            }
            else {
               end = htmlContent.length();
            }
            
            if(end > 0) {
               end = "font-size:".equals(attrName) ? end - 2: end;
            }

            String attr = htmlContent.substring(start + attrName.length(), end);
            attr = attr.trim();

            return attr;
         }
      }

      return null;
   }

   /**
    * Get the bounds of the cells.
    * @param p1 top-left cell.
    * @param p2 bottom-right cell.
    */
   public Rectangle2D getBounds(Point p1, Point p2) {
      int x = cols[p1.x];
      int y = rows[p1.y];
      int w = cols[p2.x] - x;
      int h = rows[p2.y] - y;

      return new Rectangle2D.Double(x, y, w, h);
   }

   /**
    * Draw the specified cell's comment.
    * @param cell the specified cell.
    * @param content the comment content.
    */
   public void drawComment(Cell cell, String content) {
      ClientAnchor anchor = PoiExcelVSUtil.createClientAnchor(book);
      int lines = content.split("\n").length;
      anchor.setCol1(cell.getColumnIndex() + 1);
      anchor.setCol2(cell.getColumnIndex() + 3);
      anchor.setRow1(cell.getRowIndex());
      anchor.setRow2(cell.getRowIndex() + Math.max(lines, 4));
      anchor.setDx1(255);
      anchor.setDy1(-128);

      // @by ankitmathur, Fix bug1418201323772, If the "Comment" already
      // exist, we should re-use it because there is a known issue with POI
      // for commenting an already commented workbook. This case should only
      // occur when match layout is not selected and therefore a "cloneSheet"
      // is used through OfflineExcelVSExporter.writeTable().
      Comment comment =
         sheet.getCellComment(new CellAddress(cell));

      if(comment == null) {
         comment = patriarch.createCellComment(anchor);
         comment.setString(PoiExcelVSUtil.createRichTextString(book, content));
      }

      cell.setCellComment(comment);
   }

   @Override
   protected void resetTimeSliderSize(VSAssembly assembly, VSObject obj) {
      int type = assembly.getAssemblyType();

      if(type == AbstractSheet.TIME_SLIDER_ASSET &&
         assembly.getContainer() instanceof CurrentSelectionVSAssembly)
      {
         VSAssemblyInfo info = assembly.getVSAssemblyInfo();
         VSAssembly cassembly = assembly.getContainer();
         VSAssemblyInfo cinfo = cassembly.getVSAssemblyInfo();
         ClientAnchor anchor = getAnchorPosition(cinfo, true);
         Rectangle2D cbounds = getBounds(new Point(anchor.getCol1(), anchor.getRow1()),
                                         new Point(anchor.getCol2(), anchor.getRow2() - 1));

         Dimension pixelsize = new Dimension((int) cbounds.getWidth(), info.getPixelSize().height);
         obj.setPixelSize(pixelsize);

         Point lastPos = null;
         Dimension lastSize = null;
         CoordinateHelper coordinator = new CoordinateHelper();
         coordinator.setViewsheet(vs);

         // for range slider inside a container, the title height is not included in
         // the pixelSize, which results the positions of the children not correct.
         // it's ok in the gui because we use relative layout so the children just
         // stack. it's a problem with export. we won't need this when we fix the
         // range slider handling in the selection container
         for(String child : ((CurrentSelectionVSAssembly) cassembly).getAssemblies()) {
            VSAssembly aobj = (VSAssembly) assembly.getViewsheet().getAssembly(child);

            if(lastPos == null && aobj == assembly) {
               lastPos = aobj.getPixelOffset();
               lastSize = CoordinateHelper.getAssemblySize(aobj, null);
            }
            else if(lastPos != null) {
               lastPos.y += lastSize.height;
               Point pos = aobj.getPixelOffset();
               aobj.setPixelOffset(new Point(pos.x, lastPos.y));
               lastSize = CoordinateHelper.getAssemblySize(aobj, null);
            }
         }
      }
   }

   public void setExcelToCSV(boolean excel) {
      this.excelToCSV = excel;
   }

   protected ExcelContext ec;
   protected Workbook book;
   protected Sheet sheet;
   protected String sheetName;
   protected Vector chartList;
   protected Map<XSSFFormatRecord, XSSFCellStyle> stylecache = new HashMap<>();

   private XSSFDrawing patriarch;
   private int[] rows;
   private Integer[] cols;
   private Viewsheet vs;
   private OutputStream stream;
   protected boolean excelToCSV = false;
   private static final Logger LOG = LoggerFactory.getLogger(PoiExcelVSExporter.class);
}
