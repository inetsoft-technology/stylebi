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
package inetsoft.web.composer.model.ws;

import java.util.Map;

public class SortColumnEditorModel {
   public String[] getAvailableColumns() {
      return availableColumns;
   }

   public void setAvailableColumns(String[] availableColumns) {
      this.availableColumns = availableColumns;
   }

   public String[] getColumnDescriptions() {
      return columnDescriptions;
   }

   public void setColumnDescriptions(String[] descs) {
      this.columnDescriptions = descs;
   }

   public Map<String, String> getAliasMap() {
      return aliasMap;
   }

   public void setAliasMap(Map<String, String> aliasMap) {
      this.aliasMap = aliasMap;
   }

   public String[] getSelectedColumns() {
      return selectedColumns;
   }

   public void setSelectedColumns(String[] selectedColumns) {
      this.selectedColumns = selectedColumns;
   }

   public int[] getOrders() {
      return orders;
   }

   public void setOrders(int[] orders) {
      this.orders = orders;
   }

   public String[] getOriginalNames() {
      return originalNames;
   }

   public void setOriginalNames(String[] originalNames) {
      this.originalNames = originalNames;
   }

   public String[] getRangeColumns() {
      return rangeColumns;
   }

   public void setRangeColumns(String[] rangeColumns) {
      this.rangeColumns = rangeColumns;
   }

   private String[] availableColumns;
   private String[] columnDescriptions;
   private Map<String, String> aliasMap;
   private String[] selectedColumns;
   private int[] orders;
   private String[] originalNames;
   private String[] rangeColumns;
}
