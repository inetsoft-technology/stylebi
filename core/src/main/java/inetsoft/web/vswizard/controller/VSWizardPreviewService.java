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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.web.composer.vs.objects.event.ChangeVSObjectTextEvent;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.event.SetPreviewPaneSizeEvent;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.recommender.VSRecommendationModel;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class VSWizardPreviewService {

   public VSWizardPreviewService(ViewsheetService viewsheetService,
                                 VSWizardBindingHandler bindingHandler,
                                 CoreLifecycleService coreLifecycleService,
                                 VSWizardTemporaryInfoService temporaryInfoService)
   {
      this.bindingHandler = bindingHandler;
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
      this.temporaryInfoService = temporaryInfoService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void changeDescription(@ClusterProxyKey String id, ChangeVSObjectTextEvent event,
                                 CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {

      if(id == null) {
         return null;
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      VSTemporaryInfo vsTemporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
      vsTemporaryInfo.setDescription(event.getText());
      VSAssembly latestAssembly = WizardRecommenderUtil.getTempAssembly(rvs.getViewsheet());
      bindingHandler.updateTitle(rvs, latestAssembly);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setPreviewPaneSize(@ClusterProxyKey String id, SetPreviewPaneSizeEvent event,
                                  CommandDispatcher dispatcher, String linkUri,
                                  Principal principal)
      throws Exception
   {
      if(id == null) {
         return null;
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      VSTemporaryInfo vsTemporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
      vsTemporaryInfo.setPreviewPaneSize(event.getSize());
      VSRecommendationModel rmodel = vsTemporaryInfo.getRecommendationModel();
      VSAssembly latestAssembly = WizardRecommenderUtil.getTempAssembly(rvs.getViewsheet());

      if(rmodel != null && latestAssembly != null) {
         bindingHandler.fixTempAssemblySize(latestAssembly.getVSAssemblyInfo(), rvs);
         coreLifecycleService.refreshVSAssembly(rvs, latestAssembly, dispatcher);
      }

      return null;
   }


   private final ViewsheetService viewsheetService;
   private final VSWizardBindingHandler bindingHandler;
   private final CoreLifecycleService coreLifecycleService;
   private final VSWizardTemporaryInfoService temporaryInfoService;
}
