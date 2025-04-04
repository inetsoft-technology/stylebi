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
package inetsoft.web.composer.ws.dialog;

import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.event.ReorderSubtablesEvent;
import inetsoft.web.composer.ws.event.WSMergeAddJoinTableEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
public class ReorderSubtablesController extends WorksheetController {

   public ReorderSubtablesController(ReorderSubtableServiceProxy reorderSubtableServiceProxy)
   {
      this.reorderSubtableServiceProxy = reorderSubtableServiceProxy;
   }

   /**
    * From 12.2 MoveSubTableEvent.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/reorder-subtables")
   public void reorderSubtables(
      @Payload ReorderSubtablesEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      reorderSubtableServiceProxy.reorderSubtables(getRuntimeId(), event, principal, commandDispatcher);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/reorder-join-table")
   public void reorderJoinTable(
      @Payload WSMergeAddJoinTableEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      reorderSubtableServiceProxy.reorderJoinTable(getRuntimeId(), event, principal, commandDispatcher);
   }

   private final ReorderSubtableServiceProxy reorderSubtableServiceProxy;
}
