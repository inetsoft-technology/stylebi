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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.internal.LicenseException;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.util.ThreadContext;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.composer.ws.event.OpenSheetEventValidator;
import inetsoft.web.embed.EmbedErrorCommand;
import inetsoft.web.service.LicenseService;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.command.VSDependencyChangedCommand;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.ViewsheetRouteDataModel;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Controller that processes requests to open a viewsheet instance.
 */
@Controller
public class OpenViewsheetController {
   /**
    * Creates a new instance of <tt>ViewsheetController</tt>.
    */
   @Autowired
   public OpenViewsheetController(RuntimeViewsheetRef runtimeViewsheetRef,
                                  RuntimeViewsheetManager runtimeViewsheetManager,
                                  VSLifecycleService vsLifecycleService,
                                  LicenseService licenseService,
                                  OpenViewsheetServiceProxy serviceProxy,
                                  ViewsheetService viewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.runtimeViewsheetManager = runtimeViewsheetManager;
      this.vsLifecycleService = vsLifecycleService;
      this.licenseService = licenseService;
      this.serviceProxy = serviceProxy;
      this.viewsheetService = viewsheetService;
   }

   @GetMapping("api/vs/route-data")
   @ResponseBody
   public ViewsheetRouteDataModel getRouteData(@RequestParam("id") String identifier,
                                               Principal principal) throws Exception
   {
      boolean scaleToScreen = false;
      boolean fitToWidth = false;
      AssetEntry entry = AssetEntry.createAssetEntry(identifier);
      Viewsheet vs = (Viewsheet) viewsheetService.getAssetRepository().getSheet(
         entry, principal, false, AssetContent.CONTEXT);

      if(vs != null) {
         ViewsheetInfo info = vs.getViewsheetInfo();
         scaleToScreen = info.isScaleToScreen();
         fitToWidth = info.isFitToWidth();
      }

      return ViewsheetRouteDataModel.builder()
         .scaleToScreen(scaleToScreen)
         .fitToWidth(fitToWidth)
         .build();
   }

   /**
    * Validator for whether or not the viewsheet has an autosaved version.
    */
   @RequestMapping(
      value = "api/vs/open", method = RequestMethod.POST)
   @ResponseBody
   public OpenSheetEventValidator validateOpen(
      @RequestBody OpenViewsheetEvent event, Principal principal)
   {
      String id = event.getEntryId();
      AssetEntry entry = AssetEntry.createAssetEntry(id);
      boolean autoSaveFileExists = entry != null && AutoSaveUtils.exists(entry, principal);
      return OpenSheetEventValidator.builder()
         .autoSaveFileExists(autoSaveFileExists)
         .forbiddenSourcesMessage("")
         .build();
   }

   /**
    * Opens a viewsheet.
    *
    * @param event             the event parameters.
    * @param principal         a principal identifying the current user.
    * @param commandDispatcher the command dispatcher.
    *
    * @throws Exception if the viewsheet could not be opened.
    */
   @LoadingMask(true)
   @MessageMapping("/open")
   @HandleAssetExceptions
   @SwitchOrg
   public void openViewsheet(@OrganizationID("getOrgId()") @Payload OpenViewsheetEvent event,
                             Principal principal, CommandDispatcher commandDispatcher,
                             @LinkUri String linkUri)
      throws Exception
   {
      if(licenseService.isCpuUnlicensed()) {
         throw new LicenseException(licenseService.getCpuUnlicensedMSG());
      }

      if(principal instanceof SRPrincipal) {
         ThreadContext.setLocale(((SRPrincipal) principal).getLocale());
      }

      boolean existing = event.getRuntimeViewsheetId() != null;
      String id = null;

      try {
         id = vsLifecycleService.openViewsheet(
            event, principal, commandDispatcher, runtimeViewsheetRef, runtimeViewsheetManager,
            linkUri);
      }
      catch(Exception e) {
         // embed web component failed to load
         if(event.getEmbedAssemblyName() != null) {
            commandDispatcher.sendCommand(
               EmbedErrorCommand.builder().message(e.getMessage()).build());
         }
         else if(event.isEmbed()) {
            commandDispatcher.sendCommand(
               EmbedErrorCommand.builder().message(e.getMessage()).build());
            return;
         }

         throw e;
      }

      if(!existing && !event.isViewer()) {
         serviceProxy.sendPopulateObjectTreeCommand(id, event.getEntryId(), commandDispatcher, principal);
      }

      // if viewsheet is create by wizard, it must have source, so if new sheet from wizard and
      // finished, we should send command to show warning for user.
      if(event.isNewSheet()) {
         commandDispatcher.sendCommand(new VSDependencyChangedCommand(true));
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final RuntimeViewsheetManager runtimeViewsheetManager;
   private final VSLifecycleService vsLifecycleService;
   private final LicenseService licenseService;
   private final OpenViewsheetServiceProxy serviceProxy;
   private final ViewsheetService viewsheetService;
}
