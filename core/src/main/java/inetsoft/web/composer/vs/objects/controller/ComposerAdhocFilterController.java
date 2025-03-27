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
import inetsoft.report.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.viewsheet.vslayout.AbstractLayout;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.vs.objects.event.*;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;

/**
 * Controller that processes adhoc filter events for tables and charts.
 */
@Controller
public class ComposerAdhocFilterController {
   /**
    * Creates a new instance of <tt>ComposerAdhocFilterController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated
    *                            with the WebSocket session.
    */
   @Autowired
   public ComposerAdhocFilterController(RuntimeViewsheetRef runtimeViewsheetRef,
                                        ComposerAdhocFilterServiceProxy composerAdhocFilterServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.composerAdhocFilterServiceProxy = composerAdhocFilterServiceProxy;
   }

   /**
    * Add an adhoc filter on the table column.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param linkUri    the link URI
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/addFilter")
   public void addTableFilter(@Payload FilterTableEvent event, Principal principal,
                              @LinkUri String linkUri, CommandDispatcher dispatcher)
      throws Exception
   {
      composerAdhocFilterServiceProxy.addTableFilter(runtimeViewsheetRef.getRuntimeId(), event,
                                                     principal, linkUri, dispatcher);
   }

   /**
    * Add an adhoc filter on chart axis.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param linkUri    the link URI
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/chart/addFilter")
   public void addChartFilter(@Payload FilterChartEvent event, Principal principal,
                              @LinkUri String linkUri,
                              CommandDispatcher dispatcher)
      throws Exception
   {
      composerAdhocFilterServiceProxy.addChartFilter(runtimeViewsheetRef.getRuntimeId(), event,
                                                     principal, linkUri, dispatcher);
   }

   /**
    * Relocates the adhoc filter to its appropriate container.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param linkUri    the link URI
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @LoadingMask
   @MessageMapping("/composer/viewsheet/moveAdhocFilter")
   public void moveAdhocFilter(@Payload VSObjectEvent event, Principal principal,
                               @LinkUri String linkUri, CommandDispatcher dispatcher)
      throws Exception
   {
      composerAdhocFilterServiceProxy.moveAdhocFilter(runtimeViewsheetRef.getRuntimeId(), event,
                                                      principal, linkUri, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ComposerAdhocFilterServiceProxy composerAdhocFilterServiceProxy;
}
