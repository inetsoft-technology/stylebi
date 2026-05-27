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
package inetsoft.web.binding.dnd.controller;

import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.event.VSDndEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;

/**
 * This class handles get vsobjectmodel from the server.
 */
@Controller
public class VSCalcTableDndController {
   /**
    * Creates a new instance of <tt>VSViewController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
   @Autowired
   public VSCalcTableDndController(RuntimeViewsheetRef runtimeViewsheetRef,
                                   VSCalcTableDndServiceProxy vsCalcTableDndServiceProxy)
   {

      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsCalcTableDndServiceProxy = vsCalcTableDndServiceProxy;
   }

   /**
    * This method is to get vsobjectmodel for edit binding pane.
    *
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vscalctable/dnd/addRemoveColumns")
   public void addRemoveColumns(@Payload VSDndEvent event, Principal principal,
      @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      String runtimeId = runtimeViewsheetRef.getRuntimeId();

      if(runtimeId != null) {
         vsCalcTableDndServiceProxy.addRemoveColumns(runtimeId, event, principal, linkUri, dispatcher);
      }
   }

   /**
    * This method is to get vsobjectmodel for edit binding pane.
    *
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vscalctable/dnd/addColumns")
   public void addColumns(@Payload VSDndEvent event, Principal principal,
      @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      String runtimeId = runtimeViewsheetRef.getRuntimeId();
      if(runtimeId != null) {
         vsCalcTableDndServiceProxy.addColumns(runtimeId, event, principal,
                                                 linkUri, dispatcher);
      }
   }

   protected boolean sourceChanged(VSAssembly assembly, String table) {
      SourceInfo sinfo = ((DataVSAssemblyInfo) assembly.getInfo()).getSourceInfo();
      return sinfo != null && !sinfo.getSource().equals(table);
   }

   /**
    * This method is to get vsobjectmodel for edit binding pane.
    *
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vscalctable/dnd/removeColumns")
   public void removeColumns(@Payload VSDndEvent event, Principal principal,
      @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      String runtimeId = runtimeViewsheetRef.getRuntimeId();

      if(runtimeId != null) {
         vsCalcTableDndServiceProxy.removeColumns(runtimeId, event,
                                                  principal, linkUri, dispatcher);
      }
   }

   private RuntimeViewsheetRef runtimeViewsheetRef;
   private VSCalcTableDndServiceProxy vsCalcTableDndServiceProxy;
}
