/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.io.viewsheet.html;

import inetsoft.analytic.composition.VSCSSUtil;
import inetsoft.report.Presenter;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.filter.DCMergeDatePartFilter;
import inetsoft.report.filter.DCMergeDatesCell;
import inetsoft.report.internal.table.SpanMap;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.painter.HTMLPresenter;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.SparseMatrix;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwappableObjectList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;
import java.text.Format;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Table helper used when exporting to HTML.
 */
public class HTMLCrosstabHelper extends HTMLTableDataHelper {
   /**
    * Creates a new instance of <tt>HTMLTableHelper</tt>.
    *
    * @param helper the coordinate helper.
    * @param vs     the viewsheet being exported.
    */
   public HTMLCrosstabHelper(HTMLCoordinateHelper helper, Viewsheet vs) {
      super(helper, vs);
   }

   /**
    * Creates a new instance of <tt>HTMLTableHelper</tt>.
    *
    * @param helper   the coordinate helper.
    * @param vs       the viewsheet being exported.
    * @param assembly the assembly being written.
    */
   public HTMLCrosstabHelper(HTMLCoordinateHelper helper, Viewsheet vs, VSAssembly assembly) {
      super(helper, vs, assembly);
   }

   public void write(PrintWriter writer, TableDataVSAssembly assembly, VSTableLens lens) {
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      if(lens != null) {
         lens.initTableGrid(info);
      }

      isWritten = new SparseMatrix();
      Rectangle2D bounds = vHelper.getBounds(info);
      initRowColumns(info, lens, bounds);
      fixShrinkTableBounds(info, bounds);
      VSCompositeFormat fmt = info.getFormat();
      StringBuffer table = new StringBuffer("");
      int titleH = !((TitledVSAssemblyInfo) info).isTitleVisible() ? 0 :
         ((TitledVSAssemblyInfo) info).getTitleHeight();
      table.append("<div style='");
      table.append(vHelper.getCSSStyles(bounds, fmt, true));
      table.append(";box-sizing:content-box;z-index:");
      table.append(info.getZIndex());
      table.append("'>");
      table.append(vHelper.getTitle(info));

      try {
         writer.write(table.toString());
         appendTableStyle(writer, info, lens);
         appendTableData(writer, (int) bounds.getHeight() - titleH, info, lens);
         writer.append("</div>");
      }
      catch(Exception e) {
         LOG.error("Failed to write table: " + assembly, e);
      }
   }

   private void initRowColumns(TableDataVSAssemblyInfo info, VSTableLens lens, Rectangle2D bounds) {
      totalHeight = 0;
      totalWidth = 0;
      int rowCount = lens.getRowCount();
      int colCount = lens.getColCount();

      if(rowCount == 0 || colCount == 0) {
         return;
      }

      rowHeights = new int[rowCount];

      for(int r = 0; r < rowCount; r++) {
         if(lens.getRowHeights() == null || lens.getRowHeights().length == 0) {
            rowHeights[r] = (int) lens.getRowHeightWithPadding(AssetUtil.defh, r);
            totalHeight += rowHeights[r];
            continue;
         }

         double h = lens.getWrappedHeight(r, true);

         if(Double.isNaN(h)) {
            h = AssetUtil.defh;
         }

         rowHeights[r] = (int) lens.getRowHeightWithPadding(h, r);
         totalHeight += rowHeights[r];
      }

      columnWidths = new int[colCount];
      int[] widths = lens.getColumnWidths();

      // get user set column widths
      for(int i = 0; i < colCount; i++) {
         double w =  widths != null && i < widths.length ? widths[i] : info.getColumnWidth(i);
         columnWidths[i] = (int) lens.getColumnWidthWithPadding(
            Double.isNaN(w) ? AssetUtil.defw : (int) w, i);
         totalWidth += columnWidths[i];
      }

      // fill the last column, same as the front-end logic in BaseTableController
      if(totalWidth < bounds.getWidth()) {
         for(int i = columnWidths.length - 1; i >= 0; i--) {
            if(columnWidths[i] > 0) {
               double diff = bounds.getWidth() - totalWidth;
               columnWidths[columnWidths.length - 1] += diff;
               totalWidth = (int) bounds.getWidth();
               break;
            }
         }
      }
   }

