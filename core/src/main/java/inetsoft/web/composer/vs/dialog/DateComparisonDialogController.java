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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.util.data.CommonKVModel;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.graph.ChangeChartTypeProcessor;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.graph.aesthetic.VisualFrameModel;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import inetsoft.web.composer.model.vs.DateComparisonDialogModel;
import inetsoft.web.composer.model.vs.DateComparisonPaneModel;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;

@RestController
public class DateComparisonDialogController {
   public DateComparisonDialogController(ViewsheetService viewsheetService,
                                         RuntimeViewsheetRef runtimeViewsheetRef,
                                         VSAssemblyInfoHandler vsAssemblyInfoHandler,
                                         VisualFrameModelFactoryService vFactoryService)
   {
      this.viewsheetService = viewsheetService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsAssemblyInfoHandler = vsAssemblyInfoHandler;
      this.vFactoryService = vFactoryService;
   }

   @GetMapping("/api/composer/vs/date-comparison-model/{objectId}/**")
   public DateComparisonPaneModel getDateComparison(@PathVariable("objectId") String objectId,
                                                    @RemainingPath String runtimeId,
                                                    Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly vsAssembly = vs.getAssembly(objectId);

      if(vsAssembly == null) {
         return null;
      }

      VSAssemblyInfo info = vsAssembly.getVSAssemblyInfo();

      if(!(info instanceof DateCompareAbleAssemblyInfo)) {
         return null;
      }

      DateComparisonInfo dateComparisonInfo = ((DateCompareAbleAssemblyInfo) info).getDateComparisonInfo();

      DateComparisonPaneModel model = dateComparisonInfo != null ? new DateComparisonPaneModel(dateComparisonInfo) :
         new DateComparisonPaneModel();

      VisualFrameModel visualFrameModel = model.getVisualFrameModel();
      VisualFrameWrapper wrapper;

      if(visualFrameModel == null && (dateComparisonInfo == null || dateComparisonInfo.getDcColorFrameWrapper() == null)) {
         CategoricalColorFrame frame = new CategoricalColorFrame();
         /*
         frame.setDefaultColors(Arrays.stream(CategoricalColorFrame.COLOR_PALETTE)
                                   .map(c -> GTool.getComplementaryColor(c))
                                   .toArray(Color[]::new));
         */
         frame.setShareColors(false);
         wrapper = VisualFrameWrapper.wrap(frame);
         visualFrameModel = vFactoryService.createVisualFrameModel(wrapper);
         model.setVisualFrameModel(visualFrameModel);
      }
      else {
         wrapper = dateComparisonInfo.getDcColorFrameWrapper();
      }

      visualFrameModel = vFactoryService.createVisualFrameModel(wrapper);
      model.setVisualFrameModel(visualFrameModel);

      return model;
   }

   @Undoable
   @MessageMapping("composer/vs/date-comparison-dialog-model/{assemblyName}")
   public void setDateComparison(@DestinationVariable("assemblyName") String assemblyName,
                                 @RequestBody DateComparisonDialogModel model,
                                 @LinkUri String linkUri,
                                 Principal principal,
                                 CommandDispatcher dispatcher)
      throws Exception
   {
      DateComparisonInfo comparisonInfo = model.getDateComparisonPaneModel() == null ? null :
         model.getDateComparisonPaneModel().toDateComparisonInfo();
      VisualFrameWrapper wrapper = comparisonInfo.getDcColorFrameWrapper();

      if(wrapper != null) {
         VisualFrameWrapper nwrapper = vFactoryService.updateVisualFrameWrapper(
            wrapper, model.getDateComparisonPaneModel().getVisualFrameModel());
         ((CategoricalColorFrameWrapper) nwrapper).setShareColors(false);
         comparisonInfo.setDcColorFrameWrapper(nwrapper);
      }

      setDateComparison(assemblyName, comparisonInfo, model.getShareFromAssembly(), linkUri,
         principal, dispatcher);
   }

   @Undoable
   @MessageMapping("composer/vs/date-comparison-dialog-model/clear/{assemblyName}")
   public void clearDateComparison(@DestinationVariable("assemblyName") String assemblyName,
                                   @LinkUri String linkUri,
                                   Principal principal,
                                   CommandDispatcher dispatcher)
      throws Exception
   {
      setDateComparison(assemblyName, null, null, linkUri, principal, dispatcher);
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = viewsheet.getAssembly(assemblyName);

      if(assembly instanceof ChartVSAssembly) {
         GraphUtil.fixVisualFrames(((ChartVSAssembly) assembly).getVSChartInfo());
      }
   }

