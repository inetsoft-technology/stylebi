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
package inetsoft.web.composer.ws;

import inetsoft.web.composer.ws.event.*;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
public class TableModeController extends WorksheetController {

   public TableModeController(TableModeServiceProxy tableModeServiceProxy)
   {
      this.tableModeServiceProxy = tableModeServiceProxy;
   }

   /**
    * From 12.2 DefaultEvent.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/table-mode/default")
   public void setDefaultMode(
      @Payload WSAssemblyEvent event,
      Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      tableModeServiceProxy.setDefaultMode(getRuntimeId(), event, principal, commandDispatcher);
   }

   @LoadingMask
   @MessageMapping("/composer/worksheet/dependings-table-mode/default")
   public void setDependencyJoinTableToDefaultMode(@Payload WSRefreshAssemblyEvent event,
                                                   Principal principal,
                                                   CommandDispatcher commandDispatcher)
      throws Exception
   {
      tableModeServiceProxy.setDependencyJoinTableToDefaultMode(getRuntimeId(), event,
                                                                principal, commandDispatcher);

   }

   /**
    * From 12.2 LivePreviewEvent.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/table-mode/live")
   public void setLiveMode(
      @Payload WSAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      tableModeServiceProxy.setLiveMode(getRuntimeId(), event, principal, commandDispatcher);
   }

   /**
    * From 12.2 FullEvent.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/table-mode/full")
   public void setFullMode(
      @Payload WSAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      tableModeServiceProxy.setFullMode(getRuntimeId(), event, principal, commandDispatcher);
   }

   /**
    * From 12.2 DetailPreviewEvent.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/table-mode/detail")
   public void setDetailMode(
      @Payload WSAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      tableModeServiceProxy.setDetailMode(getRuntimeId(), event, principal, commandDispatcher);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/table-mode/set-runtime")
   public void setRuntime(
      @Payload WSSetRuntimeEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      tableModeServiceProxy.setRuntime(getRuntimeId(), event, principal, commandDispatcher);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/table-mode/edit")
   public void setEditMode(
      @Payload WSAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      tableModeServiceProxy.setEditMode(getRuntimeId(), event, principal, commandDispatcher);
   }

   @LoadingMask
   @MessageMapping("/composer/worksheet/refresh-data")
   public void refreshLiveData(
      @Payload WSAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      tableModeServiceProxy.refreshLiveData(getRuntimeId(), event, principal, commandDispatcher);
   }

   @LoadingMask
   @MessageMapping("/composer/worksheet/table/refresh-data")
   public void refreshData(
      @Payload WSRefreshAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      tableModeServiceProxy.refreshData(getRuntimeId(), event, principal, commandDispatcher);
   }

   private final TableModeServiceProxy tableModeServiceProxy;
}