   private void appendTableStyle(PrintWriter table, TableDataVSAssemblyInfo info,
                                 VSTableLens lens)
   {
      int rowCount = lens.getRowCount();
      int colCount = lens.getColCount();
      cellClasses = new XSwappableObjectList[colCount];

      for(int r = 0; r < rowCount; r++) {
         for(int c = 0; c < colCount; c++) {
            if(isHidden(info, lens, c)) {
               continue;
            }

            createCellStyles(lens, r, c);
         }
      }

      Arrays.stream(cellClasses).filter(a -> a != null).forEach(a -> a.complete());
      table.append("<style>\n");
      style2Classes.forEach((style, cls) -> {
         table.append("." + cls + " {" + style + "}\n");;
      });
      table.append("</style>\n");
   }

   private void createCellStyles(VSTableLens lens, int r, int c) {
      VSFormat format = lens.getFormat(r, c);
      VSCompositeFormat cfmt = new VSCompositeFormat();
      cfmt.setUserDefinedFormat(format);

      int width = columnWidths[c];
      int height = rowHeights[r];
      Dimension span = lens.getSpan(r, c);

      if(span != null) {
         if(span.width > 1) {
            for(int i = 1; i < span.width; i++) {
               width += columnWidths[c + i];
            }
         }

         if(span.height > 1) {
            for(int i = 1; i < span.height; i++) {
               height += rowHeights[r + i];
            }
         }
      }

      String tdStyle = "padding:0;" + getCSSStyles(cfmt) +
         ";width:" + width + "px;height:" + height + "px" +
         ";max-width:" + width + "px;max-height:" + height + "px";

      String divStyle = "display:inline-flex;overflow:hidden;box-sizing:border-box;" +
         "width:" + width + "px;height:" + height + "px;max-width:" + width +
         "px;max-height:" + height + "px;align-items:" +
         VSCSSUtil.getFlexAlignment(VSCSSUtil.getvAlign(cfmt)) +
         ";justify-content:" + VSCSSUtil.getFlexAlignment(VSCSSUtil.gethAlign(cfmt));

      if(!cfmt.isWrapping()) {
         divStyle += ";white-space:nowrap";
      }
      else {
         divStyle += ";word-break:break-word";
      }

      divStyle += ";" + vHelper.getPaddingString(lens.getInsets(r, c));

      if(cellClasses[c] == null) {
         cellClasses[c] = new XSwappableObjectList<>(String[].class);
      }

      cellClasses[c].add(new String[] { getClass(tdStyle, lens), getClass(divStyle, lens) });
   }

   private String getClass(String tdStyle, VSTableLens lens) {
      return style2Classes.computeIfAbsent(tdStyle, k -> getNextClass(lens));
   }

   private String getNextClass(VSTableLens lens) {
      return "c" + System.identityHashCode(lens) + classCnt.getAndIncrement();
   }

   // Add two tables in table view. Onew show header will not scroll, data table can scroll.
   private void appendTableData(PrintWriter table, int dataH, TableDataVSAssemblyInfo info,
                                VSTableLens lens)
   {
      int rowCount = lens.getRowCount();
      int colCount = lens.getColCount();

      table.append("<div style='overflow:auto;width:100%;height:").append(Integer.toString(dataH + 1))
         .append("'><table style='border-collapse:collapse'>");

      // Write data rows.
      for(int r = 0; r < rowCount; r++) {
         table.append("<tr>");

         for(int c = 0; c < colCount; c++) {
            if(isHidden(info, lens, c)) {
               continue;
            }

            writeCell(table, lens, r, c);
         }

         table.append("</tr>");
      }

      table.append("</table></div>");
   }

   private boolean isHidden(TableDataVSAssemblyInfo info, VSTableLens lens, int c) {
      if(info.getColumnWidth2(c, lens) == 0) {
         return true;
      }
      else if(info instanceof CrosstabVSAssemblyInfo &&
         ((CrosstabVSAssemblyInfo) info).isColumnHidden(c, lens))
      {
         return true;
      }

      return false;
   }

