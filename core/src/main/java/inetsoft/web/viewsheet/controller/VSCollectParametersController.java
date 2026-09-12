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
import inetsoft.web.viewsheet.event.CollectParametersOverEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSCollectParametersController {
   /**
    * Creates a new instance of <tt>VSCollectParametersController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public VSCollectParametersController(RuntimeViewsheetRef runtimeViewsheetRef,
                                        VSCollectParametersServiceProxy vsCollectParametersServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsCollectParametersServiceProxy = vsCollectParametersServiceProxy;
   }

   @LoadingMask
   @MessageMapping("/vs/collectParameters")
   public void collectParameters(@Payload CollectParametersOverEvent event,
                                 Principal principal,
                                 @LinkUri String linkUri,
                                 CommandDispatcher dispatcher)
      throws Exception
   {
      vsCollectParametersServiceProxy.collectParameters(runtimeViewsheetRef.getRuntimeId(),
                                                        event, principal, linkUri, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private VSCollectParametersServiceProxy vsCollectParametersServiceProxy;

   private static final Logger LOG =
      LoggerFactory.getLogger(CollectParametersOverEvent.class);
}
