/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.vswizard.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.command.UpdateUndoStateCommand;
import inetsoft.web.viewsheet.model.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.vswizard.command.CloseObjectWizardCommand;
import inetsoft.web.vswizard.event.CloseObjectWizardEvent;
import inetsoft.web.vswizard.handler.*;
import inetsoft.web.vswizard.model.VSWizardEditModes;
import inetsoft.web.vswizard.model.VSWizardOriginalModel;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.security.Principal;
import java.util.Arrays;

@Controller
public class VSCloseObjectWizardController {
   @Autowired
   public VSCloseObjectWizardController(SyncChartHandler syncChartHandler,
                                        ViewsheetService viewsheetService,
                                        VSWizardObjectHandler objectHandler,
                                        WizardVSObjectService objectService,
                                        VSWizardBindingHandler bindingHandler,
                                        WizardViewsheetService wizardVSService,
                                        RuntimeViewsheetRef runtimeViewsheetRef,
                                        VSObjectModelFactoryService objectModelService,
                                        VSWizardTemporaryInfoService temporaryInfoService)
   {
      this.objectService = objectService;
      this.objectHandler = objectHandler;
      this.bindingHandler = bindingHandler;
      this.wizardVSService = wizardVSService;
      this.syncChartHandler = syncChartHandler;
      this.viewsheetService = viewsheetService;
      this.objectModelService = objectModelService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.temporaryInfoService = temporaryInfoService;
   }

   @MessageMapping("/vswizard/object/close/cancel")
   public void save(CloseObjectWizardEvent event, @LinkUri String linkUri, Principal principal,
                     CommandDispatcher dispatcher)
      throws Exception
   {
      closeHandle(false, event, linkUri, principal, dispatcher);
   }

   @MessageMapping("/vswizard/object/close/save")
   public void close(CloseObjectWizardEvent event, @LinkUri String linkUri, Principal principal,
                     CommandDispatcher dispatcher)
      throws Exception
   {
      closeHandle(true, event, linkUri, principal, dispatcher);
   }

