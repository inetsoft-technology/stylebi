/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.model.database.graph;

import inetsoft.web.portal.controller.database.RuntimePartitionService;
import inetsoft.web.portal.model.database.*;

import java.awt.*;
import java.util.*;

import static inetsoft.web.portal.model.database.JoinGraphModel.SCALE_Y;

public class PhysicalGraphLayout extends GraphLayout {
   public PhysicalGraphLayout(GraphViewModel graphModel,
                              RuntimePartitionService.RuntimeXPartition partition,
                              boolean colPriority)
   {
      super(graphModel, colPriority);
      this.partition = partition;
      calcBaseViewMaxTop();
   }

   /**
    * 1. extend view should't change base
    * 2. extend view should layout to bottom of base view.
    */
   private void calcBaseViewMaxTop() {
      if(partition.getPartition().getBasePartition() != null) {
         Iterator<GraphModel> graphs = graphModel.getGraphs().iterator();

         while(graphs.hasNext()) {
            GraphModel graph = graphs.next();

            if(graph.isBaseTable()) {
               int top = (int) Math.ceil(graph.getBounds().y * 1.0d / SCALE_Y);

               if(top > baseViewMaxTop) {
                  baseViewMaxTop = top;
               }

               // exclude base table
               graphs.remove();
            }
         }
      }
   }

   @Override
   public int getGraphWidth() {
      return partition.getGraphWidth();
   }

   @Override
   public int getGraphHeight() {
      return partition.getGraphHeight();
   }

   @Override
   public Rectangle getBounds(String name) {
      return partition.getPartition().getBounds(name, true).getBounds();
   }

   @Override
   public void setBounds(String name, Rectangle bounds, boolean runtime) {
      partition.setBounds(name, bounds, runtime);
   }

   private RuntimePartitionService.RuntimeXPartition partition;
}
