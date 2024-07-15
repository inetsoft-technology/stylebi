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
package inetsoft.uql;

import java.io.PrintWriter;

/**
 * A sequence node is used to group the child nodes sharing a same name.
 * By inserting a sequence node (automatically during tree construction)
 * to group the nodes, each node can be referenced with an unique path.
 * The sequence node has the same name as its child nodes.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XSequenceNode extends XNode {
   /**
    * Create an empty sequence node.
    */
   public XSequenceNode() {
   }

   /**
    * Create a sequence node with the specified name.
    */
   public XSequenceNode(String name) {
      super(name);
   }

   /**
    * Find the index of the child in this node.
    */
   @Override
   public int getChildIndex(XNode child) {
      for(int i = 0; i < getChildCount(); i++) {
         if(getChild(i) == child) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Sequence node does not check for duplicate children.
    */
   @Override
   protected XNode checkDuplicate(XNode child) {
      return child;
   }

   /**
    * Get the applied max rows.
    * @return the applied max rows.
    */
   public int getAppliedMaxRows() {
      return amax;
   }

   /**
    * Set the applied max rows.
    * @param amax the applied max rows.
    */
   public void setAppliedMaxRows(int amax) {
      this.amax = amax;
   }

   /**
    * Write the node XML representation.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      for(int i = 0; i < getChildCount(); i++) {
         getChild(i).writeXML(writer);
      }
   }

   public String toString() {
      String dispName = getName().indexOf("_") == -1 ?
         getName() :
         getName().substring(0, getName().indexOf("_"));

      return getName() + "[" + getChildCount() + "]";
   }

   private int amax;
}
