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
package inetsoft.report.script.viewsheet;

import inetsoft.graph.EGraph;
import inetsoft.graph.data.DataSet;

/**
 * A class for creating a EGraph.
 *
 * @version 11.3
 * @author InetSoft Technology Corp
 */

public abstract class GraphCreator {
   /**
    * Create a EGraph.
    */
   public abstract EGraph createGraph();

   /**
    * Get the dataset used in the graph generator.
    */
   public abstract DataSet getGraphDataSet();

   /**
    * Get the current graph.
    */
   public final EGraph getGraph() {
      if(scriptGraph != null) {
         return scriptGraph;
      }
      
      if(egraph == null) {
         egraph = createGraph();
         ograph = (EGraph) egraph.clone();
      }

      return egraph;
   }

   /**
    * Get the graph created from the original binding.
    */
   public final EGraph getCreatedGraph() {
      return ograph;
   }

   /**
    * Set the script created graph.
    */
   public final void setGraph(EGraph egraph) {
      this.scriptGraph = egraph;
   }

   /**
    * Set the current dataset.
    */
   public final void setDataSet(DataSet dataset) {
      this.dataset = dataset;
   }
   
   /**
    * Get the current dataset.
    */
   public final DataSet getDataSet() {
      return dataset;
   }

   /**
    * Get the data set used in the graph generator.
    */
   public final DataSet getOriginalDataSet() {
      if(odataset == null) {
         odataset = getGraphDataSet();
         dataset = odataset;
      }
      
      return odataset;
   }

   /**
    * Mark whether the script has been executed.
    */
   public void setScriptExecuted(boolean execed) {
      this.execed = execed;
   }

   /**
    * Check if the script has been executed.
    */
   public boolean isScriptExecuted() {
      return execed;
   }
   
   private EGraph egraph; // created graph
   private EGraph ograph; // original created graph
   private DataSet dataset; // current dataset
   private DataSet odataset; // original dataset
   private EGraph scriptGraph; // explictly set graph
   private boolean execed = false;
}
