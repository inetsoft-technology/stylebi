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

import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.Vector;

/**
 * The XBinaryCondition extends XFilterNode to store information of
 * binary condition of where clause and having clause.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XBinaryCondition extends XFilterNode {
   public static final String XML_TAG = "XBinaryCondition";
   /**
    * Check if the operator is a prefix operator.
    */
   public static boolean isPrefixOp(String op) {
      return prefixOpList.contains(op.toUpperCase());
   }

   /**
    * Check if the operator is a postfix operator.
    */
   public static boolean isPostfixOp(String op) {
      return postfixOpList.contains(op.toUpperCase());
   }

   /**
    * Get a list of all binary condition operators.
    */
   public static String[] getAllOperators() {
      String[] ops = new String[opNameList.size()];

      opNameList.copyInto(ops);
      return ops;
   }

   /**
    * Check if the operator is a binary condition operator.
    * @param op the name or symbol of a operator.
    */
   public static boolean isOperator(String op) {
      return opNameList.contains(op) || opList.contains(op);
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
    * Create an empty condition.
    */
   public XBinaryCondition() {
      super();
   }

   /**
    * Create a binary condition from two expressions and operation.
    */
   public XBinaryCondition(XExpression expression1, XExpression expression2, String op) {
      setExpression1(expression1);
      setExpression2(expression2);
      setOp(op);
   }

   public String toString() {
      String str;

      if(isPrefixOp(op)) {
         str = (op != null ? op.toString() : "") + " " +
            (expression1 != null ? expression1.toString() : "") + " " +
            (expression2 != null ? expression2.toString() : "");
      }
      else if(isPostfixOp(op)) {
         str = (expression1 != null ? expression1.toString() : "") + " " +
            (expression2 != null ? expression2.toString() : "") + " " +
            (op != null ? op.toString() : "");
      }
      else {
         str = (expression1 != null ? expression1.toString() : "") + " " +
            (op != null ? op.toString() : "") + " " +
            (expression2 != null ? expression2.toString() : "");
      }

      if(isIsNot()) {
         str = "not " + str;
      }

      return str;
   }

   /**
    * Parse condition XML definition.
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

      String cnullstr = node.getAttribute("containsNull");

      if(cnullstr != null && cnullstr.length() > 0) {
         setAttribute("containsNull", cnullstr);
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

      nlist = Tool.getChildNodesByTagName(node, "expression2");

      if(nlist != null && nlist.getLength() == 1) {
         NodeList expList = Tool.getChildNodesByTagName((Element) nlist.item(0),
            XExpression.XML_TAG);

         if(expList != null && expList.getLength() == 1) {
            XExpression exp2 = new XExpression();

            exp2.parseXML((Element) expList.item(0));
            this.setExpression2(exp2);
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "op");

      if(nlist != null && nlist.getLength() == 1) {
         this.setOp(Tool.getValue(((Element) nlist.item(0))));
      }
   }

   String getTag() {
      return XML_TAG;
   }

   /**
    * Write XML definition.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<");
      writer.print(getTag());
      writer.print(" isnot=" +
         ((isIsNot() == true) ? "\"true\"" : "\"false\""));
      writer.print(" clause=" + "\"" + getClause() + "\"");
      boolean cnull = "true".equals(getAttribute("containsNull"));
      writer.print(" containsNull=" + "\"" + cnull + "\"");
      writer.println(">");

      writer.println("<expression1>");
      expression1.writeXML(writer);
      writer.println("</expression1>");

      writer.println("<expression2>");
      expression2.writeXML(writer);
      writer.println("</expression2>");

      writer.print("<op>");
      writer.print("<![CDATA[" + op + "]]>");
      writer.println("</op>");

      writer.println("</" + getTag() + ">");
   }

   /**
    * Get the left-side expression.
    */
   @Override
   public XExpression getExpression1() {
      return expression1;
   }

   /**
    * Get the right-side expression.
    */
   public XExpression getExpression2() {
      return expression2;
   }

   /**
    * Get condition operators.
    */
   public String getOp() {
      return op;
   }

   /**
    * Check whether this filter node is a valid node.
    */
   @Override
   public boolean isValid() {
      return expression1 != null && !expression1.getValue().equals("") &&
         expression2 != null && !expression2.getValue().equals("");
   }

   /**
    * Set left-side expression.
    */
   public void setExpression1(XExpression expression1) {
      this.expression1 = expression1;
   }

   /**
    * Set right-side expression.
    */
   public void setExpression2(XExpression expression2) {
      this.expression2 = expression2;
   }

   /**
    * Set the condition operator.
    */
   public void setOp(String op) {
      int idx = opNameList.indexOf(op);
      this.op = idx < 0 ? op : (String) opList.elementAt(idx);
   }

   @Override
   public Object clone() {
      try {
         XBinaryCondition condition = (XBinaryCondition) super.clone();

         condition.expression1 = (XExpression) expression1.clone();
         condition.expression2 = (XExpression) expression2.clone();
         return condition;
      }
      catch(Exception e) {
         return null;
      }
   }

   public boolean equals(Object obj) {
      try {
         XBinaryCondition cond = (XBinaryCondition) obj;

         return op.equals(cond.op) && expression1.equals(cond.expression1) &&
            expression2.equals(cond.expression2) && isIsNot() == cond.isIsNot();
      }
      catch(Exception ex) {
         LOG.error("Failed to determine if conditions are equal: " +
            this + ", " + obj, ex);
         return false;
      }
   }

   private XExpression expression1, expression2;
   private String op;
   private static Vector opList = new Vector();
   private static Vector opNameList = new Vector();
   private static Vector postfixOpList = new Vector();
   private static Vector prefixOpList = new Vector();
   static {
      opNameList.addElement(Catalog.getCatalog().getString("equal to"));
      opList.addElement("=");
      opNameList.addElement(Catalog.getCatalog().getString("greater than"));
      opList.addElement(">");
      opNameList.addElement(Catalog.getCatalog().getString("less than"));
      opList.addElement("<");
      opNameList.addElement(Catalog.getCatalog().getString(
         "greater than/equal to"));
      opList.addElement(">=");
      opNameList.addElement(Catalog.getCatalog().getString(
         "common.condition.lessEqual"));
      opList.addElement("<=");
      opNameList.addElement(Catalog.getCatalog().getString("not equal to"));
      opList.addElement("<>");
      opNameList.addElement(Catalog.getCatalog().getString("like"));
      opList.addElement("LIKE");
      opNameList.addElement(Catalog.getCatalog().getString("match"));
      opList.addElement("MATCH");
      opNameList.addElement(Catalog.getCatalog().getString("in"));
      opList.addElement("IN");
      opNameList.addElement(Catalog.getCatalog().getString("one of"));
      opList.addElement("IN");
   }
   static {
      prefixOpList.add("FOR ALL");
      prefixOpList.add("FOR ANY");
      prefixOpList.add("FOR SOME");
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(XBinaryCondition.class);
}
