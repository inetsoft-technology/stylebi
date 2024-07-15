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

/**
 * CirclemapModel manages a CirclemapTree and its CirclemapView.
 *
 * @author Werner Randelshofer
 * @version 1.0 2008-01-16 Created.
 */
public class CirclemapModel {
   private CirclemapTree tree;
   private NodeInfo info;

   /**
    * Creates a new instance.
    */
   public CirclemapModel(TreeNode root, NodeInfo info, ProgressObserver p) {
      tree = new CirclemapTree(root, info, p);
      this.info = info;
   }

   public CirclemapTree getView() {
      return tree;
   }

   public NodeInfo getInfo() {
      return info;
   }
}
