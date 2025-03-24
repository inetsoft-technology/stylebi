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

import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.HandleWizardExceptions;
import inetsoft.web.vswizard.event.SetWizardBindingFormatEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSWizardFormatController {

   @Autowired
   public VSWizardFormatController(VSWizardFormatServiceProxy vsWizardFormatServiceProxy,
                                   RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsWizardFormatServiceProxy = vsWizardFormatServiceProxy;
   }

   @HandleWizardExceptions
   @MessageMapping("/vswizard/object/format")
   public void updateFormat(@Payload SetWizardBindingFormatEvent event,
                            CommandDispatcher dispatcher, Principal principal,
                            @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      vsWizardFormatServiceProxy.updateFormat(id, event, dispatcher, principal, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private VSWizardFormatServiceProxy vsWizardFormatServiceProxy;
}
