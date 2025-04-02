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
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.internal.Util;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSInsertColumnsEvent;
import inetsoft.web.composer.ws.event.WSInsertColumnsEventValidator;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class InsertColumnsService extends WorksheetControllerService {
   public InsertColumnsService(ViewsheetService viewsheetService) {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public WSInsertColumnsEventValidator validateInsertColumns(
      @ClusterProxyKey String runtimeId,
      WSInsertColumnsEvent event,
      Principal principal) throws Exception
   {
      RuntimeWorksheet rws =
         super.getWorksheetEngine().getWorksheet(runtimeId, principal);

      return validateInsertColumns0(rws, event, principal, null);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void insertColumns(
      @ClusterProxyKey String runtimeId,
      WSInsertColumnsEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String name = event.name();
      TableAssembly assembly = (TableAssembly) ws.getAssembly(name);

      if(!(assembly instanceof BoundTableAssembly)) {
         return null;
      }

      ColumnSelection columns = assembly.getColumnSelection();

      if(columns.getAttributeCount() > Util.getOrganizationMaxColumn() ||
         columns.getAttributeCount() + event.entries().length > Util.getOrganizationMaxColumn())
      {
         MessageCommand command = new MessageCommand();
         command.setMessage(Util.getColumnLimitMessage());
         command.setType(MessageCommand.Type.ERROR);
         commandDispatcher.sendCommand(command);

         return null;
      }

      insertColumns0(name, event.index(), event.entries(), columns, rws, assembly);
      WorksheetEventUtil.refreshColumnSelection(rws, name, true);
      WorksheetEventUtil.loadTableData(rws, name, true, true);
      WorksheetEventUtil.refreshAssembly(rws, name, true, commandDispatcher, principal);
      WorksheetEventUtil.layout(rws, commandDispatcher);
      AssetEventUtil.refreshTableLastModified(ws, name, true);
      return null;
   }
}
