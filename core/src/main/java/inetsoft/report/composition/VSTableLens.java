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
package inetsoft.report.composition;

import inetsoft.report.*;
import inetsoft.report.composition.execution.CrosstabVSAQuery;
import inetsoft.report.composition.execution.VSFormatTableLens;
import inetsoft.report.filter.*;
import inetsoft.report.internal.*;
import inetsoft.report.internal.table.*;
import inetsoft.report.internal.table.TableHighlightAttr.HighlightTableLens;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.painter.HTMLPresenter;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.MessageFormat;
import inetsoft.util.*;
import inetsoft.util.css.CSSTableStyle;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.*;
import java.text.*;
import java.util.List;
import java.util.*;

import static inetsoft.uql.asset.internal.AssetUtil.defh;

/**
 * VSTableLens, the viewsheet table lens, it is an xml serializable object to
 * write span/format/table_data_path to client side application.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class VSTableLens extends DefaultTableFilter implements XMLSerializable, DataSerializable {
   /**
    * Create a viewsheet table lens.
    */
   public VSTableLens(TableLens table) {
      super(table);
      underline = "true".equals(SreeEnv.getProperty("hyperlink.indicator"));
      initSpanTable();
   }

   @Override
   public void setTable(TableLens table) {
      super.setTable(table);
      formTable = null;
      highlightTable = null;
      calcTable = null;
   }

   /**
    * Set the crosstab tree.
    */
   public void setCrosstabTree(CrosstabTree ctree) {
      this.ctree = ctree;
   }

   /**
    * Limit the number of columns.
    */
   public void setMaxCols(int maxCols) {
      this.maxCols = maxCols;
   }

   @Override
   public int getColCount() {
      if(maxCols > 0) {
         return Math.min(maxCols, super.getColCount());
      }

      return super.getColCount();
   }

   public VSFormat getFormat(int r, int c) {
      return getFormat(r, c, -1);
   }

   /**
    * Get the format at a cell.
    * @param r the specified row.
    * @param c the specified column.
    * @param spanRow row index of the specified span
    * @return the format at the specified cell.
    */
   public VSFormat getFormat(int r, int c, int spanRow) {
      VSFormat userfmt = new VSFormat();
      Insets borders = new Insets(0, 0, 0, 0);
      Rectangle span = getSpanMap(0, r + 1).get(r, c);
      int bottom = r;
      int right = c;

      if(span != null) {
         // #23235, in 12.2, we take the bottom border of spanned cell in body, while take the
         // bottom border of header without span. this seems inconsistent but it's what
         // 12.2 does

         if(r >= getHeaderRowCount()) {
            bottom += span.height - 1;
         }

         if(c >= getHeaderColCount()) {
            right += span.width - 1;
         }
      }

      userfmt.shrink();
      borders.top = getRowBorder(r - 1, c);
      borders.bottom = getRowBorder(bottom, c);
      borders.left = getColBorder(r, c - 1);
      borders.right = getColBorder(r, right);

      // for VStable, the double borders between cells are not shared,
      // so set left/top borders to thin line
      // @by robertwang, fix bug1370310140404
      if(r != 0 && borders.top == StyleConstants.DOUBLE_LINE) {
         borders.top = StyleConstants.THIN_LINE;
      }

      if(c != 0 && borders.left == StyleConstants.DOUBLE_LINE) {
         borders.left = StyleConstants.THIN_LINE;
      }

      userfmt.setBorders(borders);

      BorderColors bcolors = new BorderColors();

      bcolors.topColor = getRowBorderColor(r - 1, c);
      bcolors.bottomColor = getRowBorderColor(bottom, c);
      bcolors.leftColor = getColBorderColor(r, c - 1);
      bcolors.rightColor = getColBorderColor(r, right);
      userfmt.setBorderColors(bcolors);

      userfmt.setAlignment(getAlignment(r, c));
      userfmt.setFont(getFont(r, c));
      userfmt.setWrapping(isLineWrap(r, c));
      userfmt.setBackground(spanRow == -1 || spanRow == r ?
                               getBackground(r, c) : getBackground(r, c, spanRow));
      userfmt.setForeground(getForeground(r, c));
      setFormat(userfmt, r, c);

      int alpha = getAlpha(r, c);

      if(alpha >= 0) {
         userfmt.setAlpha(alpha);
      }
      else {
         TableLens table = getTable();

         do {
            if(table instanceof VSFormatTableLens) {
               userfmt.setAlpha(table.getAlpha(r, c));
               break;
            }

            if(table instanceof TableFilter) {
               table = ((TableFilter) table).getTable();
            }
         }
         while(table instanceof TableFilter);
      }

      return userfmt;
   }

   private void setFormat(VSFormat userfmt, int row, int col) {
      Format fmt = getDefaultFormat(row, col);

      if(fmt == null) {
         return;
      }

      String format = null;
      String format_spec = null;

      if(fmt instanceof SimpleDateFormat) {
         format = DATE_FORMAT;
         format_spec = ((SimpleDateFormat) fmt).toPattern();
      }
      else if(fmt.equals(NumberFormat.getCurrencyInstance())) {
         format = CURRENCY_FORMAT;
      }
      else if(fmt.equals(NumberFormat.getPercentInstance())) {
         format = PERCENT_FORMAT;
      }
      else if(fmt instanceof DecimalFormat) {
         format = DECIMAL_FORMAT;
         format_spec = ((DecimalFormat) fmt).toPattern();
      }
      else if(fmt instanceof java.text.MessageFormat) {
         format = MESSAGE_FORMAT;
         format_spec = ((java.text.MessageFormat) fmt).toPattern();
      }
      else if(fmt instanceof MessageFormat) {
         format = MESSAGE_FORMAT;
         format_spec = ((MessageFormat) fmt).toPattern();
      }
      else if(fmt instanceof DurationFormat) {
         format = ((DurationFormat) fmt).getFormatType();
         format_spec = ((DurationFormat) fmt).toPattern();
      }

      userfmt.setFormatValue(format, false);
      userfmt.setFormatExtentValue(format_spec, false);
   }

   /**
    * Get the table data path at a cell.
    * @param row the specified row value.
    * @param col the specified col value.
    * @return the table data path at the specified cell.
    */
   public TableDataPath getTableDataPath(int row, int col) {
      TableDataDescriptor desc = getDescriptor();
      return desc.getCellDataPath(row, col);
   }

   /**
    * Get the drilling operation for the cell.
    */
   public String getDrillOp(int row, int col) {
      return (ctree == null) ? "" : ctree.getDrillOp(this, row, col);
   }

   /**
    * Get the text at a cell.
    * @param r the specified row.
    * @param c the spefified column.
    * @return the text at the specified cell. Format is applied.
    */
   public String getText(int r, int c) {
      Object obj = getObject(r, c);
      return Tool.toString(obj);
   }

   /**
    * Get the value at a cell.
    * @param r the specified row.
    * @param c the spefified column.
    * @return the value at the specified cell.
    */
   public String getValue(int r, int c) {
      FormTableLens lens = getFormTableLens();
      return lens == null ? null : Tool.toString(lens.getObject(r, c));
   }

   /**
    * Get the start point to write xml.
    * @return the start point.
    */
   public int getStart() {
      return start;
   }

   /**
    * Set the start point to write xml.
    * @param start the specified start point.
    */
   public void setStart(int start) {
      this.start = start;
   }

   /**
    * Get the row count to write xml.
    * @return the row count.
    */
   public int getCount() {
      return count;
   }

   /**
    * Set the row count to write xml.
    * @param count the specified row count.
    */
   public void setCount(int count) {
      this.count = count;
   }

   /**
    * Make sure the span map is initialized up until this region.
    */
   private synchronized void initSpanMap(int start, int count) {
      for(int r = spanInited;  r < start + count && moreRows(r); r++) {
         for(int c = 0; c < getColCount(); c++) {
            Dimension span = getSpan(r, c);

            if(span != null) {
               spanmap.add(r, c, span.height, span.width);
            }
         }
      }

      spanInited = start + count;
   }

   /**
    * Returns a copy of this VSTableLens' spanmap
    * @param start the starting row
    * @param count the number of rows to retrieve
    * @return A new SpanMap
    */
   public SpanMap getSpanMap(int start, int count) {
      if(start + count > spanInited) {
         initSpanMap(start, count);
      }

      return spanmap;
   }

   /**
    * Get column widths.
    */
   public int[] getColumnWidths() {
      return widths;
   }

   /**
    * Set each column width.
    * @param width the specified column.
    */
   public void setColumnWidths(int[] width) {
      this.widths = width != null ? width : new int[0];
   }

   /**
    * Get row heights.
    */
   public int[] getRowHeights() {
      return rows;
   }

   /**
    * Set each grid row height.
    * @param row the specified row.
    */
   public void setRowHeights(int[] row) {
      this.rows = row != null ? row : new int[0];
   }

   /**
    * Set the uri.
    * @param uri the specified service request uri.
    */
   public void setLinkURI(String uri) {
      this.luri = uri;
   }

   /**
    * Get the specified service request uri.
    * @return the uri.
    */
   public String getLinkURI() {
      return luri;
   }

   /**
    * Get varaible table.
    * @retrun the variable table.
    */
   public VariableTable getLinkVarTable() {
      return linkVarTable;
   }

   /**
    * Set varaible table.
    * @param vtable the variable table.
    */
   public void setLinkVarTable(VariableTable vtable) {
      this.linkVarTable = vtable;
   }

   /**
    * Set selections.
    */
   public void setLinkSelections(Hashtable sel) {
      selections = sel;
   }

   /**
    * Get selections.
    * @return selection assembly map.
    */
   public Hashtable getLinkSelections() {
      return selections;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      writeData(output, start, count);
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @param start the start position of the table lens.
    * @param count0 the count of the table lens to be written.
    */
   public void writeData(DataOutputStream output, int start, int count0)
         throws IOException
   {
      synchronized(getTable()) {
         writeData0(output, start, count0);
      }
   }

   /**
    * Different instances of VSTableLens may wrap the same table. Some table
    * (e.g. RuntimeCalcTableLens) may not be thread safe.
    */
   private void writeData0(DataOutputStream output, int start, int count0)
         throws IOException
   {
      int count = count0;
      initSpanMap(start, count);

      // process over but rows not enough?
      if(!moreRows(start + count)) {
         int total = getRowCount();

         if(total < 0) {
            total = -(total + 1);
         }

         count = Math.max(0, total - start);
      }

      if(getColCount() == 0) {
         count = 0;
      }

      fmtmap.clear();
      pathmap.clear();
      hmap.clear();

      if(getFormatTable() != null) {
         getFormatTable().clearCellCache();
      }

      initFormTableFormat();
      output.writeInt(getColCount());
      output.writeInt(count);
      output.writeInt(start);

      // if start == 0, write column identifier, so column identifier
      // only exist in the first data block, this is used for VSTable.as'
      // sync column selection logic
      if(start == 0) {
         for(int i = 0; i < getColCount(); i++) {
            String identifier = getColumnIdentifier(i);
            output.writeBoolean(identifier == null);

            if(identifier != null) {
               output.writeUTF(identifier);
            }
         }
      }

      ArrayList<Cell> list = new ArrayList<>();
      FormTableLens lens = getFormTableLens();

      for(int n = 0, r = start; moreRows(r) && n < count + 1; n++, r++) {
         for(int c = 0; c < getColCount(); c++) {
            Cell cell = new Cell();
            cell.setRow(r);
            cell.setCol(c);

            if(lens != null) {
               cell.setLabel(lens.getLabel(r, c));
               cell.setCellEditable(
                  FormTableRow.ADDED == lens.rows()[r].getRowState() ?
                  lens.getColumnOption(c).isForm() :
                  lens.isEdit() && lens.getColumnOption(c).isForm());
            }

            list.add(cell);
         }
      }

      for(int j = start; j < start + count; j++) {
         output.writeInt(getLineCount(j));
      }

      int len = list.size();
      output.writeInt(len);

      for(Cell cell : list) {
         cell.writeData(output);
      }

      // write shared VSFormat and table data path
      output.writeInt(fmtmap.size());

      for(Map.Entry<VSFormat, Integer> entry : fmtmap.entrySet()) {
         VSCompositeFormat fmt = new VSCompositeFormat();
         fmt.setUserDefinedFormat(entry.getKey());
         output.writeInt(entry.getValue());
         fmt.writeData(output);
      }

      output.writeInt(pathmap.size());

      for(Map.Entry<TableDataPath , Integer> entry : pathmap.entrySet()) {
         output.writeInt(entry.getValue());
         entry.getKey().writeData(output);
      }

      output.writeBoolean(isHyperlinkEnabled());

      // write shared Hyperlink.Ref
      if(isHyperlinkEnabled()) {
         output.writeInt(hmap.size());

         for(Map.Entry<Hyperlink.Ref[], Integer> entry : hmap.entrySet()) {
            Hyperlink.Ref[] refs = entry.getKey();
            output.writeInt(entry.getValue());
            output.writeInt(refs.length);

            for(Hyperlink.Ref ref : refs) {
               String cmd = XUtil.getCommand(ref, getLinkURI());
               output.writeUTF(ref.getName());
               output.writeUTF(cmd);
               String tooltip = ref.getToolTip();
               output.writeBoolean(tooltip == null);

               if(tooltip != null) {
                  output.writeUTF(tooltip);
               }
            }
         }
      }

      FormatInfo finfo = getFormatInfo();
      output.writeBoolean(finfo == null);

      if(finfo != null) {
         finfo.writeData(output);
         finfo.reset();
      }

      int[] bottomBorders = getBottomBorders();
      Color[] bottomBorderColors = getBottomBorderColors();

      for(int i = 0; i < bottomBorders.length; i++) {
         output.writeInt(bottomBorders[i]);
         output.writeInt(bottomBorderColors[i].getRGB());
      }
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @retrun <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      return true;
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Init form table format.
    */
   private void initFormTableFormat() {
      VSFormatTableLens fmtLens = getFormatTable();
      FormTableLens formLens = getFormTableLens();

      if(fmtLens == null || formLens == null || formLens.getColCount() <= 0) {
         return;
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public synchronized void writeXML(PrintWriter writer) {
      Cell cell = new Cell();
      int count = this.count;

      // process over but rows not enough?
      if(!moreRows(start + count)) {
         int total = getRowCount();
         count = Math.max(0, total - start);
      }

      if(getColCount() == 0) {
         count = 0;
      }

      fmtmap.clear();
      pathmap.clear();
      hmap.clear();
      initFormTableFormat();

      writer.println("<VSTableLens start=\"" + start + "\"" + " col=\"" +
                     getColCount() + "\" row=\"" + count + "\" class=\"" +
                     getClass().getName() + "\">");

      if(start == 0) {
         for(int i = 0; i < getColCount(); i++) {
            String identifier = getColumnIdentifier(i);

            if(identifier == null) {
               writer.println("<identifier isNull=\"true\"/>");
            }
            else {
               writer.println("<identifier value=\"" + identifier + "\"/>");
            }
         }
      }

      int r = start;
      FormTableLens lens = getFormTableLens();

      while(moreRows(r)) {
         for(int c = 0; c < getColCount(); c++) {
            cell.setRow(r);
            cell.setCol(c);

            if(lens != null) {
               cell.setLabel(lens.getLabel(r, c));
            }

            cell.writeXML(writer);
         }

         r++;
      }

      // write shared vsCompositeFormat and table data path
      for(Map.Entry<VSFormat, Integer> entry : fmtmap.entrySet()) {
         VSCompositeFormat fmt = new VSCompositeFormat();
         fmt.setUserDefinedFormat(entry.getKey());

         writer.println("<cellFormat index=\"" + entry.getValue() + "\">");
         fmt.writeXML(writer);
         writer.println("</cellFormat>");
      }

      for(Map.Entry<TableDataPath , Integer> entry : pathmap.entrySet()) {
         writer.println("<cellPath index=\"" + entry.getValue() + "\">");
         entry.getKey().writeXML(writer);
         writer.println("</cellPath>");
      }

      // write shared Hyperlink.Ref
      if(isHyperlinkEnabled()) {
         for(Map.Entry<Hyperlink.Ref[], Integer> entry : hmap.entrySet()) {
            Hyperlink.Ref[] refs = entry.getKey();
            writer.println("<cellHyperlink index=\"" + entry.getValue() + "\">");
            writer.println("<hyperlinks>");

            for(Hyperlink.Ref ref : refs) {
               writer.println("<hyperlink>");
               writer.println("<name>");
               writer.println("<![CDATA[" + Tool.byteEncode2(ref.getName()) + "]]>");
               writer.println("</name>");
               String cmd = XUtil.getCommand(ref, getLinkURI());
               writer.println("<value>");
               writer.println("<![CDATA[" + Tool.byteEncode2(cmd) + "]]>");
               writer.println("</value>");
               String tooltip = ref.getToolTip();

               if(tooltip != null) {
                  writer.print("<tooltip>");
                  writer.println("<![CDATA[" + Tool.byteEncode2(tooltip) + "]]>");
                  writer.print("</tooltip>");
               }

               writer.println("</hyperlink>");
            }

            writer.println("</hyperlinks>");
            writer.println("</cellHyperlink>");
         }
      }

      FormatInfo finfo = getFormatInfo();

      if(finfo != null) {
         finfo.writeXML(writer);
         finfo.reset();
      }

      int[] bottomBorders = getBottomBorders();
      Color[] bottomBorderColors = getBottomBorderColors();

      for(int i = 0; i < bottomBorders.length; i++) {
         writer.println("<bottomBorder style=\"" + bottomBorders[i] +
                        "\" color=\"" + bottomBorderColors[i].getRGB() +
                        "\"/>");
      }

      writer.println("</VSTableLens>");
   }

   /**
    * Get the index of the shared format.
    */
   private synchronized int getFormatIndex(VSFormat fmt) {
      return fmtmap.computeIfAbsent(fmt, k -> fmtmap.size());
   }

   /**
    * Get the index of the shared path.
    */
   private synchronized int getTableDataPathIndex(TableDataPath path) {
      return pathmap.computeIfAbsent(path, k -> pathmap.size());
   }

   /**
    * Get the index of the hyperlinks.
    */
   private synchronized int getHyperlinksIndex(Hyperlink.Ref[] refs) {
      return hmap.computeIfAbsent(refs, k -> hmap.size());
   }

   /**
    * Get the initialized vsformats in VSFormatTableLens.
    */
   private FormatInfo getFormatInfo() {
      VSFormatTableLens lens = getFormatTable();
      return lens == null ? null : lens.getFormatInfo();
   }

   /**
    * Get the format table.
    */
   private VSFormatTableLens getFormatTable() {
      TableLens table = getTable();

      while(table != null) {
         if(table instanceof VSFormatTableLens) {
            return((VSFormatTableLens) table);
         }

         table = (table instanceof TableFilter) ?
            ((TableFilter) table).getTable() : null;
      }

      return null;
   }

   /**
    * Get crosstab filter.
    */
   private void initSpanTable() {
      TableLens table = getTable();

      while(table != null) {
         if(table instanceof HiddenRowColFilter ||
            table instanceof CrossFilter ||
            table instanceof CrosstabVSAQuery.CrossTabSortFilter)
         {
            spanTable = table;
            break;
         }

         table = (table instanceof TableFilter) ?
            ((TableFilter) table).getTable() : null;
      }
   }

   /**
    * Return the spanning setting for the cell. If the specified cell
    * is not a spanning cell, it returns null. Otherwise it returns
    * a Dimension object with Dimension.width equals to the number
    * of columns and Dimension.height equals to the number of rows
    * of the spanning cell.
    * @param r row number.
    * @param c column number.
    * @return span cell rectangle.
    */
   public Rectangle getVSSpan(int r, int c) {
      if(spanTable instanceof CrossTabFilter) {
         return ((CrossTabFilter) spanTable).getVSSpan(r, c);
      }
      else if(spanTable instanceof CrosstabVSAQuery.CrossTabSortFilter) {
         return ((CrosstabVSAQuery.CrossTabSortFilter) spanTable).getVSSpan(r, c);
      }

      return null;
   }

   private String findIdentifierForSubQuery(TableLens lens, String key) {
      if(key == null) {
         return null;
      }

      if(idenMap != null && idenMap.containsKey(key)) {
         return idenMap.get(key);
      }

      if(idenMap == null) {
         idenMap = new HashMap<>();
      }

      String iden = Util.findIdentifierForSubQuery(lens, key);
      idenMap.put(key, iden);
      return iden;
   }

   /**
    *  Get all hyperlink for the specified cell.
    */
   public List<Hyperlink.Ref> getCellHyperlink(TableLens table, int r, int c) {
      List<Hyperlink.Ref> hyperLinkRefs = new ArrayList<>(); // keep all hyperlinks
      addHyperlink(table, hyperLinkRefs, r, c);
      addDrillInfo(table, hyperLinkRefs, r, c);

      return hyperLinkRefs;
   }

   /**
    * Get hyperlink for the specified cell.
    */
   private void addHyperlink(TableLens table, List<Hyperlink.Ref> vec, int r, int c) {
      Hyperlink.Ref hyperlink = null;

      if(table instanceof AttributeTableLens) {
         hyperlink = ((AttributeTableLens) table).getHyperlink(r, c);

         if(hyperlink != null) {
            if(hyperlink.isSendReportParameters()) {
               addLinkParameter(hyperlink, linkVarTable);
            }

            if(hyperlink.isSendSelectionParameters()) {
               addLinkSelectionParameter(hyperlink, selections);
            }

            vec.add(hyperlink);
         }
      }
   }

   /**
    * Get hyperlink from drill info for the specified cell.
    */
   private void addDrillInfo(TableLens table, List<Hyperlink.Ref> vec, int r, int c) {
      XDrillInfo dinfo = table.getXDrillInfo(r, c);
      TableLens dataTable = table instanceof TableFilter ?
         Util.getDataTable((TableFilter) table) : table;
      getDrillHyperlink(dinfo, dataTable, vec, r, c);
   }

   private void getDrillHyperlink(XDrillInfo dinfo, TableLens dataTable,
      List<Hyperlink.Ref> vec, int r, int c)
   {
      if(dinfo == null) {
         return;
      }

      DataRef dcol = dinfo.getColumn();

      for(int i = 0; i < dinfo.getDrillPathCount(); i++) {
         DrillPath dpath = dinfo.getDrillPath(i);
         DrillSubQuery query = dpath.getQuery();
         int r2 = TableTool.getBaseRowIndex(VSTableLens.this, dataTable, r);
         int c2 = TableTool.getBaseColIndex(VSTableLens.this, dataTable, c);

         if(r2 < 0 || c2 < 0) {
            continue;
         }

         Hyperlink.Ref ref = new Hyperlink.Ref(dpath, dataTable, r2, c2);

         if(query != null) {
            int type = dataTable.getDescriptor().getType();
            String queryParam = null;

            if(type == TableDataDescriptor.CROSSTAB_TABLE) {
               CrossFilter crosstab = Util.getCrossFilter(dataTable);
               r2 = TableTool.getBaseRowIndex(dataTable, crosstab, r2);
               c2 = TableTool.getBaseColIndex(dataTable, crosstab, c2);

               // if r2 or c2 is -1, data is already error, so set r2 or c2
               // to 0 is meaningless
               if(r2 < 0 || c2 < 0) {
                  continue;
               }

               Map map = new OrderedMap();
               map = crosstab.getKeyValuePairs(r2, c2, map);
               boolean isEmpty = map.size() == 0;
               Object key = isEmpty ? null :
                  ((OrderedMap) map).getKey(map.size() - 1);
               String iden = findIdentifierForSubQuery(crosstab,
                  key == null ? null : key.toString());
               Object val = isEmpty ? null :
                  ((OrderedMap) map).getValue(map.size() - 1);

               ref.setParameter(StyleConstants.SUB_QUERY_PARAM, val);

               // do not find val again, see TablePaintable
               if(dcol != null) {
                  queryParam = Util.findSubqueryVariable(query,
                                                         dcol.getName());
               }

               if(queryParam == null) {
                  String tableHeader = iden != null ? iden :
                     (key == null ? null : key.toString());
                  queryParam = Util.findSubqueryVariable(query, tableHeader);
               }

               if(queryParam != null) {
                  ref.setParameter(Tool.encodeWebURL(StyleConstants.SUB_QUERY_PARAM_PREFIX +
                     queryParam), val);
               }

               Iterator<String> it = query.getParameterNames();

               while(it.hasNext()) {
                  String qvar = it.next();

                  if(Tool.equals(qvar, queryParam)) {
                     continue;
                  }

                  String header = query.getParameter(qvar);
                  Map map0 = new OrderedMap();
                  map0 = crosstab.getKeyValuePairs(r2, c2, map0);
                  Object val0 = map0.get(header);

                  if(val0 == null) {
                     TableLens drilllens =
                        ParamTableLens.create(map0, crosstab);
                     int cidx = ((ParamTableLens) drilllens).
                        findColumn(header);

                     if(cidx < 0 || Tool.equals(qvar, queryParam)) {
                        continue;
                     }

                     val0 = drilllens.getObject(1, cidx);
                  }

                  if(val0 != null) {
                     ref.setParameter(Tool.encodeWebURL(StyleConstants.SUB_QUERY_PARAM_PREFIX +
                        qvar), val0);
                  }
               }
            }
            else {
               ref.setParameter(StyleConstants.SUB_QUERY_PARAM,
                                dataTable.getObject(r2, c2));

               if(dcol != null) {
                  queryParam = Util.findSubqueryVariable(query,
                                                         dcol.getName());
               }

               if(queryParam == null) {
                  String iden = dataTable.getColumnIdentifier(c2);
                  iden = iden == null ?
                     (String) Util.getHeader(dataTable, c2) : iden;
                  iden = findIdentifierForSubQuery(dataTable, iden);
                  queryParam = Util.findSubqueryVariable(query, iden);
               }

               if(queryParam != null) {
                  ref.setParameter(Tool.encodeWebURL(StyleConstants.SUB_QUERY_PARAM_PREFIX +
                     queryParam), dataTable.getObject(r2, c2));
               }

               Iterator<String> it = query.getParameterNames();
               ColumnIndexMap columnIndexMap = new ColumnIndexMap(dataTable, true);

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

                  ref.setParameter(Tool.encodeWebURL(StyleConstants.SUB_QUERY_PARAM_PREFIX +
                     qvar), dataTable.getObject(r2, col));
               }
            }
         }

         if(ref.isSendReportParameters()) {
            addLinkParameter(ref, linkVarTable);
         }

         vec.add(ref);
      }
   }

   /**
    * Cell is responsible to write/parse a cell.
    */
   private class Cell implements XMLSerializable, DataSerializable {
      public Cell() {
         super();
      }

      public Cell(int r, int c) {
         this();

         setRow(r);
         setCol(c);
      }

      public int getRow() {
         return r;
      }

      public void setRow(int r) {
         this.r = r;
      }

      public int getCol() {
         return c;
      }

      public void setCol(int c) {
         this.c = c;
      }

      public void setLabel(String label) {
         this.label = label;
      }

      public boolean isCellEditable() {
         return editable;
      }

      public void setCellEditable(boolean editable) {
         this.editable = editable;
      }

      @Override
      public boolean parseData(DataInputStream input) {
         return true;
      }

      @Override
      public void writeData(DataOutputStream output) throws IOException {
         output.writeInt(r);
         output.writeShort(c);
         output.writeBoolean(editable);

         VSFormat fmt = null;

         try {
            fmt = getFormat(r, c);
         }
         catch(Exception e) {
            // ignore it
         }

         fmt = fmt == null ? new VSFormat() : fmt;
         TableDataPath path = getTableDataPath(r, c);
         List<Hyperlink.Ref> hyperlinkList = getCellHyperlink(getTable(), r, c);
         Hyperlink.Ref hyperlink = null;
         int size = hyperlinkList.size();

         if(getTable() instanceof AttributeTableLens) {
            hyperlink = ((AttributeTableLens) getTable()).getHyperlink(r, c);

            if(hyperlink != null) {
               if(hyperlink.isSendReportParameters()) {
                  addLinkParameter(hyperlink, linkVarTable);
               }

               if(hyperlink.isSendSelectionParameters()) {
                  addLinkSelectionParameter(hyperlink, selections);
               }
            }
         }

         if(size > 0 && underline && getFormTableLens() == null) {
            fmt = (VSFormat) fmt.clone();
            Font font = fmt.getFont();
            StyleFont sfont = font == null
               ? new StyleFont(null, 0, 10) : new StyleFont(font);
            int style = font == null ? 0 : font.getStyle();
            sfont = (StyleFont) sfont.deriveFont(StyleFont.UNDERLINE | style);
            fmt.setFont(sfont);
         }

         output.writeShort(getFormatIndex(fmt));
         output.writeShort(getTableDataPathIndex(path));
         output.writeBoolean(size == 0);

         if(size > 0) {
            Hyperlink.Ref[] refs = new Hyperlink.Ref[hyperlinkList.size()];
            hyperlinkList.toArray(refs);
            output.writeShort(getHyperlinksIndex(refs));
            output.writeShort(hyperlink == null ?  -1 : 0);
         }

         Rectangle span2 = spanmap.get(r, c);
         output.writeBoolean(span2 == null);

         if(span2 != null) {
            output.writeShort(span2.width);
            output.writeShort(span2.height);
         }

         Object obj = getObject(r, c);
         output.writeBoolean(obj instanceof PresenterPainter ||
                             obj instanceof Image);

         if(obj instanceof PresenterPainter) {
            PresenterPainter p = (PresenterPainter) obj;
            output.writeBoolean(p.getPresenter() instanceof HTMLPresenter);
         }
         else {
            output.writeBoolean(false);
         }

         String text = label != null ? label : getText(r, c);
         output.writeBoolean(text == null);
         Tool.writeUTF(output, text);

         String value = getValue(r, c);

         if(value == null) {
            output.writeByte(0);
         }
         else if(value.equals(text)) {
            output.writeByte(1);
         }
         else {
            output.writeByte(2);
            output.writeUTF(value);
         }

         if(spanTable != null) {
            Rectangle vsspan = getVSSpan(r, c);
            output.writeBoolean(vsspan == null);

            if(vsspan != null) {
               output.writeShort(vsspan.x);
               output.writeShort(vsspan.y);
               output.writeShort(vsspan.width);
               output.writeShort(vsspan.height);
            }
         }
         else {
            output.writeBoolean(true);
         }

         output.writeUTF(getDrillOp(r, c));
      }

      @Override
      public void parseXML(Element elem) throws Exception {
         throw new RuntimeException("Unsupported method called!");
      }

      @Override
      public void writeXML(PrintWriter writer) {
         Object obj = getObject(r, c);
         boolean img = obj instanceof PresenterPainter || obj instanceof Image;
         boolean html = false;

         if(obj instanceof PresenterPainter) {
            PresenterPainter p = (PresenterPainter) obj;
            html = p.getPresenter() instanceof HTMLPresenter;
         }

         VSFormat fmt = getFormat(r, c);
         TableDataPath path = getTableDataPath(r, c);

         writer.print("<cell " + "r=\"" + r + "\"" + " c=\"" + c + "\"");
         writer.print(" editable=\"" + editable + "\"");
         writer.print(" img=\"" + img + "\"");
         writer.print(" html=\"" + html + "\"");
         writer.print(" format=\"" + getFormatIndex(fmt) + "\"");
         writer.print(" datapath=\"" + getTableDataPathIndex(path) + "\"");

         List<Hyperlink.Ref> hyperlinkList = getCellHyperlink(getTable(), r, c);
         Hyperlink.Ref hyperlink = null;

         if(getTable() instanceof AttributeTableLens) {
            hyperlink = ((AttributeTableLens) getTable()).getHyperlink(r, c);

            if(hyperlink != null) {
               if(hyperlink.isSendReportParameters()) {
                  addLinkParameter(hyperlink, linkVarTable);
               }

               if(hyperlink.isSendSelectionParameters()) {
                  addLinkSelectionParameter(hyperlink, selections);
               }
            }
         }

         if(hyperlinkList.size() > 0) {
            Hyperlink.Ref[] refs = new Hyperlink.Ref[hyperlinkList.size()];
            hyperlinkList.toArray(refs);
            writer.print(" hyperlinks=\"" + getHyperlinksIndex(refs) + "\"");
            writer.print(" hyperlink=\"" +
               (hyperlink == null ?  -1 : 0) + "\"");
         }

         String drillOp = getDrillOp(r, c);

         if(drillOp != null && drillOp.length() > 0) {
            writer.print(" drillop=\"" + drillOp + "\"");
         }

         writer.println(">");
         Dimension span = getSpan(r, c);

         if(span != null) {
            writer.println("<span width=\"" + span.width + "\" height=\"" +
                           span.height + "\"/>");
         }

         if(spanTable != null) {
            Rectangle vsspan = getVSSpan(r, c);

            if(vsspan != null) {
               writer.println("<vsspan x=\"" + vsspan.x + "\" y=\"" + vsspan.y +
                  "\" width=\"" + vsspan.width +
                  "\" height=\"" + vsspan.height + "\"/>");
            }
         }

         String text = label != null ? label : getText(r, c);

         writer.println("<text>");
         writer.print("<![CDATA[" + text + "]]>");
         writer.println("</text>");
         writer.println("</cell>");
      }

      private int r;
      private int c;
      private boolean editable;
      private String label;
   }

   /**
    * Add hyperlink parameter.
    * @param hlink the hyperlink to be set parameters.
    * @param vtable the variable table from sand box.
    */
   private void addLinkParameter(Hyperlink.Ref hlink, VariableTable vtable) {
      Util.addLinkParameter(hlink, vtable);
   }

   /**
    * Add selection parameter.
    * @param hlink the hyperlink to be set parameters.
    * @param sel the Hashtable from sand box.
    */
   private void addLinkSelectionParameter(Hyperlink.Ref hlink, Hashtable<String, SelectionVSAssembly> sel) {
      VSUtil.addSelectionParameter(hlink, sel);
   }

   /**
    * Get the bottom border of the table.
    */
   private int[] getBottomBorders() {
      TableLens table = getTable();
      table.moreRows(MAX_WAIT_SIZE);
      int rcnt = table.getRowCount();
      int[] borders = new int[table.getColCount()];

      if(rcnt < 0) {
         // use the row from the last row (shouldn't make any difference
         // since the table is not complete anyway) to avoid the moreRows()
         // in getRowBorder() waiting for unavailable rows
         rcnt = -rcnt - 2;
      }

      for(int i = 0; i < borders.length; i++) {
         borders[i] = table.getRowBorder(rcnt - 1, i);
      }

      return borders;
   }

   /**
    * Get the bottom border color of the table.
    */
   private Color[] getBottomBorderColors() {
      TableLens table = getTable();
      table.moreRows(MAX_WAIT_SIZE);
      int rcnt = table.getRowCount();
      Color[] borders = new Color[table.getColCount()];

      if(rcnt < 0) {
         rcnt = -rcnt - 2;
      }

      for(int i = 0; i < borders.length; i++) {
         borders[i] = table.getRowBorderColor(rcnt - 1, i);
      }

      return borders;
   }

   /**
    * Return the per cell foreground color. Return null to use default
    * color.
    * @param r row number.
    * @param c column number.
    * @return foreground color for the specified cell.
    */
   @Override
   public Color getForeground(int r, int c) {
      FormTableLens lens = getFormTableLens();

      if(lens != null) {
         Color color = lens.getForeground(r);

         if(color != null) {
            return color;
         }
      }

      return super.getForeground(r, c);
   }

   /**
    * Return the per cell background color. Return null to use default
    * color.
    * @param r row number.
    * @param c column number.
    * @return background color for the specified cell.
    */
   @Override
   public Color getBackground(int r, int c) {
      FormTableLens lens = getFormTableLens();

      if(lens != null) {
         Color color = lens.getBackground(r);

         if(color != null) {
            return color;
         }
      }

      return super.getBackground(r, c);
   }

   /**
    * Return the per cell background color. Return null to use default
    * color.
    * @param r row number.
    * @param c column number.
    * @param spanRow row index of the specified span
    * @return background color for the specified cell.
    */
   @Override
   public Color getBackground(int r, int c, int spanRow) {
      FormTableLens lens = getFormTableLens();

      if(lens != null) {
         Color color = lens.getBackground(r);

         if(color != null) {
            return color;
         }
      }

      return super.getBackground(r, c, spanRow);
   }

   /**
    * Return the per cell background alpha.
    */
   @Override
   public int getAlpha(int r, int c) {
      FormTableLens lens = getFormTableLens();

      if(lens != null) {
         return lens.getAlpha(r);
      }

      if(highlightTable == null) {
         TableLens tbl = Util.getNestedTable(getTable(), HighlightTableLens.class);
         highlightTable = tbl != null ? tbl : "not_found";
      }

      if(highlightTable instanceof HighlightTableLens) {
         HighlightTableLens table = (HighlightTableLens) highlightTable;
         Color color = table.getBackground(r, c);
         TableDataPath path = new TableDataPath(-1, TableDataPath.OBJECT);
         VSCompositeFormat vfmt = getFormatInfo().getFormat(path);

         if(vfmt != null && Tool.equals(color, vfmt.getBackground())) {
            return -1;
         }

         path = getTableDataPath(r, c);
         vfmt = getFormatInfo().getFormat(path);

         if(vfmt != null && Tool.equals(color, vfmt.getBackground())) {
            return -1;
         }

         return color != null ? (int) ((color.getAlpha() / 255.0) * 100) : -1;
      }

      return super.getAlpha(r, c);
   }

   /**
    * Get the form table.
    */
   public FormTableLens getFormTableLens() {
      if(formTable == null) {
         TableLens table = getTable();
         formTable = table;

         while(table != null) {
            if(table instanceof FormTableLens) {
               formTable = table;
               break;
            }

            table = (table instanceof TableFilter) ?
               ((TableFilter) table).getTable() : null;
         }
      }

      return formTable instanceof FormTableLens ? (FormTableLens) formTable : null;
   }

   /**
    * Set if hyperlink is enabled.
    */
   public void setHyperlinkEnabed(boolean enabled) {
      this.henabled = enabled;
   }

   /**
    * Check if hyperlink is enabled.
    */
   public boolean isHyperlinkEnabled() {
      return henabled;
   }

   /**
    * Get the line count each row.
    * @param row the specified row.
    */
   public int getLineCount(int row) {
      return getLineCount(row, true);
   }

   /**
    * Get the line count each row.
    * @param row the specified row.
    * @param flex true to compensate for flex padding
    */
   public int getLineCount(int row, boolean flex) {
      int lines = 1;
      boolean wrap = false;

      for(int i = 0; i < getColCount(); i++) {
         if(isLineWrap(row, i)) {
            wrap = true;
         }

         // @by ankitmathur, Optimization for bug1424365341122, only
         // calculate the number of lines for a wrapped cell if the current
         // cell actually contains wrapping. Bypassing this method
         // significantly speeds up exporting tables which do not have cells
         // with line wrapping.
         if(wrap) {
            lines = Math.max(lines, calculateNumOfLinesWrappedInCell(row, i, flex));
         }

         wrap = false;
      }

      return lines;
   }

   /**
    * Get the line count each row.
    * @param row the specified row.
    * @param flex true to compensate for flex padding
    */
   public int getWrappedHeight(int row, boolean flex) {
      int height = getRowHeight0(row);
      int h1 = height;

      for(int i = 0; i < getColCount(); i++) {
         boolean wrap = isLineWrap(row, i);
         String cell = row + "x" + i;

         if(wrap) {
            getRowHeight(row, i);//included for side effects on isIncludedInSpan hashmap

            if(isIncludedInSpan.get(cell) == null || !isIncludedInSpan.get(cell)) {
               height = Math.max(height, calculateHeightOfWrappedCell(row, i, flex));
            }
         }

         // make sure the height distributed from span cell is applied (49111).
         if(spanHeightMap.containsKey(cell)) {
            height = Math.max(height, spanHeightMap.get(cell));
         }
      }

      return height;
   }

   public boolean isWrapLine(int row) {
      for(int i = 0; i < getColCount(); i++) {
         if(isLineWrap(row, i)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Calculate lines of wrapped cell.
    * @param row the specified row.
    * @param col the specified col.
    */
   private int calculateHeightOfWrappedCell(int row, int col, boolean flex) {
      if(widths == null || col >= widths.length || rows == null) {
         return -1;
      }

      int width = getColumnWidth(row, col);

      if(width <= 0) {
         return -1;
      }

      // fixes for row heights
      // (41976, 38926, 36998, 34061, 34052, 33136, 32456, 31045, 30149, 29192)

      String text = getText(row, col);
      Font font = super.getFont(row, col);

      if(flex) {
         width -= 4; // subtract padding
         width -= Optional.ofNullable(getFormat(row, col)) // subtract left-right border widths.
                 .map(VSFormat::getBorders)
                 .map(b -> Common.getLineWidth(b.left) + Common.getLineWidth(b.right))
                 .orElse(0.0f);
      }

      if(isHTML(text)) {
         if(html == null) {
            html = new HTMLPresenter();
         }

         html.setFont(font);
         // java sizing is not exactly same as browser, allow a small margin of error
         int h = html.getPreferredSize(text, width / 1.05f).height;
         float rowHeight = getRowHeight0(row);
         // multiple of rowheight, see below
         return (int) (Math.ceil(h / rowHeight) * rowHeight);
      }

      Bounds bounds = new Bounds(0, 0, width, 1);
      Vector lines = Common.processText(text, bounds, getAlignment(row, col),
                                        isLineWrap(row, col), font, new Bounds(), new Vector(), 0,
                                        Common.getFractionalFontMetrics(font), 0);
      int lineCount = lines.size();
      int cellHeight = getRowHeight(row, col);
      //if cell is spanning, only increase height for line break if other cells can't hold it
      String cell = row + "x" + col;
      final boolean isSpan = spanDimensionMap.get(cell) != null;
      float lineH = Common.getHeight(font);

      // for spans we return the line height if it the text will fit in the span height
      if(!isSpan && lineCount == 1 && cellHeight >= lineH) {
         return cellHeight;
      }

      float totalH = lineH;

      // userDataRowHeight was introduced in 13.3 so it can be used to check if the row
      // height was changed post 12.2. in that case just use multiple of line height
      // since it gives a more accurate fit for wrapped lines. (48768)
      if(userDataRowHeight) {
         cellHeight = (int) Math.ceil(lineH * lines.size() +
                                         Common.getFontMetrics(font).getDescent());
      }
      // in 12.2 and before, we wrap the line at the increment of row height. here we keep the
      // same logic and increase the height at multiple of single row height to keep the
      // spacing same. this looks better too since the row heights are more uniform.
      else {
         for(int i = 1; i <= lines.size(); i++, totalH += lineH) {
            if(totalH > cellHeight) {
               lineCount++;
               int rowHeight = getRowHeight0(row);
               cellHeight += lines.size() > 1 ? Math.max(rowHeight, lineH) : rowHeight;
            }
         }
      }

      double r = isSpan ? spanDimensionMap.get(cell).getHeight() : 0;

      if(lineCount <= r) {
         return (int) lineH;
      }

      Dimension span = getSpan(row, col);

      // spread out the height for span cell so a large cell doesn't cause a single row
      // to be very tall (49111).
      if(span != null) {
         int total = cellHeight;
         cellHeight = (int) Math.ceil(cellHeight / (double) span.height);

         for(int i = 0; i < span.height; i++) {
            spanHeightMap.put((row + i) + "x" + col, cellHeight);
         }
      }

      return cellHeight;
   }

/*   private boolean isCellWrapped(int row, int col) {
      VSFormat format = this.getFormat(row, col);
      return format != null && format.isWrapping();
   }*/

   /**
    * Initializes the table row height and column width arrays
    * @param info the table assembly to initialize from
    */
   public void initTableGrid(VSAssemblyInfo info) {
      initTableLensColumnWidths(info);
      initTableLensRowHeights(info);
   }

   /**
    * Initialize the widths array
    */
   private void initTableLensColumnWidths(VSAssemblyInfo info) {
      if(!(info instanceof TableDataVSAssemblyInfo)) {
         return;
      }

      if(widths != null && widths.length > 0) {
         return;
      }

      widths = new int[getColCount()];

      TableDataVSAssemblyInfo ainfo = (TableDataVSAssemblyInfo) info;
      Viewsheet vs = ainfo.getViewsheet();
      Point offset = ainfo.getPixelOffset();
      int total = 0 ;
      int pixelWidth = ainfo.getPixelSize().width;

      if(vs == null || offset == null) {
         return;
      }

      for(int i = 0; i < getColCount(); i++) {
         final double widthDouble = ainfo.getColumnWidth2(i, this);
         final int widthInt = (int) widthDouble;
         widths[i] = (Double.isNaN(widthDouble) || widthInt < 0) ? AssetUtil.defw : widthInt;
         total += widths[i];
      }

      // Bug #43321, don't expand the last column if it is hidden (width == 0)
      if(total < pixelWidth && getColCount() > 1 && widths[getColCount() - 1] > 0) {
         widths[getColCount() - 1] += pixelWidth - total;
      }
   }

   /**
    * Initialize the rows array
    */
   private void initTableLensRowHeights(VSAssemblyInfo info) {
      if(!(info instanceof TableDataVSAssemblyInfo)) {
         return;
      }

      if(rows != null && rows.length > 0) {
         return;
      }

      int headerRowCount = getHeaderRowCount();

      if(info instanceof CalcTableVSAssemblyInfo) {
         headerRowCount = ((CalcTableVSAssemblyInfo)info).getHeaderRowCount();
      }

      TableDataVSAssemblyInfo tinfo = (TableDataVSAssemblyInfo) info;
      this.userDataRowHeight = tinfo.isUserDataRowHeight();
      moreRows(10000);
      int rcnt = getRowCount();
      rcnt = rcnt < 0 ? -rcnt - 1 : rcnt;
      rows = new int[rcnt];

      // if not a user header row height then reset it here as it could have heights
      // from old css that no longer applies
      if(!tinfo.isUserHeaderRowHeight()) {
         tinfo.setHeaderRowHeights(new int[]{ AssetUtil.defh });
      }

      int dataRowHeight = getCSSDataRowHeight(tinfo);
      dataRowHeight = dataRowHeight > 0 ? dataRowHeight : tinfo.getDataRowHeight(headerRowCount);
      int cssHeaderRowHeight = getCSSHeaderRowHeight(tinfo);

      for(int i = 0; i < rows.length; i++) {
         double row = tinfo.getRowHeight(i);
         int height = i < headerRowCount ?
            (cssHeaderRowHeight > 0 ? cssHeaderRowHeight : tinfo.getHeaderRowHeight(i)) :
            dataRowHeight;
         rows[i] = (Double.isNaN(row) || row < 0) ? height : (int) row;
      }
   }

   /**
    * Calculate lines of wrapped cell.
    * @param row the specified row.
    * @param col the specified col.
    */
   private int calculateNumOfLinesWrappedInCell(int row, int col, boolean flex) {
      int lineCount = 1;

      if(widths == null || col >= widths.length || rows == null) {
         return lineCount;
      }

      int width = getColumnWidth(row, col);
      // if header, get it's row height,
      // else if detail, get first detail row height.
      int height = row <= getHeaderRowCount() ? getRowHeight(row, col) :
                          getRowHeight(getHeaderRowCount(), col);

      if(width <= 0) {
         return lineCount;
      }

      String text = getText(row, col);
      // When apply font scale, using font with out font scale to get line count,
      // beause the clint will scale the grid with font scale.
      Font font = super.getFont(row, col);

      // @by gregm correct for flex textField 2 pixel gutter on left/right
      // + 1 for the difference between flex textField width and VSCell width.
      // http://help.adobe.com/en_US/FlashPlatform/reference/actionscript/
      // 3/flash/text/TextLineMetrics.html
      if(flex) {
         width -= 5;
      }

      Bounds bounds = new Bounds(0, 0, width, 1);

      Vector lines = Common.processText(text, bounds, getAlignment(row, col),
         isLineWrap(row, col), font, new Bounds(), new Vector(), 0,
         Common.getFontMetrics(font), 0);

      for(int i = 1; i <= lines.size(); i++) {
         float totalH = i * Common.getHeight(font);

         if(totalH > height) {
            lineCount++;
         }
      }

      return lineCount;
   }

   /**
    * Keep row heights on exported pdf.
    */
   @Override
   public int getRowHeight(int row) {
      return row < rows.length && rows[row] > 0 ? rows[row] : -1;
   }

   /**
    * Calculate height of row cell.
    * @param row the specified row.
    * @param col the specified col.
    */
   public int getRowHeight(int row, int col) {
      Rectangle span = getVSSpan(row, col);
      int height = 0;

      if(span != null) {
         if(span.x < 0) {
            return 0;
         }

         for(int i = 0; i < span.height; i++) {
            height += getRowHeight0(row + i);
         }
      }
      else {
         Dimension dim = getSpan(row, col);

         if(dim != null) {
            for(int i = 0; i < dim.height; i++) {
               height += getRowHeight0(row + i);
            }

            spanDimensionMap.put(row + "x" + col, dim);
            isIncludedInSpan.put(row + "x" + col, false);

            for(int i = 1; i < dim.height; i++) {
               spanDimensionMap.put((row + i) + "x" + col, dim);
               isIncludedInSpan.put((row + i) + "x" + col, true);
            }
         }
         else {
            if(spanHeightMap.containsKey(row + "x" + col)) {
               height = spanHeightMap.get(row + "x" + col);
            }
            else {
               height = getRowHeight0(row);
            }
         }
      }

      return height;
   }

   private int getRowHeight0(int r) {
      return r < rows.length ? rows[r] : defh;
   }

   /**
    * Calculate width of column cell.
    * @param row the specified row.
    * @param col the specified col.
    */
   private int getColumnWidth(int row, int col) {
      Rectangle span = getVSSpan(row, col);
      int width = 0;

      if(span != null) {
         if(span.x < 0) {
            return 0;
         }

         for(int i = 0; i < span.width; i++) {
            width += widths[col + i];
         }
      }
      else {
         Dimension dim = getSpan(row, col);

         if(dim != null) {
            for(int i = 0; i < dim.width && col + i < widths.length; i++) {
               width += widths[col + i];

               if(i != 0){
                  spancontainer.add(row + "x" + (col + i));
               }
            }
         }
         else {
            if(spancontainer.contains(row + "x" + col)) {
               width = 0;
            }
            else {
               width = widths[col];
            }
         }
      }

      return width;
   }

   /**
    * Return the spanning setting for the cell. If the specified cell
    * is not a spanning cell, it returns null. Otherwise it returns
    * a Dimension object with Dimension.width equals to the number
    * of columns and Dimension.height equals to the number of rows
    * of the spanning cell.
    * @param r row number.
    * @param c column number.
    * @return span cell dimension.
    */
   @Override
   public Dimension getSpan(int r, int c) {
      if(calcTable == null) {
         TableLens tbl = Util.getNestedTable(table, RuntimeCalcTableLens.class);
         calcTable = "not-found";

         if(tbl != null && Util.getNestedTable(table, DataWrapperTableLens.class) == null) {
            calcTable = tbl;
         }
      }

      if(calcTable instanceof RuntimeCalcTableLens) {
         int max = Util.getOrganizationMaxColumn();
         Dimension dim = ((TableLens) calcTable).getSpan(r, c);

         if(dim != null && c + dim.width > max) {
            dim.width = Math.max(1, max - c);
         }

         return dim;
      }

      return table.getSpan(r, c);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      Font font = super.getFont(r, c);

      if(font != null) {
         font = font.deriveFont(font.getSize() * rscaleFont);
      }

      return font;
   }

   /**
    * Get runtime scale font.
    */
   public void setRScaleFont(float scaleFont) {
      rscaleFont = scaleFont;
   }

   public int getCSSHeaderRowHeight(TableDataVSAssemblyInfo info) {
      int headerRowCount = getHeaderRowCount();

      if(headerRowCount > 0 && !info.isUserHeaderRowHeight()) {
         CSSTableStyle cssTableStyle = (CSSTableStyle) Util.getNestedTable(this,
                                                                           CSSTableStyle.class);
         if(cssTableStyle != null) {
            int baseRow = TableTool.getBaseRowIndex(this, cssTableStyle, 0);
            return cssTableStyle.getRowHeight(baseRow);
         }
      }

      return -1;
   }

   public int getCSSDataRowHeight(TableDataVSAssemblyInfo info) {
      int headerRowCount = getHeaderRowCount();

      if(!info.isUserDataRowHeight()) {
         CSSTableStyle cssTableStyle = (CSSTableStyle) Util.getNestedTable(this,
                                                                           CSSTableStyle.class);
         if(cssTableStyle != null) {
            int baseRow = TableTool.getBaseRowIndex(this, cssTableStyle, headerRowCount);

            if(baseRow >= 0) {
               return cssTableStyle.getRowHeight(baseRow);
            }
         }
      }

      return -1;
   }

   /**
    * Get the max padding (combined top and bottom padding) across the specified row
    */
   public int getCSSRowPadding(int row) {
      if(row > getHeaderRowCount()) {
         row = getHeaderRowCount();
      }

      CSSTableStyle cssTableStyle = (CSSTableStyle) Util.getNestedTable(this,
                                                                        CSSTableStyle.class);
      if(cssTableStyle != null) {
         int baseRow = TableTool.getBaseRowIndex(this, cssTableStyle, row);

         if(baseRow >= 0) {
            if(!maxRowPadding.containsKey(row)) {
               // calculate max padding
               int maxPadding = 0;

               for(int c = 0; c < cssTableStyle.getColCount(); c++) {
                  Insets padding = cssTableStyle.getInsets(baseRow, c);

                  if(padding != null) {
                     maxPadding = Math.max(maxPadding, padding.top + padding.bottom);
                  }
               }

               maxRowPadding.put(row, maxPadding);
            }

            return maxRowPadding.get(row);
         }
      }

      return 0;
   }

   /**
    * Get the max padding (combined left and right padding) across the specified column
    */
   public int getCSSColumnPadding(int col) {
      CSSTableStyle cssTableStyle = (CSSTableStyle) Util.getNestedTable(this,
                                                                        CSSTableStyle.class);
      if(cssTableStyle != null) {
         int baseCol = TableTool.getBaseColIndex(this, cssTableStyle, col);

         if(baseCol >= 0) {
            if(!maxColPadding.containsKey(col)) {
               int headerRowCount = getHeaderRowCount();
               int trailerRowCount = getTrailerRowCount();
               int maxPadding = 0;
               int r = 0;
               int baseRow;
               Insets padding;

               // check header row region
               if(headerRowCount > 0) {
                  baseRow = TableTool.getBaseRowIndex(this, cssTableStyle, r);
                  padding = cssTableStyle.getInsets(baseRow, baseCol);

                  if(padding != null) {
                     maxPadding = Math.max(maxPadding, padding.left + padding.right);
                  }

                  r += headerRowCount;
               }

               // check body region
               baseRow = TableTool.getBaseRowIndex(this, cssTableStyle, r);
               padding = cssTableStyle.getInsets(baseRow, baseCol);

               if(padding != null) {
                  maxPadding = Math.max(maxPadding, padding.left + padding.right);
               }

               // check trailer row region
               if(trailerRowCount > 0) {
                  r = getRowCount() - 1;
                  baseRow = TableTool.getBaseRowIndex(this, cssTableStyle, r);
                  padding = cssTableStyle.getInsets(baseRow, baseCol);

                  if(padding != null) {
                     maxPadding = Math.max(maxPadding, padding.left + padding.right);
                  }
               }

               maxColPadding.put(col, maxPadding);
            }

            return maxColPadding.get(col);
         }
      }

      return 0;
   }

   /**
    * Add padding to the height
    */
   public double getRowHeightWithPadding(double height, int row) {
      if(Double.isNaN(height) || height <= 0) {
         return height;
      }

      return height + getCSSRowPadding(row);
   }

   /**
    * Add padding to the width
    */
   public double getColumnWidthWithPadding(double width, int col) {
      if(Double.isNaN(width) || width <= 0) {
         return width;
      }

      return width + getCSSColumnPadding(col);
   }

   /**
    * Check if a string contains html tag.
    */
   public static boolean isHTML(String str) {
      int idx = str.indexOf('<');

      if(idx >= 0) {
         int idx2 = str.indexOf('>', idx + 1);

         if(idx2 > 0) {
            for(int i = idx + 1; i < idx2; i++) {
               if(!Character.isAlphabetic(str.charAt(i))) {
                  return false;
               }
            }

            return true;
         }
      }

      return false;
   }

   private int start = -1;
   private int count = -1;
   private static final int MAX_WAIT_SIZE = 10000;
   private volatile int spanInited = 0;
   private CrosstabTree ctree;
   private transient String luri;
   private int[] widths = {};
   private int[] rows = {};
   private int maxCols = 0;
   // VSFormat -> index
   private final transient Map<VSFormat, Integer> fmtmap = new HashMap<>();
   // Cell TableDataPath -> index
   private final transient Map<TableDataPath, Integer> pathmap = new HashMap<>();
   // Hyperlink -> index
   private final transient Map<Hyperlink.Ref[], Integer> hmap = new HashMap<>();
   private transient Map<String, String> idenMap;
   private transient VariableTable linkVarTable; // the variable for hyperlink
   private transient boolean underline = false;
   private transient boolean henabled = true;
   // row,col -> Dimension of span size
   private transient Hashtable<String, SelectionVSAssembly> selections;
   private final transient SpanMap spanmap = new SpanMap();
   private final ArrayList<String> spancontainer = new ArrayList<>();
   private final Map<String, Integer> spanHeightMap = new HashMap<>();
   private final Map<String, Dimension> spanDimensionMap = new HashMap<>();
   private final Map<String, Boolean> isIncludedInSpan = new HashMap<>();
   public float rscaleFont = 1;
   private boolean userDataRowHeight = false;
   private transient TableLens spanTable;
   private transient HTMLPresenter html;
   private transient Object formTable;
   private transient Object highlightTable;
   private transient Object calcTable;
   private Map<Integer, Integer> maxRowPadding = new HashMap<>();
   private Map<Integer, Integer> maxColPadding = new HashMap<>();
}
