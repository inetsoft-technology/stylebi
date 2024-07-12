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
package inetsoft.uql.asset;

import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Expression value.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class ExpressionValue implements AssetObject {
   /**
    * Constructor.
    */
   public ExpressionValue() {
      super();
   }

   /**
    * Get expression.
    * @return the expression.
    */
   public String getExpression() {
      return expression;
   }

   /**
    * Set expression.
    * @param expression the specific expression
    */
   public void setExpression(String expression) {
      this.expression = expression;
   }

   /**
    * Get expression type.
    * @return the expression type.
    */
   public String getType() {
      return type;
   }

   /**
    * Set expression type.
    * @param type the expression type.
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Write the xml.
    * @param writer the specified print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<expressionValue>");

      if(type != null) {
         writer.print("<type>");
         writer.print("<![CDATA[" + type + "]]>");
         writer.println("</type>");
      }

      if(expression != null) {
         writer.print("<expression>");
         writer.print("<![CDATA[" + expression + "]]>");
         writer.println("</expression>");
      }

      writer.println("</expressionValue>");
   }

   /**
    * Parse the xml.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      type = Tool.getChildValueByTagName(elem, "type");
      expression = Tool.getChildValueByTagName(elem, "expression");
   }

   /**
    * Check if equqls another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof ExpressionValue)) {
         return false;
      }

      ExpressionValue sub = (ExpressionValue) obj;

      if(Tool.equals(sub.expression, expression) &&
         Tool.equals(sub.type, type))
      {
         return true;
      }

      return false;
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "Expression[" + type + ":" + expression + "]";
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         ExpressionValue eval = (ExpressionValue) super.clone();
         eval.type = type;
         eval.expression = expression;
         return eval;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Reset the cached value.
    */
   public void reset() {
      type = null;
      expression = null;
   }

   public static final String SQL = "SQL";
   public static final String JAVASCRIPT = "Javascript";
   private String type = null;
   private String expression = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(ExpressionValue.class);
}
