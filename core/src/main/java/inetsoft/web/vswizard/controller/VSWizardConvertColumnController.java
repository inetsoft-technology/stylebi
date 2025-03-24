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

import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.vswizard.HandleWizardExceptions;
import inetsoft.web.vswizard.event.ConvertColumnEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@Controller
public class VSWizardConvertColumnController {

   @Autowired
   public VSWizardConvertColumnController(RuntimeViewsheetRef runtimeViewsheetRef,
                                          VSWizardConvertColumnServiceProxy vsWizardConvertColumnServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsWizardConvertColumnServiceProxy = vsWizardConvertColumnServiceProxy;
   }

   @PutMapping("/api/vs/wizard/checktrap")
   @ResponseBody
   public boolean checktrap(@DecodeParam("vsId") String vsId,
                            @RequestBody ConvertColumnEvent event,
                            Principal principal) throws Exception
   {
     return vsWizardConvertColumnServiceProxy.checktrap(vsId, event, principal);
   }

   @HandleWizardExceptions
   @MessageMapping("/vs/wizard/convertColumn")
   public void convertColumn(@Payload ConvertColumnEvent event,
                             Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      vsWizardConvertColumnServiceProxy.convertColumn(id, event, principal, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private VSWizardConvertColumnServiceProxy vsWizardConvertColumnServiceProxy;
}
