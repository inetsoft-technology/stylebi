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

import inetsoft.graph.treeviz.tree.TreeNode;

import java.util.*;

/**
 * SunburstCompositeNode.
 *
 * @author Werner Randelshofer
 * @version 1.0 September 18, 2007 Created.
 */
public class SunburstCompositeNode extends SunburstNode {
   private ArrayList<SunburstNode> children;

   /**
    * Creates a new instance.
    */
   public SunburstCompositeNode(SunburstNode parent, TreeNode node) {
      super(parent, node);

      children = new ArrayList<>();
      for(TreeNode c : node.children()) {
         if(!c.getAllowsChildren()) {
            children.add(new SunburstNode(this, c));
         }
         else {
            children.add(new SunburstCompositeNode(this, c));
         }
      }
   }

   public List<SunburstNode> children() {
      return Collections.unmodifiableList(children);
   }
}
