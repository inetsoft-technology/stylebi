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
package inetsoft.graph.mxgraph.layout.hierarchical.model;

import java.util.ArrayList;
import java.util.List;

/**
 * An abstraction of a hierarchical edge for the hierarchy layout
 */
public class mxGraphHierarchyEdge extends mxGraphAbstractHierarchyCell {

   /**
    * The graph edge(s) this object represents. Parallel edges are all grouped
    * together within one hierarchy edge.
    */
   public List<Object> edges;

   /**
    * The node this edge is sourced at
    */
   public mxGraphHierarchyNode source;

   /**
    * The node this edge targets
    */
   public mxGraphHierarchyNode target;

   /**
    * Whether or not the direction of this edge has been reversed
    * internally to create a DAG for the hierarchical layout
    */
   protected boolean isReversed = false;

   /**
    * Constructs a hierarchy edge
    *
    * @param edges a list of real graph edges this abstraction represents
    */
   public mxGraphHierarchyEdge(List<Object> edges)
   {
      this.edges = edges;
   }

   /**
    * Inverts the direction of this internal edge(s)
    */
   public void invert()
   {
      mxGraphHierarchyNode temp = source;
      source = target;
      target = temp;
      isReversed = !isReversed;
   }

   /**
    * @return Returns the isReversed.
    */
   public boolean isReversed()
   {
      return isReversed;
   }

   /**
    * @param isReversed The isReversed to set.
    */
   public void setReversed(boolean isReversed)
   {
      this.isReversed = isReversed;
   }

   /**
    * Returns the cells this cell connects to on the next layer up
    *
    * @param layer the layer this cell is on
    *
    * @return the cells this cell connects to on the next layer up
    */
   @SuppressWarnings("unchecked")
   public List<mxGraphAbstractHierarchyCell> getNextLayerConnectedCells(int layer)
   {
      if(nextLayerConnectedCells == null) {
         nextLayerConnectedCells = new ArrayList[temp.length];

         for(int i = 0; i < nextLayerConnectedCells.length; i++) {
            nextLayerConnectedCells[i] = new ArrayList<mxGraphAbstractHierarchyCell>(2);

            if(i == nextLayerConnectedCells.length - 1) {
               nextLayerConnectedCells[i].add(source);
            }
            else {
               nextLayerConnectedCells[i].add(this);
            }
         }
      }

      return nextLayerConnectedCells[layer - minRank - 1];
   }

   /**
    * Returns the cells this cell connects to on the next layer down
    *
    * @param layer the layer this cell is on
    *
    * @return the cells this cell connects to on the next layer down
    */
   @SuppressWarnings("unchecked")
   public List<mxGraphAbstractHierarchyCell> getPreviousLayerConnectedCells(int layer)
   {
      if(previousLayerConnectedCells == null) {
         previousLayerConnectedCells = new ArrayList[temp.length];

         for(int i = 0; i < previousLayerConnectedCells.length; i++) {
            previousLayerConnectedCells[i] = new ArrayList<mxGraphAbstractHierarchyCell>(2);

            if(i == 0) {
               previousLayerConnectedCells[i].add(target);
            }
            else {
               previousLayerConnectedCells[i].add(this);
            }
         }
      }

      return previousLayerConnectedCells[layer - minRank - 1];
   }

   /**
    * @return whether or not this cell is an edge
    */
   public boolean isEdge()
   {
      return true;
   }

   /**
    * @return whether or not this cell is a node
    */
   public boolean isVertex()
   {
      return false;
   }

   /**
    * Gets the value of temp for the specified layer
    *
    * @param layer the layer relating to a specific entry into temp
    *
    * @return the value for that layer
    */
   public int getGeneralPurposeVariable(int layer)
   {
      return temp[layer - minRank - 1];
   }

   /**
    * Set the value of temp for the specified layer
    *
    * @param layer the layer relating to a specific entry into temp
    * @param value the value for that layer
    */
   public void setGeneralPurposeVariable(int layer, int value)
   {
      temp[layer - minRank - 1] = value;
   }

}
