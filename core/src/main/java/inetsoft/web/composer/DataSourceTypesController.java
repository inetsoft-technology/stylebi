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
package inetsoft.web.composer;

import inetsoft.web.composer.model.ws.DatabaseDataSources;
import inetsoft.web.composer.model.ws.TabularDataSourceTypeModel;
import inetsoft.web.portal.data.DataSourceBrowserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
public class DataSourceTypesController {
   @Autowired
   public DataSourceTypesController(DataSourceBrowserService dataSourceBrowserService) {
      this.dataSourceBrowserService = dataSourceBrowserService;
   }

   /**
    * Gets the available tabular data source types
    *
    * @return list of tabular data source types
    */
   @GetMapping("/api/composer/tabularDataSourceTypes")
   public List<TabularDataSourceTypeModel> getTabularDataSources(Principal principal)
      throws Exception
   {
      return dataSourceBrowserService.getTabularDataSources(principal);
   }

   @GetMapping("/api/composer/databaseDataSources")
   public DatabaseDataSources getDatabaseDataSources(Principal principal) throws Exception {
      return dataSourceBrowserService.getDatabaseDataSources(principal);
   }

   private final DataSourceBrowserService dataSourceBrowserService;
}
