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


import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.vswizard.HandleWizardExceptions;
import inetsoft.web.vswizard.Recommend;
import inetsoft.web.vswizard.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * vs wizard binding tree controller.
 */
@Controller
public class VSWizardBindingController {

   @Autowired
   public VSWizardBindingController(VSWizardBindingServiceProxy vsWizardBindingServiceProxy,
                                    RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.vsWizardBindingServiceProxy = vsWizardBindingServiceProxy;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   @LoadingMask
   @HandleWizardExceptions
   @MessageMapping("/vswizard/binding/tree")
   public void getBindingTree(@Payload GetBindingTreeEvent event,
                              CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         LOGGER.error("Get runtimeId failed!");
         return;
      }

      vsWizardBindingServiceProxy.getBindingTree(id, event, dispatcher, principal);
   }

   @LoadingMask
   @Recommend
   @HandleWizardExceptions
   @MessageMapping("/vswizard/binding/tree/node-changed")
   public void bindingTreeNodeChanged(@Payload RefreshBindingFieldsEvent event,
                                      CommandDispatcher dispatcher, Principal principal,
                                      @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      vsWizardBindingServiceProxy.bindingTreeNodeChanged(id, event, dispatcher, principal, linkUri);
   }

   @Recommend
   @HandleWizardExceptions
   @MessageMapping("/vswizard/binding/tree/refresh-fields")
   public void refreshBindingRefs(@Payload RefreshBindingFieldsEvent event,
                                  CommandDispatcher dispatcher, Principal principal,
                                  @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         LOGGER.error("Get runtimeId failed!");
         return;
      }

      vsWizardBindingServiceProxy.refreshBindingRefs(id, event, dispatcher, principal, linkUri);
   }

   @Recommend
   @MessageMapping("/vswizard/binding/refresh")
   @HandleWizardExceptions
   public void refreshBindingInfo(@Payload UpdateVsWizardBindingEvent event,
                                  CommandDispatcher dispatcher, Principal principal,
                                  @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         LOGGER.error("Get runtimeId failed!");
         return;
      }

      vsWizardBindingServiceProxy.refreshBindingInfo(id, event, dispatcher, principal, linkUri);
   }

   @HandleWizardExceptions
   @MessageMapping("/vswizard/binding/update-columns")
   public void updateColumns(@Payload UpdateColumnsEvent event,
                             CommandDispatcher dispatcher, Principal principal,
                             @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         LOGGER.error("Get runtimeId failed!");
         return;
      }

      vsWizardBindingServiceProxy.updateColumns(id, event, dispatcher, principal, linkUri);
   }

   private final VSWizardBindingServiceProxy vsWizardBindingServiceProxy;
   private final RuntimeViewsheetRef runtimeViewsheetRef;


   private static final Logger LOGGER = LoggerFactory.getLogger(VSWizardBindingController.class);
}
