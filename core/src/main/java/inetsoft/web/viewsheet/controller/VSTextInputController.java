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

import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.VSListInputSelectionEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSTextInputController {
   /**
    * Creates a new instance of <tt>VSTextInputController</tt>.
    */
   @Autowired
   public VSTextInputController(VSInputServiceProxy inputServiceProxy,
                                RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.inputServiceProxy = inputServiceProxy;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   /**
    * Apply selection.
    *
    * @param principal  a principal identifying the current user.
    * @param event      the apply event
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if the selection could not be applied.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/textInput/applySelection")
   public void applySelection(@Payload VSListInputSelectionEvent event,
                              Principal principal, CommandDispatcher dispatcher,
                              @LinkUri String linkUri)
      throws Exception
   {
      inputServiceProxy.singleApplySelection(runtimeViewsheetRef.getRuntimeId(), event.assemblyName(), event.value(),
                                        principal, dispatcher, linkUri);
   }

   private VSInputServiceProxy inputServiceProxy;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
}
