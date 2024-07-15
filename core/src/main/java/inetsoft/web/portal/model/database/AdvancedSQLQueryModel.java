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
package inetsoft.web.portal.model.database;

import inetsoft.web.composer.model.ws.FreeFormSQLPaneModel;

public class AdvancedSQLQueryModel {
   public AdvancedSQLQueryModel() {
      this.linkPaneModel = new QueryLinkPaneModel();
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public boolean isSqlEdited() {
      return sqlEdited;
   }

   public void setSqlEdited(boolean sqlEdited) {
      this.sqlEdited = sqlEdited;
   }

   public QueryLinkPaneModel getLinkPaneModel() {
      return linkPaneModel;
   }

   public void setLinkPaneModel(QueryLinkPaneModel linkPaneModel) {
      this.linkPaneModel = linkPaneModel;
   }

   public QueryFieldPaneModel getFieldPaneModel() {
      return fieldPaneModel;
   }

   public void setFieldPaneModel(QueryFieldPaneModel fieldPaneModel) {
      this.fieldPaneModel = fieldPaneModel;
   }

   public QueryConditionPaneModel getConditionPaneModel() {
      return conditionPaneModel;
   }

   public void setConditionPaneModel(QueryConditionPaneModel conditionPaneModel) {
      this.conditionPaneModel = conditionPaneModel;
   }

   public FreeFormSQLPaneModel getFreeFormSQLPaneModel() {
      return freeFormSQLPaneModel;
   }

   public void setFreeFormSQLPaneModel(FreeFormSQLPaneModel freeFormSQLPaneModel) {
      this.freeFormSQLPaneModel = freeFormSQLPaneModel;
   }

   public QuerySortPaneModel getSortPaneModel() {
      return sortPaneModel;
   }

   public void setSortPaneModel(QuerySortPaneModel sortPaneModel) {
      this.sortPaneModel = sortPaneModel;
   }

   public QueryGroupingPaneModel getGroupingPaneModel() {
      return groupingPaneModel;
   }

   public void setGroupingPaneModel(QueryGroupingPaneModel groupingPaneModel) {
      this.groupingPaneModel = groupingPaneModel;
   }

   private String name;
   private boolean sqlEdited;
   private QueryLinkPaneModel linkPaneModel;
   private QueryFieldPaneModel fieldPaneModel;
   private QueryConditionPaneModel conditionPaneModel;
   private FreeFormSQLPaneModel freeFormSQLPaneModel;
   private QuerySortPaneModel sortPaneModel;
   private QueryGroupingPaneModel groupingPaneModel;
}
