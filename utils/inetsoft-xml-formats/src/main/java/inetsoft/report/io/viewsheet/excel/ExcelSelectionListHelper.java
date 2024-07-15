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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.report.TableDataPath;
import inetsoft.report.io.viewsheet.ExporterHelper;
import inetsoft.report.io.viewsheet.VSSelectionListHelper;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
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
public class ExcelSelectionListHelper extends ExporterHelper {
   /**
    * Constructor.
    */
   public ExcelSelectionListHelper(Workbook book) {
      this.book = book;
   }

   /**
    * Write the view sheet assembly to excel.
    */
   public void write(Sheet sheet, SelectionListVSAssembly assembly) {
      SelectionListVSAssemblyInfo info =
         (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      List<SelectionValue> values = getSelectedValues(info);
      writeTitle(sheet, assembly);

      if(info.getShowType() == SelectionVSAssemblyInfo.LIST_SHOW_TYPE) {
         writeList(assembly, sheet, values);
      }
   }

   /**
    * Write title.
    */
   private void writeTitle(Sheet sheet, SelectionListVSAssembly assembly) {
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(!info.isTitleVisible()) {
         return;
      }

      PoiExcelVSExporter exporter = (PoiExcelVSExporter) getExporter();
      FormatInfo finfo = info.getFormatInfo();
      VSCompositeFormat parentformat = info.getFormat();
      VSCompositeFormat format;
      VSAssembly containerAssembly = assembly.getContainer();

      if(containerAssembly instanceof CurrentSelectionVSAssembly) {
         if(finfo != null) {
            format = finfo.getFormat(new TableDataPath(-1, TableDataPath.TITLE), false);
         }
         else {
            format = parentformat;
         }

         double titleRatio = ((CurrentSelectionVSAssemblyInfo) containerAssembly.getInfo()).getTitleRatio();
         parentformat = prepareParentBorders(parentformat, assembly);
         PoiExcelVSUtil.writeTitleInContainer(info.getViewsheet().getPixelPosition(info),
                                              info.getPixelSize(), 0, Tool.localize(info.getTitle()),
                                              assembly.getDisplayValue(true, true),
                                              format, sheet, book, exporter, null, parentformat,
                                              PoiExcelVSUtil.getSelectionTitleHeight(info), true, titleRatio);
      }
      else {
         writeTitle(assembly, sheet);
      }
   }

   /**
    * Get selected values.
    * @param info the specified assembly info.
    * @return selected values.
    */
   private List<SelectionValue> getSelectedValues(SelectionListVSAssemblyInfo info) {
      List<SelectionValue> values = new ArrayList<>();
      SelectionList slist = info.getSelectionList();

      if(slist != null) {
         SelectionValue[] list = slist.getSelectionValues();

         // @by larryl, include all values but gray out the non-selected values
         // so the output looks not as if values are missing and is more
         // informative
         for(SelectionValue selectionValue : list) {
            if(!selectionValue.isExcluded()) {
               values.add(selectionValue);
            }
         }
      }

      return values;
   }

   private boolean isInContainer(SelectionListVSAssembly assembly) {
      VSAssembly containerAssembly = assembly.getContainer();

      return containerAssembly instanceof CurrentSelectionVSAssembly;
   }

   /**
    * Write the title.
    * @param assembly the specified SelectionListVSAssemblyInfo.
    * @param sheet the Sheet title which is on.
    */
   private void writeTitle(SelectionListVSAssembly assembly, Sheet sheet) {
      SelectionListVSAssemblyInfo info =
         (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();
      PoiExcelVSExporter exporter = (PoiExcelVSExporter) getExporter();
      ClientAnchor anchor = exporter.getAnchorPosition(info);
      FormatInfo finfo = info.getFormatInfo();
      VSCompositeFormat parentformat = info.getFormat();
      VSCompositeFormat format;
      CellRangeAddress cellRange;

      if(finfo != null) {
         format = finfo.getFormat(
            new TableDataPath(-1, TableDataPath.TITLE), false);
      }
      else {
         format = parentformat;
      }

      final Point pixelPos = info.getViewsheet().getPixelPosition(info);
      int titleH = PoiExcelVSUtil.getExcelTitleHeight(info);
      int y = PoiExcelVSUtil.floorY(pixelPos.y + titleH);
      int row2 = exporter.getRowCol(pixelPos.x, y, new Point()).y - 1;
      int col2 = Math.max(anchor.getCol1(), anchor.getCol2() - 1);
      cellRange = new CellRangeAddress(anchor.getRow1(), row2, anchor.getCol1(), col2);

      PoiExcelVSUtil.addMergedRegion(sheet, cellRange);

      Row row = PoiExcelVSUtil.getRow(anchor.getRow1(), sheet);
      Cell mcell = PoiExcelVSUtil.getCell(anchor.getCol1(), row);
      String title = Tool.localize(info.getTitle());

      RichTextString hrText =
         PoiExcelVSUtil.createRichTextString(book, Tool.convertHTMLSymbol(title));

      Font font = PoiExcelVSUtil.getPOIFont(format, book, true);
      hrText.applyFont(font);

      mcell.setCellValue(hrText);

      parentformat = prepareParentBorders(parentformat, assembly);

      PoiExcelVSUtil.setCellStyles(book, sheet, format, parentformat, cellRange,
                                   anchor, font,
                                   format.getFont(), format.getForeground(),
                                   true, ExcelVSUtil.CELL_HEADER,
                                   ExcelVSUtil.CELL_HEADER | ExcelVSUtil.CELL_TAIL,
                                   stylecache);
   }

   /**
    * Write the list.
    * @param assembly the specified SelectionListVSAssemblyInfo.
    * @param sheet the Sheet the list is on.
    * @param values selected SelectionValues.
    */
   private void writeList(SelectionListVSAssembly assembly, Sheet sheet, List<SelectionValue> values) {
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();
      PoiExcelVSExporter exporter = (PoiExcelVSExporter) getExporter();
      VSCompositeFormat parentformat = info.getFormat();
      Dimension size = info.getPixelSize();

      if(size.height > 0) {
         size.height = PoiExcelVSUtil.floorY(size.height);
         size.height = size.height == 0 ? AssetUtil.defh : size.height;
      }

      Point position = info.getViewsheet().getPixelPosition(info);
      position.y = PoiExcelVSUtil.floorY(position.y);
      Row row;
      Cell cell;
      SelectionValue value;
      String valueLabel;
      int ri, ci = 0;
      int i = 0;
      // divide the columns so the items are most evenly distributed
      int[] spancols = calculateColumnSpan(info, exporter);
      boolean incs = assembly.getContainer() instanceof CurrentSelectionVSAssembly;

      //titleHeight != dataRowHeight.
      List<Double> rowHeights = info.getRowHeights();

      //Now using default row height in excel, so calculate real row height in excel.
      int titleHeight = isInContainer(assembly) ? PoiExcelVSUtil.getSelectionTitleHeight(info) :
         PoiExcelVSUtil.getExcelTitleHeight(info);
      int infoRows = 0;
      int infoColsWidth = spancols.length;
      CompositeSelectionValue root = new CompositeSelectionValue();
      root.setSelectionList(info.getSelectionList());
      boolean hasSelected =
         root.getSelectionValues(-1, SelectionValue.STATE_SELECTED, 0).size() > 0;
      parentformat = prepareParentBorders(parentformat, assembly);
      int rowHeightSum = 0;

      for(Double rowHeight : rowHeights) {
         rowHeightSum += PoiExcelVSUtil.getSelectionCellHeight(rowHeight.intValue());

         if(rowHeightSum > (size.height - titleHeight)) {
            break;
         }

         infoRows++;
      }

      for(ri = 0; ri < infoRows;) {
         valueLabel = "";
         value = i < values.size() ? values.get(i) : null;

         VSCompositeFormat vsformat = value == null ? null : value.getFormat();
         VSCompositeFormat format = (vsformat == null) ? new VSCompositeFormat() : vsformat;

         format = VSSelectionListHelper.getValueFormat(value, format, hasSelected);

         int xinc = spancols[ci];
         short columnStart = (short) ci;
         xinc = Math.min(infoColsWidth - columnStart, xinc);
         short columnEnd = (short) (columnStart + xinc - 1);

         if(ri == infoRows - 1 && (ci + xinc >= infoColsWidth) &&
            values.size() - 1 > i)
         {
            // last cell but still got more elements
            valueLabel = catalog.getString("More") + "...";
         }
         else if(value != null) {
            valueLabel = value.getLabel();

            if(info.isShowText() && value.getMeasureLabel() != null) {
               valueLabel += " (" + value.getMeasureLabel() + ")";
            }
         }

         //Fixed bug #25366
         //titleRow * AssetUtil.defh != titleHeight
         //we shoude use really writeTitle's height to calculate point.
         Point p1 = exporter.getRowCol(position.x,
            position.y + titleHeight + getRowHeightSum(rowHeights, ri), new Point());

         Point p2 = exporter.getRowCol(position.x,
            position.y + titleHeight + getRowHeightSum(rowHeights, ri + 1), new Point());
         p1.x += columnStart;
         p2 = new Point(p2.x + columnEnd, p2.y - 1);

         if(p2.y < p1.y || p2.x < p1.x) {
            break;
         }

         CellRangeAddress cellRange =
            new CellRangeAddress(p1.y, p2.y, p1.x, p2.x);
         int rowstyle = ExcelVSUtil.CELL_CONTENT;
         int colstyle = ExcelVSUtil.CELL_CONTENT;

         PoiExcelVSUtil.addMergedRegion(sheet, cellRange);

         if(ri == 0) {
            rowstyle |= ExcelVSUtil.CELL_HEADER;
         }

         if(ri == infoRows - 1) {
            rowstyle |= ExcelVSUtil.CELL_TAIL;
         }

         if(ci == 0) {
            colstyle |= ExcelVSUtil.CELL_HEADER;
         }

         if(ci + xinc == infoColsWidth) {
            colstyle |= ExcelVSUtil.CELL_TAIL;
         }

         p1.y -= incs && !info.isTitleVisible() ? 1 : 0;
         row = PoiExcelVSUtil.getRow(p1.y, sheet);
         cell = PoiExcelVSUtil.getCell(p1.x, row);
         RichTextString hrText = PoiExcelVSUtil.createRichTextString(book,
                                                                     Tool.convertHTMLSymbol(valueLabel));
         Font ft = PoiExcelVSUtil.getPOIFont(format, book, false);

         hrText.applyFont(ft);
         cell.setCellValue(hrText);
         PoiExcelVSUtil.setCellStyles(book, sheet, format, parentformat, cellRange,
                                      null, ft, format.getFont(), format.getForeground(),
                                      true, rowstyle, colstyle,
                                      stylecache);

         try {
            if(value != null) {
               PoiExcelVSUtil.writeBar(value, p1, p2, exporter,
                                       info.getSelectionList(), info, book);
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to write measure bar", ex);
         }

         ci += xinc;

         if(ci >= infoColsWidth) {
            ci = 0;
            ri++;
         }

         i++;
      }

      sheet.validateMergedRegions();
   }

   private int getRowHeightSum(List<Double> rowHeights, int r) {
      int rowHeightSum = 0;

      for(int i = 0; i < r; i++) {
         rowHeightSum += PoiExcelVSUtil.getSelectionCellHeight((int)(double)(rowHeights.get(i)));
      }

      return rowHeightSum;
   }

   /**
    * Calculate the column span to fit items to grid.
    */
   private int[] calculateColumnSpan(SelectionListVSAssemblyInfo info, PoiExcelVSExporter exporter) {
      ClientAnchor anchor = exporter.getAnchorPosition(info);
      int[] cws = new int[Math.max(anchor.getCol2() - anchor.getCol1(), 1)];
      int ncol = Math.min(info.getColumnCount(), cws.length);
      // columns to be merged to hold selection items
      int[] spans = new int[cws.length];

      for(int i = 0; i < cws.length; i++) {
         cws[i] = (int) exporter.getBounds(
            new Point(anchor.getCol1() + i, 0), new Point(anchor.getCol1() + i + 1, 0)).getWidth();
         spans[i] = 1;
      }

      for(int n = spans.length; n > ncol; n--) {
         // find two columns to merge that yields the smallest width
         int idx = findMinPair(spans, cws);
         int next = idx + spans[idx];
         spans[idx] += spans[next];
         spans[next] = 0;
      }

      return spans;
   }

   /**
    * Find the minimum width pair of columns to merge.
    */
   private int findMinPair(int[] spans, int[] cws) {
      int w = Integer.MAX_VALUE;
      int idx = 0;

      for(int i = 0; i < spans.length; i++) {
         if(spans[i] == 0 || i + spans[i] >= spans.length) {
            continue;
         }

         int w2 = 0;
         int cnt = spans[i] + spans[i + spans[i]];

         for(int j = 0; j < cnt && i + j < cws.length; j++) {
            w2 += cws[i + j];
         }

         if(w2 < w) {
            w = w2;
            idx = i;
         }
      }

      return idx;
   }

   static VSCompositeFormat prepareParentBorders(VSCompositeFormat paformat, VSAssembly assembly) {
      if(paformat != null) {
         paformat = paformat.clone();
      }
      else {
         paformat = new VSCompositeFormat();
      }

      VSAssembly container = assembly.getContainer();

      if(container instanceof CurrentSelectionVSAssembly) {
         Insets borders = paformat.getBorders();
         BorderColors bcolors = paformat.getBorderColors();
         String[] children = ((CurrentSelectionVSAssembly) container).getAssemblies();
         boolean last = children[children.length - 1].equals(assembly.getName());

         if(borders == null) {
            borders = new Insets(0, 0, 0, 0);
         }

         VSCompositeFormat parentformat = container.getVSAssemblyInfo().getFormat();
         Insets pborders = parentformat.getBorders();
         BorderColors pbcolors = parentformat.getBorderColors();

         // share left/right border with container
         if(pborders != null) {
            Insets borders2 = new Insets(0, borders.left, borders.bottom, borders.right);
            BorderColors bcolors2 = new BorderColors();

            if(bcolors != null) {
               bcolors2 = new BorderColors(bcolors.topColor, bcolors.leftColor,
                                           bcolors.bottomColor, bcolors.rightColor);
            }
            else if(pbcolors != null) {
               bcolors2 = new BorderColors(pbcolors.topColor, pbcolors.leftColor,
                                           pbcolors.bottomColor, pbcolors.rightColor);
            }

            if(borders2.left == 0) {
               borders2.left = pborders.left;
               bcolors2.leftColor = pbcolors.leftColor;
            }

            if(!last) {
               borders2.bottom = 0;
            }
            else if(borders2.bottom == 0) {
               borders2.bottom = pborders.bottom;
               bcolors2.bottomColor = pbcolors.bottomColor;
            }

            if(borders2.right == 0) {
               borders2.right = pborders.right;
               bcolors2.rightColor = pbcolors.rightColor;
            }

            paformat.getUserDefinedFormat().setBorders(borders2);
            paformat.getUserDefinedFormat().setBorderColors(bcolors2);
         }
      }

      return paformat;
   }

   private final Workbook book;
   private final Catalog catalog = Catalog.getCatalog();
   private final Map<XSSFFormatRecord, XSSFCellStyle> stylecache = new HashMap<>();

   private static final Logger LOG = LoggerFactory.getLogger(ExcelSelectionListHelper.class);
}
