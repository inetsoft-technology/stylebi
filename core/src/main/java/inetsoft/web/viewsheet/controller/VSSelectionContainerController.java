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

import inetsoft.web.composer.model.vs.AddFilterDialogModel;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.HideSelectionListEvent;
import inetsoft.web.viewsheet.event.MoveSelectionChildEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
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
    */
   @Autowired
   public VSSelectionContainerController(RuntimeViewsheetRef runtimeViewsheetRef,
                                         VSSelectionContainerServiceProxy vsSelectionContainerServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsSelectionContainerServiceProxy = vsSelectionContainerServiceProxy;
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
      vsSelectionContainerServiceProxy.applySelection(runtimeViewsheetRef.getRuntimeId(), assemblyName,
                                                 event, linkUri, principal, dispatcher);
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
      vsSelectionContainerServiceProxy.applySelection(runtimeViewsheetRef.getRuntimeId(), assemblyName,
                                                 event, principal, dispatcher);
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
   public AddFilterDialogModel getTargetTree(@RequestParam("vsId") String vsId, Principal principal)
      throws Exception
   {
      return vsSelectionContainerServiceProxy.getTargetTree(vsId, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSSelectionContainerServiceProxy vsSelectionContainerServiceProxy;
}
