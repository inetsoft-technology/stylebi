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

import inetsoft.uql.XNode;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.util.Map;

/**
 * Enum type node.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class EnumType extends StringType {
   /**
    * Create a Enum type node.
    */
   public EnumType() {
   }

   /**
    * Create a Enum type node.
    */
   public EnumType(String name) {
      super(name);
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   @Override
   public String getType() {
      return XSchema.ENUM;
   }

   /**
    * Return true if this is a primitive type.
    */
   @Override
   public boolean isPrimitive() {
      return true;
   }

   /**
    * Create a value tree corresponding to the data type defined
    * by this type.
    */
   @Override
   public XNode newInstance() {
      XNode node = new EnumValue();

      node.setName(getName());
      return node;
   }

   public String[] getEnums() {
      return enums;
   }

   public void setEnums(String[] enums) {
      this.enums = enums;
   }

   /**
    * Parse the XML element that contains information on this
    * data source.
    */
   @Override
   public void parseXML(Element root) {
      super.parseXML(root);

      String str = Tool.getAttribute(root, "enum");

      if(str != null) {
         enums = Tool.split(str, ',');
      }
   }

   /**
    * Get additional attributes string.
    */
   @Override
   protected void writeAdditionalAttributes(Map<String, Object> properties) {
      StringBuilder result = new StringBuilder();

      for(int i = 0; i < enums.length; i++) {
         if(i > 0) {
            result.append(",");
         }

         result.append(enums[i]);
      }

      properties.put("enum", result.toString());
   }

   /**
    * Get additional attributes string.
    */
   @Override
   protected String getAttributeString() {
      StringBuilder buf = new StringBuilder();

      buf.append(" enum=\"");
      for(int i = 0; i < enums.length; i++) {
         if(i > 0) {
            buf.append(",");
         }

         buf.append(enums[i]);
      }

      buf.append("\"");

      return buf.toString();
   }

   String[] enums = {
   };
}

