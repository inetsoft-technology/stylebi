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
package inetsoft.uql.erm;

import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Specialization of ExpressionRef that is used internally to reference
 * ExpressionAttribute objects.
 *
 * @author InetSoft Technology
 * @since  8.0
 */
public class ExprAttributeRef extends ExpressionRef {
   /**
    * Creates an empty attribute reference.
    */
   public ExprAttributeRef() {
      super();
   }

   /**
    * Creates a new instance of ExprAttributeRef.
    *
    * @param name the name of the attribute.
    */
   public ExprAttributeRef(String name) {
      super(null, name);
   }

   /**
    * Creates a new instance of ExprAttributeRef.
    *
    * @param name the name of the attribute.
    * @param expr the SQL expression.
    */
   public ExprAttributeRef(String name, String expr) {
      super(null, name);
      setExpression(expr);
   }

   /**
    * Creates a new instance of ExprAttributeRef.
    *
    * @param attr the expression attribute to be referenced.
    */
   public ExprAttributeRef(ExpressionAttribute attr) {
      super(null, attr.getName());
      setExpression(attr.getExpression());
      setDataType(attr.getDataType());
   }

   /**
    * Set the data type of the field.
    *
    * @param dataType the specified data type defined in XSchema
    */
   @Override
   public void setDataType(String dataType) {
      this.dataType = dataType;
   }

   /**
    * Get the data type of the field.
    *
    * @return the data type defined in XSchema
    */
   @Override
   public String getDataType() {
      return dataType;
   }

   /**
    * Check if this expression is sql expression.
    *
    * @return true if is, false otherwise
    */
   @Override
   public boolean isSQL() {
      return true;
   }

   /**
    * Write an XML element representation of this object.
    *
    * @param writer the output stream to which to write the XML data
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print("dataType=\"" + getDataType() + "\" ");
   }

   /**
    * Read in the definition of this object from an XML tag.
    *
    * @param tag the XML Element representing this object
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      String value = Tool.getAttribute(tag, "dataType");

      if(value != null) {
         setDataType(value);
      }
   }

   private String dataType = XSchema.STRING;
}
