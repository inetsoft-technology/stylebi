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
import inetsoft.web.composer.ws.event.*;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;

/**
 * Controller that provides an endpoint for creating and editing the inner join tables.
 */
@Controller
public class InnerJoinController extends WorksheetController {

   public InnerJoinController(InnerJoinServiceProxy innerJoinServiceProxy)
   {
      this.innerJoinServiceProxy = innerJoinServiceProxy;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/ws/edit-inner-join")
   public void editInnerJoin(
      @Payload WSInnerJoinEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      innerJoinServiceProxy.editInnerJoin(super.getRuntimeId(), event, principal, commandDispatcher);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/drop-table-into-join-schema")
   public void dragTableIntoJoinSchema(
      @Payload WSDropTableIntoJoinSchemaEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      innerJoinServiceProxy.dragTableIntoJoinSchema(super.getRuntimeId(), event,
                                                    principal, commandDispatcher);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/dialog/inner-join-dialog/inner-join")
   public void joinTables(
      @Payload WSJoinTablesEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      innerJoinServiceProxy.joinTables(super.getRuntimeId(), event, principal, commandDispatcher);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/join-tables")
   public void joinTablePair(
      @Payload WSJoinTablePairEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      innerJoinServiceProxy.joinTablePair(super.getRuntimeId(), event, principal, commandDispatcher);
   }

   private final InnerJoinServiceProxy innerJoinServiceProxy;
}
