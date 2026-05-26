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

import inetsoft.web.composer.vs.objects.event.ResizeVSLineEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;


/**
 * Controller that processes vs line events.
 */
@Controller
public class VSLineController {
   /**
    * Creates a new instance of <tt>VSLineController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated
    *                            with the WebSocket session.
    */
   @Autowired
   public VSLineController(RuntimeViewsheetRef runtimeViewsheetRef,
                           VSLineServiceProxy vsLineServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsLineServiceProxy = vsLineServiceProxy;
   }

   /**
    * Resize vs line component.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/vsLine/resize")
   public void resize(@Payload ResizeVSLineEvent event, Principal principal,
                      CommandDispatcher dispatcher) throws Exception
   {
      vsLineServiceProxy.resize(runtimeViewsheetRef.getRuntimeId(), event, principal, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private VSLineServiceProxy vsLineServiceProxy;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSLineController.class);
}
