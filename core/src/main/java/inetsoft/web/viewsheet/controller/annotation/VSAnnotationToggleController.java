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
package inetsoft.web.viewsheet.controller.annotation;

import inetsoft.web.viewsheet.event.annotation.ToggleAnnotationStatusEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
@MessageMapping("/annotation")
public class VSAnnotationToggleController {
   @Autowired
   public VSAnnotationToggleController(VSAnnotationToggleServiceProxy vsAnnotationToggleServiceProxy,
                                       RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.vsAnnotationToggleServiceProxy = vsAnnotationToggleServiceProxy;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   /**
    *
    * @param event      event with flag to show or hide annotations
    * @param principal  the current user
    * @param linkUri    the link URI
    * @param dispatcher the command dispatcher
    */
   @MessageMapping("/toggle-status")
   public void toggleAnnotationStatus(@Payload ToggleAnnotationStatusEvent event,
                                      Principal principal,
                                      @LinkUri String linkUri,
                                      CommandDispatcher dispatcher) throws Exception
   {
      vsAnnotationToggleServiceProxy.toggleAnnotationStatus(runtimeViewsheetRef.getRuntimeId(),
                                                            event, principal, linkUri, dispatcher);
   }

   private final VSAnnotationToggleServiceProxy vsAnnotationToggleServiceProxy;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
}
