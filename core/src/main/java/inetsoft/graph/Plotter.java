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
package inetsoft.graph;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.coord.*;
import inetsoft.graph.data.*;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.StackableElement;
import inetsoft.graph.geometry.ElementGeometry;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.internal.*;
import inetsoft.graph.scale.*;
import inetsoft.graph.visual.ElementVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * A plotter is responsible for processing a graph definition, and producing a
 * visual graph that is capable of rendering the graph in a graphics output.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class Plotter {
   /**
    * Event id for GGraph created event.
    */
   public static final int GGRAPH_CREATED = 1;
   /**
    * Event id for VGraph created event.
    */
   public static final int VGRAPH_CREATED = 2;

   /**
    * Get a plotter for the specified graph.
    */
   public static Plotter getPlotter(EGraph graph) {
      return new Plotter(graph);
   }

   /**
    * Create a plotter.
    */
   private Plotter(EGraph graph) {
      super();

      this.listeners = new ArrayList<>();
      this.graph = graph;
   }

   /**
    * Create a visual graph from a graph definition and layout with the specified size.
    * This method handles the case where a VGraph may need to be re-generated if the
    * coordinate size is required and changed after layout.
    */
   public VGraph plotAndLayout(DataSet data, int x, int y, int w, int h) {
      if(graph.getCoordinate() != null) {
         graph.getCoordinate().setLayoutSize(new DimensionD(w, h));
      }

      VGraph vgraph = plot(data);

      if(vgraph == null) { // cancelled
         return null;
      }

      try {
         vgraph.layout(x, y, w, h);
      }
      catch(DepthException ex) {
         clearDepth(graph.getCoordinate());
         createVGraph(data, vgraph.getCoordinate());
         vgraph.layout(x, y, w, h);
      }

      // if the plot size changed (from graph size) and need to re-generate.
      // an alternative is to change the flow to:
      // 1. perform a partial layout of vgraph to calculate legends size and setCoordBounds().
      // 2. call coord.init()
      // 3. re-generate GGraph and visual objects.
      // it seems re-plotting is much simpler though at a higher cost. if it becomes an issue
      // consider re-arrange the flow. (51700)
      if(graph.getCoordinate().requiresReplot()) {
         vgraph = plot(data);
         vgraph.layout(x, y, w, h);
      }

      clearCache(graph, vgraph);
      return vgraph;
   }

   /**
    * Set the depth of the 3D coord to 0.
    */
   private static void clearDepth(Coordinate coord) {
      if(coord instanceof FacetCoord) {
         Coordinate[] inners = ((FacetCoord) coord).getInnerCoordinates();

         for(int i = 0; i < inners.length; i++) {
            clearDepth(inners[i]);
         }
      }

      if(coord instanceof Rect25Coord) {
         ((Rect25Coord) coord).setDepth(0);
      }
   }

   /**
    * Create a visual graph from a graph definition.
    */
   public VGraph plot(DataSet data) {
      // clear cached info between generation (48503).
      clearCache(graph, null);

      initFrames(data);

      if(cancelled) {
         return null;
      }

      Coordinate coord = createCoordinate(data);

      if(cancelled) {
         return null;
      }

      if(coord == null) {
         throw new RuntimeException(GTool.getString(
            "common.elementBindingMissing"));
      }

      createVGraph(data, coord);
      return !cancelled ? vgraph : null;
   }

   /**
    * Clear any cached information to reduce memory usage.
    */
   private void clearCache(EGraph graph, VGraph vgraph) {
      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);
         (new HashSet<>(elem.getHints().keySet())).stream()
            .filter(key -> key != null && key.startsWith("__cached_"))
            .forEach(key -> elem.setHint(key, null));
      }

      if(vgraph != null) {
         for(int i = 0; i < vgraph.getVisualCount(); i++) {
            Visualizable vobj = vgraph.getVisual(i);

            if(vobj instanceof ElementVO) {
               Geometry gobj = ((ElementVO) vobj).getGeometry();

               if(gobj instanceof ElementGeometry) {
                  ((ElementGeometry) gobj).clearTuple();
               }
            }
         }
      }
   }

   private void createVGraph(DataSet data, Coordinate coord) {
      vgraph = new VGraph(coord);
      ggraph = coord.createGGraph(data, new DataSetIndex(data, false), data, graph,
                                  null, new HashMap(), new HashMap(),
                                  Coordinate.ALL_MOST);

      if(cancelled) {
         return;
      }

      fireEvent(GGRAPH_CREATED, "GGraph created");

      if(cancelled) {
         return;
      }

      fireEvent(VGRAPH_CREATED, "VGraph created");
      vgraph.createVisuals(ggraph);
   }

   /**
    * Initialize aesthetic frames if necessary.
    */
   private void initFrames(DataSet data) {
      for(int i = 0; i < graph.getElementCount() && !cancelled; i++) {
         GraphElement elem = graph.getElement(i);
         VisualFrame[] frames = elem.getVisualFrames();
         DataSet data2 = elem.getVisualDataSet();

         if(data2 == null) {
            data2 = data;
         }

         for(int k = 0; k < frames.length; k++) {
            if(frames[k] != null) {
               if(!frames[k].isValid()) {
                  // default color frame for assigning a different color for each variable
                  if(frames[k].getField() == null && frames[k] instanceof CategoricalColorFrame) {
                     Object[] vars = getVars(graph, false);
                     ((CategoricalColorFrame) frames[k]).init(vars);
                     continue;
                  }
               }

               if(!frames[k].isValid() || data2 != data && frames[k].getField() != null) {
                  frames[k].init(data2);
               }
            }
         }
      }
   }

   /**
    * Get the intermediate GGraph object.
    * @hidden
    */
   public GGraph getGGraph() {
      return ggraph;
   }

   /**
    * Get the VGraph object.
    */
   public VGraph getVGraph() {
      return vgraph;
   }

   /**
    * Create a coordinate for the ggraph.
    */
   private Coordinate createCoordinate(DataSet data) {
      Coordinate coord = graph.getCoordinate();

      if(coord == null) {
         createScales(data);

         String[] dims = GTool.getDims(graph);
         Scale[] xscales = new Scale[dims.length];

         for(int i = 0; i < dims.length; i++) {
            xscales[i] = graph.getScale(dims[i]);
         }

         String[] vars = getVars(graph, true);
         List<Scale> yset = new ArrayList<>();

         for(int i = 0; i < vars.length; i++) {
            if(!yset.contains(graph.getScale(vars[i]))) {
               yset.add(graph.getScale(vars[i]));
            }
         }

         Scale[] yscales = yset.toArray(new Scale[0]);
         List coords = new ArrayList(); // Coordinate or Coordinate[]

         for(int xi = xscales.length - 1; xi >= 0; ) {
            // inner most coord
            if(coords.size() == 0) {
               Coordinate[] inners = new Coordinate[yscales.length];

               for(int i = 0; i < yscales.length; i++) {
                  inners[i] = new RectCoord(xscales[xi], yscales[i]);
               }

               coords.add(inners);
               xi--;
            }
            else if(xi > 0) {
               Coordinate inner = new RectCoord(xscales[xi - 1], xscales[xi]);
               coords.add(inner);
               xi -= 2;
            }
            else {
               Coordinate inner = new RectCoord(xscales[xi], null);
               coords.add(inner);
               xi -= 1;
            }
         }

         // create nested facet
         if(coords.size() > 1) {
            Coordinate outer = (Coordinate) coords.get(1);
            FacetCoord facet = new FacetCoord(outer, (Coordinate[]) coords.get(0), true);

            for(int i = 2; i < coords.size(); i++) {
               facet = new FacetCoord((Coordinate) coords.get(i), facet);
            }

            setDefaultGrid(outer);
            coord = facet;
         }
         // assemble the coords into a facet if more than one measure
         else if(coords.size() == 1) {
            Coordinate[] inners = (Coordinate[]) coords.get(0);

            if(inners.length > 1) {
               coord = new FacetCoord(null, inners, true);
            }
            else if(inners.length > 0) {
               coord = inners[0];
            }
         }
         // a one dimensional (horizontal line) coord
         else if(yscales.length > 0) {
            coord = new RectCoord(null, yscales[0]);
            coord.transpose();
         }
      }

      if(coord != null) {
         // prepare non-nested dataset, facet sub-dataset is prepared when created
         if(!(coord instanceof FacetCoord)) {
            String[] dims = GTool.getDims(graph);
            String dim = dims.length > 0 ? dims[0] : null;

            if(!(data instanceof DataSetFilter)) {
               data.removeCalcValues();
            }

            data.prepareGraph(graph, coord, null);
            data.prepareCalc(dim, null, true);
         }

         coord.init(data);
         graph.setCoordinate(coord);
      }

      return coord;
   }

   /**
    * Set the grid style for facet.
    */
   private void setDefaultGrid(Coordinate outer) {
      if(outer instanceof RectCoord) {
         Scale xscale = ((RectCoord) outer).getXScale();
         Scale yscale = ((RectCoord) outer).getYScale();

         if(xscale != null) {
            xscale.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
         }

         if(yscale != null) {
            yscale.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
         }
      }
   }

   /**
    * Create scales from a graph specification.
    */
   private void createScales(DataSet data) {
      // create scales for dimensions
      String[] cols = GTool.getDims(graph);

      for(int i = 0; i < cols.length; i++) {
         Scale scale = graph.getScale(cols[i]);

         if(scale == null) {
            scale = Scale.createScale(data, cols[i]);
            graph.setScale(cols[i], scale);
         }
      }

      String[] vars = getVars(graph, true);

      if(vars.length > 0) {
         Scale scale = graph.getScale(vars[0]);

         if(scale == null) {
            scale = Scale.createScale(data, vars);
            // default y grid
            scale.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
         }

         for(int i = 0; i < vars.length; i++) {
            graph.setScale(vars[i], scale);
         }
      }

      // Perform any default setup (e.g. stacking) of the scales according to
      // graph definition.

      // mark stack as stacked according to element setting
      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);

         if(!elem.isStack()) {
            continue;
         }

         for(int j = 0; j < elem.getVarCount(); j++) {
            Scale scale = graph.getScale(elem.getVar(j));

            if(!(scale instanceof LinearScale)) {
               continue;
            }

            LinearScale lscale = (LinearScale) scale;
            ScaleRange orange = lscale.getScaleRange();

            if(orange instanceof StackRange) {
               continue;
            }

            StackRange nrange = new StackRange();

            nrange.setAbsoluteValue(orange.isAbsoluteValue());

            if(elem instanceof StackableElement &&
               ((StackableElement) elem).isStackGroup())
            {
               nrange.setGroupField(elem.getDim(0));
	    }

            nrange.addStackFields(scale.getDataFields());
            lscale.setScaleRange(nrange);
         }
      }
   }

   /**
    * Add action listener. Action events are fired when a GGraph is created, and
    * after a VGraph is created.
    */
   public void addActionListener(ActionListener listener) {
      for(int i = 0; i < listeners.size(); i++) {
         if(listener.equals(listeners.get(i))) {
            return;
         }
      }

      listeners.add(listener);
   }

   /**
    * Remove action listener.
    */
   public void removeActionListener(ActionListener listener) {
      for(int i = listeners.size() - 1; i >= 0; i--) {
         Object obj = listeners.get(i);

         if(listener.equals(obj)) {
            listeners.remove(i);
            return;
         }
      }
   }

   /**
    * Fire event to notify listeners.
    */
   private void fireEvent(int id, String command) {
      ActionEvent event = new ActionEvent(this, id, command);

      for(int i = listeners.size() - 1; i >= 0; i--) {
         ActionListener listener = listeners.get(i);

         try {
            listener.actionPerformed(event);
         }
         catch(Exception ex) {
            LOG.warn("Failed to handle event", ex);
         }
      }
   }

   /**
    * Get variables plotted by this graph elements.
    * @param all true to include all variables, false to only return the
    * primary variables (e.g. excluding base of interval).
    */
   private String[] getVars(EGraph graph, boolean all) {
      Set dims = new HashSet();

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);

         if(all) {
            for(String col : elem.getVars()) {
               dims.add(col);
            }
         }
         else {
            for(int k = 0; k < elem.getVarCount(); k++) {
               dims.add(elem.getVar(k));
            }
         }
      }

      return (String[]) dims.toArray(new String[dims.size()]);
   }

   /**
    * Cancel any processing on the chart.
    */
   public void cancel() {
      this.cancelled = true;

      if(vgraph != null) {
         vgraph.cancel();
      }
   }

   private EGraph graph;
   private GGraph ggraph;
   private VGraph vgraph;
   private List<ActionListener> listeners;
   private boolean cancelled = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(Plotter.class);
}
