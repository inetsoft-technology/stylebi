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
package inetsoft.web.portal.controller.database;

import inetsoft.sree.security.ResourceAction;
import inetsoft.util.Catalog;
import inetsoft.web.portal.data.DataModelBrowserModel;
import inetsoft.web.portal.data.SearchDataCommand;
import inetsoft.web.portal.model.database.DatabaseDataModelBrowserModel;
import inetsoft.web.portal.model.database.StringWrapper;
import inetsoft.web.portal.model.database.events.MoveDataModelEvent;
import inetsoft.web.portal.model.database.events.RemoveDataModelEvent;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class DatabaseModelBrowserController {
   public DatabaseModelBrowserController(DatabaseModelBrowserService databaseAssetBrowserService,
                                         DataSourceService dataSourceService) {
      this.databaseAssetBrowserService = databaseAssetBrowserService;
      this.dataSourceService = dataSourceService;
   }

   /**
    * Determines if this user has write access on this datasource.
    */
   @GetMapping(value = "/api/data/database/permissions")
   public boolean checkPermission(@RequestParam("database") String database, Principal principal)
      throws Exception
   {
      return dataSourceService.checkPermission(database, ResourceAction.WRITE, principal);
   }

   /**
    * Gets the selected logical model.
    * @return the DTO structure logical model
    */
   @GetMapping(value = "/api/data/database/dataModel/browse")
   public DatabaseDataModelBrowserModel getDataModels(@RequestParam("database") String database,
                                                      @RequestParam(value = "folder", required = false) String folder,
                                                      Principal principal)
      throws Exception
   {
      return databaseAssetBrowserService.getDataModelBrowseModel(database, folder, principal, false);
   }

   /**
    * Get the data model browser model.
    */
   @GetMapping(value = "/api/data/database/dataModel/folder/browser")
   public DataModelBrowserModel getDataModelFolders(
      @RequestParam("database") String database,
      @RequestParam(value = "root", required = false) boolean root,
      Principal principal) throws Exception
   {
      return databaseAssetBrowserService.getBrowserModel(database, root, principal);
   }


   @PostMapping("api/data/search/dataModel/names")
   public DatabaseDataModelBrowserModel getSearchDataModelNames(@RequestBody SearchDataCommand command,
                                                                Principal principal)
      throws Exception
   {
      return databaseAssetBrowserService.getSearchDataModelNames(
         command.getDatabase(), command.getPath(), command.getQuery(), principal, true);
   }

   @PostMapping("api/data/search/dataModel")
   public DatabaseDataModelBrowserModel getSearchDataModel(@RequestBody SearchDataCommand command,
                                                           Principal principal)
      throws Exception
   {
      return databaseAssetBrowserService.getSearchDataModel(
         command.getDatabase(), command.getPath(), command.getQuery(), principal);
   }

   /**
    * Move the physical view or logical model to folder.
    * @param event
    * @throws Exception
    */
   @PostMapping("/api/data/database/dataModel/move")
   public void moveDataModels(@RequestBody MoveDataModelEvent event, Principal principal)
      throws Exception
   {
      databaseAssetBrowserService.moveDataModels(event.getDatabase(), event.getItems(),
         event.getFolder(), principal);
   }

   /**
    * delete the physical view, logical model, data model folder.
    * @param event
    * @throws Exception
    */
   @PostMapping("/api/data/database/dataModel/remove")
   public StringWrapper deleteDataModels(@RequestBody RemoveDataModelEvent event, Principal principal)
      throws Exception
   {
      StringWrapper msg = new StringWrapper();
      boolean allItemDeleted =
         databaseAssetBrowserService.deleteDataModels(event.getDatabase(), event.getItems(),
         principal);
      String message =
         Catalog.getCatalog().getString("common.datasource.viewUsedByLogicalModel");
      msg.setBody(!allItemDeleted ? message : null);

      return msg;
   }

   private final DataSourceService dataSourceService;
   private final DatabaseModelBrowserService databaseAssetBrowserService;
}
