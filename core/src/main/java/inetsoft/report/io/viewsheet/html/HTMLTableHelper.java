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
package inetsoft.report.io.viewsheet.html;

import inetsoft.report.Hyperlink;
import inetsoft.report.Presenter;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.filter.DCMergeDatePartFilter;
import inetsoft.report.filter.DCMergeDatesCell;
import inetsoft.report.internal.Common;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.io.viewsheet.excel.ExcelVSUtil;
import inetsoft.report.painter.HTMLPresenter;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;
import java.text.Format;
import java.util.Arrays;

/**
 * Table helper used when exporting to HTML.
 */
public class HTMLTableHelper extends HTMLTableDataHelper {
   /**
    * Creates a new instance of <tt>HTMLTableHelper</tt>.
    *
    * @param helper the coordinate helper.
    * @param vs     the viewsheet being exported.
    */
   public HTMLTableHelper(HTMLCoordinateHelper helper, Viewsheet vs) {
      super(helper, vs);
   }

   /**
    * Creates a new instance of <tt>HTMLTableHelper</tt>.
    *
    * @param helper   the coordinate helper.
    * @param vs       the viewsheet being exported.
    * @param assembly the assembly being written.
    */
   public HTMLTableHelper(HTMLCoordinateHelper helper, Viewsheet vs, VSAssembly assembly) {
      super(helper, vs, assembly);
   }

   public void write(PrintWriter writer, TableDataVSAssembly assembly, VSTableLens lens) {
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      Rectangle2D bounds = vHelper.getBounds(info);
      initRowColumns(info, bounds, lens);
      fixShrinkTableBounds(info, bounds);
      VSCompositeFormat fmt = info.getFormat();
      StringBuffer table = new StringBuffer("");
      int titleH = !((TitledVSAssemblyInfo) info).isTitleVisible() ? 0 :
         ((TitledVSAssemblyInfo) info).getTitleHeight();
      table.append("<div style='");
      table.append(";z-index:");
      table.append(info.getZIndex());
      table.append(";");
      table.append(vHelper.getCSSStyles(bounds, fmt, true));
      table.append("'>");
      table.append(vHelper.getTitle(info));
      appendTableData(table, info, (int) bounds.getHeight() - titleH, (int) bounds.getWidth(), lens);
      table.append("</div>");

      try {
         writer.write(table.toString());
      }
      catch(Exception e) {
         LOG.error("Failed to write table: " + assembly, e);
      }
   }

   private void initRowColumns(TableDataVSAssemblyInfo info, Rectangle2D bounds, VSTableLens lens) {
      lens.moreRows(XTable.EOT);
      int rowCount = lens.getRowCount();
      int colCount = lens.getColCount();
      rowHeights = new int[rowCount];
      columnWidths = new int[colCount];
      totalHeight = 0;
      totalWidth = 0;
      lens.initTableGrid(info);

      int[] heights = lens.getRowHeights();
      heights = heights == null ? new int[0] : heights;

      for(int r = 0; r < rowCount; r++) {
         if(lens.getRowHeights() == null || r >= heights.length) {
            rowHeights[r] = (int) lens.getRowHeightWithPadding(AssetUtil.defh, r);
            totalHeight += rowHeights[r];
            continue;
         }

         int h = lens.getWrappedHeight(r, true);
         rowHeights[r] = (int) lens.getRowHeightWithPadding(Double.isNaN(h) ? AssetUtil.defh : h, r);
         totalHeight += rowHeights[r];
      }

      for(int c = 0; c < colCount; c++) {
         double w = info.getColumnWidth(c);

         if(Double.isNaN(w)) {
            w = info.getColumnWidth2(c, lens);
         }

         columnWidths[c] = (int) lens.getColumnWidthWithPadding(
            Double.isNaN(w) ? AssetUtil.defw : w, c);
         totalWidth += columnWidths[c];
      }
   }

   // Add two tables in table view. Onew show header will not scroll, data table can scroll.
   private void appendTableData(StringBuffer table, TableDataVSAssemblyInfo info, int dataH,
                                int tableWidth, VSTableLens lens)
   {
      VSCompositeFormat fmt = info.getFormat();
      int headerRowCount = lens.getHeaderRowCount();
      int rowCount = lens.getRowCount();
      int headerHeight = lens.getRowHeight(0) == -1 ? 20 : lens.getRowHeight(0);
      table.append("<div style='overflow:auto;" + "width:");
      table.append(isTableYOverflow(dataH) ? "calc(100% + 17px)" : "100%");
      table.append(";height:" + dataH + "'>");
      table.append("<div style='overflow:auto;width:100%;height:" +
         dataH + "'>");
      table.append("<table style='border-collapse:collapse'>");
      table.append("<thead>");

      // Write header rows.
      for(int r = 0; r < headerRowCount; r++) {
         writeRow(table, lens, r, fmt);
      }

      table.append("</thead>");
      table.append("<tbody>");

      // Write data rows.
      for(int r = headerRowCount; r < rowCount; r++) {
         writeRow(table, lens, info, r, fmt);
      }

      table.append("</tbody>");
      table.append("</table></div></div>");
   }

   private boolean isTableXOverflow(int tableWidth) {
      if(columnWidths != null) {
         return Arrays.stream(columnWidths).sum() > tableWidth;
      }

      return false;
   }

   private boolean isTableYOverflow(int tableHeight) {
      if(rowHeights != null) {
         return Arrays.stream(rowHeights).sum() > tableHeight;
      }

      return false;
   }

   private void writeRow(StringBuffer table, VSTableLens lens, int r, VSCompositeFormat fmt) {
      writeRow(table, lens, null, r, fmt);
   }

