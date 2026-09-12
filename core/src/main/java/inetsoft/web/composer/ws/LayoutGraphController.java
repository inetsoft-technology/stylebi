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

import inetsoft.graph.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import inetsoft.graph.mxgraph.model.mxGeometry;
import inetsoft.graph.mxgraph.view.mxGraph;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.*;
import inetsoft.web.composer.ws.command.WSMoveAssembliesCommand;
import inetsoft.web.composer.ws.event.WSLayoutGraphEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import javax.swing.*;
import java.awt.*;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller that provides a message endpoint for performing a hierarchical layout on
 * the graph thumbnails.
 */
@Controller
public class LayoutGraphController extends WorksheetController {
   public LayoutGraphController(LayoutGraphServiceProxy layoutGraphService) {
      this.layoutGraphService = layoutGraphService;
   }

   @Undoable
   @LoadingMask(true)
   @MessageMapping("/composer/worksheet/layout-graph")
   public void layoutGraph(@Payload WSLayoutGraphEvent event, Principal principal,
                           CommandDispatcher commandDispatcher)
      throws Exception
   {
      layoutGraphService.layoutGraph(getRuntimeId(), event, principal, commandDispatcher);
   }

   private final LayoutGraphServiceProxy layoutGraphService;
}
