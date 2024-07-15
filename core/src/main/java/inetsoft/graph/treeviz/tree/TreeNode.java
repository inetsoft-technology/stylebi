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
package inetsoft.graph.treeviz.tree;

import java.util.List;


/**
 * The TreeNode interface is implemented by objects which encapsulate a tree
 * structure.
 *
 * @author Werner Randelshofer
 * @version 1.0 September 21, 2007 Created.
 */
public interface TreeNode {
   /**
    * Returns the children of this node
    * in a List.
    * If this object does not have children,
    * it returns an empty List.
    *
    * @return the children of this node
    */
   List<TreeNode> children();


   /**
    * Returns true, if this node can not have children.
    * This is used to make a distinction between composite nodes which have
    * no children, and leaf nodes which can have no children.
    */
   boolean getAllowsChildren();
}
