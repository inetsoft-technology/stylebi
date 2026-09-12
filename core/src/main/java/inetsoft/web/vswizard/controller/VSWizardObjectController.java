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
import inetsoft.web.vswizard.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;


@Controller
public class VSWizardObjectController {

   @Autowired
   public VSWizardObjectController(RuntimeViewsheetRef runtimeViewsheetRef,
                                   VSWizardObjectServiceProxy vsWizardObjectServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsWizardObjectServiceProxy = vsWizardObjectServiceProxy;
   }

   /**
    * temporary info is created when the vs wizard pane is opened
    */
   @MessageMapping("/vswizard/object/open")
   public void openViewsheetWizardObject(OpenWizardObjectEvent event, Principal principal,
                                         CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      vsWizardObjectServiceProxy.openViewsheetWizardObject(event.getRuntimeId(), event, principal, dispatcher, linkUri);
   }

   @HandleWizardExceptions
   @MessageMapping("/vswizard/object-wizard/refresh")
   public void refreshObjectWizard(Principal principal, CommandDispatcher dispatcher,
                                   @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         LOGGER.error("Get runtimeId failed!");
         return;
      }

      vsWizardObjectServiceProxy.refreshObjectWizard(id, principal, dispatcher, linkUri);
   }

   @HandleWizardExceptions
   @MessageMapping("/vs/wizard/update/assembly")
   public void updateObjectByTempAssembly(UpdateWizardObjectEvent event, Principal principal,
                                          CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      String rid = event.getRuntimeId();
      vsWizardObjectServiceProxy.updateObjectByTempAssembly(rid, event, principal, dispatcher, linkUri);
   }


   @HandleWizardExceptions
   @MessageMapping("/vs/wizard/use-meta")
   public void switchToMeta(@Payload SwitchToMetaModeEvent event, Principal principal,
                            CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         LOGGER.error("Get runtimeId failed!");
         return;
      }

      vsWizardObjectServiceProxy.switchToMeta(id, event, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private VSWizardObjectServiceProxy vsWizardObjectServiceProxy;
   private static final Logger LOGGER = LoggerFactory.getLogger(VSWizardObjectController.class);
}
