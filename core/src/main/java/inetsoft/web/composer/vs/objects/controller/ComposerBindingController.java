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
package inetsoft.web.composer.vs.objects.controller;


import inetsoft.web.binding.dnd.TableTransfer;
import inetsoft.web.binding.service.VSBindingServiceProxy;
import inetsoft.web.composer.model.vs.VSTableTrapModel;
import inetsoft.web.composer.vs.objects.event.ChangeVSObjectBindingEvent;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.event.InsertSelectionChildEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
/**
 * Controller that provides a REST endpoint for object actions.
 */
@Controller
public class ComposerBindingController {
   /**
    * Creates a new instance of <tt>ComposerBindingController</tt>.
    *  @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public ComposerBindingController(RuntimeViewsheetRef runtimeViewsheetRef,
                                    VSBindingServiceProxy bindingServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.bindingServiceProxy = bindingServiceProxy;
   }

   /**
    * Move object in the composer.
    *
    * @param event             the event parameters.
    * @param principal         a principal identifying the current user.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @Undoable
   @LoadingMask
   @HandleAssetExceptions
   @MessageMapping("composer/viewsheet/objects/changeBinding")
   public void changeBinding(@Payload ChangeVSObjectBindingEvent event, Principal principal,
                             CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
     bindingServiceProxy.changeBinding(runtimeViewsheetRef.getRuntimeId(),
                                       event, principal, dispatcher, linkUri);
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
   @MessageMapping("/composer/viewsheet/selectionContainer/insertChild/{name}")
   public void insertChild(@DestinationVariable("name") String assemblyName,
                              @Payload InsertSelectionChildEvent event,
                              @LinkUri String linkUri,
                              Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      bindingServiceProxy.insertChild(runtimeViewsheetRef.getRuntimeId(), event,
                                      linkUri, principal, dispatcher, true);

   }

   /**
    * Check whether the changing the data bound to the assembly will cause a trap.
    *
    * @param event     the proposed binding change
    * @param principal the user principal
    *
    * @return the table trap model stating whether or not here is a trap.
    */
   @SuppressWarnings("UnusedReturnValue")
   @PostMapping("/api/composer/viewsheet/objects/checkTrap/**")
   @ResponseBody
   public VSTableTrapModel checkVSTrap(@RequestBody ChangeVSObjectBindingEvent event,
                                       @RemainingPath String runtimeId,
                                       @LinkUri String linkUri,
                                       Principal principal)
      throws Exception
   {
      return bindingServiceProxy.checkVSTrap(runtimeId, event, linkUri, principal);
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
   @PostMapping("/api/composer/viewsheet/objects/checkSelectionTrap/**")
   @ResponseBody
   public VSTableTrapModel checkVSSelectionTrap(@RequestBody InsertSelectionChildEvent event,
                                                @RemainingPath String runtimeId,
                                                @LinkUri String linkUri,
                                                Principal principal)
      throws Exception
   {
      return this.bindingServiceProxy.checkVSSelectionTrap(runtimeId, event, principal);
   }

   /**
    * Return the data type of the table column being dropped
    *
    * @param tableData the table column data dropped
    * @param principal the user principal
    *
    * @return the data type of the TableTransfer column
    */
   @PostMapping("/api/composer/viewsheet/objects/getTableTransferDataType/**")
   @ResponseBody
   public String getTableTransferDataType(@RequestBody TableTransfer tableData,
                                          @RemainingPath String runtimeId,
                                          Principal principal) throws Exception
   {
      return bindingServiceProxy.getTableTransferDataType(runtimeId, tableData, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSBindingServiceProxy bindingServiceProxy;
}
