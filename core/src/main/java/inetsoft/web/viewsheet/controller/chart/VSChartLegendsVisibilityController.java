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
import inetsoft.web.viewsheet.event.chart.VSChartLegendsVisibilityEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSChartLegendsVisibilityController {
   @Autowired
   public VSChartLegendsVisibilityController(RuntimeViewsheetRef runtimeViewsheetRef,
                                 VSChartLegendsVisibilityServiceProxy vsChartLegendsVisibilityService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsChartLegendsVisibilityService = vsChartLegendsVisibilityService;
   }

   /**
    * Show/hide chart legends
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if the axes could not be shown.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/vschart/legends-visibility")
   public void eventHandler(@Payload VSChartLegendsVisibilityEvent event,
                            @LinkUri String linkUri, Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      vsChartLegendsVisibilityService.eventHandler(runtimeViewsheetRef.getRuntimeId(), event,
                                       linkUri, principal, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSChartLegendsVisibilityServiceProxy vsChartLegendsVisibilityService;
}
