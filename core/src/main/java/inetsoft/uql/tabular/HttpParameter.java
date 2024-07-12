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
package inetsoft.uql.tabular;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.Objects;

public class HttpParameter implements Serializable, XMLSerializable, Cloneable {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getValue() {
      return value;
   }

   public void setValue(String value) {
      this.value = value;
   }

   public ParameterType getType() {
      return type;
   }

   public void setType(ParameterType type) {
      this.type = type;
   }

   @Override
   public HttpParameter clone() {
      try {
         return (HttpParameter) super.clone();
      }
      catch(CloneNotSupportedException ex) {
         LOG.error("Failed to clone HttpParameter", ex);
      }

      return null;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      if(name == null && value == null) {
         return;
      }

      writer.print("<httpParameter class=\"" + getClass().getName() + "\">");

      if(name != null) {
         writer.format("<name><![CDATA[%s]]></name>", name);
      }

      if(value != null) {
         writer.format("<value><![CDATA[%s]]></value>", value);
      }

      writer.format("<type><![CDATA[%s]]></type>", type);

      writer.println("</httpParameter>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      name = Tool.getValue(Tool.getChildNodeByTagName(tag, "name"));
      value = Tool.getValue(Tool.getChildNodeByTagName(tag, "value"));
      type = ParameterType.valueOf(Tool.getValue(Tool.getChildNodeByTagName(tag, "type")));
   }

   @Override
   public String toString() {
      return "HttpParameter{" +
         "name='" + name + '\'' +
         ", value='" + value + '\'' +
         ", type=" + type +
         '}';
   }

   @Override
   public boolean equals(Object obj) {
      try {
         HttpParameter param = (HttpParameter) obj;
         return Objects.equals(name, param.name) &&
            Objects.equals(value, param.value) &&
            Objects.equals(type, param.type);
      }
      catch(Exception ex) {
         return false;
      }
   }

   public static Builder builder() {
      return new Builder();
   }

   public enum ParameterType {
      HEADER, QUERY
   }

   private String name;
   private String value;
   private ParameterType type;

   public static final class Builder {
      public Builder name(String name) {
         this.name = name;
         return this;
      }

      public Builder value(String value) {
         this.value = value;
         return this;
      }

      public Builder type(ParameterType type) {
         this.type = type;
         return this;
      }

      public HttpParameter build() {
         HttpParameter parameter = new HttpParameter();
         parameter.setName(name);
         parameter.setValue(value);
         parameter.setType(type);
         return parameter;
      }

      private String name;
      private String value;
      private ParameterType type;
   }

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
