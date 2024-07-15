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
package inetsoft.mv.trans;

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.internal.VSUtil;

/**
 * WSColumn stores the required information of one table column for mv
 * transformation. It's required when transforming tables.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public final class WSColumn {
   /**
    * Create a worksheet column reference.
    * @param table the specified table assembly in worksheet.
    * @param ref the specified data ref, which is used in viewsheet.
    */
   public WSColumn(String table, DataRef ref) {
      super();

      this.table = table;
      this.ref = ref;
   }

   /**
    * Get the name of the data table containing selection column.
    */
   public String getTableName() {
      return table;
   }

   /**
    * Get the column reference of the data table column.
    */
   public DataRef getDataRef() {
      return ref;
   }

   /**
    * Check if two selection column definition are equivalent.
    */
   public boolean equals(Object obj) {
      try {
         WSColumn sel = (WSColumn) obj;
         return ref.equals(sel.ref) && table.equals(sel.table);
      }
      catch(Exception ex) {
         return false;
      }
   }

   /**
    * Get the column hashcode.
    */
   public int hashCode() {
      return table.hashCode() + ref.hashCode();
   }

   /**
    * Sort columns by table name first.
    */
   public int compareTo(Object obj) {
      if(!(obj instanceof WSColumn)) {
         return 0;
      }

      WSColumn col = (WSColumn) obj;
      int rc = table.compareTo(col.table);

      if(rc == 0) {
         rc = VSUtil.getAttribute(ref).compareTo(VSUtil.getAttribute(col.ref));
      }

      return rc;
   }

   /**
    * Get the range info.
    */
   public RangeInfo getRangeInfo() {
      return rinfo;
   }

   /**
    * Set the range info.
    */
   public void setRangeInfo(RangeInfo rinfo) {
      this.rinfo = rinfo;
   }

   /**
    * Return a string description.
    */
   public String toString() {
      return "WSColumn@" + System.identityHashCode(this) + "[" + table + "." + ref + "]";
   }

   /**
    * Clone this object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(CloneNotSupportedException ex) {
         return this;
      }
   }

   private String table;
   private DataRef ref;
   private RangeInfo rinfo;
}
