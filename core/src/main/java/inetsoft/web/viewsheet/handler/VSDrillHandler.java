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
package inetsoft.web.viewsheet.handler;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.XCube;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.model.DrillFilterAction;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;

@Component
public class VSDrillHandler {

   @Autowired
   public VSDrillHandler(List<DrillHandler> drillHandlers,
                         ViewsheetService viewsheetService,
                         PlaceholderService placeholderService,
                         RuntimeViewsheetRef runtimeViewsheetRef,
                         VSBindingService bfactory)
   {
      this.drillHandlers = drillHandlers;
      this.viewsheetService = viewsheetService;
      this.placeholderService = placeholderService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.bfactory = bfactory;
   }

   public void processDrillAction(DrillFilterAction drillFilterAction,
                                  CommandDispatcher dispatcher, String linkUri,
                                  Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly vsAssembly = vs.getAssembly(drillFilterAction.getAssemblyName());

      if(!(vsAssembly instanceof DrillFilterVSAssembly)) {
         return;
      }

      if(vsAssembly.getVSAssemblyInfo() instanceof DateCompareAbleAssemblyInfo) {
         DateCompareAbleAssemblyInfo compareAbleAssemblyInfo =
            (DateCompareAbleAssemblyInfo) vsAssembly.getVSAssemblyInfo();
         DateComparisonInfo comparisonInfo = DateComparisonUtil.getDateComparison(compareAbleAssemblyInfo, vs);

         if(comparisonInfo != null && compareAbleAssemblyInfo.supportDateComparison()) {
            return;
         }
      }

      DrillFilterVSAssembly targetAssembly = (DrillFilterVSAssembly) vsAssembly;

      List<String> fields = drillFilterAction.getFields();
      Map<String, Boolean> drillEnabled = new HashMap<>();

      // note1: fix state--(down: drill and +filter)-->city--(down: +filter)-->CA(city)--(up: should -filter)-->city
      fields.stream().forEach((field) -> {
         drillEnabled.put(field, !BaseDrillHandler.containsSelfFilter(targetAssembly, field));
      });

      // 1. First process filter of target assembly
      DrillHandler handler = getDrillHandle(targetAssembly);
      handler.processDrillFilter(targetAssembly, drillFilterAction, dispatcher, linkUri, principal);

      // 2. process drill (replacing field)
      // 2.1 Process drill action one by one.
      DrillFilterVSAssembly[] assemblies = Arrays.stream(vsAssembly.getViewsheet().getAssemblies())
         .filter(obj -> obj instanceof DrillFilterVSAssembly)
         .map(ass -> ((DrillFilterVSAssembly) ass))
         .filter(currentFilterAss -> {
            if(targetAssembly != currentFilterAss) {
               XCube cube1 = targetAssembly.getXCube();
               XCube cube2 = currentFilterAss.getXCube();

               // if a user defined hierarchy exists, only drill other assemblies at
               // same time if the hierarchies are identical. otherwise the drill
               // paths will diverge and the drill-up would not work property. (42901)
               if(cube1 != null && cube2 != null) {
                  for(String field : drillEnabled.keySet()) {
                     DataRef dim = handler.getFieldByName(targetAssembly, field);

                     if(dim instanceof VSDimensionRef) {
                        DataRef next1 = VSUtil.getCubeNextLevelRef((VSDimensionRef) dim, cube1);
                        DataRef next2 = VSUtil.getCubeNextLevelRef((VSDimensionRef) dim, cube2);

                        if(!Objects.equals(next1, next2)) {
                           return false;
                        }
                     }
                  }
               }
               else if(cube1 != null || cube2 != null) {
                  XCube cube = cube1 != null ? cube1 : cube2;

                  for(String field : drillEnabled.keySet()) {
                     DataRef dim = null;

                     if(handler.isHandler(currentFilterAss)) {
                        dim = handler.getFieldByName(currentFilterAss, field);
                     }

                     // Don't drill when there is this field in the custom hierarchy,
                     // because this will cause confusion.
                     if(dim instanceof VSDimensionRef
                        && VSUtil.isCubeContainRef((VSDimensionRef) dim, cube))
                     {
                        return false;
                     }
                  }
               }
            }

            return true;
         })
         .toArray(DrillFilterVSAssembly[]::new);

      for(Map.Entry<String, Boolean> drillEnabledEntry: drillEnabled.entrySet()) {
         if(!drillEnabledEntry.getValue()) {
            continue;
         }

         // change fields to single
         drillFilterAction.setFields(Arrays.asList(drillEnabledEntry.getKey()));

         for(DrillFilterVSAssembly assembly : assemblies) {
            if(Objects.equals(inetsoft.uql.asset.internal.ConditionUtil.getAssemblySource(targetAssembly),
               ConditionUtil.getAssemblySource(assembly)))
            {
               getDrillHandle(assembly).processDrillAction(
                  assembly, drillFilterAction, targetAssembly, dispatcher, linkUri, principal);
            }
         }
      }

      // 2.2 Finally refresh
      //send bindingCommand to refresh vs binding pane
      BindingModel binding = bfactory.createModel(targetAssembly);
      final SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
      dispatcher.sendCommand(bcommand);

      // refreshViewsheet is called in VSChartController.complete(). no need to call explicitly.
      if(!(vsAssembly instanceof ChartVSAssembly)) {
         refreshViewsheet(rvs, dispatcher, linkUri);
      }
   }

   /**
    * Remove drill filter.
    * @param assembly drill filter assembly
    * @param dataRef dimension ref.
    */
   public void removeDrillFilter(RuntimeViewsheet rvs, VSAssembly assembly, DataRef dataRef,
                                 CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      if(!(assembly instanceof DrillFilterVSAssembly)) {
         return;
      }

      DrillFilterVSAssembly drillFilterVSAssembly = (DrillFilterVSAssembly) assembly;

      getDrillHandle(drillFilterVSAssembly)
         .removeDrillFilter(dataRef, drillFilterVSAssembly, false, true);

      // refreshViewsheet is called in VSChartController.complete(). no need to call explicitly.
      if(!(assembly instanceof ChartVSAssembly)) {
         refreshViewsheet(rvs, dispatcher, linkUri);
      }
   }

   private void refreshViewsheet(RuntimeViewsheet rvs,
                                 CommandDispatcher dispatcher,
                                 String linkUri)
      throws Exception
   {
      final ChangedAssemblyList clist =
         placeholderService.createList(true, dispatcher, rvs, linkUri);
      placeholderService.refreshViewsheet(rvs, rvs.getID(), linkUri, dispatcher,
         false, false, true, clist);
   }

   private DrillHandler getDrillHandle(VSAssembly assembly) {
      DrillHandler drillHandler = drillHandlers.stream()
         .filter(handle -> handle.isHandler(assembly))
         .findFirst().orElse(null);

      if(drillHandler == null) {
         LOGGER.warn("No drillHandler specified for {}", assembly);
      }

      return drillHandler;
   }

   private final List<DrillHandler> drillHandlers;
   private final ViewsheetService viewsheetService;
   private final PlaceholderService placeholderService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSBindingService bfactory;

   private static final Logger LOGGER = LoggerFactory.getLogger(VSDrillHandler.class);
}
