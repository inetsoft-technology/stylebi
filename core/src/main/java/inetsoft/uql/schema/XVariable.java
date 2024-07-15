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
package inetsoft.uql.schema;

import inetsoft.uql.VariableTable;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Map;

/**
 * A variable is a named expression. The name of a variable must be
 * unique in a query. The variable can be either based on the result
 * of a query, or entered by end users at runtime.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class XVariable implements Serializable, Cloneable, XMLSerializable {
   /**
    * Parse a variable definition and create an instance of the variable.
    */
   public static XVariable parse(Element root) {
      String type = Tool.getAttribute(root, "type");
      XVariable var;

      if(type != null && type.equals("query")) {
         var = new QueryVariable();
      }
      else {
         var = new UserVariable();
      }

      try {
         var.parseXML(root);
      }
      // avoid query to disappear in case there is an error parsing var
      catch(Exception ex) {
         LOG.error("Failed to parse variable: " + var, ex);
      }

      return var;
   }

   /**
    * Get the variable name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the variable name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Set the source this variable belongs to. The source is either a query
    * or a data source name.
    * @param source source name.
    */
   public void setSource(String source) {
      this.source = source;
   }

   /**
    * Get the source name of this variable.
    */
   public String getSource() {
      return source;
   }

   /**
    * Write attributes.
    * @param writer the specified print writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" name=\"" + Tool.escape(name) + "\"");

      if(source != null) {
         writer.print(" source=\"" + Tool.escape(source) + "\"");
      }
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) throws Exception {
      name = Tool.getAttribute(elem, "name");
      source = Tool.getAttribute(elem, "source");
   }

   protected void writeDataAttributes(Map<String, Object> map) {
      map.put("name", name);

      if(source != null) {
         map.put("source", source);
      }
   }

   protected void readDataAttributes(Map<String, Object> map) {
      name = (String) map.get("name");
      source = (String) map.get("source");
   }

   /**
    * Evaluate the XVariable.
    */
   public abstract Object evaluate(VariableTable vars);

   /**
    * Returns a clone of this object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(CloneNotSupportedException e) {
         LOG.error("Failed to clone object", e);
      }

      return null;
   }

   private String name;
   private String source;

   private static final Logger LOG = LoggerFactory.getLogger(XVariable.class);
}
