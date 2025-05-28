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

import inetsoft.web.viewsheet.event.TouchAssetEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class TouchAssetController {
   @MessageMapping("/composer/touch-asset")
   public void touchAsset(@Payload TouchAssetEvent event, Principal principal,
                          CommandDispatcher commandDispatcher, @LinkUri String linkUri)
      throws Exception
   {
      touchAssetServiceProxy.touchAsset(runtimeViewsheetRef.getRuntimeId(), event, principal,
                                        commandDispatcher, linkUri);
   }

   @Autowired
   protected void setRuntimeViewsheetRef(
      RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   @Autowired
   protected void setTouchAssetServiceProxy(TouchAssetServiceProxy touchAssetServiceProxy)
   {
      this.touchAssetServiceProxy = touchAssetServiceProxy;
   }

   private RuntimeViewsheetRef runtimeViewsheetRef;
   private TouchAssetServiceProxy touchAssetServiceProxy;
}
