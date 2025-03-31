/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.event.CloseVsWizardEvent;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import inetsoft.web.vswizard.service.WizardViewsheetService;
import org.springframework.stereotype.Service;
import java.security.Principal;

@Service
@ClusterProxy
public class VSWizardDialogService {

   public VSWizardDialogService(ViewsheetService viewsheetService,
                                CoreLifecycleService coreLifecycleService,
                                WizardViewsheetService wizardVSService,
                                VSBindingService vsBindingService,
                                VSWizardTemporaryInfoService temporaryInfoService)
   {
      this.viewsheetService = viewsheetService;
      this.wizardVSService = wizardVSService;
      this.coreLifecycleService = coreLifecycleService;
      this.vsBindingService = vsBindingService;
      this.temporaryInfoService = temporaryInfoService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void createRuntimeSheet(@ClusterProxyKey String runtimeId, String linkUri,
                                  CommandDispatcher dispatcher, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      rvs.setSocketSessionId(dispatcher.getSessionId());
      rvs.setSocketUserName(dispatcher.getUserName());
      AssetEntry vsEntry = rvs.getEntry();
      rvs.setWizardViewsheet(true);

      SetRuntimeIdCommand runtimeIdcommand = new SetRuntimeIdCommand(runtimeId);
      dispatcher.sendCommand(runtimeIdcommand);
      SetViewsheetInfoCommand setVSInfoCommand = new SetViewsheetInfoCommand();
      setVSInfoCommand.setLinkUri(linkUri);
      setVSInfoCommand.setAssetId(vsEntry.toIdentifier());
      dispatcher.sendCommand(setVSInfoCommand);

      ChangedAssemblyList clist = coreLifecycleService.createList(true, dispatcher, rvs, linkUri);
      coreLifecycleService.refreshViewsheet(rvs, vsEntry.toIdentifier(), linkUri,
                                            dispatcher, true, false, false, clist);
      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String createRuntimeSheet0(@ClusterProxyKey String runtimeId, boolean viewer,
                                     boolean temporarySheet, Principal principal) throws Exception
   {
      String nrid = this.vsBindingService.createRuntimeSheet(
         runtimeId, viewer, temporarySheet, principal, null);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(nrid, principal);
      rvs.setWizardViewsheet(true);

      return nrid;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void closeVSWizard(@ClusterProxyKey String runtimeId, CloseVsWizardEvent event,
                             CommandDispatcher dispatcher, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      AssetEntry entry = rvs.getEntry();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      box.lockWrite();

      try {
         rvs.setVSTemporaryInfo(null);
      }
      finally {
         box.unlockWrite();
      }

      SaveSheetCommand command = SaveSheetCommand.builder()
         .savePoint(rvs.getSavePoint())
         .id(entry.toIdentifier())
         .build();
      dispatcher.sendCommand(command);

      return null;
   }

   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
   private final WizardViewsheetService wizardVSService;
   private final VSBindingService vsBindingService;
   private final VSWizardTemporaryInfoService temporaryInfoService;
}
