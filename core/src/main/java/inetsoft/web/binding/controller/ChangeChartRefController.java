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
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.graph.ChangeChartDataProcessor;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.DrillFilterInfo;
import inetsoft.util.Tool;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.handler.*;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.*;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.command.RefreshWizardTreeTriggerCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Controller
public class ChangeChartRefController {
   /**
    * Creates a new instance of <tt>ChangeChartRefController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public ChangeChartRefController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      ChangeChartRefServiceProxy changeChartRefService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.changeChartRefService = changeChartRefService;
   }

   @MessageMapping("/vs/chart/changeChartRef")
   public void changeChartRef(@Payload ChangeChartRefEvent event,
                              Principal principal, CommandDispatcher dispatcher,
                              @LinkUri String linkUri) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      changeChartRefService.changeChartRef(id, event, principal, dispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ChangeChartRefServiceProxy changeChartRefService;
}
