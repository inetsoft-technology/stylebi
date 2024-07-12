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

import inetsoft.report.TableDataPath;
import inetsoft.report.internal.Common;
import inetsoft.report.io.viewsheet.VSSelectionListHelper;
import inetsoft.report.io.viewsheet.VSSelectionTreeHelper;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionVSAssemblyInfo;
import inetsoft.util.Tool;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * SelectionList helper for excel.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ExcelSelectionTreeHelper extends VSSelectionTreeHelper {
   /**
    * Constructor.
    */
   public ExcelSelectionTreeHelper(Workbook book) {
      this.book = book;
   }

   /**
    * Write the Selection tree assembly to excel.
    */
   public void write(Sheet sheet, SelectionTreeVSAssembly assembly) {
      SelectionTreeVSAssemblyInfo info =
         (SelectionTreeVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      StringBuilder sTitle = new StringBuilder();
      List<SelectionValue> dispList = new ArrayList<>();

      prepareDisplayList(info, dispList, sTitle, true);
      writeTree(assembly, sheet, dispList);

      if(info.isTitleVisible()) {
         writeTitle(info, sheet); // must be called after prepare
      }
   }

   /**
    * Write the title.
    * @param info the specified SelectionListVSAssemblyInfo.
    * @param sheet the Sheet title which is on.
    */
   private void writeTitle(SelectionTreeVSAssemblyInfo info, Sheet sheet) {
      PoiExcelVSExporter exporter = (PoiExcelVSExporter) getExporter();
      ClientAnchor anchor = exporter.getAnchorPosition(info);
      FormatInfo finfo = info.getFormatInfo();
      VSCompositeFormat format = new VSCompositeFormat();

      if(finfo != null) {
         format = finfo.getFormat(
            new TableDataPath(-1, TableDataPath.TITLE), false);
      }

      CellRangeAddress cellRange;

      if(info.getShowType() == SelectionVSAssemblyInfo.LIST_SHOW_TYPE) {
         Point pos = info.getViewsheet().getPixelPosition(info);
         pos.y = PoiExcelVSUtil.ceilY(pos.y);
         int titleH = PoiExcelVSUtil.getExcelTitleHeight(info);
         pos = new Point(pos.x, pos.y + titleH);
         int row2 = exporter.getRowCol(pos.x, pos.y, new Point()).y - 1;

         cellRange = new CellRangeAddress(anchor.getRow1(), Math.max(anchor.getRow1(), row2),
                                          anchor.getCol1(),
                                          Math.max(anchor.getCol1(), anchor.getCol2() - 1));
      }
      else {
         cellRange = new CellRangeAddress(anchor.getRow1(),
                                          Math.max(anchor.getRow1(), anchor.getRow2() - 1),
                                          anchor.getCol1(),
                                          Math.max(anchor.getCol1(), anchor.getCol2() - 1));
      }

      PoiExcelVSUtil.addMergedRegion(sheet, cellRange);

      Row row = PoiExcelVSUtil.getRow(anchor.getRow1(), sheet);
      Cell mcell = PoiExcelVSUtil.getCell(anchor.getCol1(), row);

      String title = Tool.localize(info.getTitle());
      RichTextString hrText =
         PoiExcelVSUtil.createRichTextString(book, Tool.convertHTMLSymbol(title));
      Font font = PoiExcelVSUtil.getPOIFont(format, book, true);
      hrText.applyFont(font);
      mcell.setCellValue(hrText);
      VSCompositeFormat parentformat = info.getFormat();
      PoiExcelVSUtil.setCellStyles(book, sheet, format, parentformat, cellRange, null,
                                   font, format.getFont(), format.getForeground(),
                                   true, ExcelVSUtil.CELL_HEADER,
                                   ExcelVSUtil.CELL_HEADER | ExcelVSUtil.CELL_TAIL,
                                   stylecache);
   }

   /**
    * Write the Tree content.
    * @param sheet the Sheet the tree is on.
    * @param dispList SelectionValues for display.
    */
   private void writeTree(SelectionTreeVSAssembly assembly, Sheet sheet, List<SelectionValue> dispList) {
      SelectionTreeVSAssemblyInfo info =
         (SelectionTreeVSAssemblyInfo) assembly.getVSAssemblyInfo();
      PoiExcelVSExporter exporter = (PoiExcelVSExporter) getExporter();
      ClientAnchor anchor = exporter.getAnchorPosition(info);
      VSCompositeFormat parentformat = info.getFormat();
      boolean hasSelected = info.getCompositeSelectionValue() != null &&
         info.getCompositeSelectionValue()
         .getSelectionValues(-1, SelectionValue.STATE_SELECTED, 0).size() > 0;

      parentformat = ExcelSelectionListHelper.prepareParentBorders(
         parentformat, assembly);

      short columnStart = anchor.getCol1();
      short columnEnd = (short) (anchor.getCol2() -1);

      Row row;
      Cell cell;
      SelectionValue sv;
      VSCompositeFormat lastLineFormat = null;
      VSCompositeFormat format = null;
      int cellHeight = info.getCellHeight();
      cellHeight = (int) Math.round((double) cellHeight / AssetUtil.defh) * AssetUtil.defh;
      int titleH = PoiExcelVSUtil.getExcelTitleHeight(info);
      int sizeHeight = PoiExcelVSUtil.floorY(info.getPixelSize().height);
      int infoRows = (int) Math.round((double) (sizeHeight - titleH) / cellHeight);
      int level;
      int currRow = -1;

      List<Double> rowHeights = new ArrayList<>();
      Dimension size = info.getPixelSize();

      for(int i = 1; i < dispList.size(); i++) {
         SelectionValue svalue = dispList.get(i);
         VSCompositeFormat format2 = svalue.getFormat();
         double cellHeight2 = format2 == null || !format2.isWrapping() ? info.getCellHeight() :
            Common.getWrapTextHeight(svalue.getLabel(), PoiExcelVSUtil.floorY(size.width),
                                     format2.getFont(), format2.getAlignment());

         rowHeights.add(cellHeight2);
      }

      //Fixed bug#25366 that calculate correct rowIndex to
      //avoid merger cell ,result in title missing.
      int rowHeightSum = 0;

      for(int i = 0; i < rowHeights.size(); i++) {
         Point pos = info.getViewsheet().getPixelPosition(info);
         pos.y = PoiExcelVSUtil.ceilY(pos.y);
         int rowIndex = exporter.getRowCol(pos.x,
            pos.y + titleH + rowHeightSum, new Point()).y;
         rowHeightSum = rowHeightSum +
            PoiExcelVSUtil.getSelectionCellHeight(rowHeights.get(i).intValue());

         if(currRow < 0) {
            currRow = rowIndex;
         }
         else {
            rowIndex = Math.max(rowIndex, ++currRow);
         }

         row = PoiExcelVSUtil.getRow(rowIndex, sheet);

         if(row == null || rowIndex >= anchor.getRow2()) {
            break; // out of display area
         }

         cell = PoiExcelVSUtil.getCell(columnStart, row);
         Font font = null;

         if(i < dispList.size()) {
            StringBuilder sb = new StringBuilder();
            sv = dispList.get(i + 1);
            level = sv.getLevel();

            for(int k = 0; k < level; k++) {
               sb.append(INDENT_STR);
            }

            sb.append(sv.getLabel());

            if(info.isShowText() && sv.getMeasureLabel() != null) {
               sb.append(" (").append(sv.getMeasureLabel()).append(")");
            }

            RichTextString hrText = PoiExcelVSUtil.createRichTextString(book,
                                                                        Tool.convertHTMLSymbol(sb.toString()));
            format = sv.getFormat();

            // set to gray if the parent itself is not selected
            format = VSSelectionListHelper.getValueFormat(sv, format, hasSelected);

            if(i == (dispList.size() - 1) && STR_MORE.equals(sv.getLabel())) {
               format = lastLineFormat;
            }

            font = PoiExcelVSUtil.getPOIFont(format, book, false);
            hrText.applyFont(font);

            cell.setCellValue(hrText);

            try {
               Point p1 = new Point(anchor.getCol1(), rowIndex);
               Point p2 = new Point(anchor.getCol2() - 1, rowIndex);
               PoiExcelVSUtil.writeBar(sv, p1, p2, exporter,
                                       info.getCompositeSelectionValue().getSelectionList(),
                                       info, book);
            }
            catch(Exception ex) {
               LOG.error("Failed to write measure bar", ex);
            }
         }

         int row2 = exporter.getRowCol(
            pos.x, pos.y + titleH + rowHeightSum, new Point()).y - 1;

         CellRangeAddress cellRange = new CellRangeAddress(rowIndex, Math.max(rowIndex, row2),
                                                           columnStart,
                                                           Math.max(columnStart, columnEnd));

         // if the cell's background is null or the cell is null, use the
         // object's background
         if(format == null) {
            format = new VSCompositeFormat();
            java.awt.Color bg = info.getFormat().getBackground();

            if(bg != null) {
               format.getUserDefinedFormat().setBackground(
                  PoiExcelVSUtil.getColorByAlpha(bg));
            }
         }
         else if(format.getBackground() == null || i == dispList.size()) {
            format.getUserDefinedFormat().setBackground(
               info.getFormat().getBackground());
         }

         PoiExcelVSUtil.addMergedRegion(sheet, cellRange);

         int celltype = (i == infoRows) ?
            ExcelVSUtil.CELL_TAIL :
            ((i < dispList.size() || dispList.size() == 0) ?
               ExcelVSUtil.CELL_CONTENT : ExcelVSUtil.CELL_HEADER);

         PoiExcelVSUtil.setCellStyles(book, sheet, format, parentformat, cellRange,
                                      null, font, format.getFont(), format.getForeground(),
                                      true, celltype,
                                      ExcelVSUtil.CELL_HEADER | ExcelVSUtil.CELL_TAIL,
                                      stylecache);

         lastLineFormat = format;
      }
   }

   private final Workbook book;
   private final Map<XSSFFormatRecord, XSSFCellStyle> stylecache = new HashMap<>();

   private static final Logger LOG = LoggerFactory.getLogger(ExcelSelectionTreeHelper.class);
}
