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

package inetsoft.web.composer.ws;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.asset.RelationalJoinTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.internal.CompositeTableAssemblyInfo;
import inetsoft.uql.asset.internal.SchemaTableInfo;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSResizeSchemaTableEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class WSResizeSchemaTableService extends WorksheetControllerService {

   public WSResizeSchemaTableService(ViewsheetService viewsheetService)
   {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void resizeSchemaTable(@ClusterProxyKey String runtimeId, WSResizeSchemaTableEvent event,
                                 Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(runtimeId, principal);
      final Worksheet ws = rws.getWorksheet();
      final RelationalJoinTableAssembly table =
         (RelationalJoinTableAssembly) ws.getAssembly(event.joinTableName());

      if(table != null) {
         final CompositeTableAssemblyInfo info = (CompositeTableAssemblyInfo) table.getTableInfo();
         final SchemaTableInfo schemaTableInfo = info.getSchemaTableInfo(event.schemaTableName());

         if(schemaTableInfo != null) {
            double width = Math.max(event.width(), SchemaTableInfo.MIN_SCHEMA_TABLE_WIDTH);
            double x = schemaTableInfo.getLeft();

            if(event.offsetLocation()) {
               x += schemaTableInfo.getWidth() - width;

               if(x < 0) {
                  width += x;
                  x = 0;
               }
            }

            info.setSchemaTableInfo(event.schemaTableName(),
                                    new SchemaTableInfo(x, schemaTableInfo.getTop(), width));
            WorksheetEventUtil.refreshAssembly(
               rws, table.getName(), false, commandDispatcher, principal);
         }
      }

      return null;
   }
}
