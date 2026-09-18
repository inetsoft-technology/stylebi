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

package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.binding.event.ConvertTableRefEvent;
import inetsoft.web.binding.event.RefreshBindingTreeEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.ConvertTableRefService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Optional;

@Service
@ClusterProxy
public class ConvertTableRefControllerService {
   public ConvertTableRefControllerService(
      VSBindingTreeControllerServiceProxy vsBindingTreeService,
      ConvertTableRefService convertTableRefService,
      VSAssemblyInfoHandler assemblyInfoHandler,
      ViewsheetService viewsheetService)
   {
      this.vsBindingTreeService = vsBindingTreeService;
      this.convertTableRefService = convertTableRefService;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   @ClusterWriteMethod
   public Void convertTableRef(@ClusterProxyKey String id, ConvertTableRefEvent event,
                               Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      String name = event.name();
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(id, principal);
      Viewsheet vs = rvs.getViewsheet();
      TableDataVSAssembly assembly = (TableDataVSAssembly) vs.getAssembly(name);
      String tableName = VSUtil.getTableName(event.table());
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      // Handle source changed.
      if(box.isEmpty() || assemblyInfoHandler.handleSourceChanged(assembly, tableName,
                                                 "/events/vs/table/convertRef", event, dispatcher,
                                                 box.get()))
      {
         return null;
      }

      convertTableRefService.convertTableRef(event.refNames(), event.convertType(), event.source(),
                                             event.sourceChange(), event.name(),rvs, principal, dispatcher);
      RefreshBindingTreeEvent refreshBindingTreeEvent = new RefreshBindingTreeEvent();
      refreshBindingTreeEvent.setName(event.name());
      // Was VSBindingTreeController.getBinding(refreshBindingTreeEvent, principal, dispatcher),
      // which re-derives the runtime id from RuntimeViewsheetRef -- a STOMP-message-scoped bean
      // populated only from a native header on a live browser WebSocket session. A caller reached
      // without one gets null, and that null reaches Ignite's AffinityKey constructor. Call the
      // proxy directly with the id this call already holds (its own @ClusterProxyKey parameter),
      // as #4971 did for ModifyCalculateFieldService. Bug #76674, following #76666.
      vsBindingTreeService.getBinding(id, refreshBindingTreeEvent, principal, dispatcher);
      return null;

   }

   private final VSBindingTreeControllerServiceProxy vsBindingTreeService;
   private final ConvertTableRefService convertTableRefService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final ViewsheetService viewsheetService;
}
