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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.internal.Util;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.jdbc.XJoin;
import inetsoft.util.ItemList;
import inetsoft.web.portal.model.database.DataConditionItem;

import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BasicSQLQueryModel {
   public BasicSQLQueryModel() {
      super();
      this.maxColCount = Util.getOrganizationMaxColumn();
   }

   public List<String> getDataSources() {
      if(dataSources == null) {
         dataSources = new ArrayList<>();
      }

      return dataSources;
   }

   public void setDataSources(List<String> dataSources) {
      this.dataSources = dataSources;
   }

   public Map<String, AssetEntry> getTables() {
      return tables;
   }

   public void setTables(Map<String, AssetEntry> tables) {
      this.tables = tables;
   }

   public void convertTables(AssetEntry[] tables) {
      this.tables = new HashMap<>();

      if(tables == null) {
         return;
      }

      for(AssetEntry entry : tables) {
         if(entry == null) {
            continue;
         }

         this.tables.put(entry.getProperty("source"), entry);
      }
   }

   public SQLQueryDialogColumnModel[] getColumns() {
      return columns;
   }

   public void setColumns(SQLQueryDialogColumnModel[] columns) {
      this.columns = columns;
   }

   public void convertColumnInfo(ItemList columns) {
      if(columns == null) {
         this.columns = new SQLQueryDialogColumnModel[0];
         return;
      }

      this.columns = new SQLQueryDialogColumnModel[columns.size()];

      for(int i = 0; i < columns.size(); i++) {
         final String name = (String) columns.getItem(i);
         this.columns[i] = SQLQueryDialogColumnModel.builder()
            .name(name)
            .build();
      }
   }

   public JoinItemModel[] getJoins() {
      if(joins == null) {
         joins = new JoinItemModel[0];
      }

      return joins;
   }

   public XJoin[] toXJoins() {
      XJoin[] xjoins = new XJoin[getJoins().length];

      for(int i = 0; i < joins.length; i++) {
         xjoins[i] = getJoins()[i].toXJoin();
      }

      return xjoins;
   }

   public void setJoins(JoinItemModel[] joins) {
      this.joins = joins;
   }

   public void convertJoins(XJoin[] joins, UniformSQL sql) {
      if(joins == null) {
         this.joins = new JoinItemModel[0];
         return;
      }

      this.joins = new JoinItemModel[joins.length];

      for(int i = 0; i < joins.length; i++) {
         this.joins[i] = new JoinItemModel(joins[i], sql);
      }
   }

   public List<DataConditionItem> getConditionList() {
      if(conditionList == null) {
         conditionList = new ArrayList<>();
      }

      return conditionList;
   }

   public void setConditionList(List<DataConditionItem> conditionList) {
      this.conditionList = conditionList;
   }

   public String getSqlString() {
      return sqlString;
   }

   public void setSqlString(String sqlString) {
      this.sqlString = sqlString;
   }

   public boolean isSqlEdited() {
      return sqlEdited;
   }

   public void setSqlEdited(boolean sqlEdited) {
      this.sqlEdited = sqlEdited;
   }

   public boolean getSqlEdited() {
      return sqlEdited;
   }

   public void setMaxColumnCount(int max) {
      this.maxColCount = max;
   }

   public int getMaxColumnCount() {
      return maxColCount;
   }

   @JsonIgnore
   public String[] getSelectedColumns() {
      if(getColumns() == null) {
         return new String[0];
      }

      return Arrays.stream(getColumns())
         .map(SQLQueryDialogColumnModel::name)
         .toArray(String[]::new);
   }

   public String getSqlParseResult() {
      return sqlParseResult;
   }

   public void setSqlParseResult(String sqlParseResult) {
      this.sqlParseResult = sqlParseResult;
   }

   private String name;
   private List<String> dataSources;
   private Map<String, AssetEntry> tables;
   private SQLQueryDialogColumnModel[] columns;
   private JoinItemModel[] joins;
   private List<DataConditionItem> conditionList;
   private String sqlString;
   private String sqlParseResult;
   private boolean sqlEdited;
   private int maxColCount;
}
