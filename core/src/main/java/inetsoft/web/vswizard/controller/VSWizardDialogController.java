/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.vswizard.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.event.*;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import inetsoft.web.vswizard.service.WizardViewsheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class VSWizardDialogController {
   @Autowired
   public VSWizardDialogController(ViewsheetService viewsheetService,
                                   PlaceholderService placeholderService,
                                   RuntimeViewsheetRef runtimeViewsheetRef,
                                   RuntimeViewsheetManager runtimeViewsheetManager,
                                   WizardViewsheetService wizardVSService,
                                   VSBindingService vsBindingService,
                                   VSWizardTemporaryInfoService temporaryInfoService)
   {
      this.viewsheetService = viewsheetService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.runtimeViewsheetManager = runtimeViewsheetManager;
      this.wizardVSService = wizardVSService;
      this.placeholderService = placeholderService;
      this.vsBindingService = vsBindingService;
      this.temporaryInfoService = temporaryInfoService;
   }

   @LoadingMask
   @MessageMapping("/vswizard/dialog/open")
   public void createRuntimeSheet(@Payload OpenVsWizardEvent event,
                                  @LinkUri String linkUri,
                                  CommandDispatcher dispatcher,
                                  Principal principal)
      throws Exception
   {
      String id = viewsheetService.openTemporaryViewsheet(event.getEntry(), principal, null);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      rvs.setSocketSessionId(dispatcher.getSessionId());
      rvs.setSocketUserName(dispatcher.getUserName());
      AssetEntry vsEntry = rvs.getEntry();
      rvs.setWizardViewsheet(true);

      if(runtimeViewsheetRef != null) {
         runtimeViewsheetRef.setRuntimeId(id);
      }

      if(runtimeViewsheetManager != null) {
         runtimeViewsheetManager.sheetOpened(id);
      }

      SetRuntimeIdCommand runtimeIdcommand = new SetRuntimeIdCommand(id);
      dispatcher.sendCommand(runtimeIdcommand);
      SetViewsheetInfoCommand setVSInfoCommand = new SetViewsheetInfoCommand();
      setVSInfoCommand.setLinkUri(linkUri);
      setVSInfoCommand.setAssetId(vsEntry.toIdentifier());
      dispatcher.sendCommand(setVSInfoCommand);

      ChangedAssemblyList clist = placeholderService.createList(true, dispatcher, rvs, linkUri);
      placeholderService.refreshViewsheet(rvs, vsEntry.toIdentifier(), linkUri,
                                          dispatcher, true, false, false, clist);
   }

   @GetMapping("/api/vswizard/dialog/open")
   public String createRuntimeSheet0(@RequestParam("runtimeId") String runtimeId,
                                     @RequestParam("viewer") boolean viewer,
                                     @RequestParam("temporarySheet") boolean temporarySheet,
                                     Principal principal)
      throws Exception
   {
      String nrid = this.vsBindingService.createRuntimeSheet(
         runtimeId, viewer, temporarySheet, principal, null);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(nrid, principal);
      rvs.setWizardViewsheet(true);

      return nrid;
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
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeViewsheetRef.getRuntimeId(), principal);
      AssetEntry entry = rvs.getEntry();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      box.lockWrite();

      try {
         rvs.setVSTemporaryInfo(null);
      }
      finally {
         box.unlockWrite();
      }

      /*
      if(VSWizardEditModes.WIZARD_DASHBOARD.equals(event.getEditMode())) {
         // click on finishEditing and fullEdit to go back to viewsheet pane
         // and binding pane without executing relayout viewsheet
         if(event.getWizardGridRows() != 0) {
            //wizardVSService.relayoutViewsheet(rvs, event.getWizardGridRows(), dispatcher);
            // add margin
            Arrays.stream(rvs.getViewsheet().getAssemblies())
               .forEach(a -> {
                  Point offset = a.getPixelOffset();
                  a.setPixelOffset(new Point(offset.x + 20, offset.y + 20));
               });
         }
      }
      */

      SaveSheetCommand command = SaveSheetCommand.builder()
         .savePoint(rvs.getSavePoint())
         .id(entry.toIdentifier())
         .build();
      dispatcher.sendCommand(command);
   }

   private final ViewsheetService viewsheetService;
   private final PlaceholderService placeholderService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final RuntimeViewsheetManager runtimeViewsheetManager;
   private final WizardViewsheetService wizardVSService;
   private final VSBindingService vsBindingService;
   private final VSWizardTemporaryInfoService temporaryInfoService;
}
