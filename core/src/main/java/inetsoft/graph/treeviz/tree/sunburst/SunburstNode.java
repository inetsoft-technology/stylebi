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
package inetsoft.graph.treeviz.tree.sunburst;

import inetsoft.graph.treeviz.tree.*;

import java.util.Collections;
import java.util.List;

/**
 * The SunburstNode encapsulatets a {@link TreeNode} whithin a {@link SunburstTree}.
 * <p>
 * It holds the computed left, right and depth value of a data.
 *
 * @author Werner Randelshofer
 * @version 1.0 September 18, 2007 Created.
 */
public class SunburstNode {
   private SunburstNode parent;

   private TreePath2<TreeNode> dataNodePath;
   /**
    * Nested Sets Tree: left preorder sequence number.
    */
   private double left;
   /**
    * Nested Sets Tree: right preorder sequence number.
    */
   private double right;

   private int maxDepth = -1;

   /**
    * Creates a new instance.
    */
   public SunburstNode(SunburstNode parent, TreeNode data) {
      this.dataNodePath = (parent == null) ? new TreePath2<>(data) : parent.getDataNodePath().pathByAddingChild(data);
      this.parent = parent;
   }

   public TreeNode getNode() {
      return dataNodePath.getLastPathComponent();
   }

   public TreePath2<TreeNode> getDataNodePath() {
      return dataNodePath;
   }

   public int getMaxDepth() {
      if(maxDepth == -1) {
         maxDepth = getMaxDepth(this, 1);
      }
      return maxDepth;
   }

   private int getMaxDepth(SunburstNode node, int depth) {
      int max = depth;
      for(SunburstNode child : node.children()) {
         max = Math.max(max, getMaxDepth(child, depth + 1));
      }
      return max;
   }

   public void renumber(NodeInfo info) {
      renumber(info, 0, 0);
   }

   private int renumber(NodeInfo info, int depth, int number) {
      if(children().size() == 0) {
         left = number;
         number += info.getWeight(dataNodePath);
         right = number;
      }
      else {
         left = number;
         for(SunburstNode child : children()) {
            number = child.renumber(info, depth + 1, number);
         }
         right = number;
      }
      return number;
   }

   public List<SunburstNode> children() {
      return Collections.EMPTY_LIST;
   }

   public void dump() {
      System.out.println(getDepth() + "," + left + "," + right + " " + toString());
      for(SunburstNode child : children()) {
         child.dump();
      }
   }

   public boolean isLeaf() {
      return !dataNodePath.getLastPathComponent().getAllowsChildren();
   }

   public double getLeft() {
      return left;
   }

   public double getRight() {
      return right;
   }

   public double getExtent() {
      return right - left;
   }

   public int getDepth() {
      return dataNodePath.getPathCount();
   }

   public boolean isDescendant(SunburstNode node) {
      return node.getLeft() >= getLeft() &&
         node.getRight() <= getRight() &&
         node.getDepth() >= getDepth();
   }

   public SunburstNode findNode(int depth, double number) {
      if(getLeft() <= number && getRight() > number) {
         if(depth == 0) {
            return this;
         }
         else {
            for(SunburstNode child : children()) {
               SunburstNode found = child.findNode(depth - 1, number);
               if(found != null) {
                  return found;
               }
            }
         }
      }
      return null;
   }
}
