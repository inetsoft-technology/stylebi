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

import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.event.CancelViewsheetLoadingEvent;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class CancelViewsheetLoadingController {
   @Autowired
   public CancelViewsheetLoadingController(CancelViewsheetLoadingServiceProxy cancelViewsheetLoadingServiceProxy)
   {
      this.cancelViewsheetLoadingServiceProxy = cancelViewsheetLoadingServiceProxy;
   }

   @LoadingMask
   @MessageMapping("/composer/viewsheet/cancelViewsheet")
   public void cancelViewsheet(@Payload CancelViewsheetLoadingEvent event,
                               @LinkUri String linkUri,
                               Principal principal,
                               CommandDispatcher dispatcher) throws Exception
   {
      cancelViewsheetLoadingServiceProxy.cancelViewsheet(event.getRuntimeViewsheetId(), event, linkUri, principal, dispatcher);
   }

   private CancelViewsheetLoadingServiceProxy cancelViewsheetLoadingServiceProxy;
}
