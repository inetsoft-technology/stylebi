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
package inetsoft.uql.jdbc;

import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.Vector;

/**
 * The XTrinaryCondition extends XFilterNode to store information of
 * trinary condition of where clause and having clause.
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XTrinaryCondition extends XFilterNode {
   public static final String XML_TAG = "XTrinaryCondition";
   /**
    * Get the name list of all trinary condition operators.
    */
   public static String[] getAllOperators() {
      String[] ops = new String[opNameList.size()];

      opNameList.copyInto(ops);
      return ops;
   }

   /**
    * Check if the operator is a trinary condition operator.
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
    * Create a default condition.
    */
   public XTrinaryCondition() {
   }

   /**
    * Create a trinary condition, e.g. between.
    */
   public XTrinaryCondition(XExpression expression1,
      XExpression expression2,
      XExpression expression3,
      String op) {
      this.expression1 = expression1;
      this.expression2 = expression2;
      this.expression3 = expression3;
      setOp(op);
   }

   /**
    * Convert condition to string format.
    */
   public String toString() {
      String str;

      str = (expression1 != null ? expression1.toString() : "") + " " +
         (op != null ? op.toString() : "") + " " +
         (expression2 != null ? expression2.toString() : "") + " and " +
         (expression3 != null ? expression3.toString() : "");

      if(isIsNot()) {
         str = "not " + str;
      }

      return str;
   }

   /**
    * Parse the XML element that contains information on this node.
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

      nlist = Tool.getChildNodesByTagName(node, "expression3");

      if(nlist != null && nlist.getLength() == 1) {
         NodeList expList = Tool.getChildNodesByTagName((Element) nlist.item(0),
            XExpression.XML_TAG);

         if(expList != null && expList.getLength() == 1) {
            XExpression exp3 = new XExpression();

            exp3.parseXML((Element) expList.item(0));
            this.setExpression3(exp3);
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "op");

      if(nlist != null && nlist.getLength() == 1) {
         this.setOp(Tool.getValue(((Element) nlist.item(0))));
      }
   }

   /**
    * Write the node XML representation.
    * @param writer - PrintWriter
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<");
      writer.print(XML_TAG);
      writer.print(" isnot=" + (isIsNot() ? "\"true\"" : "\"false\""));
      writer.print(" clause= " + "\"" + getClause() + "\"");
      writer.println(">");

      writer.println("<expression1>");
      expression1.writeXML(writer);
      writer.println("</expression1>");

      writer.println("<expression2>");
      expression2.writeXML(writer);
      writer.println("</expression2>");

      writer.println("<expression3>");
      expression3.writeXML(writer);
      writer.println("</expression3>");

      writer.print("<op>");
      writer.print("<![CDATA[" + op + "]]>");
      writer.println("</op>");

      writer.println("</" + XML_TAG + ">");
   }

   /**
    * Get the left-side expression.
    */
   @Override
   public XExpression getExpression1() {
      return expression1;
   }

   /**
    * Get the second (first in the pair) expression.
    */
   public XExpression getExpression2() {
      return expression2;
   }

   /**
    * Get the third (second in the pair) expression.
    */
   public XExpression getExpression3() {
      return expression3;
   }

   /**
    * Get condition operator.
    */
   public String getOp() {
      return op;
   }

   /**
    * Set the left-side expression in the condition.
    */
   public void setExpression1(XExpression expression1) {
      this.expression1 = expression1;
   }

   /**
    * Set the second expression in the condition.
    */
   public void setExpression2(XExpression expression2) {
      this.expression2 = expression2;
   }

   /**
    * Set the third expression in the condition.
    */
   public void setExpression3(XExpression expression3) {
      this.expression3 = expression3;
   }

   /**
    * Set condition operator.
    */
   public void setOp(String op) {
      int idx = opNameList.indexOf(op);

      this.op = idx < 0 ? op : (String) opList.elementAt(idx);
   }

   /**
    * Check whether this filter node is a valid node.
    */
   @Override
   public boolean isValid() {
      return expression1 != null && !expression1.getValue().equals("") &&
         expression2 != null && !expression2.getValue().equals("") &&
         expression3 != null && !expression3.getValue().equals("");         
   }
   
   @Override
   public Object clone() {
      try {
         XTrinaryCondition node = (XTrinaryCondition) super.clone();

         node.expression1 = (XExpression) expression1.clone();
         node.expression2 = (XExpression) expression2.clone();
         node.expression3 = (XExpression) expression3.clone();
         return node;
      }
      catch(Exception e) {
         return null;
      }
   }

   public boolean equals(Object obj) {
      try {
         XTrinaryCondition cond = (XTrinaryCondition) obj;

         return op.equals(cond.op) && expression1.equals(cond.expression1) &&
            expression2.equals(cond.expression2) &&
            expression3.equals(cond.expression3) && isIsNot() == cond.isIsNot();
      }
      catch(Exception ex) {
         return false;
      }
   }

   private XExpression expression1, expression2, expression3;
   private String op;
   private static Vector opList = new Vector();
   private static Vector opNameList = new Vector();

   static {
      opNameList.addElement(Catalog.getCatalog().getString("between"));
      opList.addElement("BETWEEN");
   }
}

