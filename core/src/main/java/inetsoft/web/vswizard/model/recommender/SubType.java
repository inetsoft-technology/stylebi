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
package inetsoft.web.vswizard.model.recommender;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
              include = JsonTypeInfo.As.PROPERTY,
              property = "classType")
@JsonSubTypes({
   @JsonSubTypes.Type(value = VSSubType.class, name = "VSSubType"),
   @JsonSubTypes.Type(value = ChartSubType.class, name = "ChartSubType"),
})
public interface SubType extends Serializable, XMLSerializable {
   /**
    * Setter for type.
    */
   void setType(String type);

   /**
    * Getter for type.
    */
   String getType();

   /**
    * Setter for selected.
    */
   void setSelected(boolean selected);

   /**
    * Getter for selected.
    */
   boolean isSelected();

   @Override
   default void writeXML(PrintWriter writer) {
      writer.print("<SubType class=\"" + getClass().getName() + "\"");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</SubType>");
   }

   default void writeAttributes(PrintWriter writer) {
      if(getType() != null) {
         writer.print(" type=\"" + getType() + "\"");
      }

      writer.print(" selected=\"" + isSelected() + "\"");
   }

   default void writeContents(PrintWriter writer) {
      // do nothing
   }

   @Override
   default void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   default void parseAttributes(Element elem) {
      setType(Tool.getAttribute(elem, "type"));
      setSelected("true".equalsIgnoreCase(Tool.getAttribute(elem, "selected")));
   }

   default void parseContents(Element elem) throws Exception {
      // do nothing
   }

   public static SubType createSubType(Element elem)
      throws Exception
   {
      String cls = Tool.getAttribute(elem, "class");
      SubType subType = null;

      try {
         subType = (SubType) Class.forName(cls).newInstance();
         subType.parseXML(elem);
      }
      catch(InstantiationException ex) {
         throw new RuntimeException("Failed to create subtype for class " + cls, ex);
      }

      return subType;
   }
}
