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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.util.*;
import inetsoft.web.composer.vs.event.CloseSheetEvent;
import inetsoft.web.composer.vs.event.NewViewsheetEvent;
import inetsoft.web.composer.ws.event.SaveSheetEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.event.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Controller that provides a REST endpoint for composer viewsheet actions.
 */
@Controller
public class ComposerViewsheetController {
   /**
    * Creates a new instance of <tt>ComposerViewsheetController</tt>.
    */
   @Autowired
   public ComposerViewsheetController(RuntimeViewsheetRef runtimeViewsheetRef,
                                      ViewsheetService viewsheetService,
                                      ComposerViewsheetServiceProxy composerViewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.viewsheetService = viewsheetService;
      this.composerViewsheetService = composerViewsheetService;
   }

   /**
    * Open a new viewsheet in composer.
    *
    * @param principal a principal identifying the current user.
    *
    * @throws Exception if unable to open runtime viewsheet
    */
   @LoadingMask
   @MessageMapping("composer/viewsheet/new")
   public void newViewsheet(@Payload NewViewsheetEvent event, Principal principal,
                            CommandDispatcher commandDispatcher, @LinkUri String linkUri)
      throws Exception
   {
      String runtimeId = viewsheetService.openTemporaryViewsheet(event.getDataSource(), principal, null);
      runtimeViewsheetRef.setRuntimeId(runtimeId);

      composerViewsheetService.newViewsheet(runtimeId, event, principal, commandDispatcher, linkUri);
   }

   /**
    * Save viewsheet.
    *
    * @param principal a principal identifying the current user.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @MessageMapping("composer/viewsheet/save")
   public boolean saveViewsheet(@Payload SaveSheetEvent event, Principal principal,
                                CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      return composerViewsheetService.saveViewsheet(runtimeViewsheetRef.getRuntimeId(),
                                             event, principal, dispatcher, linkUri );
   }

   /**
    * Save viewsheet and send a close event to the client.
    *
    * @param principal a principal identifying the current user.
    *
    * @throws Exception if save viewsheet fails
    */
   @LoadingMask
   @MessageMapping("composer/viewsheet/save-and-close")
   public void saveAndCloseViewsheet(@Payload SaveSheetEvent event, Principal principal,
                                     CommandDispatcher commandDispatcher, @LinkUri String linkUri)
      throws Exception
   {
      if(saveViewsheet(event, principal, commandDispatcher, linkUri)) {
         commandDispatcher.sendCommand(CloseSheetCommand.builder().build());
      }
   }

   /**
    * Close viewsheet.
    *
    * @param event      the event indicating if the autosave file should be deleted
    * @param principal  a principal identifying the current user.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @MessageMapping("composer/viewsheet/close")
   public void closeViewsheet(@Payload CloseSheetEvent event, Principal principal) throws Exception {
      if(runtimeViewsheetRef.getRuntimeId() == null) {
         LOG.warn("Attempted to close viewsheet without runtime ID");
         return;
      }

      composerViewsheetService.closeViewsheet(runtimeViewsheetRef.getRuntimeId(), event, principal);
   }

   /**
    * Preview viewsheet.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @LoadingMask(true)
   @MessageMapping("composer/viewsheet/preview")
   public void previewViewsheet(@Payload OpenPreviewViewsheetEvent event,
                                Principal principal,
                                CommandDispatcher dispatcher,
                                @LinkUri String linkUri) throws Exception
   {
      composerViewsheetService.previewViewsheet(runtimeViewsheetRef.getRuntimeId(),
                                                event, principal, dispatcher, linkUri);
   }

   /**
    * Refresh the preview viewsheet.
    *
    * @param event               the event parameters.
    * @param principal           a principal identifying the current user.
    * @param commandDispatcher   the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @LoadingMask
   @MessageMapping("composer/viewsheet/preview/refresh")
   public void refreshPreviewViewsheet(@Payload OpenPreviewViewsheetEvent event,
                                       Principal principal,
                                       CommandDispatcher commandDispatcher,
                                       @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(composerViewsheetService.refreshPreviewViewsheet(id, event,
                                                          principal, commandDispatcher, linkUri))
      {
         runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
      }
   }

   /**
    * Refresh viewsheet.
    *
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @MessageMapping("composer/viewsheet/refresh")
   public void refreshViewsheet(@Payload OpenViewsheetEvent event,
                                Principal principal, CommandDispatcher dispatcher,
                                @LinkUri String linkUri)
      throws Exception
   {
      composerViewsheetService.refreshViewsheet(runtimeViewsheetRef.getRuntimeId(), event,
                                                principal, dispatcher, linkUri);
   }

   @MessageMapping("composer/viewsheet/checkmv")
   public void checkMV(@Payload CheckMVEvent event, Principal principal,
      CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      composerViewsheetService.checkMV(runtimeViewsheetRef.getRuntimeId(), event, principal,
                                       dispatcher, linkUri);
   }

   @GetMapping("/api/composer/viewsheet/help-url")
   @ResponseBody
   public String getHelpUrL() {
      return Encode.forUri(Tool.getHelpBaseURL());
   }

   @GetMapping("/api/composer/viewsheet/script-help-url")
   @ResponseBody
   public String getScriptHelpUrL() {
      String base = Tool.getHelpBaseURL();
      return Encode.forUri(base + "#cshid=GeneralScriptFunctions");
   }

   @GetMapping("/api/composer/viewsheet/checkDependChanged")
   @ResponseBody
   public boolean checkDependChanged(@RequestParam("rid") String rid) {
      return composerViewsheetService.checkDependChanged(rid);
   }

   @MessageMapping("composer/viewsheet/check-base-ws-expired")
   public void checkWorksheetChanged(CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      composerViewsheetService.checkWorksheetChanged(id, dispatcher, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
   private final ComposerViewsheetServiceProxy composerViewsheetService;
   private static final Logger LOG = LoggerFactory.getLogger(ComposerViewsheetController.class);
}
