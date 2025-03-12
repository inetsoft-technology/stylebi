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
package inetsoft.web.admin.content.repository;

import inetsoft.report.internal.Util;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DriverAvailability;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.portal.data.DataSourceBrowserService;
import inetsoft.web.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Objects;

@RestController
public class RepositoryDataSourcesController {
   @Autowired
   public RepositoryDataSourcesController(DatabaseDatasourcesService databaseDatasourcesService,
                                          ResourcePermissionService permissionService)
   {
      this.databaseDatasourcesService = databaseDatasourcesService;
      this.permissionService = permissionService;
   }

   @GetMapping("/api/em/settings/content/repository/dataSource/driverAvailability")
   public DriverAvailability getDriverAvailability() {
      return this.databaseDatasourcesService.getDriverAvailability();
   }

   @GetMapping("/api/em/settings/content/repository/dataSourceFolder")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE_FOLDER, actions = ResourceAction.ADMIN
      )
   })
   public DataSourceFolderSettingsModel getFolderModel(
      @PermissionPath @RequestParam("path") String path, Principal principal) throws Exception
   {
      return databaseDatasourcesService.getDataSourceFolder(path, principal);
   }

   @PostMapping("/api/em/settings/content/repository/dataSourceFolder")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE_FOLDER, actions = ResourceAction.ADMIN
      )
   })
   public DataSourceFolderSettingsModel setFolderModel(
      @PermissionPath @RequestParam("path") String path,
      @RequestBody DataSourceFolderSettingsModel model, Principal principal) throws Exception
   {
      String fullPath = Util.getObjectFullPath(RepositoryEntry.DATA_SOURCE_FOLDER, path, principal);
      String newPath = databaseDatasourcesService.setDataSourceFolder(path, fullPath, model,
                                                                      principal);
      return databaseDatasourcesService.getDataSourceFolder(newPath, principal);
   }

   /**
    * Retrieve the definition of a database connection for a given data source.
    *
    * @param path path of datasource
    *
    * @return definition the database definition.
    */
   @GetMapping("/api/data/databases")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.ADMIN
      )
   })
   public DataSourceSettingsModel getDataSourceModel(@PermissionPath @RequestParam("path") String path,
                                                     Principal principal)
      throws Exception
   {
      return DataSourceSettingsModel.builder()
         .dataSource(databaseDatasourcesService.getDatabaseDefinition(path, principal))
         .permissions(permissionService.getTableModel(
            path, ResourceType.DATA_SOURCE, ResourcePermissionService.ADMIN_ACTIONS, principal))
         .uploadEnabled(true)
         .build();
   }

   /**
    * Save the definition of a database connection for a given data source.
    *
    * @param path path of datasource
    *
    * @return definition the database definition.
    */
   @PostMapping("/api/data/databases")
   public ConnectionStatus setDataSourceModel(@RequestParam("path") String path,
                                              @RequestBody() DataSourceSettingsModel model,
                                              Principal principal)
      throws Exception
   {
      ConnectionStatus status = null;

      String fullPath = this.databaseDatasourcesService.getDataSourceAuditPath(path,
         model.dataSource(), principal);

      if(model.dataSource() != null) {
         String actionName = databaseDatasourcesService.getActionName(path,
            model.dataSource().getName());
         DatabaseDefinition database = model.dataSource();
         String actionError = null;

         if(database != null) {
            actionError = Tool.equals(database.getName(), database.getOldName()) ? null :
            "new Name:" + database.getName();
         }

         status = databaseDatasourcesService.saveDatabase(path,
            model, actionName, fullPath, actionError, principal);
      }

      if(model.permissions() != null && model.permissions().changed()) {
         permissionService.setResourcePermissions(
            path, ResourceType.DATA_SOURCE, fullPath, model.permissions(), principal);
      }

      return status;
   }

   @PostMapping("/api/data/databases/test")
   public ConnectionStatus testDataSourceConnection(@RequestParam("path") String path,
                                                    @RequestBody() DataSourceSettingsModel model,
                                                    Principal principal)
   {
      return this.databaseDatasourcesService.testDataSourceConnection(path, model, principal);
   }

   @GetMapping("/api/data/database/default")
   public DataSourceSettingsModel getDefaultModel(Principal principal) {
      return this.databaseDatasourcesService.getDefaultDatabase(principal);
   }

   @GetMapping("/api/data/database/listing/**")
   public DataSourceSettingsModel getDatabaseFromListing(@RemainingPath String listingName,
                                                         Principal principal) throws Exception
   {
      return this.databaseDatasourcesService.getDatabaseFromListing(listingName, principal);
   }

   @GetMapping("/api/data/database/test-query/default")
   public String getDefaultTestQuery(@RequestParam("type") String dbType,
                                     @RequestParam("driver") String driver)
   {
      if(CustomDatabaseType.TYPE.equals(dbType)) {
         dbType = Config.getJDBCType(driver);
      }

      SQLHelper sqlHelper = SQLHelper.getSQLHelper(Objects.toString(dbType, "").toLowerCase());

      return sqlHelper.getConnectionTestQuery();
   }

   private final DatabaseDatasourcesService databaseDatasourcesService;
   private final ResourcePermissionService permissionService;
}
