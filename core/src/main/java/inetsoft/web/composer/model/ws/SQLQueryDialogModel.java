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
package inetsoft.web.composer.model.ws;

import inetsoft.web.portal.model.database.AdvancedSQLQueryModel;

import java.util.ArrayList;
import java.util.List;

public class SQLQueryDialogModel {
   public SQLQueryDialogModel() {
   }

   public boolean isAdvancedEdit() {
      return advancedEdit;
   }

   public void setAdvancedEdit(boolean advancedEdit) {
      this.advancedEdit = advancedEdit;
   }

   public BasicSQLQueryModel getSimpleModel() {
      return simpleModel;
   }

   public void setSimpleModel(BasicSQLQueryModel simpleModel) {
      this.simpleModel = simpleModel;
   }

   public AdvancedSQLQueryModel getAdvancedModel() {
      return advancedModel;
   }

   public void setAdvancedModel(AdvancedSQLQueryModel advancedModel) {
      this.advancedModel = advancedModel;
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

   public boolean isMashUpData() {
      return mashUpData;
   }

   public void setMashUpData(boolean mashUpData) {
      this.mashUpData = mashUpData;
   }

   public boolean isCloseDialog() {
      return closeDialog;
   }

   public void setCloseDialog(boolean closeDialog) {
      this.closeDialog = closeDialog;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getRuntimeId() {
      return runtimeId;
   }

   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   public String getDataSource() {
      return dataSource;
   }

   public void setDataSource(String dataSource) {
      this.dataSource = dataSource;
   }

   public String[] getVariableNames() {
      if(variableNames == null) {
         variableNames = new String[0];
      }

      return variableNames;
   }

   public void setVariableNames(String[] variableNames) {
      this.variableNames = variableNames;
   }

   public boolean isPhysicalTablesEnabled() {
      return physicalTablesEnabled;
   }

   public void setPhysicalTablesEnabled(boolean physicalTablesEnabled) {
      this.physicalTablesEnabled = physicalTablesEnabled;
   }

   public boolean isFreeFormSqlEnabled() {
      return freeFormSqlEnabled;
   }

   public void setFreeFormSqlEnabled(boolean freeFormSqlEnabled) {
      this.freeFormSqlEnabled = freeFormSqlEnabled;
   }

   public List<Boolean> getSupportsFullOuterJoin() {
      if(supportsFullOuterJoin == null) {
         supportsFullOuterJoin = new ArrayList<>();
      }

      return supportsFullOuterJoin;
   }

   public void setSupportsFullOuterJoin(List<Boolean> supportsFullOuterJoin) {
      this.supportsFullOuterJoin = supportsFullOuterJoin;
   }

   private boolean mashUpData;
   private boolean closeDialog;
   private String name;
   private String runtimeId;
   private List<String> dataSources;
   private String dataSource;
   private String[] variableNames;
   private boolean advancedEdit;
   private boolean physicalTablesEnabled;
   private boolean freeFormSqlEnabled;
   private List<Boolean> supportsFullOuterJoin;
   private BasicSQLQueryModel simpleModel;
   private AdvancedSQLQueryModel advancedModel;
}
