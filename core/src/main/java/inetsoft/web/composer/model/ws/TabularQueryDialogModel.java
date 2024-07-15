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
package inetsoft.web.composer.model.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.uql.tabular.TabularView;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TabularQueryDialogModel {
   public String getDataSource() {
      return dataSource;
   }

   public void setDataSource(String dataSource) {
      this.dataSource = dataSource;
   }

   public List<String> getDataSources() {
      return dataSources;
   }

   public void setDataSources(List<String> dataSources) {
      this.dataSources = dataSources;
   }

   public String getTableName() {
      return tableName;
   }

   public void setTableName(String tableName) {
      this.tableName = tableName;
   }

   public TabularView getTabularView() {
      return tabularView;
   }

   public void setTabularView(TabularView tabularView) {
      this.tabularView = tabularView;
   }

   private String dataSource;
   private List<String> dataSources;
   private String tableName;
   private TabularView tabularView;
}
