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
package inetsoft.uql.util;

import inetsoft.uql.XTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * XIdentifierContainer contains and manages column identifiers.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class XIdentifierContainer implements Serializable, Cloneable {
   /**
    * Constructor.
    */
   public XIdentifierContainer(XTable table) {
      this.table = table;
   }

   /**
    * Get the column identifier of a column.
    * @param col the specified column index.
    * @return the column indentifier of the column, <tt>null</tt> not specified.
    * The identifier might be different from the column name, for it may contain
    * more locating information than the column name.
    */
   public String getColumnIdentifier(int col) {
      return identifiers == null || identifiers.length <= col || col < 0 ?
         null :
         identifiers[col];
   }

   /**
    * Find the column by identifier.
    */
   public final int indexOfColumnIdentifier(String identifier) {
      if(identifiers == null || identifier == null) {
         return -1;
      }

      for(int i = 0; i < identifiers.length; i++) {
         if(identifier.equals(identifiers[i])) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Set the column identifier of a column.
    * @param col the specified column index.
    * @param identifier the column indentifier of the column. The identifier
    * might be different from the column name, for it may contain more
    * locating information than the column name.
    */
   public void setColumnIdentifier(int col, String identifier) {
      if(col < 0 || col >= table.getColCount()) {
         return;
      }

      if(identifiers == null || identifiers.length != table.getColCount()) {
         String[] nidentifiers = new String[table.getColCount()];

         if(identifiers != null) {
            System.arraycopy(identifiers, 0, nidentifiers, 0,
                             Math.min(identifiers.length, nidentifiers.length));
         }

         identifiers = nidentifiers;
      }

      identifiers[col] = identifier;
   }

   /**
    * Get the base table.
    * @return the table.
    */
   public XTable getTable() {
      return table;
   }

   /**
    * Set the base table.
    * @param table the specified table.
    */
   public void setTable(XTable table) {
      this.table = table;
   }

   public void removeIdentifier(int col) {
      if(identifiers == null) {
         return;
      }

      String[] nIdentifiers = new String[identifiers.length - 1];
      System.arraycopy(identifiers, 0, nIdentifiers, 0, col);
      System.arraycopy(identifiers, col + 1, nIdentifiers, col, identifiers.length - col - 1);
      identifiers = nIdentifiers;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone XIdentifierContainer", ex);
         return null;
      }
   }

   private XTable table;
   private String[] identifiers;

   private static final Logger LOG =
      LoggerFactory.getLogger(XIdentifierContainer.class);
}
