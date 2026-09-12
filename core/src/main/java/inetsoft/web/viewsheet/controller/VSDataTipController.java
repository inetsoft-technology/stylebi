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
package inetsoft.web.viewsheet.controller;

import inetsoft.web.viewsheet.DataTipDependencyCheckResult;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.event.OpenDataTipEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
public class VSDataTipController {
   @Autowired
   public VSDataTipController(RuntimeViewsheetRef runtimeViewsheetRef,
                              VSDataTipServiceProxy vsDataTipServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsDataTipServiceProxy = vsDataTipServiceProxy;
   }

   /**
    * Handle displaying and applying conditions for a datatip.
    */
   @LoadingMask
   @MessageMapping("/datatip/open")
   public void applyDataTip(@Payload OpenDataTipEvent event,
                            Principal principal,
                            @LinkUri String linkUri,
                            CommandDispatcher dispatcher) throws Exception
   {
      vsDataTipServiceProxy.applyDataTip(runtimeViewsheetRef.getRuntimeId(), event,
                                         principal, linkUri, dispatcher);
   }

   @RequestMapping(
      value = "/api/composer/vs/check-datatip-dependency",
      method = RequestMethod.GET
   )
   @ResponseBody
   public DataTipDependencyCheckResult checkDataTipDependency(
      @RequestParam("runtimeId") String runtimeId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("tipView") String tipView,
      Principal principal) throws Exception
   {
      return vsDataTipServiceProxy.checkDataTipDependency(runtimeId, assemblyName, tipView, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSDataTipServiceProxy vsDataTipServiceProxy;
}