   private void writeRow(StringBuffer table, VSTableLens lens, TableDataVSAssemblyInfo info,
                         int r, VSCompositeFormat fmt)
   {
      int colCount = lens.getColCount();

      if(r < rowHeights.length && rowHeights[r] > 0) {
         table.append("<tr>");

         for(int c = 0; c < colCount; c++) {
            writeCell(table, lens, info, r, c);
         }

         table.append("</tr>");
      }
   }

   /**
    * Return the table cell style.
    */
   private String getCellStyle(VSCompositeFormat format) {
      StringBuffer styles = new StringBuffer("");
      styles.append(vHelper.getCommonStyleString(format) + ";");
      styles.append(vHelper.getBorderString(format, false) + ";");

      return styles.toString();
   }

   /**
    * Return the table cell content alignment style. Avoid to add alignment style
    * to cell, because that may effect the table's layout.
    */
   private String getCellContentStyle(VSCompositeFormat format) {
      boolean isWrap = format.isWrapping();

      return vHelper.getAlignmentString(format) + ";" +
         (isWrap ? "word-break: break-word;" : "white-space:nowrap;");
   }

   private void writeCell(StringBuffer table, VSTableLens lens,
                          TableDataVSAssemblyInfo info, int r, int c)
   {
      VSFormat format = lens.getFormat(r, c);
      VSCompositeFormat cfmt = new VSCompositeFormat();
      boolean isHeader = r < lens.getHeaderRowCount();

      if(isHeader) {
         format = (VSFormat) format.clone();
         Insets borders = format.getBorders();

         // header top border should be ignored as it will not be displayed and only
         // leaves a 1px gap to see through
         if(borders != null) {
            borders.top = 0;
         }
      }

      cfmt.setUserDefinedFormat(format);
      updateAnnotations(r, c, cfmt);
      int w = columnWidths[c];

      if(w <= 0) {
         return;
      }

      table.append("<td style='");
      table.append(getCellStyle(cfmt));
      int h = rowHeights[r];

      String link = getCellHyperlink(lens, info, r, c);
      float vBorderWidth = 0;
      float hBorderWidth = 0;

      if(cfmt != null && cfmt.getBorders() != null) {
         vBorderWidth = (Common.getLineWidth(cfmt.getBorders().bottom) +
                         Common.getLineWidth(cfmt.getBorders().top)) / 2;
         hBorderWidth = (Common.getLineWidth(cfmt.getBorders().left) +
                         Common.getLineWidth(cfmt.getBorders().right)) / 2;
      }

      if(isHeader) {
         table.append(";position:sticky;top:0px;");
      }

      boolean lastColumn = c == lens.getColCount() - 1;
      table.append("width:");
      table.append(lastColumn ? "100%" : w + "px");
      table.append(";max-width:");
      table.append(lastColumn ? "unset" : w + "px");
      table.append(";height:" + h + "px");
      table.append(";max-height:" + h + "px'>");

      table.append("<div style='overflow:hidden;box-sizing:border-box;width:" +
         (lastColumn ? "100%" : (w - hBorderWidth * 2 + "px")) + ";height:" +
         (h - vBorderWidth) + "px;");
      table.append(getCellContentStyle(cfmt));
      table.append(vHelper.getPaddingString(lens.getInsets(r, c)));

      if(link != null) {
         table.append("text-decoration:underline' onclick='window.open(\"" + link + "\");");
      }

      table.append("'>");

      String fmt = vHelper.getCellFormat(lens, r, c);
      Object obj = vHelper.getTableCellObject(lens, r, c, fmt);

      if(obj instanceof DCMergeDatesCell) {
         obj = ((DCMergeDatesCell) obj).getFormatedOriginalDate();
      }
      else if(obj instanceof DCMergeDatePartFilter.MergePartCell) {
         Format cellfmt = TableFormat.getFormat(format.getFormat(), format.getFormatExtent());
         TableDateComparisonFormat dcDatePartFormat = new TableDateComparisonFormat(cellfmt);
         obj = dcDatePartFormat.format(obj);
      }

      if(obj instanceof BufferedImage) {
         try {
            table.append(vHelper.getImage((BufferedImage) obj, null, null));
         }
         catch(Exception e) {
            LOG.error("Failed to paint image table cell: ", e);
         }
      }
      else if(obj instanceof PresenterPainter) {
         PresenterPainter painter = (PresenterPainter) obj;
         Presenter presenter = painter.getPresenter();

         if(presenter instanceof HTMLPresenter) {
            Object hobj = painter.getObject();
            table.append(hobj + "");
         }
         else {
            BufferedImage image = vHelper.getPresenter(painter, cfmt, w, h);

            try {
               table.append(vHelper.getImage(image, null, null));
            }
            catch(Exception e) {
               LOG.error("Failed to paint presenter: ", e);
            }
         }
      }
      else {
         String value = Tool.toString(lens.getObject(r, c));
         value = value.replace("\n", "<br>");
         table.append(value);
      }

      table.append("</div></td>");
   }

   private String getCellHyperlink(VSTableLens lens, TableDataVSAssemblyInfo info, int r, int c) {
      if(info instanceof TableVSAssemblyInfo && !(info instanceof EmbeddedTableVSAssemblyInfo)) {
         Hyperlink rowHyperlink = info.getRowHyperlink();

         if(rowHyperlink != null) {
            Hyperlink.Ref ref = new Hyperlink.Ref(rowHyperlink, lens, r, -1);

            if(ref.getLinkType() == Hyperlink.WEB_LINK) {
               return ExcelVSUtil.getURL(ref);
            }
         }
      }

      return vHelper.getTableCellURL(lens, r, c);
   }

   private static final Logger LOG = LoggerFactory.getLogger(HTMLTableHelper.class);
}
