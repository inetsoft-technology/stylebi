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

import inetsoft.util.*;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.event.AllowCrossJoinEvent;
import inetsoft.web.composer.ws.event.WSCrossJoinEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;

/**
 * Controller that provides a REST endpoint for creating and fetching the assemblies of cross joins.
 */
@Controller
public class CrossJoinController extends WorksheetController {

   public CrossJoinController(CrossJoinServiceProxy crossJoinServiceProxy)
   {
      this.crossJoinServiceProxy = crossJoinServiceProxy;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/ws/dialog/inner-join-dialog/cross-join")
   public void doCrossJoin(
      @Payload WSCrossJoinEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      String runtimeId = super.getRuntimeId();
      crossJoinServiceProxy.doCrossJoin(Tool.byteDecode(runtimeId), event, principal, commandDispatcher);
   }

   @MessageMapping("/ws/crossjoin/confirm")
   public void allowCrossJoin(@Payload AllowCrossJoinEvent event,
                              Principal principal,
                              CommandDispatcher dispatcher,
                              @LinkUri String linkUri) throws Exception
   {
      final String id = Tool.byteDecode(getRuntimeId());
      final String tableName = event.tableName();
      crossJoinServiceProxy.executeCrossjoinAssemblies(id, principal, dispatcher, linkUri, tableName);
   }

   private final CrossJoinServiceProxy crossJoinServiceProxy;
}
