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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.web.binding.dnd.*;
import inetsoft.web.binding.event.VSDndEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSTableBindingHandler;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.objects.controller.ComposerVSTableController;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * This class handles get vsobjectmodel from the server.
 */
@Controller
public class VSTableDndController {
   /**
    * Creates a new instance of <tt>VSViewController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public VSTableDndController(RuntimeViewsheetRef runtimeViewsheetRef,
                               VSTableDndServiceProxy vsTableDndServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsTableDndServiceProxy = vsTableDndServiceProxy;
   }

   /**
    * Add a column to a table or change the binding of a column in the table via drag and drop
    * from another assembly onto the target table assembly
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vstable/dnd/addRemoveColumns")
   public void dnd(@Payload VSDndEvent event, Principal principal,
      @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      vsTableDndServiceProxy.dnd(runtimeViewsheetRef.getRuntimeId(), event,
                                 principal, linkUri, dispatcher);
   }

   /**
    * This method is to get vsobjectmodel for edit binding pane.
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vstable/dnd/addColumns")
   public void dndFromTree(@Payload VSDndEvent event, Principal principal,
                           @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      vsTableDndServiceProxy.dndFromTree(runtimeViewsheetRef.getRuntimeId(), event, principal,
                                         linkUri, dispatcher);
   }

   /**
    * This method is to get vsobjectmodel for edit binding pane.
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vstable/dnd/removeColumns")
   public void dndTotree(@Payload VSDndEvent event, Principal principal,
                         @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      vsTableDndServiceProxy.dndTotree(runtimeViewsheetRef.getRuntimeId(), event, principal,
                                       linkUri, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSTableDndServiceProxy vsTableDndServiceProxy;

}
