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
package inetsoft.report.lens;

import inetsoft.report.*;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.schema.XSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Header row table lens.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class HeaderRowTableLens extends AttributeTableLens {
   /**
    * Create a table filter that can override the table header.
    * @param table the specified base table.
    */
   public HeaderRowTableLens(TableLens table) {
      setTable(table);
   }

   /**
    * Create a table filter that can override the table header.
    * @param table the specified base table.
    * @param headerRowCount the specified header row count.
    */
   public HeaderRowTableLens(TableLens table, int headerRowCount) {
      this(table);
      this.headerRowCount = headerRowCount;
   }

   /**
    * Set the number of header rows.
    */
   @Override
   public void setHeaderRowCount(int headerRowCount) {
      this.headerRowCount = headerRowCount;
   }

   /**
    * Get the column header.
    */
   public Object getHeader(Object col, int row) {
      int cnum = 0;
      int ccnt = table.getColCount();
      int rnum = findNeighbourBaseHeaderRow(row);

      for(int i = 0; i < ccnt; i++) {
         if(col.equals(table.getObject(rnum, i))) {
            cnum = i;
            break;
         }
      }

      return getHeader0(col, row, cnum);
   }

   /**
    * Get the column header.
    */
   private Object getHeader0(Object ocol, int row, int col) {
      if(row < 0 || row >= headerRowCount) {
         return null;
      }

      int br = findNeighbourBaseHeaderRow(row);
      String oheader = br == -1 ? ("column" + col) :
         ("" + table.getObject(br, col));

      Object key = createHeaderKey(ocol, oheader, row, col);
      Vector rowHeader = (Vector) headermap.get(key);

      if(rowHeader != null && rowHeader.size() > row) {
         return rowHeader.elementAt(row);
      }

      return null;
   }

   /**
    * Set the base table to be used with the attribute table table.
    * @param table base table.
    */
   @Override
   public void setTable(TableLens table) {
      super.setTable(table);
      crosstab = table.getDescriptor().getType() ==
         TableDataDescriptor.CROSSTAB_TABLE;
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(hdescriptor == null) {
         hdescriptor = new HeaderRowDataDescriptor();
      }

      return hdescriptor;
   }

   /**
    * Get the base table row index corresponding to the filtered table.
    * If the row does not exist in the base table, it returns -1.
    * @param row row index in the filtered table.
    * @return corresponding row index in the base table.
    */
   @Override
   public int getBaseRowIndex(int row) {
      if(row < getHeaderRowCount()) {
         if(rowmap != null && row >=0 && row < rowmap.length) {
            return rowmap[row];
         }

         // defaults to mapping the headers rows to the bottom
         int hdiff = getHeaderRowCount() - table.getHeaderRowCount();

         if(row >= hdiff) {
            return row - hdiff;
         }
         else {
            return -1;
         }
      }

      return row - getHeaderRowCount() + table.getHeaderRowCount();
   }

   /**
    * Get the base table column index corresponding to the filtered table.
    * If the column does not exist in the base table, it returns -1.
    * @param col column index in  the filtered table.
    * @return corresponding column index in the bast table.
    */
   @Override
   public int getBaseColIndex(int col) {
      if(col < 0) {
         return col;
      }

      return (colmap == null || colmap.length == 0) ? col : colmap[col];
   }

   /**
    * Set the column header.
    */
   public void setHeader(Object col, int row, Object header) {
      int cnum = -1;
      int ccnt = table.getColCount();
      int rnum = findNeighbourBaseHeaderRow(row);

      for(int i = 0; i < ccnt; i++) {
         if(col.equals(table.getObject(rnum, i))) {
            cnum = i;
            break;
         }
      }

      if(cnum >= 0) {
         setHeader0(col, row, cnum, header);
      }
   }

   /**
    * Set the column header.
    */
   private void setHeader0(Object ocol, int row, int col, Object header) {
      if(row < 0 || row >= headerRowCount) {
         return;
      }

      int br = findNeighbourBaseHeaderRow(row);
      Object oheader = null;

      if(br == -1) {
         oheader = "column" + col;
      }
      else {
         int ccnt = table.getColCount();

         for(int c = 0; c < ccnt; c++) {
            Object hdr = table.getObject(br, c);

            if(ocol != null && ocol.equals(hdr)) {
               oheader = hdr;
               break;
            }
         }

         if(oheader == null) {
            oheader = "" + table.getObject(br, col);
         }
      }

      Object key = createHeaderKey(ocol, oheader, row, col);
      Vector rowHeader = (Vector) headermap.get(key);

      if(rowHeader == null) {
         rowHeader = new Vector();
         headermap.put(key, rowHeader);
      }

      if(row >= rowHeader.size()) {
         rowHeader.setSize(row + 1);
      }

      rowHeader.setElementAt((header == null ? "" : header), row);
   }

   private Object createHeaderKey(Object ocol, Object header, int row, int col)
   {
      return ocol + "-" + header + "-" +
         getDescriptor().getCellDataPath(row, col);
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return (colmap == null) ? table.getColCount() : colmap.length;
   }

   /**
    * Get the number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      return Math.max(headerRowCount, table.getHeaderRowCount());
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return table.getHeaderColCount();
   }

   /**
    * Get hyperlink of a table cell.
    * @param row the specified row
    * @param col the specified col
    */
   @Override
   public Hyperlink.Ref getHyperlink(int row, int col) {
      row = getBaseRowIndex(row);
      col = getBaseColIndex(col);
      return row >= 0 ? super.getHyperlink(row, col) : null;
   }

   /**
    * Get the current row heights setting. The meaning of row heights
    * depends on the table layout policy setting. If the row height
    * is to be calculated by the ReportSheet based on the content,
    * return -1.
    * @return row height.
    */
   @Override
   public int getRowHeight(int row) {
      // @by larryl, when populated from FreehandLayout, the row height
      // is set in the AttributeTableLens, needs to check it first
      if(row < getHeaderRowCount()) {
         Integer height = super.getRowHeight0(row);

         if(height != null) {
            return height.intValue();
         }
      }

      int row0 = getBaseRowIndex(row);

      return (row0 >= 0) ? table.getRowHeight(row0) : -1;
   }

   /**
    * Get the current column width setting. The meaning of column widths
    * depends on the table layout policy setting. If the column width
    * is to be calculated by the ReportSheet based on the content,
    * return -1. A special value, StyleConstants.REMAINDER, can be returned
    * by this method to indicate that width of this column should be
    * calculated based on the remaining space after all other columns'
    * widths are satisfied. If there are more than one column that return
    * REMAINDER as their widths, the remaining space is distributed
    * evenly among these columns.
    * @return column width.
    */
   @Override
   public int getColWidth(int col) {
      col = getBaseColIndex(col);
      return table.getColWidth(col);
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      c = getBaseColIndex(c);

      if(r < 0) {
         return table.getRowBorderColor(r, c);
      }

      r = getBaseRowIndex(r);
      return table.getRowBorderColor((r < 0) ? 0 : r, c);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      c = getBaseColIndex(c);

      if(r < 0) {
         return table.getColBorderColor(r, c);
      }

      r = getBaseRowIndex(r);
      return table.getColBorderColor((r < 0) ? 0 : r, c);
   }

   /**
    * Return the style for bottom border of the specified cell. The flag
    * must be one of the style options defined in the StyleConstants
    * class. If the row number is -1, it's checking the outside ruling
    * on the top.
    * @param r row number.
    * @param c column number.
    * @return ruling flag.
    */
   @Override
   public int getRowBorder(int r, int c) {
      c = getBaseColIndex(c);

      if(r < 0) {
         return table.getRowBorder(r, c);
      }
      // @by davyc, it is strange, why not use crosstab border directly,
      // otherwise for keep header case, the border is wrong.
      // and now from version 10.3, crosstab not support freehand
      /*
      else if(crosstab && r >= 0 && r < getHeaderRowCount() - 1 &&
              c < getHeaderColCount()) {
         return StyleConstants.NO_BORDER;
      }
      */

      r = getBaseRowIndex(r);
      return table.getRowBorder((r < 0) ? table.getHeaderRowCount() - 1 : r, c);
   }

   /**
    * Return the style for right border of the specified row. The flag
    * must be one of the style options defined in the StyleConstants
    * class. If the column number is -1, it's checking the outside ruling
    * on the left.
    * @param r row number.
    * @param c column number.
    * @return ruling flag.
    */
   @Override
   public int getColBorder(int r, int c) {
      c = getBaseColIndex(c);

      if(r < 0) {
         return table.getColBorder(r, c);
      }

      r = getBaseRowIndex(r);
      return table.getColBorder((r < 0) ? 0 : r, c);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);
      return table.getInsets((r < 0) ? 0 : r, c);
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
      c = getBaseColIndex(c);
      Dimension span = super.getSpan0(r, c);

      if(span != null) {
         return span;
      }

      int row = getBaseRowIndex(r);

      row = (row < 0 && r < getHeaderRowCount()) && !crosstab ?
         findNeighbourBaseHeaderRow(r) : row;

      return (row < 0) ? null : table.getSpan(row, c);
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);
      return table.getAlignment((r < 0) ? 0 : r, c);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);
      return table.getFont((r < 0) ? 0 : r, c);
   }

   /**
    * Return the per cell line wrap mode. If the line wrap mode is true,
    * lines are wrapped when the text can not fit on one line. Otherwise
    * the wrapping is never done and any overflow text will be truncated.
    * @param r row number.
    * @param c column number.
    * @return true if line wrapping should be done.
    */
   @Override
   public boolean isLineWrap(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);
      return table.isLineWrap((r < 0) ? 0 : r, c);
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
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);
      return table.getForeground((r < 0) ? 0 : r, c);
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
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);
      return table.getBackground((r < 0) ? 0 : r, c);
   }

   /**
    * Get the table cell value.
    */
   @Override
   public Object getObject(int r, int c) {
      c = getBaseColIndex(c);

      if(r < getHeaderRowCount()) {
         Object oheader = table.getObject(findNeighbourBaseHeaderRow(r), c);
         Object header = getHeader0(oheader, r, c);

         if(header == null) {
            /* @by larryl, the FreehandLayout based should have all mapping
               properly done. Don't think this should cause any backward
               compatibility issue but leave here for now (7.0)
            if(r == 0) {
               header = col;
            }
            */
            if((r = getBaseRowIndex(r)) >= 0) {
               header = table.getObject(r, c);
            }
         }

         return header;
      }
      else {
         return table.getObject(getBaseRowIndex(r), c);
      }
   }

   /**
    * Return the data at the specified cell.
    * @param r row number.
    * @param c column number.
    * @param val cell value.
    */
   @Override
   public void setData(int r, int c, Object val) {
      // @by mikec, see comment for setObject.
      if(r < getHeaderRowCount()) {
         setObject(r, c, val);
      }
      else {
         c = getBaseColIndex(c);
         super.setData(r, c, val);
      }
   }

   /**
    * Set a cell value.
    * @param r row index.
    * @param c column index.
    * @param val cell value.
    */
   @Override
   public void setObject(int r, int c, Object val) {
      c = getBaseColIndex(c);

      if(r < getHeaderRowCount()) {
         // @by mikec, the headerRowLens should take the responsibility
         // to keep the header part content, any change to the header
         // part should only be stored in here instead of delegate to
         // the base table.
         setHeader0(table.getObject(0, c), r, c, val);
         fireChangeEvent();
      }
      else {
         table.setObject(getBaseRowIndex(r), c, val);
      }
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      return table.getRowCount() == -1 ? -1 :
         getHeaderRowCount() + table.getRowCount() - table.getHeaderRowCount();
   }

   /**
    * Check if there are more rows. The row index is the row that will be
    * accessed. This method must block until the row is available, or
    * return false if the row does not exist in the table. This method is
    * used to iterate through the table, and allow partial table to be
    * accessed in report processing.
    * @param row row number.
    * @return true if the row exists, or false if no more rows.
    */
   @Override
   public boolean moreRows(int row) {
      if(row < getHeaderRowCount()) {
         return true;
      }

      return table.moreRows(getBaseRowIndex(row));
   }

   /**
    * Make a copy of this table.
    */
   @Override
   public HeaderRowTableLens clone() {
      try {
         HeaderRowTableLens lens = (HeaderRowTableLens) super.clone();

         lens.setTable(table);

         if(colmap != null) {
            lens.colmap = (int[]) colmap.clone();
         }

         return lens;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Find the first neighbour base header row.
    * This is for search table data path for crosstab with header row lens.
    */
   private int findNeighbourBaseHeaderRow(int r) {
      int brow = -1;
      int row = r;
      boolean found = false;

      while((brow = getBaseRowIndex(row++)) < table.getHeaderRowCount()) {
         if(brow >= 0) {
            found = true;
            break;
         }
      }

      if(!found) {
         row = r;

         while(row >= 0 && (brow = getBaseRowIndex(row--)) < 0) {
         }
      }

      return brow;
   }

   /**
    * HeaderRowTableLens data descriptor.
    */
   private class HeaderRowDataDescriptor implements TableDataDescriptor {
      /**
       * Create a HeaderRowTableLens data descriptor
       */
      public HeaderRowDataDescriptor() {
         this.descriptor = table.getDescriptor();
      }

      /**
       * Get table data path of a specified table column.
       * @param col the specified table column
       * @return table data path of the table column
       */
      @Override
      public TableDataPath getColDataPath(int col) {
         col = getBaseColIndex(col);
         return descriptor.getColDataPath(col);
      }

      /**
       * Get table data path of a specified table row.
       * @param row the specified table row
       * @return table data path of the table row
       */
      @Override
      public TableDataPath getRowDataPath(int row) {
         int r0 = getBaseRowIndex(row);
         TableDataPath path = null;

         if(r0 >= 0) {
            path = descriptor.getRowDataPath(r0);
         }

         return (path != null) ? path
            : new TableDataPath(-1, TableDataPath.HEADER, row);
      }

      /**
       * Get table data path of a specified table cell.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @return table data path of the table cell
       */
      @Override
      public TableDataPath getCellDataPath(int row, int col) {
         col = getBaseColIndex(col);
         int r0 = getBaseRowIndex(row);

         if(r0 >= 0) {
            return descriptor.getCellDataPath(r0, col);
         }
         else {
            int br = findNeighbourBaseHeaderRow(row);

            if(br >= 0) {
               TableDataPath path = descriptor.getCellDataPath(br, col);
               String[] pathstr = path.getPath();
               String[] tpath2 = new String[pathstr.length + 1];

               System.arraycopy(pathstr, 0, tpath2, 0, pathstr.length);
               tpath2[tpath2.length - 1] = "@" + row;

               return new TableDataPath(path.getLevel(), path.getType(),
                                        path.getDataType(), tpath2);
            }
         }

         return new TableDataPath(-1, TableDataPath.HEADER, XSchema.STRING,
                                  new String[] {"Cell ["+row + "," + col+"]"});
      }

      /**
       * Check if a column belongs to a table data path.
       * @param col the specified table col
       * @param path the specified table data path
       * @return true if the col belongs to the table data path, false otherwise
       */
      @Override
      public boolean isColDataPath(int col, TableDataPath path) {
         col = getBaseColIndex(col);
         return descriptor.isColDataPath(col, path);
      }

      /**
       * Check if a row belongs to a table data path.
       * @param row the specified table row
       * @param path the specified table data path
       * @return true if the row belongs to the table data path, false otherwise
       */
      @Override
      public boolean isRowDataPath(int row, TableDataPath path) {
         int r0 = getBaseRowIndex(row);

         if(r0 >= 0) {
            return descriptor.isRowDataPath(r0, path);
         }

         return path.getType() == TableDataPath.HEADER &&
            path.getIndex() == row;
      }

      /**
       * Check if a cell belongs to a table data path in a loose way.
       * Note: when cheking, path in the table data path will be ignored.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @param path the specified table data path
       * @return true if the cell belongs to the table data path,
       * false otherwise
       */
      @Override
      public boolean isCellDataPathType(int row, int col, TableDataPath path) {
         if(path.getPath().length == 0) {
            return false;
         }

         String path0 = path.getPath()[0];

         // for location based path, there is only a single match
         if(path0.startsWith("Cell [")) {
            return isCellDataPath(row, col, path);
         }

         col = getBaseColIndex(col);
         int r0 = getBaseRowIndex(row);

         if(r0 >= 0) {
            return descriptor.isCellDataPathType(r0, col, path);
         }
         else {
            int br = findNeighbourBaseHeaderRow(row);

            if(br >= 0) {
               String[] pathstr = path.getPath();
               String[] tpath2 = new String[pathstr.length - 1];
               String r = pathstr[pathstr.length - 1];

               System.arraycopy(pathstr, 0, tpath2, 0, pathstr.length - 1);

               TableDataPath p = new TableDataPath(path.getLevel(),
                                                   path.getType(),
                                                   path.getDataType(),
                                                   tpath2);

               return descriptor.isCellDataPathType(br, col, p) &&
                  r.equals("@" + row);
            }
         }

         return false;
      }

      /**
       * Check if a cell belongs to a table data path.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @param path the specified table data path
       * @return true if the cell belongs to the table data path,
       * false otherwise
       */
      @Override
      public boolean isCellDataPath(int row, int col, TableDataPath path) {
         col = getBaseColIndex(col);
         int r0 = getBaseRowIndex(row);

         if(r0 >= 0) {
            return path.getPath().length == 1 &&
               descriptor.isCellDataPath(r0, col, path);
         }
         else {
            int br = findNeighbourBaseHeaderRow(row);

            if(br >= 0) {
               String[] pathstr = path.getPath();
               String[] tpath2 = new String[pathstr.length - 1];
               String r = pathstr[pathstr.length - 1];

               System.arraycopy(pathstr, 0, tpath2, 0, pathstr.length - 1);

               TableDataPath p = new TableDataPath(path.getLevel(),
                                                   path.getType(),
                                                   path.getDataType(),
                                                   tpath2);

               return descriptor.isCellDataPath(br, col, p) &&
                  r.equals("@" + row);
            }
         }

         return path.getType() == TableDataPath.HEADER &&
            path.getPath()[0].equals("Cell [" + row + "," + col + "]");
      }

      /**
       * Get level of a specified table row, which is required for nested table.
       * The default value is <tt>-1</tt>.
       * @param row the specified table row
       * @return level of the table row
       */
      @Override
      public int getRowLevel(int row) {
         int r0 = getBaseRowIndex(row);

         if(r0 >= 0) {
            return descriptor.getRowLevel(r0);
         }

         return -1;
      }

      /**
       * Get table type which is one of the table types defined in table data
       * descriptor like <tt>NORMAL_TABLE</tt>, <tt>CROSSTAB_TABLE</tt>, etc.
       * @return table type
       */
      @Override
      public int getType() {
         return descriptor.getType();
      }

      /**
       * Get table xmeta info.
       * @param path the specified table data path
       */
      @Override
      public XMetaInfo getXMetaInfo(TableDataPath path) {
         return descriptor.getXMetaInfo(path);
      }

      @Override
      public List<TableDataPath> getXMetaInfoPaths() {
         return descriptor.getXMetaInfoPaths();
      }

      /**
       * Check if contains format.
       * @return true if contains format
       */
      @Override
      public boolean containsFormat() {
         return descriptor.containsFormat();
      }

      /**
       * Check if contains drill.
       * @return <tt>true</tt> if contains drill, <tt>false</tt> otherwise
       */
      @Override
      public boolean containsDrill() {
         return descriptor.containsDrill();
      }

      private TableDataDescriptor descriptor;
   }

   private Hashtable headermap = new Hashtable();
   private int headerRowCount = 1;
   private int[] colmap = null;
   private int[] rowmap = null; // header row map
   private boolean crosstab;
   private TableDataDescriptor hdescriptor;

   private static final Logger LOG =
      LoggerFactory.getLogger(HeaderRowTableLens.class);
}
