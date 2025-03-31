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

import inetsoft.web.composer.vs.objects.event.ChangeVSObjectTextEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Controller that processes vs text events.
 */
@Controller
public class VSTextController {
   /**
    * Creates a new instance of <tt>VSTextController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated
    *                            with the WebSocket session.
    */
   @Autowired
   public VSTextController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      VSTextServiceProxy vsTextService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsTextService = vsTextService;
   }

   /**
    * Change inner text of the object.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/vsText/changeText")
   public void changeText(@Payload ChangeVSObjectTextEvent event, @LinkUri String linkUri,
                          Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      String runtimeId = this.runtimeViewsheetRef.getRuntimeId();
      vsTextService.changeText(runtimeId, event, linkUri, principal, dispatcher);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/printLayout/vsText/changeText/{region}")
   public void changeText(@DestinationVariable("region") int region,
                           @Payload ChangeVSObjectTextEvent event, @LinkUri String linkUri,
                          Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      String runtimeId = this.runtimeViewsheetRef.getRuntimeId();
      String focusedLayoutName = this.runtimeViewsheetRef.getFocusedLayoutName();
      boolean isPresent = vsTextService
         .changeText(runtimeId, focusedLayoutName, region, event, linkUri, principal, dispatcher);

      if(isPresent) {
         this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSTextServiceProxy vsTextService;
}
