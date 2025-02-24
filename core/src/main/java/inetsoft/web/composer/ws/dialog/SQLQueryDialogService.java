/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.composer.ws.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.web.composer.model.ws.SQLQueryDialogModel;
import inetsoft.web.portal.controller.database.QueryManagerService;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class SQLQueryDialogService {
   public SQLQueryDialogService(ViewsheetService wsEngine, QueryManagerService queryManagerService)
   {
      this.wsEngine = wsEngine;
      this.queryManagerService = queryManagerService;
   }

   @ClusterProxyMethod("runtimeSheets")
   public SQLQueryDialogModel getModel(@ClusterProxyKey String runtimeId, String tableName,
                                       String dataSource, Principal principal)
      throws Exception
   {
      boolean sqlEnabled = SecurityEngine.getSecurity().checkPermission(
         principal, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS);

      if(!sqlEnabled) {
         throw new MessageException(
            Catalog.getCatalog().getString("composer.nopermission.physicalTable"));
      }

      RuntimeWorksheet rws = wsEngine.getWorksheet(runtimeId, principal);
      return queryManagerService.getSqlQueryDialogModel(rws, tableName, dataSource, principal);
   }

   private final ViewsheetService wsEngine;
   private final QueryManagerService queryManagerService;
}
