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
package inetsoft.uql.jdbc;

import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.Vector;

/**
 * The XUnaryCondition extends XFilterNode to store information of
 * unary condition of where clause and having clause
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XUnaryCondition extends XFilterNode {
   public static final String XML_TAG = "XUnaryCondition";
   /**
    * Get the name list of all unary condition operators.
    */
   public static String[] getAllOperators() {
      String[] ops = new String[opNameList.size()];

      opNameList.copyInto(ops);
      return ops;
   }

   /**
    * Check if the operator is a unary condition operator.
    * @param op the name or symbol of a operator.
    */
   public static boolean isOperator(String op) {
      return opNameList.contains(op) || opList.contains(op);
   }

   /**
    * Check if the operator is a prefix operator.
    */
   public static boolean isPrefixOp(String op) {
      return prefixionOpList.contains(op.toUpperCase());
   }

   /**
    * Get the operator symbol by its name.
    */
   public static String getOpSymbol(String opname) {
      int idx = opNameList.indexOf(opname);

      if(idx < 0) {
         return "";
      }

      return (String) opList.elementAt(idx);
   }

   /**
    * Get the operator name.
    */
   public static String getOpName(String symbol) {
      int idx = opList.indexOf(symbol.toUpperCase());

      if(idx < 0) {
         return "";
      }

      return (String) opNameList.elementAt(idx);
   }

   /**
    * Create a default condition.
    */
   public XUnaryCondition() {
   }

   /**
    * Create a unary condition.
    */
   public XUnaryCondition(XExpression expression1, String op) {
      this();
      this.expression1 = expression1;
      setOp(op);
   }

   /**
    * Convert condition to its string representation.
    */
   public String toString() {
      String str;

      if(isPrefixOp(op)) {
         str = op.toString() + " " +
            (expression1 != null ? expression1.toString() : "");
      }
      else {
         str = (expression1 != null ? expression1.toString() : "") + " " +
            op.toString();
      }

      if(isIsNot()) {
         str = "not " + str;
      }

      return str;
   }

   /**
    * Parse condition definition.
    */
   @Override
   public void parseXML(Element node) throws Exception {
      String isnotStr = node.getAttribute("isnot");

      if(isnotStr.equals("false")) {
         this.setIsNot(false);
      }
      else {
         this.setIsNot(true);
      }

      this.setClause(getClause());

      NodeList nlist;

      nlist = Tool.getChildNodesByTagName(node, "expression1");

      if(nlist != null && nlist.getLength() == 1) {
         NodeList expList = Tool.getChildNodesByTagName((Element) nlist.item(0),
            XExpression.XML_TAG);

         if(expList != null && expList.getLength() == 1) {
            XExpression exp1 = new XExpression();

            exp1.parseXML((Element) expList.item(0));
            this.setExpression1(exp1);
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "op");
      if(nlist != null && nlist.getLength() == 1) {
         this.setOp(Tool.getValue(((Element) nlist.item(0))));
      }
   }

   /**
    * Write condition definition.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<");
      writer.print(XML_TAG);
      writer.print(" isnot=" + (isIsNot() == true ? "\"true\"" : "\"false\""));
      writer.print(" clause= " + "\"" + getClause() + "\"");
      writer.println(">");

      writer.println("<expression1>");
      expression1.writeXML(writer);
      writer.println("</expression1>");

      writer.print("<op>");
      writer.print("<![CDATA[" + op + "]]>");
      writer.println("</op>");

      writer.println("</" + XML_TAG + ">");
   }

   /**
    * Get the expression value.
    */
   @Override
   public XExpression getExpression1() {
      return expression1;
   }

   /**
    * Get condition operator.
    */
   public String getOp() {
      return op;
   }

   /**
    * Set condition expression.
    */
   public void setExpression1(XExpression expression1) {
      this.expression1 = expression1;
   }

   /**
    * Set condition operator.
    */
   public void setOp(String op) {
      int idx = opNameList.indexOf(op);

      this.op = idx < 0 ? op : (String) opList.elementAt(idx);
   }

   @Override
   public Object clone() {
      try {
         XUnaryCondition node = (XUnaryCondition) super.clone();

         if(expression1 != null) {
            node.expression1 = (XExpression) expression1.clone();
         }

         return node;
      }
      catch(Exception e) {
         LOG.error("Failed to clone object", e);
         return null;
      }
   }

   public boolean equals(Object obj) {
      try {
         XUnaryCondition cond = (XUnaryCondition) obj;

         return op.equals(cond.op) && expression1.equals(cond.expression1) &&
            isIsNot() == cond.isIsNot();
      }
      catch(Exception ex) {
         return false;
      }
   }

   private XExpression expression1;
   private String op = "";
   // static var
   private static Vector opList = new Vector();
   private static Vector opNameList = new Vector();
   private static Vector prefixionOpList = new Vector();

   static {
      opNameList.addElement(Catalog.getCatalog().getString("exists"));
      opList.addElement("EXISTS");
      opNameList.addElement(Catalog.getCatalog().getString("null"));
      opList.addElement("IS NULL");
      opNameList.addElement(Catalog.getCatalog().getString("unique"));
      opList.addElement("UNIQUE");
   }

   static {
      prefixionOpList.add("EXISTS");
      prefixionOpList.add("UNIQUE");
      prefixionOpList.add("SUM");
      prefixionOpList.add("AVERAGE");
      prefixionOpList.add("COUNT");
      prefixionOpList.add("MAX");
      prefixionOpList.add("MIN");
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(XUnaryCondition.class);
}

