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
package inetsoft.web.viewsheet.controller.chart;

import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.event.chart.VSChartEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
public class VSChartAreasController {
   @Autowired
   public VSChartAreasController(VSChartAreasServiceProxy vsChartAreasServiceProxy,
                                 RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsChartAreasServiceProxy = vsChartAreasServiceProxy;
   }

   @MessageMapping("/vschart/areas")
   @ExecutionMonitoring
   @HandleAssetExceptions
   @SwitchOrg
   public void getChartAreas(@OrganizationID("getOrgId()") @Payload VSChartEvent event,
                             CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {
      vsChartAreasServiceProxy.refreshChartAreasModel(runtimeViewsheetRef.getRuntimeId(), event, dispatcher, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private VSChartAreasServiceProxy vsChartAreasServiceProxy;

}
