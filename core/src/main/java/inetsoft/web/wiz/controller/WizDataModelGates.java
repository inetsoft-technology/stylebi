/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.controller;

import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.LogicalModelService;

import java.security.Principal;

/**
 * The permission gates the data model bridge routes apply before calling a service, as the Portal
 * does. Each is equal to or narrower than what the service enforces itself, never looser; none is
 * the site-administrator gate. Every denial leaves as a {@code SecurityException}, which
 * {@code WizControllerErrorHandler} turns into a 403 (batch routes catch it per item instead).
 *
 * <p>Same {@code SecurityException} text style as {@code WizDatasourceGates}.</p>
 */
class WizDataModelGates {
   WizDataModelGates(DataSourceService dataSourceService, LogicalModelService modelService) {
      this.dataSourceService = dataSourceService;
      this.modelService = modelService;
   }

   /** Fails unless the caller holds the action on the data source. */
   void requireDataSourcePermission(String database, ResourceAction action, Principal principal)
      throws Exception
   {
      if(!dataSourceService.checkPermission(database, action, principal)) {
         throw denied(database, principal);
      }
   }

   /**
    * Fails unless the caller holds the action on a data model folder; an empty folder means the
    * data source itself, as in {@code DataSourceService.checkPermission}.
    */
   void requireFolderPermission(String database, String folder, ResourceAction action,
                                Principal principal)
      throws Exception
   {
      if(!dataSourceService.checkPermission(database, folder, action, principal)) {
         throw denied(folder == null || folder.isEmpty() ? database : database + "/" + folder,
                      principal);
      }
   }

   /**
    * Fails unless the caller holds the action on a base logical model, resolved with the model's
    * STORED folder.
    */
   void requireModelPermission(String database, String storedFolder, String name,
                               ResourceAction action, Principal principal)
   {
      if(!modelService.checkPermission(database, storedFolder, name, null, action, principal)) {
         throw denied(database + "/" + (storedFolder == null || storedFolder.isEmpty() ? "" :
                                           storedFolder + "/") + name, principal);
      }
   }

   /**
    * Fails unless the caller may edit the data models of a data source: READ on the source and
    * WRITE on the source or on one of its folders (what the Data tab requires); an additional
    * connection, if named, also needs READ.
    */
   void requireEditAccess(String database, String additional, Principal principal)
      throws Exception
   {
      dataSourceService.checkDataModelEditPermission(database, additional, principal);
   }

   // -- answer a not-visible item exactly like a missing one (DM-A9, review R1-4) --------------

   /**
    * Resolves the data model of a database the caller may see. The READ gate runs BEFORE any
    * existence or type answer, and a caller without READ gets exactly the {@code NOT_FOUND} of a
    * source that does not exist, so a source name (or its type) cannot be probed.
    */
   XDataModel requireVisibleJdbcDataModel(WizDataModelSource source, String database,
                                          Principal principal)
      throws Exception
   {
      if(!dataSourceService.checkPermission(database, ResourceAction.READ, principal)) {
         throw WizDataModelSource.missingSource(database);
      }

      return source.requireJdbcDataModel(database);
   }

   /**
    * Whether the caller may see the item, by the rule that lists it in the Data tab: a logical
    * model needs READ on the model, a physical view WRITE on its folder (or the source), a VPM
    * WRITE on the source, a folder READ on the folder. An item that does not exist is not visible.
    */
   boolean maySee(XDataModel dataModel, String database, String kind, String name,
                  Principal principal)
   {
      try {
         switch(kind) {
         case WizDataModelWire.KIND_LOGICAL_MODEL:
            XLogicalModel model = dataModel.getLogicalModel(name);
            return model != null && modelService.checkPermission(
               database, model.getFolder(), name, null, ResourceAction.READ, principal);
         case WizDataModelWire.KIND_PHYSICAL_VIEW:
            XPartition view = dataModel.getPartition(name);
            return view != null && dataSourceService.checkPermission(
               database, view.getFolder(), ResourceAction.WRITE, principal);
         case WizDataModelWire.KIND_VPM:
            return dataModel.getVirtualPrivateModel(name) != null &&
               dataSourceService.checkPermission(database, ResourceAction.WRITE, principal);
         case WizDataModelWire.KIND_FOLDER:
            return WizDataModelSupport.hasFolder(dataModel, name) &&
               dataSourceService.checkDataModelFolderPermission(
                  database + "/" + name, ResourceAction.READ, principal);
         default:
            return false;
         }
      }
      catch(Exception e) {
         return false;
      }
   }

   /**
    * The kind of the item with this name that the caller may see, or null: a model the caller may
    * not see is answered like a missing one, and its kind is never named.
    */
   String visibleKindOfName(XDataModel dataModel, String database, String name,
                            Principal principal)
   {
      String kind = WizDataModelSupport.kindOfName(dataModel, name);
      return kind != null && maySee(dataModel, database, kind, name, principal) ? kind : null;
   }

   /** The one text for an item that is missing or not visible to the caller. */
   static WizDataModelErrors.DmFailure notFound(String kind, String field, String database,
                                                String name)
   {
      return new WizDataModelErrors.DmFailure(
         WizDataModelWire.NOT_FOUND, field, "no " + kind + " named \"" + name + "\" in " +
         database + " (check the name with browse_data_models)");
   }

   private static SecurityException denied(String resource, Principal principal) {
      return new SecurityException(
         "Unauthorized access to resource \"" + resource + "\" by user " + principal);
   }

   private final DataSourceService dataSourceService;
   private final LogicalModelService modelService;
}
