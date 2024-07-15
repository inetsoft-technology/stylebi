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
package inetsoft.web.composer.ws.assembly.tableinfo;

import inetsoft.uql.asset.internal.TabularTableAssemblyInfo;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.util.Catalog;
import inetsoft.web.composer.model.ws.TabularDataSourceTypeModel;

import java.security.Principal;

public class TabularTableAssemblyInfoModel extends BoundTableAssemblyInfoModel {
   public TabularTableAssemblyInfoModel(TabularTableAssemblyInfo info, Principal principal) {
      super(info);

      Catalog catalog = Catalog.getCatalog(principal);
      TabularQuery query = info.getQuery();
      String type = query.getType();
      dataSourceType = new TabularDataSourceTypeModel();
      dataSourceType.setName(type);
      dataSourceType.setExists(true);

      if(query.getDataSource() != null) {
         dataSourceType.setLabel(catalog.getString(query.getDataSource().getName()));
         dataSourceType.setDataSource(query.getDataSource().getFullName());
      }
   }

   public TabularDataSourceTypeModel getDataSourceType() {
      return dataSourceType;
   }

   public void setDataSourceType(
      TabularDataSourceTypeModel dataSourceType)
   {
      this.dataSourceType = dataSourceType;
   }

   private TabularDataSourceTypeModel dataSourceType;
}
