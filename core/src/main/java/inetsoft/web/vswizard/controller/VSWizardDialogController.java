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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.event.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
public class VSWizardDialogController {
   @Autowired
   public VSWizardDialogController(ViewsheetService viewsheetService,
                                   RuntimeViewsheetManager runtimeViewsheetManager,
                                   RuntimeViewsheetRef runtimeViewsheetRef,
                                   VSWizardDialogServiceProxy vsWizardDialogServiceProxy)
   {
      this.viewsheetService = viewsheetService;
      this.runtimeViewsheetManager = runtimeViewsheetManager;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsWizardDialogServiceProxy =vsWizardDialogServiceProxy;
   }

   @LoadingMask
   @MessageMapping("/vswizard/dialog/open")
   public void createRuntimeSheet(@Payload OpenVsWizardEvent event,
                                  @LinkUri String linkUri,
                                  CommandDispatcher dispatcher,
                                  Principal principal)
      throws Exception
   {
      String runtimeId = viewsheetService.openTemporaryViewsheet(event.getEntry(), principal, null);
      vsWizardDialogServiceProxy.createRuntimeSheet(runtimeId, linkUri, dispatcher, principal);

      if(runtimeViewsheetManager != null) {
         runtimeViewsheetManager.sheetOpened(runtimeId);
      }

      if(runtimeViewsheetRef != null) {
         runtimeViewsheetRef.setRuntimeId(runtimeId);
      }
   }

   @GetMapping("/api/vswizard/dialog/open")
   public String createRuntimeSheet0(@RequestParam("runtimeId") String runtimeId,
                                     @RequestParam("viewer") boolean viewer,
                                     @RequestParam("temporarySheet") boolean temporarySheet,
                                     Principal principal)
      throws Exception
   {
      return vsWizardDialogServiceProxy.createRuntimeSheet0(runtimeId, viewer, temporarySheet, principal);
   }

   @LoadingMask
   @MessageMapping("/vswizard/dialog/update-runtimeid")
   public void updateRuntimeId(@Payload UpdateRuntimeIdEvent event, CommandDispatcher dispatcher) {
      String id = event.getRuntimeId();

      if(runtimeViewsheetRef != null) {
         runtimeViewsheetRef.setRuntimeId(id);
      }

      if(runtimeViewsheetManager != null) {
         runtimeViewsheetManager.sheetOpened(id);
      }
   }

   @MessageMapping("/vswizard/dialog/close")
   public void closeVSWizard(@Payload CloseVsWizardEvent event,
                             CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {
      vsWizardDialogServiceProxy.closeVSWizard(runtimeViewsheetRef.getRuntimeId(), event,
                                               dispatcher, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSWizardDialogServiceProxy vsWizardDialogServiceProxy;
   private final RuntimeViewsheetManager runtimeViewsheetManager;
   private final ViewsheetService viewsheetService;

}
