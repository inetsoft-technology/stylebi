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
package inetsoft.report.internal.table;

import inetsoft.report.*;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DataTableAssembly;
import inetsoft.uql.asset.internal.ColumnInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.*;

import java.text.Format;
import java.util.*;

/**
 * FormatTableLens2 is used to apply table format.
 *
 * @version 11.5, 1/23/2013
 * @author InetSoft Technology Corp
 */
public class FormatTableLens2 extends FormatTableLens {
   /**
    * Create a format table lens.
    */
   public FormatTableLens2(TableLens table) {
      super(table);
      this.loc = Catalog.getCatalog().getLocale();

      if(this.loc == null) {
         this.loc = Locale.getDefault();
      }
   }

   /**
    * Get the locale.
    * @return the locale.
    */
   @Override
   protected Locale getLocale() {
      return loc;
   }

   /**
    * Get the format map.
    * @return the format map.
    */
   @Override
   public Map<TableDataPath, TableFormat> getFormatMap() {
      return map;
   }

   /**
    * Get the cell object.
    * @return the cell Object.
    */
   @Override
   public Object getObject(int row, int col) {
      if(row < getHeaderRowCount() && col >= 0 && col < headers.length && headers[col] != null) {
         return headers[col];
      }

      return super.getObject(row, col);
   }

   /**
    * Set the column caption.
    */
   public void setHeaderCaption(int col, String caption) {
      headers[col] = caption;
   }

   /**
    * Get table format at a table col.
    * @param col the specified col
    * @return table format at a table col
    */
   @Override
   public TableFormat getColTableFormat(int col) {
      TableDataPath path = table.getDescriptor().getColDataPath(col);
      TableFormat fmt = (TableFormat) map.get(path);

      if(fmt == null || fmt == SparseMatrix.NULL) {
         fmt = super.getColTableFormat(col);
      }

      return fmt;
   }

   /**
    * Add a getCellFormat method for 3 parameters overriding
    * the method in AttributeTableLens.
    * @param r the specified row.
    * @param c the specified col.
    * @param cellOnly specified cellonly.
    * @return cell format for the specified cell.
    */
   @Override
   public Format getCellFormat(int r, int c, boolean cellOnly) {
      checkInit();
      TableFormat colf = r == 0 ? super.getColTableFormat(c) :
         getColTableFormat(c);
      TableFormat rowf = getRowTableFormat(r);
      TableFormat cellf = getCellTableFormat(r, c);
      Format defaultformat = table.getDefaultFormat(r, c);

      if(table instanceof AbstractTableLens) {
         ((AbstractTableLens) table).setLocal(getLocale());
      }

      return mergeFormat(defaultformat,
         attritable == null ? null : attritable.getCellFormat(r, c),
         colf == null ? null : colf.getFormat(getLocale()),
         rowf == null ? null : rowf.getFormat(getLocale()),
         cellf == null ? null : cellf.getFormat(getLocale()));
   }

   /**
    * Get table format from VSCompositeFormat.
    * @return the table format
    */
   public TableFormat getTableFormat(VSCompositeFormat vfmt, DataRef column) {
      TableFormat fmt = new TableFormat();

      if(vfmt != null) {
         boolean numeric = column != null && XSchema.isNumericType(column.getDataType());
         VSFormat vsfmt = vfmt.getUserDefinedFormat();
         fmt.format = vsfmt.getFormat();
         fmt.format_spec = vsfmt.getFormatExtent();
         fmt.background = vsfmt.getBackground();
         fmt.foreground = vsfmt.getForeground();
         fmt.font = vsfmt.getFont();
         fmt.alignment = numeric ? StyleConstants.H_RIGHT : vsfmt.getAlignment();
         fmt.borders = vsfmt.getBorders();
      }

      return fmt;
   }

   /**
    * Add the table format to the formatMap and DataVSAssembly.
    * @param data the assembly which have the table.
    * @param columns the table select columns.
    * @param info the columninfo which will change the format.
    * @param format the current column format.
    */
   public void addTableFormat(DataVSAssembly data, DataTableAssembly assembly,
      ColumnSelection columns, ColumnInfo info, VSCompositeFormat format)
   {
      FormatInfo finfo = data.getDataFormatInfo();

      if(finfo == null) {
         finfo = new FormatInfo();
         finfo.reset();
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         TableDataPath path = getDescriptor().getColDataPath(i);
         DataRef column = columns.getAttribute(i);
         TableFormat fmt = getTableFormat(finfo.getFormat(path), column);
         getFormatMap().put(path, fmt);
         assembly.getTableInfo().getFormatMap().put(column.getName(), finfo.getFormat(path));
      }

      if(info != null && format != null) {
         ColumnRef column = info.getColumnRef();
         int index = columns.indexOfAttribute(column);

         if(index >= 0) {
            TableDataPath path = getDescriptor().getColDataPath(index);
            finfo.setFormat(path, format);
            TableFormat fmt = getTableFormat(format, column);
            getFormatMap().put(path, fmt);
            assembly.getTableInfo().getFormatMap().put(info.getName(), finfo.getFormat(path));
         }
      }
   }

   /**
    * Add the column format to the formatMap.
    * @param columnName the column name which will change the format.
    * @param columns the table select columns.
    * @param format the current column format.
    */
   public void addColumnFormat(String columnName, ColumnSelection columns, VSCompositeFormat format)
   {
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         TableDataPath path = getDescriptor().getColDataPath(i);
         DataRef column = columns.getAttribute(i);

         if(column == null) {
            continue;
         }

         if(Tool.equals(column.getName(), columnName)) {
            getFormatMap().put(path, getTableFormat(format, column));
         }
      }
   }

   private Locale loc;
   private Map<TableDataPath, TableFormat> map = new HashMap<>();
   private String[] headers = new String[table.getColCount()];
}
