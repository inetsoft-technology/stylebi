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
package inetsoft.web.portal.data;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.uql.XFactory;
import inetsoft.uql.XRepository;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.util.XUtil;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.DataSourceSettingsModel;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.portal.controller.database.DatabaseModelBrowserService;
import inetsoft.web.portal.model.database.StringWrapper;
import inetsoft.web.portal.model.database.events.CheckDependenciesEvent;
import inetsoft.web.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@RestController
public class DatabaseDatasourcesController {
   @Autowired
   public DatabaseDatasourcesController(DatabaseDatasourcesService databaseDatasourcesService,
                                        DatabaseModelBrowserService databaseModelBrowserService,
                                        DataModelFolderManagerService folderManagerService)
   {
      this.databaseDatasourcesService = databaseDatasourcesService;
      this.databaseModelBrowserService = databaseModelBrowserService;
      this.folderManagerService = folderManagerService;
   }


   /**
    * Retrieve the definition of a database connection for a given data source.
    *
    * @param path path of datasource
    *
    * @return definition the database definition.
    */
   @GetMapping("/api/portal/data/databases/**")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.WRITE
      )
   })
   public DataSourceSettingsModel getDataSourceModel(@RemainingPath @PermissionPath String path,
                                                     Principal principal)
      throws Exception
   {
      return DataSourceSettingsModel.builder()
         .dataSource(databaseDatasourcesService.getDatabaseDefinition(path, principal))
         .additionalDataSources(databaseDatasourcesService.getAdditionalDatabaseDefinition(path, principal))
         .permissions(null)
         .uploadEnabled(databaseDatasourcesService.isUploadEnabled(principal))
         .build();
   }

   /**
    * Save the definition of a database connection for a given data source.
    *
    * @param path path of datasource
    *
    * @return definition the database definition.
    */
   @PostMapping("/api/portal/data/databases/**")
   public ConnectionStatus setDataSourceModel(@RemainingPath() String path,
                                              @RequestBody() DataSourceSettingsModel model,
                                              Principal principal)
      throws Exception
   {
      String actionName = databaseDatasourcesService.getActionName(path,
         model.dataSource().getName());

      return databaseDatasourcesService.saveDatabase(path, model ,actionName,
         principal);
   }

   @PutMapping("/api/portal/data/databases/additional/check/**")
   public DeleteDatasourceInfo checkStatus(@RemainingPath() String path,
                                           @RequestBody DeleteDatasourceInfo deleteInfo)
      throws Exception
   {
      return databaseDatasourcesService.additionalDeletable(path, deleteInfo);
   }

   @PostMapping("/api/portal/data/databases/additional/test")
   public ConnectionStatus testDataSourceConnection(@RequestParam("path") String path,
                                                    @RequestParam("isAdditionalSource")
                                                    boolean isAdditionalSource,
                                                    @RequestBody() DatabaseDefinition model,
                                                    Principal principal)
   {
      return this.databaseDatasourcesService.testDataSourceConnection(path, model, principal,
                                                                      isAdditionalSource);
   }

   @GetMapping("/api/portal/data/datasource/refresh-metadata")
   public boolean refreshMetadata(@RequestParam("dataSource") String dataSource) {
      try {
         XRepository repository = XFactory.getRepository();

         if(dataSource != null && repository instanceof XEngine) {
            repository.refreshMetaData(dataSource);
         }

         XFactory.clear();
         return true;
      }
      catch(Exception ignored) {
      }

      return false;
   }

   /**
    * Save the definition of a database connection for a given data source.
    *
    * @param database path of database.
    *
    * @return definition the database definition.
    */
   @GetMapping("/api/portal/data/database/additionConnections")
   public List<String> getAdditionalConnections(@RequestParam("database") String database,
                                                Principal principal)
      throws Exception
   {
      List<String> result = new ArrayList<>();
      String[] connections = XUtil.getConnections(getXRepository(), database);

      if(connections == null) {
         return result;
      }

      for(String connection : connections) {
         if(databaseDatasourcesService.checkAdditionalPermission(database, connection,
            ResourceAction.READ, principal))
         {
            result.add(connection);
         }
      }

      return result;
   }

   /**
    * Gets the data repository instance.
    *
    * @return the repository.
    *
    * @throws Exception if the repository could not be obtained.
    */
   protected XRepository getXRepository() throws Exception {
      return XFactory.getRepository();
   }

   /**
    * add a new data source folder.
    */
   @PostMapping("/api/portal/data/database/dataModelFolder")
   public void addDataModelFolder(
      @RequestBody @PermissionPath("parentPath()") AddFolderRequest request, Principal principal)
      throws Exception
   {
      String name = request.name();
      String parentPath = request.parentPath();

      if(parentPath == null || name == null) {
         return;
      }

      folderManagerService.setDataModelFolder(parentPath, name, principal);
   }

   /**
    * Check outer dependencies for logical models under the target folder;
    */
   @PostMapping("/api/data/database/dataModelFolder/checkOuterDependencies")
   public StringWrapper checkOuterDependencies(@RequestBody CheckDependenciesEvent event)
      throws Exception
   {
      if(event == null || event.isNewCreate()) {
         return null;
      }

      return folderManagerService.checkOuterDependencies(
         event.getDatabaseName(), event.getDataModelFolder());
   }

   /**
    * Delete a new data source folder.
    */
   @DeleteMapping("/api/portal/data/database/dataModelFolder")
   public void deleteDataModelFolder(@RequestParam("databasePath") String databasePath,
                                              @RequestParam("folderName") String folderName,
                                              Principal principal)
      throws Exception
   {
      folderManagerService.deleteDataModelFolder(databasePath, folderName, principal);
   }

   /**
    * Rename a new data source folder.
    */
   @PutMapping("/api/portal/data/database/dataModelFolder")
   public void renameDataModelFolder(@RequestBody RenameFolderRequest request, Principal principal)
      throws Exception
   {
      String oldPath = request.path();
      String newName = request.name();
      String databasePath;

      if(oldPath == null) {
         return;
      }

      int index = oldPath.lastIndexOf("/");

      if(index < 0 || index == oldPath.length() - 1) {
         return;
      }

      databasePath = oldPath.substring(0, index);
      String oldName = oldPath.substring(index + 1);
      databaseModelBrowserService.renameDataModelFolder(databasePath, newName, oldName, principal);
   }

   /**
    * Rename a new data source folder.
    */
   @GetMapping("/api/portal/data/database/dataModelFolder/duplicateCheck")
   public boolean dataModelFolderDuplicateCheck(@RequestParam("databasePath") String databasePath,
                                                @RequestParam("name") String folderName)
      throws Exception
   {
      return databaseModelBrowserService.dataModelFolderDuplicateCheck(databasePath, folderName);
   }

   /**
    * Build database custom url.
    */
   @PostMapping("/api/portal/data/database/customUrl")
   public String buildDatabaseCustomUrl(@RequestParam("path") String path, @RequestBody() DatabaseDefinition model) {
      return this.databaseDatasourcesService.buildDatabaseCustomUrl(path, model);
   }

   /**
    * Build database definition.
    */
   @PostMapping("/api/portal/data/database/definition")
   public DatabaseDefinition buildDatabaseDefinition(@RequestBody() DatabaseDefinition model) {
      return this.databaseDatasourcesService.buildDatabaseDefinition(model);
   }

   private final DatabaseDatasourcesService databaseDatasourcesService;
   private final DatabaseModelBrowserService databaseModelBrowserService;
   private final DataModelFolderManagerService folderManagerService;
}
