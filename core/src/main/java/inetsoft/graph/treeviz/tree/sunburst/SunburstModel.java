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

import inetsoft.graph.treeviz.tree.NodeInfo;
import inetsoft.graph.treeviz.tree.TreeNode;

/**
 * SunburstModel manages a SunburstTree and its SunburstView.
 *
 * @author Werner Randelshofer
 * @version 1.0 September 18, 2007 Created.
 */
public class SunburstModel {
   private SunburstTree tree;
   private NodeInfo info;

   /**
    * Creates a new instance.
    */
   public SunburstModel(TreeNode root, NodeInfo info) {
      tree = new SunburstTree(root, info);
      this.info = info;
   }

   public SunburstTree getView() {
      return tree;
   }

   public NodeInfo getInfo() {
      return info;
   }
}
