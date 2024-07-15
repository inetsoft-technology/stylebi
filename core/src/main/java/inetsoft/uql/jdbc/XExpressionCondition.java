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

import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * XExpressionCondition, extends XFilterNode to store information of
 * expression condition of where clause and having clause. It's useful
 * when applying vpm conditions.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class XExpressionCondition extends XFilterNode {
   /**
    * The xml tag constant.
    */
   public static final String XML_TAG = "XExpressionCondition";

   /**
    * Constructor.
    */
   public XExpressionCondition() {
      super();
   }

   /**
    * Constructor.
    */
   public XExpressionCondition(XExpression exp) {
      this();

      setExpression(exp);
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return exp == null ? "" : exp.toString();
   }

   /**
    * Method to parse an xml segment.
    * @param node the specified xml element.
    */
   @Override
   public void parseXML(Element node) throws Exception {
      Element enode = Tool.getChildNodeByTagName(node, "expression");

      if(enode != null) {
         enode = Tool.getFirstChildNode(enode);
         XExpression exp = new XExpression();
         exp.parseXML(enode);
         setExpression(exp);
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<" + XML_TAG + ">");

      if(exp != null) {
         writer.println("<expression>");
         exp.writeXML(writer);
         writer.println("</expression>");
      }

      writer.println("</" + XML_TAG + ">");
   }

   /**
    * Get the condition expression.
    * @return the condition expression.
    */
   public XExpression getExpression() {
      return exp;
   }

   /**
    * Set the condition expression.
    * @param exp the specified expression.
    */
   public void setExpression(XExpression exp) {
      this.exp = exp;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         XExpressionCondition cond = (XExpressionCondition) super.clone();

         if(exp != null) {
            cond.exp = (XExpression) exp.clone();
         }

         return cond;
      }
      catch(Exception e) {
         LOG.error("Failed to clone object", e);
      }

      return null;
   }

   /**
    * Check if equals to another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof XExpressionCondition)) {
         return false;
      }

      XExpressionCondition cond = (XExpressionCondition) obj;
      return Tool.equals(exp, cond.exp);
   }

   private XExpression exp;

   private static final Logger LOG =
      LoggerFactory.getLogger(XExpressionCondition.class);
}
