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
import inetsoft.web.viewsheet.event.MaxObjectEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class MaxModeAssemblyController {
   @Autowired
   public MaxModeAssemblyController(MaxModeAssemblyServiceProxy maxModeAssemblyServiceProxy,
                                    RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.maxModeAssemblyServiceProxy = maxModeAssemblyServiceProxy;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/vs/assembly/max-mode/toggle")
   public void toggleMaxMode(MaxObjectEvent event,
                              Principal principal, CommandDispatcher dispatcher,
                              @LinkUri String linkUri)
      throws Exception
   {
      maxModeAssemblyServiceProxy.toggleMaxMode(runtimeViewsheetRef.getRuntimeId(), event.assemblyName(),
                                           event.maxSize(), dispatcher, linkUri, principal);
   }

   private RuntimeViewsheetRef runtimeViewsheetRef;
   private MaxModeAssemblyServiceProxy maxModeAssemblyServiceProxy;
}
