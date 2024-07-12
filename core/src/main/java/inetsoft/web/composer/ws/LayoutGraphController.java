/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
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
   @Undoable
   @LoadingMask(true)
   @MessageMapping("/composer/worksheet/layout-graph")
   public void layoutGraph(@Payload WSLayoutGraphEvent event, Principal principal,
                           CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeWorksheet worksheet = getRuntimeWorksheet(principal);

      mxGraph graph = new mxGraph();
      Object parent = graph.getDefaultParent();

      //set parent position
      graph.getModel().setGeometry(parent, new mxGeometry());

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
   }
}
