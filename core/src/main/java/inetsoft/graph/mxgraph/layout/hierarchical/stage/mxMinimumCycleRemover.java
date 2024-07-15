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
package inetsoft.graph.mxgraph.layout.hierarchical.stage;

import inetsoft.graph.mxgraph.layout.hierarchical.model.*;
import inetsoft.graph.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import inetsoft.graph.mxgraph.view.mxGraph;

import java.util.*;

/**
 * An implementation of the first stage of the Sugiyama layout. Straightforward
 * longest path calculation of layer assignment
 */
public class mxMinimumCycleRemover implements mxHierarchicalLayoutStage {

   /**
    * Reference to the enclosing layout algorithm
    */
   protected mxHierarchicalLayout layout;

   /**
    * Constructor that has the roots specified
    */
   public mxMinimumCycleRemover(mxHierarchicalLayout layout)
   {
      this.layout = layout;
   }

   /**
    * Produces the layer assignmment using the graph information specified
    */
   public void execute(Object parent)
   {
      mxGraphHierarchyModel model = layout.getModel();
      final Set<mxGraphHierarchyNode> seenNodes = new HashSet<mxGraphHierarchyNode>();
      final Set<mxGraphHierarchyNode> unseenNodes = new HashSet<mxGraphHierarchyNode>(
         model.getVertexMapper().values());

      // Perform a dfs through the internal model. If a cycle is found,
      // reverse it.
      mxGraphHierarchyNode[] rootsArray = null;

      if(model.roots != null) {
         Object[] modelRoots = model.roots.toArray();
         rootsArray = new mxGraphHierarchyNode[modelRoots.length];

         for(int i = 0; i < modelRoots.length; i++) {
            Object node = modelRoots[i];
            mxGraphHierarchyNode internalNode = model
               .getVertexMapper().get(node);
            rootsArray[i] = internalNode;
         }
      }

      model.visit(new mxGraphHierarchyModel.CellVisitor() {
         public void visit(mxGraphHierarchyNode parent,
                           mxGraphHierarchyNode cell,
                           mxGraphHierarchyEdge connectingEdge, int layer, int seen)
         {
            // Check if the cell is in it's own ancestor list, if so
            // invert the connecting edge and reverse the target/source
            // relationship to that edge in the parent and the cell
            if((cell)
               .isAncestor(parent))
            {
               connectingEdge.invert();
               parent.connectsAsSource.remove(connectingEdge);
               parent.connectsAsTarget.add(connectingEdge);
               cell.connectsAsTarget.remove(connectingEdge);
               cell.connectsAsSource.add(connectingEdge);
            }
            seenNodes.add(cell);
            unseenNodes.remove(cell);
         }
      }, rootsArray, true, null);

      Set<Object> possibleNewRoots = null;

      if(unseenNodes.size() > 0) {
         possibleNewRoots = new HashSet<Object>(unseenNodes);
      }

      // If there are any nodes that should be nodes that the dfs can miss
      // these need to be processed with the dfs and the roots assigned
      // correctly to form a correct internal model
      Set<mxGraphHierarchyNode> seenNodesCopy = new HashSet<mxGraphHierarchyNode>(
         seenNodes);

      // Pick a random cell and dfs from it
      mxGraphHierarchyNode[] unseenNodesArray = new mxGraphHierarchyNode[1];
      unseenNodes.toArray(unseenNodesArray);

      model.visit(new mxGraphHierarchyModel.CellVisitor() {
         public void visit(mxGraphHierarchyNode parent,
                           mxGraphHierarchyNode cell,
                           mxGraphHierarchyEdge connectingEdge, int layer, int seen)
         {
            // Check if the cell is in it's own ancestor list, if so
            // invert the connecting edge and reverse the target/source
            // relationship to that edge in the parent and the cell
            if((cell)
               .isAncestor(parent))
            {
               connectingEdge.invert();
               parent.connectsAsSource.remove(connectingEdge);
               parent.connectsAsTarget.add(connectingEdge);
               cell.connectsAsTarget.remove(connectingEdge);
               cell.connectsAsSource.add(connectingEdge);
            }
            seenNodes.add(cell);
            unseenNodes.remove(cell);
         }
      }, unseenNodesArray, true, seenNodesCopy);

      mxGraph graph = layout.getGraph();

      if(possibleNewRoots != null && possibleNewRoots.size() > 0) {
         Iterator<Object> iter = possibleNewRoots.iterator();
         List<Object> roots = model.roots;

         while(iter.hasNext()) {
            mxGraphHierarchyNode node = (mxGraphHierarchyNode) iter.next();
            Object realNode = node.cell;
            int numIncomingEdges = graph.getIncomingEdges(realNode).length;

            if(numIncomingEdges == 0) {
               roots.add(realNode);
            }
         }
      }
   }
}
