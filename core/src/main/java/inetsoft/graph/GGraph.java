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
package inetsoft.graph;

import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.FacetCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.scale.Scale;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A geometry graph is a graph containing geometry objects. The geometries are
 * objects within the logic coordinate space. They need to be transformed by
 * the coordinates to become visual objects.
 * <br>
 * A GGraph is created as a intermediatory representation of a graph and
 * normally doesn't need to be modified directly.
 *
 * @hidden
 * @author InetSoft Technology Corp.
 * @since  10.0
 */
public class GGraph {
   /**
    * Create a geometry graph from a graph specification.
    */
   public GGraph(EGraph graph, Coordinate coord, DataSet data) {
      this.graph = graph;
      this.coord = coord;
      this.data = data;
      initScaleMap(graph.getCoordinate());
      initScaleMap(coord);
      scalemap.trim();
   }

   /**
    * Init scale map.
    */
   private void initScaleMap(Coordinate coord) {
      Scale[] scales = coord.getScales();

      // we give primary scale a higher priority than secondary
      for(int i = scales.length - 1; i >= 0; i--) {
         String[] flds = scales[i].getFields();

         for(int j = 0; j < flds.length; j++) {
            scalemap.put(flds[j], scales[i]);
         }
      }
   }

   /**
    * Initialize geometries.
    */
   private void initGeometries() {
      if(inited) {
         return;
      }

      inited = true;

      // create geometry objects for graph elements
      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);

         // only let the contained element create geometry
         if(containsElement(elem)) {
            elem.createGeometry(data, this);
         }
      }
   }

   /**
    * Check if contains the specified graph element.
    * @param elem the specified graph element.
    * @return true if contains the specified graph element, false otherwise.
    */
   private boolean containsElement(GraphElement elem) {
      if(coord instanceof FacetCoord) {
         return false;
      }

      // single graph, draw all elements
      if(coord.getParentCoordinate() == null) {
         return true;
      }

      Scale[] scales = coord.getScales();
      ObjectOpenHashSet dims = new ObjectOpenHashSet(elem.getDims());
      dims.addAll(new ObjectOpenHashSet(elem.getVars()));

      for(int i = 0; i < scales.length; i++) {
         String[] flds = scales[i].getFields();

         for(int j = 0; j < flds.length; j++) {
            dims.remove(flds[j]);
         }
      }

      dims.removeAll(coord.getParentValues(true).keySet());
      dims.removeAll(coord.getParentValues(false).keySet());

      // make sure the element is handled by this coord
      return dims.size() == 0;
   }

   /**
    * Add a geometry object to the graph.
    */
   public synchronized void addGeometry(Geometry gobj) {
      if(geometries == null) {
         geometries = new ArrayList<>(1);
      }

      geometries.add(gobj);
   }

   /**
    * Get the specified geometry object.
    */
   public Geometry getGeometry(int idx) {
      initGeometries();
      return geometries != null ? geometries.get(idx) : null;
   }

   /**
    * Get the number of geometry objects in the graph.
    */
   public int getGeometryCount() {
      initGeometries();
      return geometries != null ? geometries.size() : 0;
   }

   /**
    * Remove the specified geometry object.
    */
   public void removeGeometry(int idx) {
      initGeometries();

      if(geometries != null) {
         geometries.remove(idx);
      }
   }

   /**
    * Remove all geometry objects.
    */
   public void removeAllGeometries() {
      initGeometries();
      geometries = null;
   }

   /**
    * Sort the geometries in the graph.
    */
   public void sortGeometries() {
      initGeometries();
      
      if(geometries != null) {
         Collections.sort(geometries);
      }
   }

   /**
    * Get the associated EGraph.
    */
   public EGraph getEGraph() {
      return graph;
   }

   /**
    * Get the coordinate for this ggraph.
    */
   public Coordinate getCoordinate() {
      return coord;
   }

   /**
    * Get the scale used for mapping values for a column.
    * @param col the column identifier of a dataset.
    * @return scale the scale applied to the column.
    */
   public Scale getScale(String col) {
      Scale scale = scalemap != null ? scalemap.get(col) : null;

      if(scale == null) {
         scale = graph.getScale(col);
      }

      return scale;
   }

   /**
    * This method is called after visualizables have been created.
    */
   public void layoutCompleted() {
      scalemap = null;
   }

   private List<Geometry> geometries = null;
   private EGraph graph;
   private boolean inited;
   private DataSet data;
   private Coordinate coord;
   private Object2ObjectOpenHashMap<String, Scale> scalemap = new Object2ObjectOpenHashMap<>();

   private static final Logger LOG = LoggerFactory.getLogger(GGraph.class);
}
