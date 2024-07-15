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
package inetsoft.uql.jdbc;

import inetsoft.uql.XNode;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Vector;

/**
 * The XFilterNode is a abstract class extends XNode. XFilterNode is extended
 * by XSet, XJoin, XUnaryCondition, XBinaryCondition, and XTrinaryCondition
 * be used to build where clause and having clause.
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class XFilterNode
   extends XNode implements Cloneable, Serializable, XMLSerializable
{
   public boolean isGroup() {
      return group;
   }

   public void setGroup(boolean group) {
      this.group = group;
   }

   /**
    * Flag for join condition.
    */
   public static final String JOIN = "join";
   /**
    * Flag for regular condition.
    */
   public static final String CONDITION = "condition";
   /**
    * Flag for grouping (having) condition.
    */
   public static final String GROUP = "group";
   /**
    * Condition branch root.
    */
   public static final String SET = "set";
   /**
    * 'and' operator.
    */
   public static final String AND = "and";
   /**
    * 'or' operator.
    */
   public static final String OR = "or";
   /**
    * 'not' operator.
    */
   public static final String NOT = "not";
   /**
    * 'where' clause condition.
    */
   public static final String WHERE = "where";
   /**
    * 'having' clause condition.
    */
   public static final String HAVING = "having";

   /**
    * Get the name list of all condition operators.
    */
   public static String[] getAllOperators() {
      return getAllOperators(true);
   }

   /**
    * Get the name list of all condition operators.
    */
   public static String[] getAllOperators(boolean withUnary) {
      Vector vec = new Vector();
      String[] list;

      if(withUnary) {
         list = XUnaryCondition.getAllOperators();
         for(int i = 0; i < list.length; i++) {
            vec.addElement(list[i]);
         }
      }

      list = XBinaryCondition.getAllOperators();
      for(int i = 0; i < list.length; i++) {
         vec.addElement(list[i]);
      }

      list = XTrinaryCondition.getAllOperators();
      for(int i = 0; i < list.length; i++) {
         vec.addElement(list[i]);
      }

      String[] ops = new String[vec.size()];

      vec.copyInto(ops);
      return ops;
   }

   /**
    * Get the operator symbol by its name.
    */
   public static String getOpSymbol(String opname) {
      String op = XUnaryCondition.getOpSymbol(opname);

      if(op != null && !op.equals("")) {
         return op;
      }

      op = XBinaryCondition.getOpSymbol(opname);

      if(op != null && !op.equals("")) {
         return op;
      }

      op = XTrinaryCondition.getOpSymbol(opname);

      if(op != null && !op.equals("")) {
         return op;
      }

      throw new RuntimeException("Unsupported operation name found: " + opname);
   }

   /**
    * Get the operator name.
    */
   public static String getOpName(String symbol) {
      String opname = XUnaryCondition.getOpName(symbol);

      if(opname != null && !opname.equals("")) {
         return opname;
      }

      opname = XBinaryCondition.getOpName(symbol);

      if(opname != null && !opname.equals("")) {
         return opname;
      }

      opname = XTrinaryCondition.getOpName(symbol);

      if(opname != null && !opname.equals("")) {
         return opname;
      }

      throw new RuntimeException("Unsupported operation found: " + symbol);
   }

   public XExpression getExpression1() {
      return null;
   }

   /**
    * Create a condition node.
    */
   public static XFilterNode createConditionNode(String op) {
      if(XUnaryCondition.isOperator(op)) {
         return new XUnaryCondition(new XExpression(), op);
      }
      else if(XBinaryCondition.isOperator(op)) {
         return new XBinaryCondition(new XExpression(), new XExpression(), op);
      }
      else if(XTrinaryCondition.isOperator(op)) {
         return new XTrinaryCondition(new XExpression(), new XExpression(),
            new XExpression(), op);
      }
      else {
         return null;
      }
   }

   /**
    * Create a condition node.
    */
   public static XFilterNode createConditionNode(Element elem) throws Exception
   {
      XFilterNode node;

      if(elem.getTagName().equals(XSet.XML_TAG)) {
         node = new XSet();
      }
      else if(elem.getTagName().equals(XJoin.XML_TAG)) {
         node = new XJoin();
      }
      else if(elem.getTagName().equals(XUnaryCondition.XML_TAG)) {
         node = new XUnaryCondition();
      }
      else if(elem.getTagName().equals(XBinaryCondition.XML_TAG)) {
         node = new XBinaryCondition();
      }
      else if(elem.getTagName().equals(XTrinaryCondition.XML_TAG)) {
         node = new XTrinaryCondition();
      }
      else {
         throw new RuntimeException("Unsupported element found: " + elem);
      }

      node.parseXML(elem);

      return node;
   }

   /**
    * Construct a XFilterNode with default setting. The clause will
    * be WHERE and the isNot will be false by default.
    */
   public XFilterNode() {
      super();
   }

   /**
    * Construct a XFilterNode with node name.
    * @param name - node name
    */
   public XFilterNode(String name) {
      this(name, "");
   }

   /**
    * Construce a XFilterNode with name and clause.
    * @param name - node name
    * @param clause - clause type should be WHERE or HAVING
    */
   public XFilterNode(String name, String clause) {
      super(name);
      this.clause = clause;
   }

   /**
    * Get the condition type, either WHERE or HAVING.
    */
   public String getClause() {
      return clause;
   }

   /**
    * Check if 'not' is specified.
    */
   public boolean isIsNot() {
      return isNot;
   }

   /**
    * Set the condition type.
    */
   public void setClause(String clause) {
      this.clause = clause;
   }

   /**
    * Set the 'not' operator.
    */
   public void setIsNot(boolean isNot) {
      this.isNot = isNot;
   }

   /**
    * Remove all joins from the condition tree.
    */
   public void removeAllJoins() {
      // @by jasons move logic to separate recursive method to avoid having to
      // have the same code applied to the current node as well as it's children
      removeJoinsRecursive(this);
   }

   /**
    * Recursive method used to remove joins from the node tree and remove or
    * collapse unneccessary junctions.
    *
    * @param node the node to process. This should be an XSet; other types of
    *             XFilterNodes won't generate an error, but will be ignored.
    */
   private void removeJoinsRecursive(XFilterNode node) {
      if(!(node instanceof XSet)) {
         return;
      }

      for(int i = node.getChildCount() - 1; i >= 0; i--) {
         XFilterNode child = (XFilterNode) node.getChild(i);

         if(child instanceof XJoin) {
            node.removeChild(i, false);
         }
         else if(child instanceof XSet) {
            removeJoinsRecursive(child);

            if(child.getChildCount() == 1) {
               // @by jasons remove junctions with only one operand
               node.insertChild(i, child.getChild(0));
               child.removeChild(0, false);
               node.removeChild(i + 1, false);
            }
            else if(((XSet) node).getRelation().equals(((XSet) child).
               getRelation()) &&
               // @by vincentx, 2004-09-15, fix bug1095061780997
               // do not collapse junction with NOT set.
               !((XSet) child).isIsNot()) {
               // @by jasons collapse duplicate junctions
               int j;

               for(j = i; child.getChildCount() > 0; j++) {
                  node.insertChild(j, child.getChild(0));
                  child.removeChild(0, false);
               }

               node.removeChild(j, false);
            }
         }
      }
   }

   /**
    * Allow duplicate node.
    */
   @Override
   protected XNode checkDuplicate(XNode child) {
      return child;
   }

   /**
    * Generate condition string.
    * @return condition string
    */
   public abstract String toString();

   /**
    * Parse the XML element that contains information on this node.
    */
   @Override
   public abstract void parseXML(Element node) throws Exception;

   /**
    * Write the node XML representation.
    * @param writer - PrintWriter
    */
   @Override
   public abstract void writeXML(PrintWriter writer);

   /**
    * Generate condition string with clause ahead.
    * @return return clause string
    */
   public String getClauseString() {
      return clause + " " + toString();
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Check whether this filter node is a valid node.
    */
   public boolean isValid() {
      return true;
   }

   private String clause = ""; // clause type should be WHERE or HAVING
   private boolean isNot = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(XFilterNode.class);
   private boolean group = false;
}

