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

import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.chart.VSMapPanEvent;
import inetsoft.web.viewsheet.event.chart.VSMapZoomEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSChartMapController {
   @Autowired
   public VSChartMapController(RuntimeViewsheetRef runtimeViewsheetRef,
                               VSChartMapServiceProxy vsChartMapService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsChartMapService = vsChartMapService;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/vsmap/zoom")
   public void zoomIn(@Payload VSMapZoomEvent event,
                      Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      String runtimeId = runtimeViewsheetRef.getRuntimeId();
      vsChartMapService.zoomIn(runtimeId, event, principal, dispatcher);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/vsmap/pan")
   public void pan(@Payload VSMapPanEvent event,
                   Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      String runtimeId = runtimeViewsheetRef.getRuntimeId();
      vsChartMapService.pan(runtimeId, event, principal, dispatcher);
   }


   @Undoable
   @LoadingMask
   @MessageMapping("/vsmap/clear")
   public void clear(@Payload VSMapPanEvent event,
                   Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      String runtimeId = runtimeViewsheetRef.getRuntimeId();
      vsChartMapService.clear(runtimeId, event, principal, dispatcher);
   }

   private RuntimeViewsheetRef runtimeViewsheetRef;
   private VSChartMapServiceProxy vsChartMapService;
}
