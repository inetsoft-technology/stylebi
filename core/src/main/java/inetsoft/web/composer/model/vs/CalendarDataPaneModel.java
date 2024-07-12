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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.web.composer.model.TreeNodeModel;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CalendarDataPaneModel {
   public OutputColumnRefModel getSelectedColumn() {
      return selectedColumn;
   }

   public void setSelectedColumn(OutputColumnRefModel selectedColumn) {
      this.selectedColumn = selectedColumn;
   }

   public String getSelectedTable() {
      return selectedTable;
   }

   public void setSelectedTable(String selectedTable) {
      this.selectedTable = selectedTable;
   }

   public List<String> getAdditionalTables() {
      return additionalTables;
   }

   public void setAdditionalTables(List<String> additionalTables) {
      this.additionalTables = additionalTables;
   }

   public TreeNodeModel getTargetTree() {
      return targetTree;
   }

   public void setTargetTree(TreeNodeModel targetTree) {
      this.targetTree = targetTree;
   }

   private OutputColumnRefModel selectedColumn;
   private String selectedTable;
   private List<String> additionalTables;
   private TreeNodeModel targetTree;
}
