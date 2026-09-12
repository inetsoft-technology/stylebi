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
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.web.binding.command.RefreshBindingTreeCommand;
import inetsoft.web.binding.event.RefreshBindingTreeEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.VSBindingTreeService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class VSBindingTreeControllerService {
   public VSBindingTreeControllerService(VSBindingTreeService vsBindingTreeService,
                                  VSAssemblyInfoHandler assemblyHandler,
                                  ViewsheetService viewsheetService)
   {
      this.vsBindingTreeService = vsBindingTreeService;
      this.assemblyHandler = assemblyHandler;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void getBinding(@ClusterProxyKey String id, RefreshBindingTreeEvent event,
                          Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      TreeNodeModel tree = vsBindingTreeService.getBinding(id, event.getName(),
                                                           event.isLayoutMode(), principal);
      RefreshBindingTreeCommand command = new RefreshBindingTreeCommand(tree);
      dispatcher.sendCommand(command);
      assemblyHandler.getGrayedOutFields(viewsheetService.getViewsheet(id, principal), dispatcher);
      return null;
   }

   private final VSAssemblyInfoHandler assemblyHandler;
   private final ViewsheetService viewsheetService;
   private final VSBindingTreeService vsBindingTreeService;
}
