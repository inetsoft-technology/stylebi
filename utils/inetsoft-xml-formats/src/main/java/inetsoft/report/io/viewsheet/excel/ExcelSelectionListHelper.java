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
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssemblyInfo;
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

      if(!info.isTitleVisible() && (assembly.getContainer() == null ||
         !(assembly.getContainer() instanceof CurrentSelectionVSAssembly))) {
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
      Point position = info.getViewsheet().getPixelPosition(info);
      int rawY = position.y;
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
      int infoColsWidth = spancols.length;
      CompositeSelectionValue root = new CompositeSelectionValue();
      root.setSelectionList(info.getSelectionList());
      boolean hasSelected =
         root.getSelectionValues(-1, SelectionValue.STATE_SELECTED, 0).size() > 0;
      parentformat = prepareParentBorders(parentformat, assembly);

      // grid rows [bounds[k], bounds[k + 1]) hold data row k; the fit and the drawing below
      // both use this one mapping so they cannot disagree
      int[] bounds = getRowBounds(assembly, info, exporter, rowHeights, rawY, size.height,
                                  position.x, titleHeight, incs);
      int infoRows = bounds.length - 1;
      int slots = (int) Arrays.stream(spancols).filter(n -> n > 0).count();
      // "More..." only when values really cannot fit; a single slot is never given to it
      // because that would replace the one value that does fit
      boolean showMore = infoRows * slots >= 2 && values.size() > infoRows * slots;

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

         if(showMore && ri == infoRows - 1 && (ci + xinc >= infoColsWidth) &&
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
         Point p1 = exporter.getRowCol(position.x, position.y, new Point());
         Point p2 = new Point(p1);
         p1.y = bounds[ri];
         p2.y = bounds[ri + 1];
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

   /**
    * Map the raw pixel layout of the list onto the 20px excel grid cumulatively. A data row
    * spans the grid rows from the row its raw top falls in to the row the next raw top falls in
    * (at least one row), so rounding error does not accumulate per row. Rows stop at the
    * bottom of the box's own grid extent and at the first row owned by another assembly below
    * the list that overlaps it horizontally.
    * @return the grid row boundaries, bounds[k] is the first grid row of data row k and
    * bounds[n] the row after the last data row that fits (n rows fit).
    */
   private int[] getRowBounds(SelectionListVSAssembly assembly, SelectionListVSAssemblyInfo info,
                              PoiExcelVSExporter exporter, List<Double> rowHeights, int rawY,
                              int rawHeight, int x, int excelTitleHeight, boolean incs)
   {
      int top = PoiExcelVSUtil.floorY(rawY);
      int firstRow = exporter.getRowCol(x, top, new Point()).y;
      int dataRow = exporter.getRowCol(x, top + excelTitleHeight, new Point()).y;
      int endRow = firstRow;

      if(rawHeight > 0) {
         int bottom = Math.max(PoiExcelVSUtil.floorY(rawY + rawHeight), top + AssetUtil.defh);
         endRow = exporter.getRowCol(x, bottom, new Point()).y;
      }

      endRow = Math.min(endRow, getNeighborRow(assembly, info, exporter, x, firstRow));
      // the dashboard title takes its full height (even hidden in a container)
      int rawTitle = info.isTitleVisible() || incs ? info.getTitleHeight() : 0;
      double rawTop = rawY + rawTitle;
      List<Integer> bounds = new ArrayList<>();
      int prev = dataRow;
      bounds.add(prev);

      for(Double rowHeight : rowHeights) {
         rawTop += rowHeight;
         int next = Math.max(prev + 1, exporter.getRowCol(x, Math.max(0, (int) rawTop),
                                                           new Point()).y);

         if(next > endRow) {
            break;
         }

         bounds.add(next);
         prev = next;
      }

      return bounds.stream().mapToInt(Integer::intValue).toArray();
   }

   /**
    * Get the first grid row (below the list's first row) owned by another exported assembly
    * that overlaps the list horizontally.
    */
   private int getNeighborRow(SelectionListVSAssembly assembly, SelectionListVSAssemblyInfo info,
                              PoiExcelVSExporter exporter, int x, int firstRow)
   {
      int row = Integer.MAX_VALUE;
      Viewsheet vs = info.getViewsheet();
      Dimension size = info.getPixelSize();

      for(Assembly other : vs.getAssemblies()) {
         // children of a container are stacked by the container writer, the container itself
         // encloses the list
         if(other == assembly || !(other instanceof VSAssembly) ||
            ((VSAssembly) other).getContainer() != null ||
            !vs.isVisible(other, AbstractSheet.SHEET_RUNTIME_MODE))
         {
            continue;
         }

         AssemblyInfo oinfo = other.getInfo();
         Point opos = vs.getPixelPosition(oinfo);
         Dimension osize = oinfo.getPixelSize();

         if(osize == null || osize.width <= 0 || osize.height <= 0 ||
            opos.x >= x + size.width || opos.x + osize.width <= x)
         {
            continue;
         }

         int orow = exporter.getRowCol(opos.x, Math.max(0, PoiExcelVSUtil.floorY(opos.y)),
                                       new Point()).y;

         if(orow > firstRow) {
            row = Math.min(row, orow);
         }
      }

      return row;
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
