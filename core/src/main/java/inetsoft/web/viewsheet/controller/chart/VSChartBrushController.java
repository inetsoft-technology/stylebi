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
import inetsoft.web.viewsheet.event.chart.VSChartBrushEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSChartBrushController {
   @Autowired
   public VSChartBrushController(RuntimeViewsheetRef runtimeViewsheetRef,
                                 VSChartBrushServiceProxy vsChartBrushService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsChartBrushService = vsChartBrushService;
   }

   /**
    * Brush the chart
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if the chart could not be brushed.
    */
   // from analytic.composition.event.BrushEvent.process()
   @Undoable
   @LoadingMask
   @MessageMapping("/vschart/brush")
   @HandleAssetExceptions
   public void eventHandler(@Payload VSChartBrushEvent event, @LinkUri String linkUri,
                            Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      vsChartBrushService.eventHandler(runtimeViewsheetRef.getRuntimeId(), event,
                                       linkUri, principal, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSChartBrushServiceProxy vsChartBrushService;
}
