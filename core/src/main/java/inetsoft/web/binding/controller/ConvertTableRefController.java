/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.binding.event.ConvertTableRefEvent;
import inetsoft.web.binding.event.RefreshBindingTreeEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.ConvertTableRefService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ConvertTableRefController {
   /**
    * Creates a new instance of <tt>ConvertTableRefController</tt>.
    *
    * @param bindingTreeController
    * @param convertTableRefService
    */
   @Autowired
   public ConvertTableRefController(VSBindingTreeController bindingTreeController,
                                    ConvertTableRefService convertTableRefService,
                                    VSAssemblyInfoHandler assemblyInfoHandler,
                                    RuntimeViewsheetRef runtimeViewsheetRef,
                                    ViewsheetService viewsheetService)
   {
      this.bindingTreeController = bindingTreeController;
      this.convertTableRefService = convertTableRefService;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.viewsheetService = viewsheetService;
   }

   @MessageMapping("/vs/table/convertRef")
   public void convertTableRef(@Payload ConvertTableRefEvent event,
                               Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      String name = event.name();
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(id, principal);
      Viewsheet vs = rvs.getViewsheet();
      TableDataVSAssembly assembly = (TableDataVSAssembly) vs.getAssembly(name);
      String tableName = VSUtil.getTableName(event.table());

      // Handle source changed.
      if(assemblyInfoHandler.handleSourceChanged(assembly, tableName,
                                                 "/events/vs/table/convertRef", event, dispatcher,
                                                 rvs.getViewsheetSandbox()))
      {
         return;
      }

      convertTableRefService.convertTableRef(event.refNames(), event.convertType(), event.source(),
         event.sourceChange(), event.name(),rvs, principal, dispatcher);
      RefreshBindingTreeEvent refreshBindingTreeEvent = new RefreshBindingTreeEvent();
      refreshBindingTreeEvent.setName(event.name());
      bindingTreeController.getBinding(refreshBindingTreeEvent, principal, dispatcher);
   }

   private final VSBindingTreeController bindingTreeController;
   private final ConvertTableRefService convertTableRefService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   public final ViewsheetService viewsheetService;
}
