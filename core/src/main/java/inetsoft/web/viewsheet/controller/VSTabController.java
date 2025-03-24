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

import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.ChangeTabStateEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSTabController {
   /**
    * Creates a new instance of <tt>VSTabController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public VSTabController(RuntimeViewsheetRef runtimeViewsheetRef, VSTabServiceProxy vsTabServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsTabServiceProxy = vsTabServiceProxy;
   }

   @Undoable
   @MessageMapping("/tab/changetab/{name}")
   public void changeTab(@DestinationVariable("name") String name,
                         @Payload ChangeTabStateEvent event, @LinkUri String linkUri,
                         Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      vsTabServiceProxy.changeTab(runtimeViewsheetRef.getRuntimeId(), name, event, linkUri,
                                  principal, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private VSTabServiceProxy vsTabServiceProxy;
}
