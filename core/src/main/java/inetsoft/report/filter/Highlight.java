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
package inetsoft.report.filter;

import inetsoft.uql.ConditionList;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.XMLSerializable;

import java.awt.*;
import java.io.PrintWriter;

/**
 * HighLight interface.
 * This class defines the common API of the highlight attribute.
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface Highlight extends java.io.Serializable, XMLSerializable, Cloneable {

   /**
    * Text highlight attribute
    */
   String TEXT = "text";
   /**
    * Table highlight attribute
    */
   String TABLE = "table";

   /**
    * Set the font.
    * @param f Font value;
    */
   void setFont(Font f);

   /**
    * Set the foreground.
    * @param foreGround color value
    */
   void setForeground(Color foreGround);

   /**
    * Set the background.
    * @param backGround color value
    */
   void setBackground(Color backGround);

   /**
    * Set the name value.
    * @param name string value
    */
   void setName(String name);

   /**
    * Set the condition value.
    * @param con Vector value
    */
   void setConditionGroup(ConditionList con);

   /**
    * Get the font.
    */
   Font getFont();

   /**
    * Get the foreground.
    */
   Color getForeground();

   /**
    * Get the background.
    */
   Color getBackground();

   /**
    * Get the name value.
    */
   String getName();

   /**
    * Check if the highlight is empty.
    */
   boolean isEmpty();

   /**
    * Check if the Condition vector is empty.
    */
   boolean isConditionEmpty();

   /**
    * Clear the condition.
    */
   void removeAllConditions();

   /**
    * Get the condition group.
    */
   ConditionList getConditionGroup();

   /**
    * Writer a group of hightlight condition attributes to XML.
    */
   @Override
   void writeXML(PrintWriter writer);

   /**
    * Make a copy of this object.
    */
   Highlight clone();

   /**
    * Get all variable from condition
    * @return user variable array
    */
   UserVariable[] getAllVariables();

   /**
    * Replace variable with value user inputed
    * @param vart variable table
    */
   void replaceVariables(VariableTable vart);
}