   @GetMapping("/api/composer/vs/date-comparison-dialog-model/{objectId}/**")
   public DateComparisonDialogModel getShare(@PathVariable("objectId") String objectId,
                                             @RemainingPath String runtimeId,
                                             Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly currentAssembly = vs.getAssembly(objectId);
      Assembly[] assemblies = vs.getAssemblies(true);
      ArrayList<CommonKVModel<String, Integer>> assemblyNames = new ArrayList<>();
      String shareFrom = null;

      for(Assembly assembly : assemblies) {
         if(!(assembly instanceof VSAssembly) || assembly == currentAssembly) {
            continue;
         }

         VSAssemblyInfo vinfo = ((VSAssembly) assembly).getVSAssemblyInfo();

         if(!(vinfo instanceof DateCompareAbleAssemblyInfo)) {
            continue;
         }

         if(DateComparisonUtil.isDateComparisonDefined(vinfo, false)) {
            assemblyNames.add(
               new CommonKVModel(assembly.getAbsoluteName(), assembly.getAssemblyType()));
         }
      }

      if(currentAssembly.getInfo() instanceof DateCompareAbleAssemblyInfo) {
         shareFrom =
            ((DateCompareAbleAssemblyInfo) currentAssembly.getInfo()).getComparisonShareFrom();
      }

      DateComparisonPaneModel dateComparisonModel =
         getDateComparison(Tool.isEmptyString(shareFrom) ? objectId : shareFrom, runtimeId, principal);
      DateComparisonUtil.fixDCModelProperties(vs, dateComparisonModel, objectId, shareFrom);

      DateComparisonDialogModel model = new DateComparisonDialogModel();
      model.setShareFromAvailableAssemblies(
         assemblyNames.toArray(new CommonKVModel[assemblyNames.size()]));
      model.setDateComparisonPaneModel(dateComparisonModel);
      model.setShareFromAssembly(shareFrom);

      return model;
   }

   private void setDateComparison(String assemblyName, DateComparisonInfo comparisonInfo,
                                  String shareAssembly, String linkUri, Principal principal,
                                  CommandDispatcher dispatcher)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs =
         engine.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = viewsheet.getAssembly(assemblyName);
      VSAssemblyInfo assemblyInfo = assembly.getVSAssemblyInfo().clone();

      // color aesthetic may change.
      rvs.getViewsheet().clearSharedFrames();

      if(assemblyInfo instanceof DateCompareAbleAssemblyInfo) {
         DateCompareAbleAssemblyInfo dinfo = (DateCompareAbleAssemblyInfo) assemblyInfo;

         dinfo.setDateComparisonInfo(comparisonInfo);
         dinfo.setComparisonShareFrom(shareAssembly);

         if(assemblyInfo instanceof ChartVSAssemblyInfo && shareAssembly == null &&
            comparisonInfo == null)
         {
            VSChartInfo cinfo = ((ChartVSAssemblyInfo) assemblyInfo).getVSChartInfo();

            if(GraphTypes.CHART_AUTO == cinfo.getChartType() && !cinfo.isMultiStyles()) {
               cinfo.updateChartType(!cinfo.isMultiStyles(), cinfo.getXFields(), cinfo.getYFields());
               new ChangeChartTypeProcessor(cinfo.getRTChartType(), cinfo.getRTChartType(),
                  null, cinfo).fixShapeField(cinfo, cinfo, cinfo.getRTChartType());
            }
         }

         executeDateComparison(assemblyInfo, rvs, dispatcher, linkUri);
         DateComparisonUtil.updateNestedShareFrom(viewsheet, assemblyInfo);

         if(comparisonInfo == null) {
            DateCompareAbleAssemblyInfo.cleanShareDependencies(assemblyInfo.getName(), viewsheet);
         }
      }
   }

   private void executeDateComparison(VSAssemblyInfo assemblyInfo, RuntimeViewsheet rvs,
                                      CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      this.vsAssemblyInfoHandler.apply(rvs, assemblyInfo, viewsheetService, false, false,
         true, false, dispatcher, null, null, linkUri, null);

      Assembly[] assemblies = rvs.getViewsheet().getAssemblies();

      if(assemblies == null) {
         return;
      }

      for(Assembly assembly : assemblies) {
         if(!(assembly instanceof VSAssembly)) {
            continue;
         }

         VSAssemblyInfo vsAssemblyInfo = ((VSAssembly) assembly).getVSAssemblyInfo();

         if(!(vsAssemblyInfo instanceof DateCompareAbleAssemblyInfo)) {
            continue;
         }

         DateCompareAbleAssemblyInfo dinfo = (DateCompareAbleAssemblyInfo) vsAssemblyInfo;

         if(Tool.equals(dinfo.getComparisonShareFrom(), assemblyInfo.getName())) {
            this.vsAssemblyInfoHandler.apply(rvs, vsAssemblyInfo, viewsheetService, false, false,
               true, false, dispatcher, null, null, linkUri, null);
         }
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
   private final VSAssemblyInfoHandler vsAssemblyInfoHandler;
   private final VisualFrameModelFactoryService vFactoryService;
}
