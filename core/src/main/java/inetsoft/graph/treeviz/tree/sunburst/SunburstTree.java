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
package inetsoft.graph.treeviz.tree.sunburst;

import inetsoft.graph.treeviz.tree.NodeInfo;
import inetsoft.graph.treeviz.tree.TreeNode;

/**
 * The SunburstTree class implements the model for the SunBurstTree.
 * It's a tree of SunburstNode, each keeping the
 * initial layout of the tree in the SunBurst's Model.
 *
 * @author Werner Randelshofer
 * @version 1.0 September 18, 2007 Created.
 */
public class SunburstTree {
   private SunburstNode root;
   private NodeInfo info;

   /**
    * Creates a new instance.
    */
   public SunburstTree(TreeNode root, NodeInfo info) {
      this.info = info;
      if(!root.getAllowsChildren()) {
         this.root = new SunburstNode(null, root);
      }
      else {
         this.root = new SunburstCompositeNode(null, root);
      }
      info.init(root);
      this.root.renumber(info);
      //this.root.dump();
   }

   public NodeInfo getInfo() {
      return info;
   }

   public SunburstNode getRoot() {
      return root;
   }
}
