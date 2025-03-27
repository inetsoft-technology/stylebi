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

import inetsoft.web.composer.vs.event.CopyVSObjectsEvent;
import inetsoft.web.composer.vs.objects.event.CopyHighlightEvent;
import inetsoft.web.composer.vs.objects.event.PasteHighlightEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import java.security.Principal;

/**
 * Controller that provides a REST endpoint for viewsheet clipboard events.
 */
@Controller
public class ClipboardController {
   /**
    * Creates a new instance of <tt>ClipboardController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public ClipboardController(RuntimeViewsheetRef runtimeViewsheetRef,
                              ClipboardControllerServiceProxy clipboardControllerServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.clipboardControllerServiceProxy = clipboardControllerServiceProxy;
   }

   /**
    * Copy or cut composer vs object.
    *
    * @param event     the event parameters.
    * @param principal a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @MessageMapping("composer/viewsheet/objects/copy")
   public void copyObject(@Payload CopyVSObjectsEvent event,
                          SimpMessageHeaderAccessor headerAccessor, Principal principal,
                          CommandDispatcher dispatcher,
                          @LinkUri String linkUri) throws Exception
   {
      clipboardControllerServiceProxy.copyOrCut(runtimeViewsheetRef.getRuntimeId(), event,
                                                headerAccessor, principal, dispatcher, linkUri);
   }

   @Undoable
   @MessageMapping("composer/viewsheet/objects/cut")
   public void cutObject(@Payload CopyVSObjectsEvent event,
                          SimpMessageHeaderAccessor headerAccessor, Principal principal,
                          CommandDispatcher dispatcher,
                          @LinkUri String linkUri) throws Exception
   {
      clipboardControllerServiceProxy.copyOrCut(runtimeViewsheetRef.getRuntimeId(), event,
                                                headerAccessor, principal, dispatcher, linkUri);
   }

   /**
    * Copy or cut composer vs object.
    *
    * @param principal a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/objects/paste/{x}/{y}")
   public void pasteObject(@DestinationVariable("x") int x,
                           @DestinationVariable("y") int y,
                           Principal principal, CommandDispatcher dispatcher,
                           SimpMessageHeaderAccessor headerAccessor,
                           @LinkUri String linkUri)
      throws Exception
   {
      clipboardControllerServiceProxy.pasteObject(runtimeViewsheetRef.getRuntimeId(), x, y,
                                                  principal, dispatcher, headerAccessor, linkUri);
   }

   /**
    * copy table cell highlight
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    * @param headerAccessor the header accessor for the current session
    * @throws Exception if unable to retrieve/edit object.
    */
   @MessageMapping("/composer/viewsheet/table/copyHighlight")
   public void copyHighlight(@Payload CopyHighlightEvent event, Principal principal,
                             CommandDispatcher dispatcher, SimpMessageHeaderAccessor headerAccessor)
      throws Exception
   {
      clipboardControllerServiceProxy.copyHighlight(runtimeViewsheetRef.getRuntimeId(), event,
                                                    principal, dispatcher, headerAccessor);
   }

   /**
    * paste table cell highlight
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    * @param headerAccessor the header accessor for the current session
    * @param linkUri  the link Uri
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @MessageMapping("/composer/viewsheet/table/pasteHighlight")
   public void pasteHighlight(@Payload PasteHighlightEvent event, Principal principal,
                              SimpMessageHeaderAccessor headerAccessor,
                              CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      clipboardControllerServiceProxy.pasteHighlight(runtimeViewsheetRef.getRuntimeId(), event,
                                                     principal, headerAccessor, dispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ClipboardControllerServiceProxy clipboardControllerServiceProxy;

}
