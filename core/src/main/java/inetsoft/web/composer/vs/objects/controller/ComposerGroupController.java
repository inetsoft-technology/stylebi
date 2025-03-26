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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.PopVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.composer.vs.objects.event.GroupVSObjectsEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;

/**
 * Controller that processes group events.
 */
@Controller
public class ComposerGroupController {
   /**
    * Creates a new instance of <tt>ComposerGroupController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated
    *                            with the WebSocket session.
    */
   @Autowired
   public ComposerGroupController(RuntimeViewsheetRef runtimeViewsheetRef,
                                  ComposerGroupServiceProxy composerGroupServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.composerGroupServiceProxy = composerGroupServiceProxy;
   }

   /**
    * Put assemblies into a group.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/group")
   public void groupComponents(@Payload GroupVSObjectsEvent event, @LinkUri String linkUri,
                               Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      composerGroupServiceProxy.groupComponents(runtimeViewsheetRef.getRuntimeId(), event, linkUri,
                                                principal, dispatcher);
   }

   /**
    * Ungroup assemblies.
    *
    * @param objectName the object identifier in the vs.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/ungroup/{objectName}")
   public void ungroup(@DestinationVariable("objectName") String objectName,
                       @LinkUri String linkUri, Principal principal,
                       CommandDispatcher dispatcher)
      throws Exception
   {
      composerGroupServiceProxy.ungroup(runtimeViewsheetRef.getRuntimeId(), objectName,
                                        linkUri, principal, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private ComposerGroupServiceProxy composerGroupServiceProxy;
}