   private void closeHandle(boolean save, CloseObjectWizardEvent event,
                            String linkUri, Principal principal,
                            CommandDispatcher dispatcher)
      throws Exception
   {
      VSObjectModel model = null;
      String vsId = runtimeViewsheetRef.getRuntimeId();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      Viewsheet vs = rvs != null ? rvs.getViewsheet() : null;

      if(rvs == null || vs == null) {
         LOGGER.info("Sheet doesn't exist!");
         return;
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      // shouldn't hold up save/close a vs by a long running query/filter since the
      // saved info doesn't need the runtime data.
      box.cancel();
      box.lockWrite();

      try {
         VSTemporaryInfo tempInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
         VSAssembly tempAssembly = WizardRecommenderUtil.getTempAssembly(vs);
         VSWizardOriginalModel originalModel = tempInfo.getOriginalModel();
         VSAssembly originalAssembly =
            originalModel == null || StringUtils.isEmpty(originalModel.getOriginalName()) ?
               null : vs.getAssembly(originalModel.getOriginalName());

         if(originalAssembly != null) {
            originalAssembly.setWizardEditing(false);

            if(!save) {
               bindingHandler.syncChartAssembly(tempInfo.getTempChart(), originalAssembly);
            }
         }

         VSAssemblyInfo info = null;

         if(tempAssembly != null) {
            info = tempAssembly.getVSAssemblyInfo();
         }

         // Treat the finish of the wizard empty assembly as cancel.
         if(!save || tempAssembly == null) {
            temporaryInfoService.destroyTemporary(vsId, principal);

            // if cancelled a new chart, clean up calc fields created in the temp chart
            if(info instanceof ChartVSAssemblyInfo && originalAssembly == null) {
               ((ChartVSAssemblyInfo) info).getVSChartInfo().removeFields();
               WizardRecommenderUtil.cleanupCalculateRefs(vs, (ChartVSAssemblyInfo) info);
            }

            RuntimeViewsheet orvs = objectHandler.getOriginalRViewsheet(rvs, principal);

            if(save) {
               updateAllCalcField(vs, orvs.getViewsheet());
            }

            return;
         }

         if(info instanceof ChartVSAssemblyInfo && originalAssembly instanceof ChartVSAssembly) {
            ((ChartVSAssemblyInfo) info).setMaxSize(null);
            vs.setMaxMode(false);
            ((ChartVSAssemblyInfo) info).getVSChartInfo().setWidthResized(false);
            ((ChartVSAssemblyInfo) info).getVSChartInfo().setHeightResized(false);

            // restore original assembly's descriptor. some descriptor settings may be
            // changed for chart display in the wizard preview (e.g. inPlot)
            syncChartHandler.syncChart(tempInfo, (ChartVSAssembly) originalAssembly,
               (ChartVSAssembly) tempAssembly, false, false);
         }

         if(!VSWizardEditModes.FULL_EDITOR.equals(event.getEditMode()) &&
            info instanceof DataVSAssemblyInfo)
         {
            ((DataVSAssemblyInfo) info).setEditedByWizard(true);
         }

         boolean wizardDashboard = VSWizardEditModes.WIZARD_DASHBOARD.equals(event.getEditMode());
         RuntimeViewsheet orvs = objectHandler.getOriginalRViewsheet(rvs, principal);
         updateAllCalcField(vs, orvs.getViewsheet());
         tempAssembly = objectHandler.updateAssemblyByTemporary(
            orvs, rvs, tempAssembly, originalAssembly, dispatcher, linkUri);
         boolean maxMode = vs.isMaxMode();
         vs = orvs.getViewsheet();

         if(vs != null) {
            vs.setMaxMode(maxMode);
         }

         if(!VSWizardEditModes.FULL_EDITOR.equals(event.getEditMode())) {
            temporaryInfoService.destroyTemporary(vsId, principal);
         }
         else {
            temporaryInfoService.destroyTempAssembly(rvs);
         }

         if(save && event.getOldOriginalName() != null) {
            vs.removeAssembly(event.getOldOriginalName());
         }

         if(event.getViewer() && originalAssembly != null) {
            tempAssembly.getInfo().setName(originalAssembly.getInfo().getName());
         }

         if(wizardDashboard) {
            Point newRowAndCol = wizardVSService.updateGridRowsAndNewBlock(vs.getAssemblies(),
               dispatcher);

            if(vs.getAssembly(tempAssembly.getName()) != null) {
               vs.removeAssembly(tempAssembly.getName());
            }

            this.objectService.addVsObject(orvs, tempAssembly, newRowAndCol.y, newRowAndCol.x,
               originalAssembly != null, linkUri, principal, dispatcher);
         }
         else {
            vs.addAssembly(tempAssembly, originalAssembly == null);
         }

         model = objectModelService.createModel(tempAssembly, orvs);
         orvs.addCheckpoint(orvs.getViewsheet().prepareCheckpoint(), null);

         UpdateUndoStateCommand command = new UpdateUndoStateCommand();
         command.setPoints(orvs.size());
         command.setCurrent(orvs.getCurrent());
         command.setSavePoint(orvs.getSavePoint());
         command.setId(orvs.getID());
         dispatcher.sendCommand(command);
      }
      finally {
         box.unlockWrite();
         // In any case, the user should be able to close the dialog.
         CloseObjectWizardCommand command = new CloseObjectWizardCommand(model, save);
         dispatcher.sendCommand(command);
      }
   }

   private void updateAllCalcField(Viewsheet currentVS, Viewsheet originalVS) {
      if(currentVS.getCalcFieldSources() == null || currentVS.getCalcFieldSources().size() == 0) {
         for(String source : originalVS.getCalcFieldSources()) {
            originalVS.removeCalcField(source);
         }
      }
      else {
         for(String source : currentVS.getCalcFieldSources()) {
            updateCalcField(currentVS, originalVS, source);
         }
      }
   }

   private void updateCalcField(Viewsheet currentVS, Viewsheet originalVS, String source) {
      if(currentVS == originalVS || source == null) {
         return;
      }

      originalVS.removeCalcField(source);
      originalVS.removeAggrField(source);
      CalculateRef[] calcs = currentVS.getCalcFields(source);

      if(calcs != null) {
         Arrays.stream(calcs).forEach((CalculateRef calc) -> {
            originalVS.addCalcField(source, calc);
         });
      }

      AggregateRef[] aggrs = currentVS.getAggrFields(source);

      if(aggrs != null) {
         Arrays.stream(aggrs).forEach((AggregateRef ref) -> {
            originalVS.addAggrField(source, ref);
         });
      }
   }

   private final SyncChartHandler syncChartHandler;
   private final ViewsheetService viewsheetService;
   private final VSWizardObjectHandler objectHandler;
   private final WizardVSObjectService objectService;
   private final VSWizardBindingHandler bindingHandler;
   private final WizardViewsheetService wizardVSService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSObjectModelFactoryService objectModelService;
   private final VSWizardTemporaryInfoService temporaryInfoService;

   private static final Logger LOGGER = LoggerFactory.getLogger(VSCloseObjectWizardController.class);
}
