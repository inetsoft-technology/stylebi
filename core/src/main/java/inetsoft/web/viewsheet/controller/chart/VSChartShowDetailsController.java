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

import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.adhoc.model.FormatInfoModel;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.event.chart.VSChartShowDetailsEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@Controller
public class VSChartShowDetailsController {
   @Autowired
   public VSChartShowDetailsController(RuntimeViewsheetRef runtimeViewsheetRef,
                                       VSChartShowDetailsServiceProxy serviceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.serviceProxy = serviceProxy;
   }

   @LoadingMask
   @MessageMapping("/vschart/showdetails")
   public void eventHandler(@Payload VSChartShowDetailsEvent event,
                            @LinkUri String linkUri,
                            Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      serviceProxy.eventHandler(runtimeViewsheetRef.getRuntimeId(), event, linkUri, principal, dispatcher);
   }

   /**
    * Get the format info for the specified detail table column.
    */
   @GetMapping("/vschart/showdetails/format-model")
   @ResponseBody
   public FormatInfoModel getFormatModel(@DecodeParam("vsId") String vsId,
                                         @DecodeParam("wsId") String wsId,
                                         @RequestParam("assemblyName") String assemblyName,
                                         @RequestParam("columnIndex") int columnIndex,
                                         @RequestParam("selected") String selected,
                                         @RequestParam("rangeSelection") boolean isRangeSelection,
                                         Principal principal)
      throws Exception
   {
      return serviceProxy.getFormatModel(vsId, wsId, assemblyName, columnIndex, selected,
                                         isRangeSelection, principal);
   }

   @PutMapping("/api/vs/showdetails/colwidth")
   @ResponseBody
   public void setColWidth(@DecodeParam("vsId") String vsId,
                             @RequestParam("assemblyName") String assemblyName,
                             @RequestParam("columnIndex") int columnIndex,
                             @RequestBody int width,
                             Principal principal)
      throws Exception
   {
      serviceProxy.setColWidth(vsId, assemblyName, columnIndex, width, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSChartShowDetailsServiceProxy serviceProxy;
}
