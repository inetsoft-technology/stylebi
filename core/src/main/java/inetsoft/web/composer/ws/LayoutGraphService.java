/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.graph.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import inetsoft.graph.mxgraph.model.mxGeometry;
import inetsoft.graph.mxgraph.view.mxGraph;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.asset.*;
import inetsoft.web.composer.ws.command.WSMoveAssembliesCommand;
import inetsoft.web.composer.ws.event.WSLayoutGraphEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.awt.*;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Service
@ClusterProxy
public class LayoutGraphService extends WorksheetControllerService {
   public LayoutGraphService(ViewsheetService viewsheetService) {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void layoutGraph(@ClusterProxyKey String runtimeId,
                           WSLayoutGraphEvent event, Principal principal,
                           CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeWorksheet worksheet = getRuntimeWorksheet(runtimeId, principal);

      mxGraph graph = new mxGraph();
      Object parent = graph.getDefaultParent();
      Map<String, Object> vertices = new HashMap<>();
      graph.getModel().beginUpdate();

      for(int i = 0; i < event.names().length; i++) {
         String name = event.names()[i];
         vertices.put(name, graph.insertVertex(
            parent, name, null, 0, i, event.widths()[i], event.heights()[i]));
      }

      for(String name : event.names()) {
         AssemblyEntry entry =
            worksheet.getWorksheet().getAssembly(name).getAssemblyEntry();

         for(AssemblyRef dependency : worksheet.getWorksheet().getDependeds(entry)) {
            String dname = dependency.getEntry().getName();
            // reverse the direction of the dependency during layout so that the direction
            // of the graph flows top-to-bottom (specifying SwingConstants.SOUTH does not
            // seem to work at all)
            Object source = vertices.get(dname);
            Object target = vertices.get(name);
            graph.insertEdge(parent, null, "", source, target);
         }
      }

      graph.getModel().endUpdate();

      mxHierarchicalLayout layout = new mxHierarchicalLayout(graph, SwingConstants.NORTH);
      layout.execute(parent);
      adjustNodesPositionToPositive(graph, vertices);

      double[] tops = new double[event.names().length];
      double[] lefts = new double[event.names().length];

      for(int i = 0; i < event.names().length; i++) {
         Object vertex = vertices.get(event.names()[i]);
         mxGeometry geometry = graph.getModel().getGeometry(vertex);
         tops[i] = geometry.getY() + 10;
         lefts[i] = geometry.getX() + 10;

         AbstractWSAssembly assembly = (AbstractWSAssembly)
            worksheet.getWorksheet().getAssembly(event.names()[i]);
         assembly.setPixelOffset(
            new Point((int) Math.round(lefts[i]), (int) Math.round(tops[i])));
      }

      WSMoveAssembliesCommand command = new WSMoveAssembliesCommand();
      command.setAssemblyNames(event.names());
      command.setTops(tops);
      command.setLefts(lefts);
      commandDispatcher.sendCommand(command);
      return null;
   }

   private void adjustNodesPositionToPositive(mxGraph graph, Map<String, Object> vertices) {
      double minX = Double.MAX_VALUE;
      double minY = Double.MAX_VALUE;

      for(Object vertex : vertices.values()) {
         mxGeometry geometry = graph.getModel().getGeometry(vertex);

         if(geometry == null) {
            continue;
         }

         if(geometry.getX() < 0 && geometry.getX() < minX) {
            minX = geometry.getX();
         }

         if(geometry.getY() < 0 && geometry.getY() < minY) {
            minY = geometry.getY();
         }
      }

      if(minX >= 0 && minY >= 0) {
         return;
      }

      for(Object vertex : vertices.values()) {
         mxGeometry geometry = graph.getModel().getGeometry(vertex);

         if(geometry == null) {
            continue;
         }

         if(minX < 0) {
            geometry.setX(geometry.getX() - minX);
         }

         if(minY < 0) {
            geometry.setY(geometry.getY() - minY);
         }
      }
   }
}