   private void writeCell(PrintWriter table, VSTableLens lens, int r, int c) {
      if("T".equals(isWritten.get(r, c))) {
         return;
      }

      VSFormat format = lens.getFormat(r, c);
      VSCompositeFormat cfmt = new VSCompositeFormat();
      cfmt.setUserDefinedFormat(format);
      updateAnnotations(r, c, cfmt);
      int rowCount = lens.getRowCount();
      int colCount = lens.getColCount();
      Dimension bounds = lens.getSpan(r, c);

      if(bounds != null) {
         for(int m = r; m < r + bounds.height && m < rowCount; m++) {
            for(int n = c ; n < c + bounds.width && n < colCount; n++) {
               isWritten.set(m, n, "T");
            }
         }
      }
      else {
         isWritten.set(r, c, "T");
      }

      table.append("<td ");

      int width = columnWidths[c];
      int height = rowHeights[r];

      if(bounds != null) {
         if(bounds.width > 1) {
            int colspan = bounds.width;

            for(int s = 1; s < bounds.width && s + c < colCount; s++) {
               width += columnWidths[c + s];

               // Bug #44321, don't include hidden columns in span, they aren't added to HTML
               if(columnWidths[c + s] == 0) {
                  --colspan;
               }
            }

            table.append("colspan='" + colspan + "'");
         }

         if(bounds.height > 1) {
            table.append("rowspan='" + bounds.height + "'");

            for(int s = 1; s < bounds.height && s + r < rowCount; s++) {
               height += rowHeights[r + s];
            }
         }
      }

      String[] tdDivClass = cellClasses[c].get(r);

      table.append(" class='" + tdDivClass[0] + "'>");
      table.append("<div class='" + tdDivClass[1] + "' ");

      String link = vHelper.getTableCellURL(lens, r, c);

      if(link != null) {
         table.append("style='text-decoration:underline' onclick='window.open(\"" + link + "\")'");
      }

      table.append(">");
      String fmt = vHelper.getCellFormat(lens, r, c);
      Object obj = vHelper.getTableCellObject(lens, r, c, fmt);

      table.append("<div style='width:100%;'>");

      if(obj instanceof PresenterPainter) {
         PresenterPainter painter = (PresenterPainter) obj;
         Presenter presenter = painter.getPresenter();

         if(presenter instanceof HTMLPresenter) {
            Object hobj = painter.getObject();
            table.append(hobj + "");
         }
         else {
            BufferedImage image = vHelper.getPresenter(painter, cfmt, width, height);

            try {
               table.append(vHelper.getImage(image, null, null));
            }
            catch(Exception e) {
               LOG.error("Failed to paint presenter: ", e);
            }
         }
      }
      else if(obj instanceof DCMergeDatesCell) {
         table.append(Tool.toString(((DCMergeDatesCell) obj).getFormatedOriginalDate()));
      }
      else if(obj instanceof DCMergeDatePartFilter.MergePartCell) {
         Format cellfmt = TableFormat.getFormat(format.getFormat(), format.getFormatExtent());
         TableDateComparisonFormat dcDatePartFormat = new TableDateComparisonFormat(cellfmt);
         table.append(dcDatePartFormat.format(obj));
      }
      else if(getTableObject(lens, r, c) != null) {
         table.append(Tool.toString(getTableObject(lens, r, c)));
      }

      table.append("</div>");
      table.append("</div>");
      table.append("</td>");
   }

   public Object getTableObject(VSTableLens lens, int row, int col) {
      SpanMap spanMap = lens.getSpanMap(0, row + 1);
      Rectangle span = spanMap.get(row, col);

      // beginning of span cell may be hidden, should find the top-left of the span cell. (62502)
      if(span != null) {
         col += span.x;
      }

      return lens.getObject(row, col);
   }

   private String getCSSStyles(VSCompositeFormat format) {
      String font = VSCSSUtil.getFont(format);
      String foreground = VSCSSUtil.getForeground(format);
      String background = VSCSSUtil.getBackgroundRGBA(format);
      String hAlign = VSCSSUtil.gethAlign(format);
      String top = VSCSSUtil.getBorder(format, "top");
      String bottom = VSCSSUtil.getBorder(format, "bottom");
      String left = VSCSSUtil.getBorder(format, "left");
      String right = VSCSSUtil.getBorder(format, "right");
      StringBuffer styles = new StringBuffer("");

      styles.append("font:" + font + ";color:" + foreground + ";background:" + background +
         ";text-align:" + hAlign + ";border-top:" + top +
         ";border-left:" + left + ";border-bottom:" + bottom + ";border-right:" + right + ";");
      String decoration = VSCSSUtil.getDecoration(format);

      if(decoration != null) {
         styles.append(";text-decoration: " + decoration);
      }

      return styles.toString();
   }

   private SparseMatrix isWritten = null;
   // css style -> style class
   private Map<String, String> style2Classes = new HashMap<>();
   private XSwappableObjectList<String[]>[] cellClasses;
   private AtomicLong classCnt = new AtomicLong(1);
   private static final Logger LOG = LoggerFactory.getLogger(HTMLCrosstabHelper.class);
}
