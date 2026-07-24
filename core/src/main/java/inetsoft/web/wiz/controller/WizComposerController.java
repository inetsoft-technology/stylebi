/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.controller;

import inetsoft.web.wiz.event.AddFilterEvent;
import inetsoft.web.wiz.event.AddVisualizationEvent;
import inetsoft.web.wiz.event.AddVisualizationsByIdsEvent;
import inetsoft.web.viewsheet.command.RefreshWizFiltersCommand;
import inetsoft.web.viewsheet.controller.VSRefreshServiceProxy;
import inetsoft.web.viewsheet.event.VSRefreshEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.wiz.service.AddFilterServiceProxy;
import inetsoft.web.wiz.service.AddVisualizationServiceProxy;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;

/**
 * STOMP controller for wiz composer actions on the viewsheet pane.
 */
@Controller
public class WizComposerController {
   public WizComposerController(RuntimeViewsheetRef runtimeViewsheetRef,
                                AddVisualizationServiceProxy addVisualizationServiceProxy,
                                AddFilterServiceProxy addFilterServiceProxy,
                                VSRefreshServiceProxy vsRefreshServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.addVisualizationServiceProxy = addVisualizationServiceProxy;
      this.addFilterServiceProxy = addFilterServiceProxy;
      this.vsRefreshServiceProxy = vsRefreshServiceProxy;
   }

   /**
    * Handles a visualization node being dropped onto the viewsheet pane.
    *
    * @param event      the drop event carrying the visualization entry and drop coordinates.
    * @param principal  the current user.
    * @param dispatcher command dispatcher for sending commands back to the client.
    * @param linkUri    the base link URI for the current session.
    */
   @MessageMapping("/composer/wiz/addVisualization")
   public void addVisualization(@Payload AddVisualizationEvent event,
                                Principal principal,
                                CommandDispatcher dispatcher,
                                @LinkUri String linkUri)
      throws Exception
   {
      String runtimeId = runtimeViewsheetRef.getRuntimeId();
      addVisualizationServiceProxy.addVisualization(
         runtimeId, event.getEntry(), event.getxOffset(), event.getyOffset(),
         event.getScale(), null, principal);
      dispatcher.sendCommand(new RefreshWizFiltersCommand());
      vsRefreshServiceProxy.refreshViewsheetAsync(this.runtimeViewsheetRef.getRuntimeId(),
         VSRefreshEvent.builder().confirmed(false).build(), principal, dispatcher, linkUri);
   }

   /**
    * Handles a filter node being dropped onto the viewsheet pane.
    *
    * @param event      the drop event carrying the filter entry and drop coordinates.
    * @param principal  the current user.
    * @param dispatcher command dispatcher for sending commands back to the client.
    * @param linkUri    the base link URI for the current session.
    */
   @MessageMapping("/composer/wiz/addFilter")
   public void addFilter(@Payload AddFilterEvent event,
                         Principal principal,
                         CommandDispatcher dispatcher,
                         @LinkUri String linkUri)
      throws Exception
   {
      String runtimeId = runtimeViewsheetRef.getRuntimeId();
      addFilterServiceProxy.addFilter(
         runtimeId, event.getEntry(), event.getxOffset(), event.getyOffset(),
         event.getScale(), principal);
      // RefreshWizFiltersCommand is intentionally NOT dispatched here.
      // Adding a filter creates a VS assembly but does not alter the base worksheet, so the
      // set of filterable columns (shown in the filter tree) is unchanged. Dispatching the
      // command would only trigger a redundant HTTP round-trip on the client side.
      vsRefreshServiceProxy.refreshViewsheetAsync(runtimeId,
         VSRefreshEvent.builder().confirmed(false).build(), principal, dispatcher, linkUri);
   }

   /**
    * Bulk-adds multiple visualizations to the wiz dashboard by their AssetEntry identifiers.
    * Used when the composer is opened with {@code wizComposer=true} and pre-selected
    * visualization IDs ({@code wizVizIds}).  Placement is computed dynamically inside the
    * service — each visualization is stacked below the previous one based on the actual
    * measured bottom edge of the dashboard assemblies.
    *
    * @param event      carries the list of AssetEntry identifier strings.
    * @param principal  the current user.
    * @param dispatcher command dispatcher for sending commands back to the client.
    * @param linkUri    the base link URI for the current session.
    */
   @MessageMapping("/composer/wiz/addVisualizationsByIds")
   public void addVisualizationsByIds(@Payload AddVisualizationsByIdsEvent event,
                                      Principal principal,
                                      CommandDispatcher dispatcher,
                                      @LinkUri String linkUri)
      throws Exception
   {
      List<String> identifiers = event.getIdentifiers();

      if(identifiers == null || identifiers.isEmpty()) {
         return;
      }

      String runtimeId = runtimeViewsheetRef.getRuntimeId();
      addVisualizationServiceProxy.addVisualizationsByIds(runtimeId, identifiers, principal);
      dispatcher.sendCommand(new RefreshWizFiltersCommand());
      vsRefreshServiceProxy.refreshViewsheetAsync(runtimeId,
         VSRefreshEvent.builder().confirmed(false).build(), principal, dispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final AddVisualizationServiceProxy addVisualizationServiceProxy;
   private final AddFilterServiceProxy addFilterServiceProxy;
   private final VSRefreshServiceProxy vsRefreshServiceProxy;
}
