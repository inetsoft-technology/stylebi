/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.binding.event.GetVSObjectModelEvent;
import inetsoft.web.viewsheet.controller.table.BaseTableController;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * This class handles get vsobjectmodel from the server.
 */
@Controller
public class VSViewController {
   /**
    * Creates a new instance of <tt>VSViewController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    * @param viewsheetService
    */
   @Autowired
   public VSViewController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      PlaceholderService holder, VSObjectModelFactoryService objectModelService,
      ViewsheetService viewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.holder = holder;
      this.objectModelService = objectModelService;
      this.viewsheetService = viewsheetService;
   }

   /**
    * This method is to get vsobjectmodel for edit binding pane.
    *
    */
   @MessageMapping(value="/vsview/object/model")
   public void getModel(@Payload GetVSObjectModelEvent event, Principal principal,
      CommandDispatcher dispatcher) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VSAssembly assembly = (VSAssembly)viewsheet.getAssembly(event.getName());
      holder.refreshVSObject(assembly, rvs, null, box, dispatcher);

      if(assembly instanceof TableDataVSAssembly) {
         int hint = VSAssembly.BINDING_CHANGED;
         holder.execute(rvs, event.getName(), null, hint, dispatcher);
         BaseTableController.loadTableData(
            rvs, event.getName(), 0, 0, 100, null, dispatcher);
      }
   }

   private final PlaceholderService holder;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSObjectModelFactoryService objectModelService;
   private final ViewsheetService viewsheetService;
}
