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

package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.viewsheet.controller.chart.VSChartControllerService;
import inetsoft.web.viewsheet.event.chart.VSChartLegendResizeEvent;
import inetsoft.web.viewsheet.model.ViewsheetRouteDataModel;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Service
@ClusterProxy
public class OpenViewsheetService {

   public OpenViewsheetService(ViewsheetService viewsheetService,
                               VSObjectTreeService vsObjectTreeService) {
      this.viewsheetService = viewsheetService;
      this.vsObjectTreeService = vsObjectTreeService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ViewsheetRouteDataModel getRouteData(@ClusterProxyKey String identifier,
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

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void sendPopulateObjectTreeCommand(@ClusterProxyKey String runtimeId, String eventEntryId,
                             CommandDispatcher commandDispatcher, Principal principal) throws Exception {
      AssetEntry entry = AssetEntry.createAssetEntry(eventEntryId);

      if(entry.getScope() != AssetRepository.TEMPORARY_SCOPE) {
         VSEventUtil.deleteAutoSavedFile(entry, principal);
      }

      if(commandDispatcher.stream().noneMatch(c -> "CollectParametersCommand".equals(c.getType()))) {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
         VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
         PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
         commandDispatcher.sendCommand(treeCommand);
      }

      return null;
   }

   private final ViewsheetService viewsheetService;
   private final VSObjectTreeService vsObjectTreeService;
}
