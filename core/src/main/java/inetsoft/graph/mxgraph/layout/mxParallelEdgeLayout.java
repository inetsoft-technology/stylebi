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
package inetsoft.graph.mxgraph.layout;

import inetsoft.graph.mxgraph.model.*;
import inetsoft.graph.mxgraph.util.mxPoint;
import inetsoft.graph.mxgraph.view.*;

import java.util.*;

public class mxParallelEdgeLayout extends mxGraphLayout {

   /**
    * Specifies the spacing between the edges. Default is 20.
    */
   protected int spacing;

   /**
    * Constructs a new stack layout layout for the specified graph,
    * spacing, orientation and offset.
    */
   public mxParallelEdgeLayout(mxGraph graph)
   {
      this(graph, 20);
   }

   /**
    * Constructs a new stack layout layout for the specified graph,
    * spacing, orientation and offset.
    */
   public mxParallelEdgeLayout(mxGraph graph, int spacing)
   {
      super(graph);
      this.spacing = spacing;
   }

   /*
    * (non-Javadoc)
    * @see inetsoft.graph.mxgraph.layout.mxIGraphLayout#execute(java.lang.Object)
    */
   public void execute(Object parent)
   {
      Map<String, List<Object>> lookup = findParallels(parent);

      graph.getModel().beginUpdate();
      try {
         Iterator<List<Object>> it = lookup.values().iterator();

         while(it.hasNext()) {
            List<Object> parallels = it.next();

            if(parallels.size() > 1) {
               layout(parallels);
            }
         }
      }
      finally {
         graph.getModel().endUpdate();
      }
   }

   /**
    *
    */
   protected Map<String, List<Object>> findParallels(Object parent)
   {
      Map<String, List<Object>> lookup = new Hashtable<String, List<Object>>();
      mxIGraphModel model = graph.getModel();
      int childCount = model.getChildCount(parent);

      for(int i = 0; i < childCount; i++) {
         Object child = model.getChildAt(parent, i);

         if(!isEdgeIgnored(child)) {
            String id = getEdgeId(child);

            if(id != null) {
               if(!lookup.containsKey(id)) {
                  lookup.put(id, new ArrayList<Object>());
               }

               lookup.get(id).add(child);
            }
         }
      }

      return lookup;
   }

   /**
    *
    */
   protected String getEdgeId(Object edge)
   {
      mxGraphView view = graph.getView();
      mxCellState state = view.getState(edge);
      Object src = (state != null) ? state.getVisibleTerminal(true) : view
         .getVisibleTerminal(edge, true);
      Object trg = (state != null) ? state.getVisibleTerminal(false) : view
         .getVisibleTerminal(edge, false);

      if(src instanceof mxICell && trg instanceof mxICell) {
         String srcId = mxCellPath.create((mxICell) src);
         String trgId = mxCellPath.create((mxICell) trg);

         return (srcId.compareTo(trgId) > 0) ? trgId + "-" + srcId : srcId
            + "-" + trgId;
      }

      return null;
   }

   /**
    *
    */
   protected void layout(List<Object> parallels)
   {
      Object edge = parallels.get(0);
      mxIGraphModel model = graph.getModel();
      mxGeometry src = model.getGeometry(model.getTerminal(edge, true));
      mxGeometry trg = model.getGeometry(model.getTerminal(edge, false));

      // Routes multiple loops
      if(src == trg) {
         double x0 = src.getX() + src.getWidth() + this.spacing;
         double y0 = src.getY() + src.getHeight() / 2;

         for(int i = 0; i < parallels.size(); i++) {
            route(parallels.get(i), x0, y0);
            x0 += spacing;
         }
      }
      else if(src != null && trg != null) {
         // Routes parallel edges
         double scx = src.getX() + src.getWidth() / 2;
         double scy = src.getY() + src.getHeight() / 2;

         double tcx = trg.getX() + trg.getWidth() / 2;
         double tcy = trg.getY() + trg.getHeight() / 2;

         double dx = tcx - scx;
         double dy = tcy - scy;

         double len = Math.sqrt(dx * dx + dy * dy);

         double x0 = scx + dx / 2;
         double y0 = scy + dy / 2;

         double nx = dy * spacing / len;
         double ny = dx * spacing / len;

         x0 += nx * (parallels.size() - 1) / 2;
         y0 -= ny * (parallels.size() - 1) / 2;

         for(int i = 0; i < parallels.size(); i++) {
            route(parallels.get(i), x0, y0);
            x0 -= nx;
            y0 += ny;
         }
      }
   }

   /**
    *
    */
   protected void route(Object edge, double x, double y)
   {
      if(graph.isCellMovable(edge)) {
         setEdgePoints(edge,
                       Arrays.asList(new mxPoint(x, y)));
      }
   }

}
