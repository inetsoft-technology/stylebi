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
package inetsoft.uql.jdbc.util;

import inetsoft.uql.XNode;
import inetsoft.util.Tool;

/**
 * XMetaDataNode, stores metadata information in database.
 *
 * @author InetSoft Technology
 * @version 8.0
 */
public class XMetaDataNode extends XNode {
   /**
    * Constructor.
    */
   public XMetaDataNode() {
      super();
   }

   /**
    * Constructor.
    */
   public XMetaDataNode(String name) {
      super(name);
   }

   /**
    * Find the node specified by the node path. The path is a dot delimited
    * concatenation of node names from the root of the tree to the node.
    * The child of a sequence node is refered to as the name of the
    * sequence node followed by a sequence index, params[2].
    */
   @Override
   public XNode getNode(String path) {
      if(path == null || path.length() == 0) {
         return this;
      }

      if(getName() == null) {
         return null;
      }

      int nameEnd = getName().length();
      int idx = path.indexOf('.', path.startsWith("this") ? 4 : nameEnd);
      String name2 = (idx >= 0) ? path.substring(0, idx) : path;
      XNode node = this;

      if(node == null) {
         return null;
      }

      if(node.getName().equals(name2) || name2.equals("this")) {
         if(idx < 0) {
            return node;
         }

         path = path.substring(idx + 1);

         while(path.startsWith("this") &&
               (path.length() == 4 || path.charAt(4) == '.')) {
            path = path.substring(Math.min(5, path.length()));
         }

         if(path.length() == 0) {
            return node;
         }

         for(int i = 0; i < node.getChildCount(); i++) {
            XNode child = node.getChild(i).getNode(path);

            if(child != null) {
               return child;
            }
         }
      }

      return null;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      XNode node = (XNode) obj;

      if(!Tool.equals(getName(), node.getName())) {
         return false;
      }

      if(!Tool.equals(getAttribute("type"), node.getAttribute("type"))) {
         return false;
      }

      if(!Tool.equals(getAttribute("catalog"), node.getAttribute("catalog"))) {
         return false;
      }

      if(!Tool.equals(getAttribute("schema"), node.getAttribute("schema"))) {
         return false;
      }

      return true;
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      // do not override the method for mixed usage
      return super.hashCode();
   }
}
