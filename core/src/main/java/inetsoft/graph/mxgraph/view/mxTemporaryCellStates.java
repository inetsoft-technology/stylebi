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
package inetsoft.graph.mxgraph.view;

import inetsoft.graph.mxgraph.util.mxRectangle;

import java.util.Hashtable;

public class mxTemporaryCellStates {
   /**
    *
    */
   protected mxGraphView view;

   /**
    *
    */
   protected Hashtable<Object, mxCellState> oldStates;

   /**
    *
    */
   protected mxRectangle oldBounds;

   /**
    *
    */
   protected double oldScale;

   /**
    * Constructs a new temporary cell states instance.
    */
   public mxTemporaryCellStates(mxGraphView view)
   {
      this(view, 1, null);
   }

   /**
    * Constructs a new temporary cell states instance.
    */
   public mxTemporaryCellStates(mxGraphView view, double scale)
   {
      this(view, scale, null);
   }

   /**
    * Constructs a new temporary cell states instance.
    */
   public mxTemporaryCellStates(mxGraphView view, double scale, Object[] cells)
   {
      this.view = view;

      // Stores the previous state
      oldBounds = view.getGraphBounds();
      oldStates = view.getStates();
      oldScale = view.getScale();

      // Creates space for the new states
      view.setStates(new Hashtable<Object, mxCellState>());
      view.setScale(scale);

      if(cells != null) {
         mxRectangle bbox = null;

         // Validates the vertices and edges without adding them to
         // the model so that the original cells are not modified
         for(int i = 0; i < cells.length; i++) {
            mxRectangle bounds = view.getBoundingBox(view.validateCellState(view.validateCell(cells[i])));

            if(bbox == null) {
               bbox = bounds;
            }
            else {
               bbox.add(bounds);
            }
         }

         if(bbox == null) {
            bbox = new mxRectangle();
         }

         view.setGraphBounds(bbox);
      }
   }

   /**
    * Destroys the cell states and restores the state of the graph view.
    */
   public void destroy()
   {
      view.setScale(oldScale);
      view.setStates(oldStates);
      view.setGraphBounds(oldBounds);
   }

}
