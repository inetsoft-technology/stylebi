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
package inetsoft.uql.asset;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;

import java.io.PrintWriter;

/**
 * Data table assembly, the <tt>TableAssembly</tt> contains data directly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class DataTableAssembly extends AbstractTableAssembly {
   /**
    * Constructor.
    */
   public DataTableAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public DataTableAssembly(Worksheet ws, String name) {
      super(ws, name);
   }

   /**
    * Get the data of this table assembly.
    * @return the data of this table assembly.
    */
   public XTable getData() {
      return data;
   }

   /**
    * Set the data to this table assembly.
    * @param data the specified data.
    */
   public void setData(XTable data) {
      this.data = data;
      ColumnSelection columns = new ColumnSelection();

      for(int i = 0; i < data.getColCount(); i++) {
         String header = AssetUtil.format(XUtil.getHeader(data, i));
         AttributeRef attr = new AttributeRef(null, header);
         ColumnRef column = new ColumnRef(attr);
         column.setDataType(Tool.getDataType(data.getColType(i)));
         columns.addAttribute(column, false);
      }

      setColumnSelection(columns, false);
   }

   /**
    * Get the source of the table assembly.
    * @return the source of the table assembly.
    */
   @Override
   public String getSource() {
      return null;
   }

   /**
    * Get the hash code only considering content.
    * @return the hash code only considering content.
    */
   @Override
   public int getContentCode() {
      int hash = super.getContentCode();

      if(data != null) {
         hash = hash ^ data.hashCode();
      }

      return hash;
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      return false;
   }

   /**
    * Check if equals another object in content.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(!(obj instanceof DataTableAssembly)) {
         return false;
      }

      DataTableAssembly table = (DataTableAssembly) obj;
      return Tool.equals(table.data, data);
   }

   /**
    * update properties of table.
    */
   @Override
   public void updateTable(TableAssembly table) {
      super.updateTable(table);

      if(!(table instanceof DataTableAssembly)) {
         return;
      }

      DataTableAssembly dtable = (DataTableAssembly) table;

      if(!Tool.equals(dtable.data, data)) {
         setData(dtable.data);
      }
   }

   private XTable data;
}
