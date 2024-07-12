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
package inetsoft.uql.path;

import inetsoft.uql.*;
import inetsoft.uql.path.expr.Expr;
import inetsoft.uql.util.XUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A PathNode is a node in the XNodePath. The path node specifies the
 * selection of tree nodes on one subtree level. If the subtree is
 * a sequence node (multiple nodes with same node name), a condition
 * can be attached to the path node to select a subset of nodes from
 * the sequence.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class PathNode implements java.io.Serializable, Cloneable {
   /**
    * Create a path node.
    * @param name tree node to select.
    */
   public PathNode(String name) {
      this.name = name;
   }

   /**
    * Create a path node.
    * @param name tree node to select.
    * @param cond condition used to select nodes from the sequence.
    */
   public PathNode(String name, ConditionExpression cond) {
      this.name = name;
      this.cond = cond;
   }

   /**
    * Get the path node name.
    */
   public String getName() {
      return name;
   }

   /**
    * Get the path node condition.
    */
   public ConditionExpression getCondition() {
      return cond;
   }

   /**
    * Set the path node condition.
    */
   public void setCondition(ConditionExpression cond) {
      this.cond = cond;
   }

   /**
    * Select a node from the tree that matches this node.
    */
   public XNode select(XNode root, VariableTable vars) throws Exception {
      if(!root.getName().equals(name)) {
         return null;
      }

      if(cond == null) {
         return root;
      }

      // if a sequence, check each node for inclusion
      if(root instanceof XSequenceNode) {
         XSequenceNode seq = new XSequenceNode();

         seq.setName(root.getName());
         seq.setValue(root.getValue());

         for(int i = 0; i < root.getChildCount(); i++) {
            XNode child = root.getChild(i);
            Object rc = cond.execute(child, vars);

            try {
               // index number, e.g. a.b[2] or a.b[-1]
               if(rc instanceof Number) {
                  double idx = ((Number) rc).doubleValue();

                  if(idx < 0) {
                     idx += root.getChildCount();
                  }

                  if(idx == i) {
                     seq.addChild((XNode) child.clone());
                  }

                  break;
               }
               else if(Expr.booleanValue(rc)) {
                  seq.addChild((XNode) child.clone());
               }
            }
            catch(Exception e) {
               LOG.error("Failed to mark node for inclusion: " + child, e);
            }
         }

         return seq;
      }
      else if(Expr.booleanValue(cond.execute(root, vars))) {
         return root;
      }

      return null;
   }

   public String toString() {
      // @by larryl, handle special characters in name
      String quoted = XUtil.quoteAlias(name, null);

      return (cond == null) ? quoted : quoted + "[" + cond + "]";
   }

   /**
    * Returns a clone of this object.
    */
   @Override
   public Object clone() {
      try {
         PathNode node = (PathNode) super.clone();

         node.name = name;
         node.cond = cond;

         return node;
      }
      catch(CloneNotSupportedException e) {
         LOG.error("Failed to clone object", e);
      }

      return null;
   }

   String name;
   ConditionExpression cond;

   private static final Logger LOG =
      LoggerFactory.getLogger(PathNode.class);
}

