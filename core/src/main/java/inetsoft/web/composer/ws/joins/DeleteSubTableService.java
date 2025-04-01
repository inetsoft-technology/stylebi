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

package inetsoft.web.composer.ws.joins;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.asset.*;
import inetsoft.util.Catalog;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSDeleteSubtablesEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class DeleteSubTableService extends WorksheetControllerService {

   public DeleteSubTableService(ViewsheetService viewsheetService)
   {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void deleteSubTable(@ClusterProxyKey String runtimeId, WSDeleteSubtablesEvent event,
                              Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String tname = event.joinTable();
      String[] subtables = event.subTables();
      ComposedTableAssembly ctbl =
         (ComposedTableAssembly) ws.getAssembly(tname);

      if(ctbl == null) {
         return null;
      }

      CompositeTableAssembly assembly = ctbl instanceof CompositeTableAssembly ?
         (CompositeTableAssembly) ctbl : null;
      WSAssembly oassembly = (WSAssembly) ctbl.clone();
      boolean removed = assembly == null;

      for(String subtable : subtables) {
         removed = removed || assembly.removeTable(subtable);
      }

      if(!removed) {
         WorksheetEventUtil.refreshColumnSelection(rws, tname, true);
         WorksheetEventUtil.loadTableData(rws, tname, true, true);
         WorksheetEventUtil.refreshAssembly(rws, tname, true, commandDispatcher, principal);
      }
      else {
         AssemblyRef[] arr = ws.getDependings(ctbl.getAssemblyEntry());

         if(arr.length > 0) {
            ws.addAssembly(oassembly);
            MessageCommand command = new MessageCommand();
            command.setMessage(Catalog.getCatalog().getString(
               "common.removeAssemblyDependencyFailed"));
            command.setType(MessageCommand.Type.ERROR);
            command.setAssemblyName(tname);
            commandDispatcher.sendCommand(command);
            return null;
         }

         WorksheetEventUtil.removeAssembly(rws, ctbl, commandDispatcher);
      }

      WorksheetEventUtil.layout(rws, commandDispatcher);

      return null;
   }
}
