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
package inetsoft.web.vswizard.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.web.composer.vs.objects.event.MoveVSObjectEvent;
import inetsoft.web.composer.vs.objects.event.MultiMoveVsObjectEvent;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class WizardObjectMoveController {
   public WizardObjectMoveController(WizardObjectMoveServiceProxy wizardObjectMoveServiceProxy,
                                     RuntimeViewsheetRef runtimeViewsheetRef,
                                     ViewsheetService engine)
   {
      this.wizardObjectMoveServiceProxy = wizardObjectMoveServiceProxy;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   @Undoable
   @MessageMapping("/composer/vswizard/object/move")
   public void moveObject(@Payload MoveVSObjectEvent event, @LinkUri String linkUri,
                          Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      wizardObjectMoveServiceProxy.moveObject(runtimeViewsheetRef.getRuntimeId(), event, linkUri, principal, commandDispatcher);
   }

   @Undoable
   @MessageMapping("composer/vswizard/objects/multimove")
   public void moveObjects(@Payload MultiMoveVsObjectEvent multiEvent,
                           Principal principal, CommandDispatcher dispatcher,
                           @LinkUri String linkUri)
      throws Exception
   {
      wizardObjectMoveServiceProxy.moveObjects(runtimeViewsheetRef.getRuntimeId(), multiEvent, principal, dispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final WizardObjectMoveServiceProxy wizardObjectMoveServiceProxy;
}
