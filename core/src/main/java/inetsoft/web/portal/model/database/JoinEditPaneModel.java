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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The physical join edit pane model.
 */
@JsonIgnoreProperties
public class JoinEditPaneModel {

   public JoinEditPaneModel() {
   }

   public JoinEditPaneModel(String runtimeID, String datasource, String physicalView,
                            List<TableGraphModel> tables)
   {
      this.runtimeID = runtimeID;
      this.datasource = datasource;
      this.physicalView = physicalView;
      this.tables = tables;
   }

   public List<TableGraphModel> getTables() {
      return tables;
   }

   public void setTables(List<TableGraphModel> tables) {
      this.tables = tables;
   }

   public String getRuntimeID() {
      return runtimeID;
   }

   public void setRuntimeID(String runtimeID) {
      this.runtimeID = runtimeID;
   }

   public String getDatasource() {
      return datasource;
   }

   public void setDatasource(String datasource) {
      this.datasource = datasource;
   }

   public String getPhysicalView() {
      return physicalView;
   }

   public void setPhysicalView(String physicalView) {
      this.physicalView = physicalView;
   }

   private String runtimeID;
   private String datasource;
   private String physicalView;
   private List<TableGraphModel> tables;
}
