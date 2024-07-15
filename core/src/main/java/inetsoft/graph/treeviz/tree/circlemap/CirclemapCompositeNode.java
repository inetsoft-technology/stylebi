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
package inetsoft.graph.treeviz.tree.circlemap;

import inetsoft.graph.treeviz.ProgressObserver;
import inetsoft.graph.treeviz.tree.NodeInfo;
import inetsoft.graph.treeviz.tree.TreeNode;

import java.util.*;

/**
 * The CirclemapNode class encapsulates a composite {@link TreeNode} whithin a
 * {@link CirclemapTree}.
 *
 * @author Werner Randelshofer
 * @version 1.1 2010-08-19 Includes the weight of the composite node itself
 * into the size calculation of a circle.
 * <br>1.0 Jan 16, 2008 Created.
 */
public class CirclemapCompositeNode extends CirclemapNode {

   private int descendants = -1;
   private ArrayList<CirclemapNode> children;

   /**
    * Creates a new instance.
    */
   public CirclemapCompositeNode(CirclemapNode parent, TreeNode node) {
      super(parent, node);

      children = new ArrayList<>();
      for(TreeNode c : node.children()) {
         if(!c.getAllowsChildren()) {
            children.add(new CirclemapNode(this, c));
         }
         else {
            children.add(new CirclemapCompositeNode(this, c));
         }
      }
   }

   @Override
   public boolean isLeaf() {
      return false;
   }

   @Override
   public List<CirclemapNode> children() {
      return Collections.unmodifiableList(children);
   }

   @Override
   public void layout(NodeInfo info, ProgressObserver p) {
      if(p.isCanceled()) {
         return;
      }

      for(CirclemapNode child : children) {
         child.layout(info, p);
      }
      updateNodeLayout(info);
   }

   /**
    * Updates the layout of this node only. Does not update the layout
    * of child nodes or parent nodes.
    *
    * @param info
    */
   public void updateNodeLayout(NodeInfo info) {
      if(children.size() == 0) {
         radius = Math.max(10, getWeightRadius(info));
         return;
      }
      else if(children.size() == 1) {
         radius = //children.get(0).radius + 1;
            radius = Math.max(children.get(0).radius + 1, getWeightRadius(info));
         return;
      }


      ArrayList<Circle> circles = new ArrayList<>();
      circles.addAll(children);

      Circles.pairPack(circles);
      // Circles.phyllotacticPack(circles);

      Circle cbounds = Circles.boundingCircle(circles);
      radius = cbounds.radius;
      radius = Math.max(radius, getWeightRadius(info));
      for(CirclemapNode child : children) {
         child.cx -= cbounds.cx;
         child.cy -= cbounds.cy;
      }
   }

   @Override
   public int getDescendantCount() {
      if(descendants == -1) {
         descendants += children.size();
         for(CirclemapNode child : children) {
            descendants += child.getDescendantCount();
         }
      }
      return descendants;
   }

   /**
    * Call this method when a new child node has been added to the underlying
    * TreeNode.
    * <p>
    * For performance reasons, this method will not update the layout of
    * the circlemap.
    *
    * @param c the new child
    *
    * @return Returns the new CirclemapNode which holds the child.
    */
   public CirclemapNode newChildAdded(TreeNode c) {
      CirclemapNode cn;
      if(!c.getAllowsChildren()) {
         children.add(cn = new CirclemapNode(this, c));
      }
      else {
         children.add(cn = new CirclemapCompositeNode(this, c));
      }
      return cn;
   }
}
