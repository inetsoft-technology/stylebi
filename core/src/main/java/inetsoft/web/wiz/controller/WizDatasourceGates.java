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

import inetsoft.web.wiz.WizUtil;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;

import java.security.Principal;

/**
 * The per-resource permission gates the data source bridge endpoints apply, as the Portal does.
 *
 * <p>Same semantics and {@code SecurityException} text as the private methods of
 * {@code WizDatabaseController}; duplicated on purpose so the existing controller is not rewritten
 * by the endpoints added next to it. {@code WizDatasourceGatesTest} asserts both give the same
 * outcome on a shared table of cases. Every denial leaves as a {@code SecurityException}, which
 * {@code WizControllerErrorHandler} turns into a 403. These are never the site-admin gate.</p>
 */
class WizDatasourceGates {
   WizDatasourceGates(SecurityEngine securityEngine) {
      this.securityEngine = securityEngine;
   }

   /** Fails unless the caller holds the action on a data source. */
   void requireDataSourcePermission(String path, ResourceAction action, Principal principal)
      throws Exception
   {
      if(!WizUtil.checkPermission(securityEngine, principal, ResourceType.DATA_SOURCE, path, action)) {
         throw new SecurityException(
            "Unauthorized access to data source \"" + path + "\" by user " + principal);
      }
   }

   /** Fails unless the caller holds the action on a data source folder. */
   void requireFolderPermission(String path, ResourceAction action, Principal principal)
      throws Exception
   {
      if(!WizUtil.checkPermission(securityEngine, principal, ResourceType.DATA_SOURCE_FOLDER, path, action)) {
         throw new SecurityException(
            "Unauthorized access to data source folder \"" + path + "\" by user " + principal);
      }
   }

   /**
    * Fails unless the caller may create a data source in a folder: WRITE on the parent folder, or,
    * at the root only, the standalone {@code CREATE_DATA_SOURCE} grant.
    *
    * @param parentPath the parent folder, or null for the root.
    *
    * @return whether the standalone grant was the only thing that let the caller through.
    */
   boolean requireCreatePermission(String parentPath, Principal principal) throws Exception {
      boolean root = parentPath == null;
      boolean writeOnParent = WizUtil.checkPermission(securityEngine,
         principal, ResourceType.DATA_SOURCE_FOLDER, root ? "/" : parentPath, ResourceAction.WRITE);

      if(writeOnParent) {
         return false;
      }

      boolean createDataSourceOnly = root && WizUtil.checkPermission(securityEngine,
         principal, ResourceType.CREATE_DATA_SOURCE, "*", ResourceAction.ACCESS);

      if(!createDataSourceOnly) {
         throw new SecurityException("Unauthorized access to data source folder \"" +
                                        (root ? "/" : parentPath) + "\" by user " + principal);
      }

      return true;
   }

   private final SecurityEngine securityEngine;
}
