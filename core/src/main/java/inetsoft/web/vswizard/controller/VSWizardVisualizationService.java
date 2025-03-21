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
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.vswizard.command.RefreshDescriptionCommand;
import inetsoft.web.vswizard.event.ChangeVisualizationTypeEvent;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.VSWizardOriginalModel;
import inetsoft.web.vswizard.model.recommender.*;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.recommender.object.VSGaugeRecommendationFactory;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class VSWizardVisualizationService {

   public VSWizardVisualizationService(ViewsheetService viewsheetService,
                                       VSWizardBindingHandler bindingHandler,
                                       VSWizardTemporaryInfoService temporaryInfoService)
   {
      this.bindingHandler = bindingHandler;
      this.viewsheetService = viewsheetService;
      this.temporaryInfoService = temporaryInfoService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void changeSelectedType(@ClusterProxyKey String id, ChangeVisualizationTypeEvent event,
                                  CommandDispatcher dispatcher, Principal principal,
                                  @LinkUri String linkUri)
      throws Exception
   {
      if(id == null) {
         LOGGER.error("Get runtimeId failed!");
         return null;
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      box.cancel(true);
      box.lockWrite();

      try {
         VSTemporaryInfo vsTemporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
         VSWizardOriginalModel originalModel = vsTemporaryInfo.getOriginalModel();
         VSRecommendType originalType = originalModel == null ? null : originalModel.getOriginalType();
         VSRecommendationModel recommendationModel = vsTemporaryInfo.getRecommendationModel();
         // if vs is in max-mode and the new recommend obj is not, VSEventUtil.isVisible()
         // will return false and the newly selected chart will be invisible. (48379)
         rvs.getViewsheet().setMaxMode(false);

         // already destroyed
         if(recommendationModel == null) {
            return null;
         }

         if(changeType(rvs, recommendationModel, event.getType(), event.getSubTypeIndex())) {
            // change object type to clean description.
            vsTemporaryInfo.setDescription(null);
            RefreshDescriptionCommand descCommand = new RefreshDescriptionCommand(null);
            dispatcher.sendCommand(descCommand);
         }

         recommendationModel.setSelectedType(event.getType());
         vsTemporaryInfo.setSelectedType(event.getType());
         // switching in a new assembly, need t oreset max mode (49158)
         rvs.getViewsheet().setMaxMode(false);

         if(event.getType().equals(originalType)) {
            bindingHandler.addOriginalAsPrimary(id, principal, linkUri, true, dispatcher);
            return null;
         }

         if(originalModel != null && originalModel.getOriginalName() != null) {
            bindingHandler.clearOriginalBrush(rvs.getViewsheet(), originalModel.getOriginalName());
         }

         VSObjectRecommendation vr =
            WizardRecommenderUtil.getSelectedRecommendation(event.getType(), rvs);

         if(vr != null) {
            vr.setSelectedIndex(event.getSubTypeIndex());

            if(vr.getSubTypes().size() > 0) {
               vsTemporaryInfo.setSelectedSubType(getSubType(vr, event.getSubTypeIndex()));
            }

            bindingHandler.updatePrimaryAssembly(vr, rvs, false, linkUri, dispatcher, false);
         }
      }
      finally {
         box.unlockWrite();
      }

      return null;
   }

   private Boolean changeType(RuntimeViewsheet rvs, VSRecommendationModel rmodel,
                              VSRecommendType type, int subTypeIdx)
   {
      if(rmodel == null || rmodel.getSelectedType() == null) {
         return false;
      }

      VSRecommendType selectedType = rmodel.getSelectedType();
      VSObjectRecommendation vr = WizardRecommenderUtil.getSelectedRecommendation(type, rvs);

      if(!selectedType.equals(type)) {
         return true;
      }

      VSSubType selectedSubType = getSubType(vr, subTypeIdx);

      return selectedSubType != null && !(selectedSubType instanceof ChartSubType ||
         isGaugeSubType(selectedSubType));
   }

   private VSSubType getSubType(VSObjectRecommendation vr, int subTypeIndex) {
      return vr == null || vr.getSubTypes().size() == 0 || vr.getSubTypes().size() <= subTypeIndex
         ? null : vr.getSubTypes().get(subTypeIndex);
   }

   private Boolean isGaugeSubType(VSSubType subType) {
      return VSGaugeRecommendationFactory.GAUGE_FACES.stream()
         .anyMatch((VSSubType faceType) -> faceType.equals(subType));
   }

   private final ViewsheetService viewsheetService;
   private final VSWizardBindingHandler bindingHandler;
   private final VSWizardTemporaryInfoService temporaryInfoService;

   private static final Logger LOGGER = LoggerFactory.getLogger(VSWizardVisualizationService.class);
}
