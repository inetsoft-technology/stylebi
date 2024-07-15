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
package inetsoft.uql.tabular;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Query parameter
 *
 * @author InetSoft Technology Corp
 * @version 12.2
 */
@JsonDeserialize(using = QueryParameter.Deserializer.class)
public class QueryParameter implements XMLSerializable, Cloneable {
   /**
    * Gets the name of the parameter.
    *
    * @return the name
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the name of the parameter.
    *
    * @param name the name
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Gets the data type of the parameter.
    *
    * @return the data type.
    */
   public DataType getType() {
      return type;
   }

   /**
    * Sets the data type of the parameter.
    *
    * @param type the data type.
    */
   public void setType(DataType type) {
      this.type = type;
   }

   /**
    * Gets the value of the parameter.
    *
    * @return the value
    */
   public Object getValue() {
      return value;
   }

   /**
    * Sets the value of the parameter.
    *
    * @param value the value
    */
   public void setValue(Object value) {
      this.value = value;
   }

   /**
    * Gets the flag that determines whether the value is a variable
    *
    * @return <tt>true</tt> if variable; <tt>false</tt> otherwise.
    */
   public boolean isVariable() {
      return variable;
   }

   /**
    * Sets the flag that determines whether the value is a variable
    *
    * @param variable <tt>true</tt> if variable; <tt>false</tt> otherwise.
    */
   public void setVariable(boolean variable) {
      this.variable = variable;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<queryParameter class=\"" + getClass().getName() + "\">");
      writer.format("<name><![CDATA[%s]]></name>", name);
      writer.format("<type><![CDATA[%s]]></type>", type.type());
      writer.format("<variable><![CDATA[%s]]></variable>", variable);

      if(value != null) {
         writer.format("<value><![CDATA[%s]]></value>",
            Tool.getDataString(value, type.type()));
      }

      writer.println("</queryParameter>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      Element node = Tool.getChildNodeByTagName(tag, "name");
      name = Tool.getValue(node);

      node = Tool.getChildNodeByTagName(tag, "type");
      type = DataType.fromType(Tool.getValue(node));

      node = Tool.getChildNodeByTagName(tag, "variable");
      variable = Boolean.valueOf(Tool.getValue(node));

      node = Tool.getChildNodeByTagName(tag, "value");

      if(variable) {
         value = Tool.getValue(node);
      }
      else {
         value = Tool.getData(type.type(), Tool.getValue(node));
      }
   }

   @Override
   public Object clone() {
      try {
         QueryParameter parameter = (QueryParameter) super.clone();
         return parameter;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone QueryParameter", ex);
      }

      return null;
   }

   @Override
   public String toString() {
      return "QueryParameter{" +
         "name='" + name + '\'' +
         ", type=" + type +
         ", value=" + value +
         ", variable=" + variable +
         '}';
   }

   private String name;
   private DataType type;
   private Object value;
   private boolean variable;

   private static final Logger LOG =
      LoggerFactory.getLogger(QueryParameter.class);

   public static final class Deserializer extends StdDeserializer<QueryParameter> {
      public Deserializer() {
         super(QueryParameter.class);
      }

      @Override
      public QueryParameter deserialize(JsonParser parser,
         DeserializationContext context) throws IOException
      {
         QueryParameter parameter = new QueryParameter();
         JsonNode node = parser.getCodec().readTree(parser);
         JsonNode child;

         if((child = node.get("name")) != null) {
            parameter.setName(child.textValue());
         }

         if((child = node.get("type")) != null) {
            parameter.setType(
               child.textValue() != null ? DataType.valueOf(child.textValue()) : null);
         }

         if((child = node.get("variable")) != null) {
            parameter.setVariable(child.asBoolean());
         }

         if((child = node.get("value")) != null) {
            if(parameter.isVariable()) {
               parameter.setValue(child.textValue());
            }
            else {
               parameter
                  .setValue(Tool.getData(parameter.getType().type(), child.asText()));
            }
         }

         return parameter;
      }
   }
}
