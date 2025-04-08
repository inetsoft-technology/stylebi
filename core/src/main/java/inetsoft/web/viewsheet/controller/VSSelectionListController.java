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

import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.event.ApplySelectionListEvent;
import inetsoft.web.viewsheet.event.SortSelectionListEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Controller that provides REST endpoints and message handling for selection list
 * assemblies.
 *
 * @since 12.3
 */
@Controller
public class VSSelectionListController {
   @Autowired
   public VSSelectionListController(RuntimeViewsheetRef runtimeViewsheetRef,
                                    VSSelectionServiceProxy vsSelectionServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsSelectionServiceProxy = vsSelectionServiceProxy;
   }

   /**
    * Applies a new selection for a selection list.
    *
    * @param assemblyName the absolute name of the selection list assembly.
    * @param event        the selection event.
    * @param principal    a principal identifying the current user.
    * @param dispatcher   the command dispatcher.
    *
    * @throws Exception if the selection could not be applied.
    */
   @Undoable
   @LoadingMask
   @ExecutionMonitoring
   @MessageMapping("/selectionList/update/{name}")
   public void applySelection(@DestinationVariable("name") String assemblyName,
                              @Payload ApplySelectionListEvent event, Principal principal,
                              CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      vsSelectionServiceProxy.applySelection(runtimeViewsheetRef.getRuntimeId(), assemblyName,
                                             event, principal, dispatcher, linkUri);
   }

   @Undoable
   @MessageMapping("/selectionContainer/unselectAll/{name}")
   public void unselectAll(@DestinationVariable("name") String assemblyName,
                           Principal principal, CommandDispatcher dispatcher,
                           @LinkUri String linkUri) throws Exception
   {
      vsSelectionServiceProxy.unselectAll(runtimeViewsheetRef.getRuntimeId(), assemblyName, principal,
                                     dispatcher, linkUri);
   }

   /**
    * Sets the subtree as selected
    *
    * @param assemblyName the absolute name of the selection list assembly.
    * @param event        the selection event.
    * @param principal    a principal identifying the current user.
    * @param dispatcher   the command dispatcher.
    *
    * @throws Exception if the selection could not be applied.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/selectionTree/selectSubtree/{name}")
   public void selectSubtree(@DestinationVariable("name") String assemblyName,
                             @Payload ApplySelectionListEvent event, Principal principal,
                             CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      vsSelectionServiceProxy.selectSubtree(runtimeViewsheetRef.getRuntimeId(), assemblyName,
                                       event, principal, dispatcher, linkUri);
   }

   @Undoable
   @MessageMapping("/currentSelection/unSelectChild/{name}")
   public void unSelectChild(@DestinationVariable("name") String assemblyName,
                             Principal principal, CommandDispatcher dispatcher,
                             @LinkUri String linkUri) throws Exception
   {
      vsSelectionServiceProxy.applySelection(runtimeViewsheetRef.getRuntimeId(), assemblyName, null,
                                             principal, dispatcher, linkUri);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/selectionList/sort/{name}")
   public void sortSelection(@DestinationVariable("name") String assemblyName,
                             @Payload SortSelectionListEvent event, Principal principal,
                             CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      vsSelectionServiceProxy.sortSelection(runtimeViewsheetRef.getRuntimeId(), assemblyName, event, principal, dispatcher, linkUri);
   }

   @MessageMapping("/selectionList/toggle/{name}")
   public void toggleRuntimeSelectionStyle(@DestinationVariable("name") String assemblyName,
                                           Principal principal, CommandDispatcher dispatcher,
                                           @LinkUri String linkUri)
      throws Exception
   {
      vsSelectionServiceProxy.toggleSelectionStyle(runtimeViewsheetRef.getRuntimeId(), assemblyName,
                                              principal, dispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSSelectionServiceProxy vsSelectionServiceProxy;
}
