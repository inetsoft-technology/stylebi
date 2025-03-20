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
package inetsoft.web.binding;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.*;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.VSTableTrapModel;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.InsertSelectionChildEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Controller that provides a REST endpoint for composer viewsheet actions.
 */
@RestController
public class VSBindingController {
   @Autowired
   public VSBindingController(VSBindingService vsBindingService,
                              VSBindingServiceProxy vsBindingServiceProxy)
   {
      this.vsBindingService = vsBindingService;
      this.vsBindingServiceProxy = vsBindingServiceProxy;
   }

   /**
    * Websocket notification that binding pane has opened
    */
   @MessageMapping("/vs/binding/open/{name}")
   public void openBindingPane(@DestinationVariable("name") String assemblyName,
                               CommandDispatcher dispatcher)
   {
      dispatcher.sendCommand(new OpenBindingPaneCommand(assemblyName));
   }

   /**
    * Websocket notification that binding pane has closed
    */
   @MessageMapping("/vs/binding/close")
   public void closeBindingPane(CommandDispatcher dispatcher) {
      dispatcher.sendCommand(new CloseBindingPaneCommand());
   }

   /**
    * Open vs assembly edit binding pane.
    *
    * @param vsId      the runtime viewsheet id.
    * @param principal  a principal identifying the current user.
    *
    * @return new runtime viewsheet id for edit binding pane.
    */
   @RequestMapping(value = "/api/vsbinding/open", method = RequestMethod.GET)
   public BindingPaneData open(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam(value = "viewer", defaultValue = "false") boolean viewer,
      @RequestParam("temporarySheet") boolean temporarySheet, Principal principal)
      throws Exception
   {
      return vsBindingServiceProxy.open(vsId, assemblyName, viewer, temporarySheet, principal);
   }

   /**
    * Commit vs assembly edit binding pane.
    *
    * @param vsId      the runtime viewsheet id.
    * @param assemblyName      the vs assembly name.
    * @param principal  a principal identifying the current user.
    *
    * @return String.
    */
   @RequestMapping(value = "/api/vsbinding/commit", method = RequestMethod.GET)
   public String commit(@RequestParam("vsId") String vsId,
                        @RequestParam("assemblyName") String assemblyName,
                        @RequestParam(name = "editMode", required = false) String editMode,
                        @RequestParam(name = "originalMode", required = false) String originalMode,
                        Principal principal)
      throws Exception
   {
      return vsBindingServiceProxy.commit(vsId, assemblyName, editMode, originalMode, principal);
   }

   /**
    * Insert a child into a selection container between two other selection children
    * @param assemblyName  the name of the selection container
    * @param event         the InsertSelectionChildEvent object
    * @param principal     the principal
    * @param dispatcher    the command dispatcher
    * @throws Exception    if failed to insert the child
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/viewsheet/selectionContainer/insertChild/{name}")
   public void insertChild(@DestinationVariable("name") String assemblyName,
                           @Payload InsertSelectionChildEvent event,
                           @LinkUri String linkUri,
                           Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      this.vsBindingService.insertChild(event, linkUri, principal, dispatcher);
   }

   /**
    * Check whether the inserting a new child into a selection container will cause a trap.
    *
    * @param event     the proposed binding change
    * @param principal the user principal
    *
    * @return the table trap model stating whether or not here is a trap.
    */
   @SuppressWarnings("UnusedReturnValue")
   @PostMapping("/api/viewsheet/objects/checkSelectionTrap/**")
   @ResponseBody
   public VSTableTrapModel checkVSSelectionTrap(@RequestBody InsertSelectionChildEvent event,
                                                @RemainingPath String runtimeId,
                                                @LinkUri String linkUri,
                                                Principal principal)
      throws Exception
   {
      return vsBindingServiceProxy.checkVSSelectionTrap(runtimeId, event, linkUri, principal);
   }

   private final VSBindingService vsBindingService;
   private final VSBindingServiceProxy vsBindingServiceProxy;
   private static final Logger LOG = LoggerFactory.getLogger(VSBindingController.class);
}
