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
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSColumnDescriptionEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class ColumnDescriptionDialogService extends WorksheetControllerService {

   public ColumnDescriptionDialogService(ViewsheetService viewsheetService)
   {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void updateColumnType(@ClusterProxyKey String runtimeId, WSColumnDescriptionEvent event,
                                Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String name = event.tableName();
      TableAssembly table = (TableAssembly) ws.getAssembly(name);
      String desc = event.description();

      if(table != null) {
         int mode = WorksheetEventUtil.getMode(table);
         boolean pub = AssetQuerySandbox.isRuntimeMode(mode) || AssetQuerySandbox.isEmbeddedMode(mode) ||
            table.isAggregate();
         ColumnRef column = (ColumnRef) table.getColumnSelection(pub)
            .getAttribute(event.columnIndex());
         ColumnSelection columns = table.getColumnSelection();
         ColumnRef column2 = (ColumnRef) columns.findAttribute(column);

         if(column2 == null) {
            String columnName = column.getAttribute();

            for(int i = 0; i < columns.getAttributeCount(); i++) {
               DataRef dref = columns.getAttribute(i);

               if(dref == null) {
                  continue;
               }

               ColumnRef cref = (ColumnRef) dref;
               String alias = ((ColumnRef) cref).getAlias();
               String name0 = alias != null && !"".equals(alias) ?
                  alias : cref.getAttribute();

               if(columnName.equals(name0)) {
                  cref.setDescription(desc);
               }
            }
         }
         else {
            column2.setDescription(desc);
         }

         WorksheetEventUtil.refreshColumnSelection(rws, name, true);
         WorksheetEventUtil.loadTableData(rws, name, true, true);
         WorksheetEventUtil.refreshAssembly(rws, name, true, commandDispatcher, principal);
         AssetEventUtil.refreshTableLastModified(ws, name, true);
      }

      return null;
   }
}
