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
package inetsoft.graph.geometry;

import inetsoft.graph.GGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.visual.GraphVO;
import inetsoft.graph.visual.VisualObject;

/**
 * GraphGeometry is a container of goemetries in an embedded (nested) graph.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class GraphGeometry extends Geometry {
   /**
    * Create a graph geometry to hold geometries in a sub-graph.
    * @param ggraph this sub-graph.
    * @param pcoord the coord of the parent graph.
    * @param coord the coord of this sub-graph.
    * @param tuple tuple in the parent coord space.
    */
   public GraphGeometry(GGraph ggraph, Coordinate pcoord, Coordinate coord, double[] tuple) {
      this.ggraph = ggraph;
      this.pcoord = pcoord;
      this.coord = coord;
      this.tuple = tuple;
   }

   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    */
   @Override
   public VisualObject createVisual(Coordinate coord) {
      return new GraphVO(this);
   }

   /**
    * Get the ggraph of this sub-graph.
    */
   public GGraph getGGraph() {
      return ggraph;
   }

   /**
    * Get the coordinate of the parent graph.
    */
   public Coordinate getOuterCoordinate() {
      return pcoord;
   }

   /**
    * Get the coordinate of this sub-graph.
    */
   public Coordinate getCoordinate() {
      return coord;
   }

   /**
    * Get the tuple of this sub-graph in the parent coordinate.
    */
   public double[] getTuple() {
      return tuple;
   }

   private GGraph ggraph;
   private Coordinate pcoord; // parent coord
   private Coordinate coord; // coord of this sub-graph
   private double[] tuple;
}
