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
package inetsoft.graph.treeviz.tree.circlemap;

import inetsoft.graph.treeviz.ProgressObserver;
import inetsoft.graph.treeviz.tree.*;

import java.util.Collections;
import java.util.List;

/**
 * The CirclemapNode class encapsulates a {@link TreeNode} whithin a
 * {@link CirclemapTree}.
 * <p>
 * It holds the radius of the data as an absolute value.
 * The location is held relative to the center of the parent data.
 * <p>
 * This data can layout its subtree in a space-filling circular treemap.
 *
 * @author Werner Randelshofer
 * @version 1.0 Jan 16, 2008 Created.
 */
public class CirclemapNode extends Circle {
   private CirclemapNode parent;
   private TreePath2<TreeNode> dataNodePath;

   public CirclemapNode(CirclemapNode parent, TreeNode data) {
      this.parent = parent;
      this.dataNodePath = (parent == null) ? new TreePath2<>(data) : parent.getDataNodePath().pathByAddingChild(data);
   }

   public List<CirclemapNode> children() {
      return Collections.EMPTY_LIST;
   }

   public boolean isLeaf() {
      return true;
   }

   public TreePath2<TreeNode> getDataNodePath() {
      return dataNodePath;
   }

   /**
    * Lays out the subtree starting at this data in a space-filling
    * circular treemap.
    */
   public void layout(NodeInfo info, ProgressObserver p) {
      radius = getWeightRadius(info);
      //radius = 1;
   }

   public double getWeightRadius(NodeInfo info) {
      return Math.max(1, Math.sqrt(info.getCumulatedWeight(dataNodePath) / Math.PI));
   }

   public CirclemapNode getParent() {
      return parent;
   }

   public TreeNode getDataNode() {
      return dataNodePath.getLastPathComponent();
   }

   @Override
   public String toString() {
      return this.getClass() + "[x:" + cx + ",y:" + cy + ",r:" + radius + "]";
//        return dataNodePath.getLastPathComponent().toString();
   }

   public int getDescendantCount() {
      return 0;
   }

   /**
    * Updates the layout of all parent nodes.
    *
    * @param info
    */
   public void updateParentLayouts(NodeInfo info) {
      for(CirclemapNode n = getParent(); n != null; n = n.getParent()) {
         CirclemapCompositeNode cn = (CirclemapCompositeNode) n;
         cn.updateNodeLayout(info);
      }
   }
}
