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
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.EmptyTableToEmbeddedException;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSAssemblyEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class ConvertEmbeddedService extends WorksheetControllerService {
   public ConvertEmbeddedService(ViewsheetService viewsheetService) {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void convertEmbedded(
      @ClusterProxyKey String runtimeId,
      WSAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String name = event.getAssemblyName();
      TableAssembly tabAssembly = (TableAssembly) ws.getAssembly(name);

      if(tabAssembly == null) {
         return null;
      }

      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      boolean allowConvert = allowConvertEmbeddedTable(rws, tabAssembly, commandDispatcher);

      if(!allowConvert) {
         return null;
      }

      EmbeddedTableAssembly assembly = null;

      try {
         assembly = AssetEventUtil.convertEmbeddedTable(box, tabAssembly, false, false, false, true);
      }
      catch(EmptyTableToEmbeddedException e) {
         throw new MessageException(catalog.getString("composer.ws.emptyTableToEmbeddedError"));
      }

      if(assembly == null) {
         return null;
      }

      String newName = assembly.getName();
      ws.addAssembly(assembly);

      // default to edit mode
      assembly.setRuntime(false);
      assembly.setEditMode(true);

      WorksheetEventUtil.createAssembly(rws, assembly, commandDispatcher, principal);
      WorksheetEventUtil.loadTableData(rws, newName, false, false);
      WorksheetEventUtil.refreshAssembly(rws, newName, false, commandDispatcher, principal);
      WorksheetEventUtil.layout(rws, commandDispatcher);
      return null;
   }

   private boolean allowConvertEmbeddedTable(RuntimeWorksheet rws, TableAssembly tab,
                                             CommandDispatcher dispatcher)
      throws Exception
   {
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      int mode = AssetQuerySandbox.RUNTIME_MODE;
      boolean allowConvert = true;

      TableLens table = box.getTableLens(tab.getName(), mode);

      if(table == null) {
         return false;
      }

      if(table.moreRows(ROW_LIMIT + 1) || table.getColCount() > COL_LIMIT) {
         MessageCommand cmd = new MessageCommand();
         cmd.setMessage(catalog.getString("common.worksheet.convert.embedded.limit"));
         cmd.setType(MessageCommand.Type.INFO);
         cmd.setAssemblyName(tab.getName());
         dispatcher.sendCommand(cmd);
         allowConvert = false;
         String tabAbsoluteName = tab.getAbsoluteName();

         if(rws.getEntry() != null) {
            tabAbsoluteName = rws.getEntry().getName() != null ?
               rws.getEntry().getName() + "." + tabAbsoluteName : tabAbsoluteName;
         }

         String logStr = catalog.getString("common.worksheet.convert.embedded.limit.log",
                                           tabAbsoluteName);
         LOG.info(logStr);
      }

      return allowConvert;
   }

   private static final Catalog catalog = Catalog.getCatalog();
   private static final Logger LOG = LoggerFactory.getLogger(ConvertEmbeddedService.class);
}
