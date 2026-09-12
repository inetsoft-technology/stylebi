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

import inetsoft.web.viewsheet.model.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.vswizard.event.CloseObjectWizardEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
public class VSCloseObjectWizardController {
   @Autowired
   public VSCloseObjectWizardController(RuntimeViewsheetRef runtimeViewsheetRef,
                                        VSCloseObjectWizardServiceProxy vsCloseObjectWizardServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsCloseObjectWizardServiceProxy = vsCloseObjectWizardServiceProxy;
   }

   @MessageMapping("/vswizard/object/close/cancel")
   public void save(CloseObjectWizardEvent event, @LinkUri String linkUri, Principal principal,
                     CommandDispatcher dispatcher)
      throws Exception
   {
      closeHandle(false, event, linkUri, principal, dispatcher);
   }

   @MessageMapping("/vswizard/object/close/save")
   public void close(CloseObjectWizardEvent event, @LinkUri String linkUri, Principal principal,
                     CommandDispatcher dispatcher)
      throws Exception
   {
      closeHandle(true, event, linkUri, principal, dispatcher);
   }

   private void closeHandle(boolean save, CloseObjectWizardEvent event,
                            String linkUri, Principal principal,
                            CommandDispatcher dispatcher)
      throws Exception
   {
      String vsId = runtimeViewsheetRef.getRuntimeId();

      vsCloseObjectWizardServiceProxy.closeHandle(vsId, save, event, linkUri, principal, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private VSCloseObjectWizardServiceProxy vsCloseObjectWizardServiceProxy;
}
