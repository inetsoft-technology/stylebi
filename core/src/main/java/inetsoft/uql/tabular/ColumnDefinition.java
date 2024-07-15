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
package inetsoft.uql.tabular;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Class that encapsulates the definition of a tabular data source column.
 *
 * @since 12.2
 */
public class ColumnDefinition implements XMLSerializable, Cloneable {
   /**
    * Gets the name of the column.
    *
    * @return the name.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the name of the column.
    *
    * @param name the name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Gets the user-defined alias of the column.
    *
    * @return the alias.
    */
   public String getAlias() {
      return alias;
   }

   /**
    * Sets the user-defined alias of the column.
    *
    * @param alias the alias.
    */
   public void setAlias(String alias) {
      this.alias = alias;
   }

   /**
    * Gets the data type of the column.
    *
    * @return the data type.
    */
   public DataType getType() {
      return type;
   }

   /**
    * Sets the data type of the column.
    *
    * @param type the data type.
    */
   public void setType(DataType type) {
      this.type = type;
   }

   /**
    * Gets the format pattern used to parse the column values.
    *
    * @return the format pattern.
    */
   public String getFormat() {
      return format;
   }

   /**
    * Sets the format pattern used to parse the column values.
    *
    * @param format the format pattern.
    */
   public void setFormat(String format) {
      this.format = format;
   }

   /**
    * Gets the flag that indicates if the column is included in the output.
    *
    * @return <tt>true</tt> if selected.
    */
   public boolean isSelected() {
      return selected;
   }

   /**
    * Sets the flag that indicates if the column is included in the output.
    *
    * @param selected <tt>true</tt> if selected.
    */
   public void setSelected(boolean selected) {
      this.selected = selected;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<column class=\"" + getClass().getName() + "\">");

      if(name != null) {
         writer.format("<name><![CDATA[%s]]></name>", name);
      }

      if(alias != null) {
         writer.format("<alias><![CDATA[%s]]></alias>", alias);
      }

      assert type != null;
      writer.format("<type>%s</type>", type.name());

      if(format != null) {
         writer.format("<format><![CDATA[%s]]></format>", format);
      }

      writer.format("<selected>%s</selected>", selected);
      writer.println("</column>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      Element element = Tool.getChildNodeByTagName(tag, "name");

      if(element != null) {
         name = Tool.getValue(element);
      }

      if((element = Tool.getChildNodeByTagName(tag, "alias")) != null) {
         alias = Tool.getValue(element);
      }

      element = Tool.getChildNodeByTagName(tag, "type");
      type = DataType.valueOf(Tool.getValue(element));

      if((element = Tool.getChildNodeByTagName(tag, "format")) != null) {
         format = Tool.getValue(element);
      }

      element = Tool.getChildNodeByTagName(tag, "selected");
      selected = "true".equals(Tool.getValue(element));
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }
      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ColumnDefinition column = (ColumnDefinition) o;

      if(selected != column.selected) {
         return false;
      }

      if(name != null ? !name.equals(column.name) : column.name != null) {
         return false;
      }

      if(alias != null ? !alias.equals(column.alias) : column.alias != null) {
         return false;
      }

      if(type != column.type) {
         return false;
      }

      return format != null ? format.equals(column.format) : column.format == null;
   }

   @Override
   public int hashCode() {
      int hash = 0;

      if(name != null) {
         hash += name.hashCode();
      }

      if(alias != null) {
         hash += alias.hashCode();
      }

      if(type != null) {
         hash += type.type().hashCode();
      }

      if(format != null) {
         hash += format.hashCode();
      }

      return hash;
   }

   private String name;
   private String alias;
   private DataType type;
   private String format;
   private boolean selected;

   private static final Logger LOG = LoggerFactory.getLogger(ColumnDefinition.class);
}
