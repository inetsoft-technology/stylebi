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
package inetsoft.web.composer.ws.joins;

import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.viewsheet.HandleAssetExceptions;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
public class JoinViewController extends WorksheetController {

   public JoinViewController(JoinViewServiceProxy joinViewServiceProxy)
   {
      this.joinViewServiceProxy = joinViewServiceProxy;
   }

   @LoadingMask
   @MessageMapping("/composer/ws/join/open-join/")
   @HandleAssetExceptions
   public void openJoin(Principal principal) throws Exception {
      joinViewServiceProxy.openJoin(super.getRuntimeId(), principal);
   }

   @LoadingMask
   @MessageMapping("/composer/ws/join/cancel-ws-join/")
   @HandleAssetExceptions
   public void cancelJoin(Principal principal, CommandDispatcher dispatcher) throws Exception {
      joinViewServiceProxy.cancelJoin(super.getRuntimeId(), principal, dispatcher);
   }

   private final JoinViewServiceProxy joinViewServiceProxy;
}
