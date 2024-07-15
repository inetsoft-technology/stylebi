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
package inetsoft.graph.treeviz.tree.circlemap;

import inetsoft.graph.treeviz.ProgressObserver;
import inetsoft.graph.treeviz.tree.NodeInfo;
import inetsoft.graph.treeviz.tree.TreeNode;

/**
 * CirclemapTree lays out a tree structure in a space-filling circular treemap.
 *
 * @author Werner Randelshofer
 * @version 1.2 2009-03-22 Made layout progress observable.
 * <br>1.0 Jan 16, 2008 Created.
 */
public class CirclemapTree {

   private CirclemapNode root;
   private NodeInfo info;

   /**
    * Creates a new instance.
    */
   public CirclemapTree(TreeNode root, NodeInfo info, ProgressObserver p) {
      p.setNote("Constructing tree…");
      this.info = info;
      if(!root.getAllowsChildren()) {
         this.root = new CirclemapNode(null, root);
      }
      else {
         this.root = new CirclemapCompositeNode(null, root);
      }
      info.init(root);
      p.setNote("Calculating layout…");
      p.setMaximum(p.getMaximum() + this.root.getDescendantCount());
      p.setIndeterminate(false);
      this.root.layout(info, p);
   }

   public NodeInfo getInfo() {
      return info;
   }

   public CirclemapNode getRoot() {
      return root;
   }
}
