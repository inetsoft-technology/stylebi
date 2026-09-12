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
package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.report.internal.graph.ChangeChartDataProcessor;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.dnd.ChartAestheticDropTarget;
import inetsoft.web.binding.dnd.DropTarget;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Controller
public class ChangeChartAestheticController {
   /**
    * Creates a new instance of <tt>ChangeChartRefController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    * @param viewsheetService
    */
   @Autowired
   public ChangeChartAestheticController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      ChangeChartAestheticServiceProxy changeChartAestheticService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.changeChartAestheticService = changeChartAestheticService;
   }

   @MessageMapping("/vs/chart/changeChartAesthetic")
   public void changeChartAesthetic(@Payload ChangeChartRefEvent event,
                                    Principal principal, CommandDispatcher dispatcher,
                                    @LinkUri String linkUri) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      changeChartAestheticService.changeChartAesthetic(id, event, principal, dispatcher, linkUri);
   }

   /**
    * Copy static color to word cloud text color when added text field.
    */
   public static void syncWorldCloudColor(ChartInfo ncinfo, DropTarget target) {
      if(target instanceof ChartAestheticDropTarget) {
         int dropType = Integer.parseInt(((ChartAestheticDropTarget) target).getDropType());

         if(dropType == ChartConstants.DROP_REGION_TEXT) {
            GraphUtil.syncWorldCloudColor(ncinfo);
         }
      }
   }

   /**
    * Fix the SizeField of the chart aggregateRef.
    */
   public static void fixAggregateRefSizeField(ChartInfo vinfo) {
      if(vinfo.isMultiAesthetic()) {
         return;
      }

      VSDataRef[] arr = vinfo.getFields();

      for(VSDataRef ref : arr) {
         if(ref instanceof ChartAggregateRef) {
            ((ChartAggregateRef) ref).setSizeField(null);
         }
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ChangeChartAestheticServiceProxy changeChartAestheticService;
   private static final Logger LOG =
      LoggerFactory.getLogger(ChangeChartAestheticController.class);
}
