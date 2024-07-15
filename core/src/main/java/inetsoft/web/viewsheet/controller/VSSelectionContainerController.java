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
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.CurrentSelectionVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.HideSelectionListEvent;
import inetsoft.web.viewsheet.event.MoveSelectionChildEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Controller that provides REST endpoints and message handling for selection container
 * assemblies.
 *
 * @since 12.3
 */
@Controller
public class VSSelectionContainerController {
   /**
    * Creates a new instance of <tt>VSSelectionContainerController</tt>.
    *
    * @param runtimeViewsheetRef the runtime viewsheet reference.
    * @param viewsheetService
    */
   @Autowired
   public VSSelectionContainerController(RuntimeViewsheetRef runtimeViewsheetRef,
                                         PlaceholderService placeholderService,
                                         VSSelectionContainerService vsSelectionContainerService,
                                         VSObjectModelFactoryService objectModelService,
                                         ViewsheetService viewsheetService,
                                         VSOutputService vsOutputService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.vsSelectionContainerService = vsSelectionContainerService;
      this.objectModelService = objectModelService;
      this.viewsheetService = viewsheetService;
      this.vsOutputService = vsOutputService;
   }

   /**
    * Applies a new status for a selection container. (Handles dropdown visibility)
    *
    * @param assemblyName the absolute name of the selection container assembly.
    * @param event        the selection event.
    * @param principal    a principal identifying the current user.
    * @param dispatcher   the command dispatcher.
    *
    * @throws Exception if the selection could not be applied.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/selectionContainer/update/{name}")
   public void applySelection(@DestinationVariable("name") String assemblyName,
                              @Payload HideSelectionListEvent event,
                              @LinkUri String linkUri,
                              Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      vsSelectionContainerService.applySelection(rvs, assemblyName, event.getHide(),
                                                 dispatcher, linkUri);
   }

   /**
    * Move a child inside selection container
    * @param assemblyName  the name of the selection container
    * @param event         the MoveSelectionChildEvent object
    * @param principal     the principal
    * @param dispatcher    the command dispatcher
    * @throws Exception    if failed to move the child
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/selectionContainer/moveChild/{name}")
   public void applySelection(@DestinationVariable("name") String assemblyName,
                              @Payload MoveSelectionChildEvent event,
                              Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CurrentSelectionVSAssembly containerAssembly = (CurrentSelectionVSAssembly)
         viewsheet.getAssembly(assemblyName);

      //move the currentSelection/childObject and refresh container
      containerAssembly.update(event.getFromIndex(), event.getToIndex(),
                               event.isCurrentSelection());

      placeholderService.refreshVSAssembly(rvs, containerAssembly, dispatcher);
   }

   /**
    * Get the target tree for add filter dialog,
    * @param vsId      runtime viewsheet id
    * @param principal the user
    * @return
    * @throws Exception
    */
   @GetMapping("api/selectioncontainer/add-filter/tree")
   @ResponseBody
   public TreeNodeModel getTargetTree(@RequestParam("vsId") String vsId, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);

      if(rvs == null) {
         return null;
      }

      return this.vsOutputService.getSelectionTablesTree(rvs, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;
   private final VSSelectionContainerService vsSelectionContainerService;
   private final VSObjectModelFactoryService objectModelService;
   private final ViewsheetService viewsheetService;
   private final VSOutputService vsOutputService;
}
