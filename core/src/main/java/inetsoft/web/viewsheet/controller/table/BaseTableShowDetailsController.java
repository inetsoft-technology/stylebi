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
package inetsoft.web.viewsheet.controller.table;

import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.adhoc.model.FormatInfoModel;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.table.ShowDetailsEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
public class BaseTableShowDetailsController {
   @Autowired
   public BaseTableShowDetailsController(RuntimeViewsheetRef runtimeViewsheetRef,
                                     BaseTableShowDetailsServiceProxy baseTableShowDetailsService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.baseTableShowDetailsService = baseTableShowDetailsService;
   }

   /**
    * Get the format model for the detail table column. Uses FormatInfoModel,
    * but only really needs the format information.
    * @param vsId      runtime viewsheet id
    * @param event     information about the table
    * @param principal the user
    * @return
    * @throws Exception
    */
   @PostMapping("/api/table/show-details/format-model")
   @ResponseBody
   public FormatInfoModel getFormatModel(@DecodeParam("vsId") String vsId,
                                         @RequestBody ShowDetailsEvent event,
                                         Principal principal)
      throws Exception
   {
      return baseTableShowDetailsService.getFormatModel(vsId, event, principal);
   }

   @LoadingMask
   @Undoable
   @MessageMapping("/table/show-details")
   public void eventHandler(@Payload ShowDetailsEvent event, Principal principal,
                            CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      baseTableShowDetailsService.eventHandler(runtimeViewsheetRef.getRuntimeId(), event,
                                               principal, dispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final BaseTableShowDetailsServiceProxy baseTableShowDetailsService;
}
