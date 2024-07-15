/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.io.viewsheet;

import inetsoft.graph.VGraph;
import inetsoft.graph.internal.GTool;
import inetsoft.report.Painter;
import inetsoft.report.*;
import inetsoft.report.composition.RegionTableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.VSFormatTableLens;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.filter.ColumnMapFilter;
import inetsoft.report.internal.*;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.io.excel.ExcelUtil;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.painter.*;
import inetsoft.report.pdf.PDFDevice;
import inetsoft.uql.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.MessageFormat;
import inetsoft.util.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.*;
import java.util.List;
import java.util.*;

/**
 * Common utilities for exporting.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ExportUtil {
   /**
    * The max title text width.
    */
   public static final int MAX_TITLE_WIDTH = 100;

   /**
    * Geth the text string of the title cell in current selection.
    * @param title the title of the title cell.
    * @param value the value of the title cell.
    * @param width the width of the title cell.
    * @param font the font of the title ant value.
    */
   public static String getTextInTitleCell(String title, String value,
      int width, Font font, double titleRatio)
   {
      title = title == null? "" : title;
      int tcWidth = Double.isNaN(titleRatio) ?
         (width >= 3 * MAX_TITLE_WIDTH ? MAX_TITLE_WIDTH : width / 3) :
         (int) (width * titleRatio);
      StringBuilder text = new StringBuilder(title);

      while(getStringPixelWidth(text.toString(), font) > tcWidth &&
         text.toString().length() > 1)
      {
         text.deleteCharAt(text.length() - 1);
      }

      while(getStringPixelWidth(text.toString(), font) < tcWidth) {
         text.append(" ");
      }

      int swidth1 = getStringPixelWidth(text.toString(), font);
      int swidth2 = getStringPixelWidth(
         text.toString().substring(0, text.toString().length() - 1), font);

      if(Math.abs(swidth1 - tcWidth) >= Math.abs(swidth2 - tcWidth)) {
         text.deleteCharAt(text.length() - 1);
      }

      return text.append("    " + value).toString();
   }

   /**
    * Get background color of time slider in current selection.
    * @param titleFormat format of slider title.
    * @param objFormat format of slider object.
    */
   public static Color getBackGroundColor(VSCompositeFormat titleFormat,
                                          VSCompositeFormat objFormat) {
      return titleFormat != null && titleFormat.getBackground() != null ?
         titleFormat.getBackground() : objFormat != null &&
         objFormat.getBackground() != null ? objFormat.getBackground() : null;
   }

   /**
    * Geth the pixel width of a string.
    * @param str the string which to get width of.
    * @param font the font of the title ant value.
    */
   private static int getStringPixelWidth(String str, Font font) {
      FontMetrics fm = Common.getFontMetrics(font);
      int width = (int) Common.stringWidth(str, font, fm);
      return width;
   }

   /**
    * Draw text.
    */
   public static void drawTextBox(Graphics printer, Rectangle2D bounds, Rectangle2D textBounds,
                                  VSCompositeFormat format, String dispText,
                                  boolean paintBackground, boolean underline,
                                  Insets shapeBorders, Insets padding)
   {
      drawTextBox(printer, bounds, textBounds, format, dispText, paintBackground,
         underline, shapeBorders, padding, false);
   }

   /**
    * Draw text.
    */
   public static void drawTextBox(Graphics printer, Rectangle2D bounds, Rectangle2D textBounds,
                                  VSCompositeFormat format, String dispText,
                                  boolean paintBackground, boolean underline,
                                  Insets shapeBorders, Insets padding, boolean shadow)
   {
      if(format != null) {
         Margin mg = calcBorderMargin(format, shapeBorders);
         //printer.setBackground(format.getBackground());
         printer.setFont(format.getFont());
         BorderColors borderColors = format.getBorderColors();
         Insets borders = format.getBorders();
         Point point = new Point((int) bounds.getX(), (int) bounds.getY());
         Dimension size = new Dimension((int) bounds.getWidth(), (int) bounds.getHeight());

         if(shapeBorders != null) {
            point.x += mg.left;
            point.y += mg.top;
            size.width -= (mg.left + mg.right);
            size.height -= (mg.top + mg.bottom);
         }

         BorderColors defbcolors = new BorderColors(
            VSAssemblyInfo.DEFAULT_BORDER_COLOR,
            VSAssemblyInfo.DEFAULT_BORDER_COLOR,
            VSAssemblyInfo.DEFAULT_BORDER_COLOR,
            VSAssemblyInfo.DEFAULT_BORDER_COLOR);

         borderColors = borderColors == null ? defbcolors : borderColors;
         boolean artifactStarted = false;

         if(format.getBackground() != null && paintBackground) {
            if(printer instanceof PDFDevice) {
               ((PDFDevice) printer).startArtifact();
               artifactStarted = true;
            }

            printer.setColor(format.getBackground());
            printer.fillRect(point.x, point.y, size.width, size.height);
         }

         if(borders != null && borderColors != null) {
            if(borders.top != StyleConstants.NO_BORDER && borderColors.topColor == null) {
               borderColors.topColor = defbcolors.topColor;
            }

            if(borders.bottom != StyleConstants.NO_BORDER && borderColors.bottomColor == null) {
               borderColors.bottomColor = defbcolors.bottomColor;
            }

            if(!artifactStarted && (printer instanceof PDFDevice)) {
               ((PDFDevice) printer).startArtifact();
               artifactStarted = true;
            }

            drawBorders(printer, new Point((int) bounds.getX(), (int) bounds.getY()),
                        new Dimension((int) bounds.getWidth(), (int) bounds.getHeight()),
                        borderColors, borders, format.getRoundCorner());
         }

         if(artifactStarted) {
            ((PDFDevice) printer).endArtifact();
         }

         Bounds nbounds = new Bounds(
            (float) (textBounds.getX() + mg.left),
            (float) (textBounds.getY() + mg.top),
            (float) (textBounds.getWidth() - mg.left - mg.right),
            (float) (textBounds.getHeight() - mg.top - mg.bottom));

         if(padding != null) {
            nbounds.x += padding.left;
            nbounds.y += padding.top;
            nbounds.width -= padding.left + padding.right;
            nbounds.height -= padding.top + padding.bottom;
         }

         int oldStyle = 0;

         if(underline) {
            Font font = format.getFont();

            if(font != null && font instanceof StyleFont) {
               oldStyle = font.getStyle();
               format.getUserDefinedFormat()
                  .setFont(font.deriveFont(oldStyle | StyleFont.UNDERLINE));
            }
         }

         if(dispText != null) {
            //bug1323903348099
            Color color = format.getForeground() == null ? Color.BLACK : format.getForeground();
            printer.setColor(color);

            if(VSTableLens.isHTML(dispText)) {
               HTMLPresenter presenter = new HTMLPresenter();
               presenter.setFont(printer.getFont());
               presenter.paint(printer, dispText, (int) nbounds.x, (int) nbounds.y,
                               (int) nbounds.width, (int) nbounds.height);
            }
            else {
               if(printer instanceof PDFDevice) {
                  ((PDFDevice) printer).startParagraph(null);
               }

               int align = format.getAlignment() == StyleConstants.NONE ?
                  StyleConstants.H_LEFT | StyleConstants.V_TOP : format.getAlignment();

               if((align & StyleConstants.V_ALIGN_MASK) == 0) {
                  align |= StyleConstants.V_TOP;
               }

               Color textColor = printer.getColor();

               //paint text shadow
               if(shadow) {
                  printer.setColor(new Color(119, 119, 119, 128));
                  Common.paintText(printer, Tool.convertHTMLSymbol(dispText),
                     new Bounds(nbounds.x + 3, nbounds.y + 3, nbounds.width, nbounds.height), align,
                     format.isWrapping(), false, 0);
               }

               printer.setColor(textColor);
               Common.paintText(printer, Tool.convertHTMLSymbol(dispText), nbounds, align,
                                format.isWrapping(), false, 0);

               if(printer instanceof PDFDevice) {
                  ((PDFDevice) printer).endParagraph();
               }
            }
         }

         if(underline) {
            Font font = format.getFont();

            if(font != null && font instanceof StyleFont) {
               format.getUserDefinedFormat().setFont(font.deriveFont(oldStyle));
            }
         }
      }
   }

   /**
    * Calculate the border margin.
    */
   private static Margin calcBorderMargin(VSCompositeFormat format,
                                          Insets shapeBorders)
   {
      Margin adj = new Margin();
      Insets borders = format.getBorders();
      borders = borders == null ? shapeBorders : borders;

      if(borders != null) {
         adj.top = (float) Math.ceil(Common.getLineWidth(borders.top));
         adj.left = (float) Math.ceil(Common.getLineWidth(borders.left));
         adj.bottom = (float) Math.ceil(Common.getLineWidth(borders.bottom));
         adj.right = (float) Math.ceil(Common.getLineWidth(borders.right));
      }

      return adj;
   }

   /**
    * Get the image for a presenter.
    */
   public static BufferedImage getPainterImage(Object obj, int w, int h,
                                               VSCompositeFormat fmt)
   {
      Color fg = fmt.getForeground();
      Color bg = fmt.getBackground();

      if(fg == null) {
         fg = Color.BLACK;
      }

      if(bg == null) {
         bg = Color.WHITE;
      }

      BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = (Graphics2D) image.getGraphics();

      g.setColor(bg);
      g.fillRect(0, 0, w, h);
      g.setColor(fg);

      if(obj instanceof ObjectWrapper) {
         obj = ((ObjectWrapper) obj).unwrap();
      }

      if(obj instanceof PresenterPainter) {
         PresenterPainter painter = (PresenterPainter) obj;
         painter.getPresenter().setFont(fmt.getFont());
         // @by stephenwebster, For bug1426021152663.
         // Same as AssetWebHandler.getCellImage
         // Removed artificial padding around image.  This code should not
         // be concerned with positioning and sizing calculations.  It should
         // simply return the image from the presenter.  Calling code needs to
         // handle the positioning / sizing based on output's needs.
         Bounds b = new Bounds(0, 0, w, h);
         Dimension psize = painter.getPresenter().getPreferredSize(
            painter.getObject());
         b = !painter.getPresenter().isFill() ?
            Common.alignCell(b, new Size(psize), fmt.getAlignment()) : b;

         if(painter.getPresenter() instanceof CCWRotatePresenter) {
            Common.paint(g, b.x, b.y, b.width, b.height, painter,
                         -2f, -2f, b.width, b.height, b.width, b.height,
                         fg, fmt.getBackground(), -b.y, -1);
         }
         else {
            Common.paint(g, b.x, b.y, b.width, b.height, painter,
                         0f, 0f, b.width, b.height, b.width, b.height,
                         fg, fmt.getBackground(), -b.y, -1);
         }
      }
      else if(obj instanceof Painter) {
         ((Painter) obj).paint(g, 0, 0, w, h);
      }
      else if(obj instanceof Image) {
         g.drawImage((Image) obj, 0, 0, null);
      }

      g.dispose();

      return image;
   }

   /**
    * Print the presenter content directly to the Writer (Graphics object).
    */
   public static void printPresenter(Object obj, int w, int h,
                                     VSCompositeFormat fmt, Graphics g,
                                     Rectangle2D bounds)
   {
      Color fg = fmt.getForeground();
      Color bg = fmt.getBackground();

      if(fg == null) {
         fg = Color.BLACK;
      }

      if(bg == null) {
         bg = Color.WHITE;
      }

      g.setColor(bg);
      g.fillRect((int) bounds.getX(), (int) bounds.getY(), w, h);
      g.setColor(fg);

      if(obj instanceof ObjectWrapper) {
         obj = ((ObjectWrapper) obj).unwrap();
      }

      if(obj instanceof PresenterPainter) {
         PresenterPainter painter = (PresenterPainter) obj;
         painter.getPresenter().setFont(fmt.getFont());
         Bounds b = new Bounds((int) bounds.getX(), (int) bounds.getY(), w, h);
         Dimension psize = painter.getPresenter().getPreferredSize(
            painter.getObject());
         b = !painter.getPresenter().isFill() ?
            Common.alignCell(b, new Size(psize), fmt.getAlignment()) : b;

         if(painter.getPresenter() instanceof CCWRotatePresenter) {
            Common.paint(g, b.x, b.y, b.width, b.height, painter,
                         -2f, -2f, b.width, b.height, b.width, b.height,
                         fg, fmt.getBackground(), -b.y, -1);
         }
         else {
            Common.paint(g, b.x, b.y, b.width, b.height, painter,
                         0f, 0f, b.width, b.height, b.width, b.height,
                         fg, fmt.getBackground(), -b.y, -1);
         }
      }
      else if(obj instanceof Painter) {
         ((Painter) obj).paint(g,(int) bounds.getX(), (int) bounds.getY(), w, h);
      }
   }

   /**
    * Get cell data's format.
    */
   public static String getCellFormat(XTable lens, int row, int col,
      boolean matches)
   {
      return getCellFormat(lens, row, col, matches, false);
   }

   /**
    * Get cell data's format.
    */
   public static String getCellFormat(XTable lens, int row, int col,
      boolean matches, boolean isForm)
   {
      VSFormatTableLens vsformat = null;
      int c = col;
      int r = row;

      if(lens instanceof TableFilter) {
         XTable filter = lens;

         while(c >= 0) {
            if(filter instanceof VSFormatTableLens) {
               vsformat = (VSFormatTableLens) filter;
               break;
            }
            else if(filter instanceof TableFilter && r > 0 && c > 0) {
               c = ((TableFilter) filter).getBaseColIndex(c);
               r = ((TableFilter) filter).getBaseRowIndex(r);
               filter = ((TableFilter) filter).getTable();
            }
            else {
               break;
            }
         }
      }

      if(vsformat == null) {
         return null;
      }

      Format fmt = vsformat.getCellFormat(r, c);
      Class<?> type = getColType(lens, col);
      Object obj =
         vsformat.getTable().getObject(vsformat.getBaseRowIndex(r), vsformat.getBaseColIndex(c));
      String text = Tool.toString(lens.getObject(row, col));
      String ftmPatt = null;

      if((fmt instanceof NumberFormat) && Tool.isDateClass(type)) {
         return "yyyy-m-d";
      }

      if(fmt instanceof ExtendedDateFormat) {
         ExtendedDateFormat exFmt = (ExtendedDateFormat) fmt;
         List<String> list = exFmt.getExtendedFormats();
         String pattern = exFmt.toPattern();
         boolean equals = false;
         boolean contains = false;

         for(int i = 0; i < list.size(); i++) {
            if(pattern != null && pattern.equals("'" + list.get(i) + "'")) {
               equals = true;

               break;
            }
         }

         equals |= list.contains(pattern);

         if(equals) {
            String format = getNumberFormat(text);

            if(format != null) {
               return format;
            }

            boolean isQuarter = pattern.indexOf("QQQ") > 0;

            return matches ? null : (isQuarter ? "0" : pattern);
         }

         // mm ... shouldn't be contained
         for(int i = 0; i < list.size() - 4; i++) {
            if(pattern != null && pattern.indexOf(list.get(i)) > -1) {
               contains = true;

               break;
            }
         }

         if(contains) {
            boolean isQuarter = exFmt.isQuarter();

            return matches || isQuarter ? null : "yyyy-m-d";
         }

         return pattern.replaceAll("\\ba\\b", "AM/PM");
      }

      if(fmt instanceof ExtendedDecimalFormat) {
         ExtendedDecimalFormat exFmt = (ExtendedDecimalFormat) fmt;
         String pattern = exFmt.toPattern();

         if((obj instanceof Number) && text != null && text.length() > 0) {
            char suffix = text.charAt(text.length() - 1);
            char[] extDataFmt = exFmt.getExtendedDataFormat();

            if(Arrays.binarySearch(extDataFmt, suffix) > -1) {
               return null;
            }
         }
      }

      if(fmt instanceof DurationFormat) {
         return null;
      }

      // fix bug1404983918404, keep the date format
      if(fmt instanceof SimpleDateFormat) {
         SimpleDateFormat sfmt = (SimpleDateFormat) fmt;
         return ExcelUtil.analyze(fmt);
      }

      if(fmt instanceof MessageFormat) {
         return "@";
      }

      if(Time.class.isAssignableFrom(type)) {
         return Tool.DEFAULT_TIME_PATTERN;
      }
      else if(Timestamp.class.isAssignableFrom(type)) {
         return Tool.DEFAULT_DATETIME_PATTERN;
      }
      else if(java.sql.Date.class.isAssignableFrom(type)) {
         return Tool.DEFAULT_DATE_PATTERN;
      }
      else if(Tool.isStringClass(type)) {
         // if string type, we should set text format in excel, but if format is
         // decimal format, we should use its original format.
         if(fmt instanceof DecimalFormat && obj instanceof Number) {
            return ExcelUtil.analyze(fmt);
         }

         return "@";
      }

      if(fmt == null && !isForm) {
         String format = getNumberFormat(text);

         if(format != null) {
            int n = text.contains(".") ? text.indexOf('.') : text.length();
            // Bug #58461, Excel changes any digit values past 15 to zero.
            // To prevent loss of information, switch the format to text.
            if(n > 15) {
               return "@";
            }

            return format;
         }
      }

      ftmPatt = ExcelUtil.analyze(fmt);

      return "".equals(ftmPatt) ? "General" : ftmPatt;
   }

   /**
    * Get current cell value.
    */
   public static Object getObject(XTable lens, int row, int col, String fmt, boolean excel) {
      Object obj0 = lens.getObject(row, col);

      if(obj0 instanceof PresenterPainter &&
         (!excel || !((PresenterPainter) obj0).isExportedValue()) ||
         obj0 instanceof Image)
      {
         return obj0;
      }

      Object obj = obj0;
      VSFormatTableLens vsfmt = null;
      boolean isDate = obj instanceof Date;

      // find raw data
      if(fmt != null) {
         int r = row, c = col;
         XTable t = lens;

         while(t instanceof TableFilter) {
            TableFilter filter = (TableFilter) t;
            r = filter.getBaseRowIndex(r);
            c = filter.getBaseColIndex(c);
            t = filter.getTable();

            if(t instanceof VSFormatTableLens) {
               vsfmt = (VSFormatTableLens) t;
               obj = vsfmt.getTable().getObject(r, c);
               break;
            }
         }
      }
      else {
         VSFormatTableLens nestedTable =
            (VSFormatTableLens) Util.getNestedTable(lens, VSFormatTableLens.class);

         if(nestedTable != null) {
            Format cellFormat = nestedTable.getCellFormat(row, col);

            if(excel && cellFormat instanceof ExtendedDecimalFormat &&
               ((ExtendedDecimalFormat) cellFormat).getExtendedDataSymbol() != ' ')
            {
               obj = nestedTable.getTable().getObject(row, col);
            }
         }
      }

      if(isNumberDateFormat(vsfmt, row, col, fmt)) {
         if("0.0".equals(fmt)) {
            return Double.valueOf(obj0.toString());
         }
         else {
            return Integer.valueOf(obj0.toString());
         }
      }
      else if(vsfmt != null) {
         Format cellFormat = vsfmt.getCellFormat(row, col);

         if(cellFormat instanceof DateFormat) {
            // @by stephenwebster, For Bug #25543
            // Use formatted value instead of number value since the date value will be lost
            if(obj instanceof Number) {
               obj = obj0;
            }
            // if format is date format and obj is date, format it so it appears
            // same as on ui and xls. (43834)
            else if(!excel && obj instanceof Date) {
               obj = cellFormat.format(obj);
            }
         }
         else if(cellFormat instanceof DurationFormat) {
            return obj0;
         }
         else if(excel && cellFormat instanceof ExtendedDecimalFormat &&
            ((ExtendedDecimalFormat) cellFormat).getExtendedDataSymbol() != ' ')
         {
            obj = vsfmt.getTable().getObject(row, col);
         }
      }

      Class<?> type = getColType(lens, col);

      if(Tool.isNumberClass(type) && (obj instanceof String) && !isDate) {
         if(Double.class.isAssignableFrom(type)) {
            try {
               return Double.valueOf(obj.toString());
            }
            catch(NumberFormatException ex) {
            }
         }
         else {
            try {
               return Integer.valueOf(obj.toString());
            }
            catch(NumberFormatException ex) {
            }
         }
      }

      return obj;
   }

   /*
    * Bug #59833, the VSTableLens created by AbstractVSExporter loses the trailer row count from
    * the underlying crosstab. This causes Util.getColType() to return the incorrect type in this
    * case because it doesn't ignore the trailer row and use the data type of the total label. We
    * can't just set the trailer row count on the VSTableLens because that may change the styling.
    */
   private static Class<?> getColType(XTable table, int col) {
      if(table instanceof TableFilter) {
         XTable filter = table;
         int c = col;

         while(true) {
            if(filter instanceof RuntimeCalcTableLens) {
               RuntimeCalcTableLens runtime = (RuntimeCalcTableLens) filter;
               CalcTableLens calc = runtime.getCalcTableLens();

               if(calc == null) {
                  break;
               }

               TableLens script = calc.getScriptTable();

               if(script == null) {
                  break;
               }

               int trailerRowCount = runtime.getTrailerRowCount();
               boolean setTrailer = script.getTrailerRowCount() > trailerRowCount;

               if(setTrailer) {
                  runtime.setTrailerRowCount(script.getTrailerRowCount());
               }

               Class<?> type = runtime.getColType(c);

               if(setTrailer) {
                  runtime.setTrailerColCount(trailerRowCount);
               }

               return type;
            }
            else if(filter instanceof TableFilter) {
               c = ((TableFilter) filter).getBaseColIndex(c);

               if(c < 0) {
                  break;
               }

               filter = ((TableFilter) filter).getTable();
            }
            else {
               break;
            }
         }
      }

      return table.getColType(col);
   }

   /**
    * Check whether current cell is a date cell but display as a number.
    */
   private static boolean isNumberDateFormat(
      VSFormatTableLens lens, int row, int col, String format)
   {
      if(lens == null || (!"0.0".equals(format) && !"0".equals(format))) {
         return false;
      }

      Object obj = lens.getObject(row, col);
      Format fmt = lens.getCellFormat(row, col);

      if(!(obj instanceof String) || !(fmt instanceof ExtendedDateFormat)) {
         return false;
      }

      return getNumberFormat((String) obj) != null;
   }

   /**
    * Get number format if the text is a number.
    */
   private static String getNumberFormat(String text) {
      try {
         Long.parseLong(text);

         return "0";
      }
      catch(NumberFormatException ex) {
      }

      try {
         Double.parseDouble(text);

         return "0.0";
      }
      catch(NumberFormatException ex) {
      }

      return null;
   }

   /**
    * Get the table cell hyperlink.
    */
   public static Hyperlink.Ref getTableCellHyperLink(VSTableLens lens, int r, int c,
                                                     ColumnIndexMap columnIndexMap)
   {
      Hyperlink.Ref hyperlink = null;
      TableLens table;

      if(lens instanceof RegionTableLens) {
         if(lens.getTable() instanceof ColumnMapFilter) {
            // Bug #43321, when hidden columns are present, they are removed by using a column map
            // filter
            ColumnMapFilter columnMapFilter = (ColumnMapFilter) lens.getTable();
            r = columnMapFilter.getBaseRowIndex(r);
            c = columnMapFilter.getBaseColIndex(c);
            table = ((VSTableLens) columnMapFilter.getTable()).getTable();
         }
         else {
            table = ((VSTableLens) lens.getTable()).getTable();
         }
      }
      else {
         table = lens.getTable();
      }

      if(table instanceof AttributeTableLens) {
         hyperlink = ((AttributeTableLens) table).getHyperlink(r, c);

         if(hyperlink != null) {
            if(hyperlink.isSendSelectionParameters()) {
               VSUtil.addSelectionParameter(hyperlink, lens.getLinkSelections());
            }

            if(hyperlink.getLinkType() == Hyperlink.WEB_LINK) {
               return hyperlink;
            }
         }
      }

      XDrillInfo dinfo = lens.getTable().getXDrillInfo(r, c);
      TableLens dataTable = lens.getTable() instanceof TableFilter ?
         Util.getDataTable((TableFilter) lens.getTable()) : lens.getTable();

      if(columnIndexMap == null) {
         columnIndexMap = new ColumnIndexMap(dataTable, true);
      }

      for(int i = 0; dinfo != null && i < dinfo.getDrillPathCount(); i++) {
         DrillPath dpath = dinfo.getDrillPath(i);
         DrillSubQuery query = dpath.getQuery();
         hyperlink = new Hyperlink.Ref(dpath, dataTable, r, c);

         if(query != null) {
            hyperlink.setParameter(StyleConstants.SUB_QUERY_PARAM,
                                   dataTable.getObject(r, c));
            String tableHeader = dataTable.getColumnIdentifier(c);
            tableHeader = tableHeader == null ?
               (String) Util.getHeader(dataTable, c) : tableHeader;
            String queryParam = Util.findSubqueryVariable(query, tableHeader);

            if(queryParam != null) {
               hyperlink.setParameter(StyleConstants.SUB_QUERY_PARAM_PREFIX + queryParam,
                  dataTable.getObject(r, c));
            }

            Iterator<String> it = query.getParameterNames();

            while(it.hasNext()) {
               String qvar = it.next();

               if(Tool.equals(qvar, queryParam)) {
                  continue;
               }

               String header = query.getParameter(qvar);
               int col = Util.findColumn(columnIndexMap, header);

               if(col < 0) {
                  continue;
               }

               hyperlink.setParameter(StyleConstants.SUB_QUERY_PARAM_PREFIX + qvar,
                  dataTable.getObject(r, col));
            }
         }

         if(hyperlink.getLinkType() == Hyperlink.WEB_LINK) {
            return hyperlink;
         }
      }

      return hyperlink;
   }

   public static String getSuffix(int format) {
      switch(format) {
         case FileFormatInfo.EXPORT_TYPE_EXCEL:
            return "xlsx";
         case FileFormatInfo.EXPORT_TYPE_POWERPOINT:
            return "pptx";
         case FileFormatInfo.EXPORT_TYPE_PDF:
            return "pdf";
         case FileFormatInfo.EXPORT_TYPE_SNAPSHOT:
            return "vso";
         case FileFormatInfo.EXPORT_TYPE_PNG:
            return "png";
         case FileFormatInfo.EXPORT_TYPE_HTML:
            return "html";
         case FileFormatInfo.EXPORT_TYPE_CSV:
            return "zip";
         default:
            throw new RuntimeException("Unsupport file format: " + format) ;
      }
   }

   public static int getHorizontalAlignment(int textAlign) {
      switch(textAlign) {
         case StyleConstants.H_CENTER:
            return SwingConstants.CENTER;
         case StyleConstants.H_LEFT:
            return SwingConstants.LEFT;
         case StyleConstants.H_RIGHT:
            return SwingConstants.RIGHT;
         default:
            return SwingConstants.LEFT;
      }
   }

   public static int getVerticalAlignment(int align) {
      //56 is 111000 in binary which is all possible vertical alignment values
      switch(align & 56) {
         case StyleConstants.V_TOP:
            return SwingConstants.TOP;
         case StyleConstants.V_CENTER:
            return SwingConstants.CENTER;
         case StyleConstants.V_BOTTOM:
            return SwingConstants.BOTTOM;
         default:
            return SwingConstants.TOP;
      }
   }

   /**
    * Draw Borders on the Graphics.
    */
   public static void drawBorders(Graphics g, Point point, Dimension size,
      BorderColors borderColors, Insets insets, int borderRadius)
   {
      int x = point.x;
      int y = point.y;
      float leftBorderWidth = Common.getLineWidth(insets.left);
      float rightBorderWidth = Common.getLineWidth(insets.right);
      float topBorderWidth = Common.getLineWidth(insets.top);
      float bottomBorderWidth = Common.getLineWidth(insets.bottom);
      int fixedLeftDot = leftBorderWidth == 3 ? 1 : 0;
      int fixedRightDot = rightBorderWidth == 3 ? 1 : 0;
      int fixedTopDot = topBorderWidth == 3 ? 1 : 0;
      int fixedBottomDot = bottomBorderWidth == 3 ? 1 : 0;
      float rightBorderPos =
         x + size.width + Math.max(rightBorderWidth - fixedRightDot, 1);
      float bottomBorderPos =
         y + size.height + Math.max(bottomBorderWidth - fixedBottomDot, 1);
      boolean isNoneBorder = leftBorderWidth == 0 && rightBorderWidth == 0 &&
         topBorderWidth == 0 && bottomBorderWidth == 0;

      // if set round borders, maybe it will have different border in top/bottom/left/right.
      // draw round rect 4 times with clip set to get a better looking round border
      if(borderRadius > 0 && !isNoneBorder) {
         Shape oldClip = g.getClip();

         // border rectangle
         Rectangle2D rect = new Rectangle2D.Float();
         rect.setRect(x + fixedLeftDot, y + fixedTopDot,
                      size.width - fixedLeftDot - fixedRightDot,
                      size.height - fixedTopDot - fixedBottomDot);

         // larger bounds in case the borders spill over
         Rectangle clipBounds = new Rectangle((int) (rect.getX() - leftBorderWidth),
                                              (int) (rect.getY() - topBorderWidth),
                                              (int) (rect.getWidth() + rightBorderWidth + leftBorderWidth),
                                              (int) (rect.getHeight() + bottomBorderWidth + topBorderWidth));
         int borderRadiusWidth = (int) Math.ceil(borderRadius / 2d);

         // left
         g.setClip(clipBounds.x, clipBounds.y, (int) (leftBorderWidth + borderRadiusWidth),
                   clipBounds.height);
         drawRoundRect(g, rect, borderRadius, insets.left, borderColors.leftColor);

         // right
         g.setClip((int) (clipBounds.getMaxX() - rightBorderWidth - borderRadiusWidth),
                   clipBounds.y, (int) (rightBorderWidth + borderRadiusWidth), clipBounds.height);
         drawRoundRect(g, rect, borderRadius, insets.right, borderColors.rightColor);

         // top
         g.setClip(clipBounds.x, clipBounds.y, clipBounds.width,
                   (int) (topBorderWidth + borderRadiusWidth));
         drawRoundRect(g, rect, borderRadius, insets.top, borderColors.topColor);

         // bottom
         g.setClip(clipBounds.x, (int) (clipBounds.getMaxY() - bottomBorderWidth - borderRadiusWidth),
                   clipBounds.width, (int) (bottomBorderWidth + borderRadiusWidth));
         drawRoundRect(g, rect, borderRadius, insets.bottom, borderColors.bottomColor);

         // revert to old clip
         g.setClip(oldClip);
      }
      else if(borderRadius <= 0) {
         if(borderColors.topColor != null) {
            g.setColor(borderColors.topColor);
            Common.drawHLine(g, y - fixedTopDot, x - fixedLeftDot,
                             rightBorderPos, insets.top, 0, 0);
         }

         if(borderColors.bottomColor != null) {
            g.setColor(borderColors.bottomColor);
            Common.drawHLine(g, y + size.height - fixedBottomDot, x - fixedLeftDot,
                             rightBorderPos, insets.bottom, 0, 0);
         }

         if(borderColors.leftColor != null) {
            g.setColor(borderColors.leftColor);
            Common.drawVLine(g, x - fixedLeftDot, y - fixedTopDot,
                             bottomBorderPos, insets.left, 0, 0);
         }

         if(borderColors.rightColor != null) {
            g.setColor(borderColors.rightColor);
            Common.drawVLine(g, x + size.width - fixedRightDot, y - fixedTopDot,
                             bottomBorderPos, insets.right, 0, 0);
         }
      }
   }

   /**
    * Draw backgroud on the Graphics.
    */
   public static void drawBackground(Graphics g, Point point, Dimension size,
      Color background, int borderRadius)
   {
      int x = point.x;
      int y = point.y;

      if(background == null) {
         return;
      }

      g.setColor(background);

      if(borderRadius > 0) {
         g.fillRoundRect(x, y, size.width, size.height, borderRadius * 2, borderRadius * 2);
      }
      else {
         g.fillRect(x, y, size.width + 1, size.height + 1);
      }
   }

   /**
    * Check the chart assembly should expand or not.
    */
   public static boolean needExpandChart(VGraphPair pair) {
      if(pair != null) {
         VGraph vgraph = pair.getRealSizeVGraph();
         VGraph evgraph = pair.getExpandedVGraph();
         Rectangle2D vpb = null;
         Rectangle2D evpb = null;

         if(vgraph != null) {
            vpb = vgraph.getPlotBounds();
         }

         if(evgraph != null) {
            evpb = evgraph.getPlotBounds();
         }

         if(vpb != null && evpb != null) {
            return vpb.getHeight() * 2 < evpb.getHeight() || vpb.getWidth() * 2 < evpb.getWidth();
         }
      }

      return false;
   }

   public static boolean annotationIsOuterTable(Assembly base, AnnotationVSAssemblyInfo info,
                                          CoordinateHelper helper)
   {
      if(!(base instanceof TableDataVSAssembly)) {
         return false;
      }

      TableDataVSAssemblyInfo tinfo = (TableDataVSAssemblyInfo) base.getInfo();
      Rectangle2D bounds = helper.getBounds(info);
      Rectangle2D tbounds = helper.getBounds(tinfo);

      if(bounds.getX() > tbounds.getX() + tbounds.getWidth() ||
         bounds.getY() > tbounds.getY() + tbounds.getHeight())
      {
         return true;
      }

      return false;
   }

   private static void drawRoundRect(Graphics g, Rectangle2D rect, int borderRadius,
                              int borderStyle, Color borderColor)
   {
      if(borderStyle == StyleConstants.NO_BORDER || borderColor == null) {
         return;
      }

      g.setColor(borderColor);
      Stroke oldStroke = null;

      if(g instanceof Graphics2D) {
         oldStroke = ((Graphics2D) g).getStroke();
         Stroke stroke = GTool.getStroke(borderStyle);
         ((Graphics2D) g).setStroke(stroke);
      }

      g.drawRoundRect((int) rect.getX(), (int) rect.getY(), (int) rect.getWidth(),
                      (int) rect.getHeight(), borderRadius * 2, borderRadius * 2);

      if(g instanceof Graphics2D) {
         ((Graphics2D) g).setStroke(oldStroke);
      }
   }
}
