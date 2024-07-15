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
package inetsoft.report.internal.binding;

/**
 * Crosstab sort info.
 */
public class CrosstabSortInfo {
   /**
    * Constructor.
    */
   public CrosstabSortInfo(int[] cols, boolean[] asc) {
      this.cols = cols;
      this.asc = asc;
   }
   
   public boolean isEmpty() {
      return cols == null || cols.length == 0;
   }
   
   public String toString() {
      StringBuilder sb = new StringBuilder();

      sb.append("[");
      
      for(int i = 0; cols != null && i < cols.length; i++) {
         if(i > 0) {
            sb.append("; ");
         }
         
         sb.append(cols[i]);
         sb.append(":");
         sb.append(asc[i] ? "ascending" : "descending");
      }
      
      sb.append("]");
      return sb.toString();
   }

   public int[] cols;
   public boolean[] asc;
} 