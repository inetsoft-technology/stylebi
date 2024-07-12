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
package inetsoft.uql.util.filereader;

import java.util.*;

public class DateParseInfo {
   public DateParseInfo() {
      super();
   }

   public Boolean isDmyOrder(int col) {
      return dmyOrderArr != null && dmyOrderArr.length > col ? dmyOrderArr[col] : null;
   }

   public void setDmyOrder(int col, Boolean dmyOrder) {
      if(dmyOrderArr != null && dmyOrderArr.length > col) {
         dmyOrderArr[col] = dmyOrder;
      }
   }

   public void setDmyOrderArr(Boolean[] dmyOrderArr) {
      this.dmyOrderArr = dmyOrderArr;
   }

   public Map<Integer, String> getProspectTypeMap() {
      if(prospectTypeMap == null) {
         prospectTypeMap = new HashMap<>();
      }

      return prospectTypeMap;
   }

   public void setProspectTypeMap(Map<Integer, String> prospectTypeMap) {
      this.prospectTypeMap = prospectTypeMap;
   }

   public List<Integer> getIgnoreTypeColumns() {
      if(ignoreTypeColumns == null) {
         ignoreTypeColumns = new ArrayList<>();
      }

      return ignoreTypeColumns;
   }

   public void setIgnoreTypeColumns(List<Integer> ignoreTypeColumns) {
      this.ignoreTypeColumns = ignoreTypeColumns;
   }

   private Boolean dmyOrderArr[]; // day before month flag for each column.
   // key-> col index, value: prospect type for the column.
   private Map<Integer, String> prospectTypeMap;
   // columns don't need to detect types.
   private List<Integer> ignoreTypeColumns;
}