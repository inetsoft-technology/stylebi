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
package inetsoft.report.internal.binding;

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.XMLSerializable;

/**
 * Field represents a column set bind to a table, it can be a normal field,
 * a formula field, or a composite field which stores multiple fields.
 *
 * @version 6.0
 * @author InetSoft Technology Corp
 */
public interface Field extends DataRef, XMLSerializable {
   /**
    * Get the base field if this is a wrapper, or return self otherwise.
    */
   Field getField();

   /**
    * Set the visibility of the field.
    *
    * @param visible true if is visible, false otherwise
    */
   void setVisible(boolean visible);

   /**
    * Check the visibility of the field.
    *
    * @return true if is visible, false otherwise
    */
   boolean isVisible();

   /**
    * Set the sorting order of the field.
    *
    * @param order the specified sorting order defined in StyleConstants
    */
   void setOrder(int order);

   /**
    * Get the sorting order of the field.
    *
    * @return the sorting order defined in StyleConstants
    */
   int getOrder();

   /**
    * Set the data type of the field.
    * @param type the specified data type defined in XSchema
    */
   void setDataType(String type);

   /**
    * Get the data type of the field.
    * @return the data type defined in XSchema
    */
   @Override
   String getDataType();

   /**
    * Get the type node presentation of this field.
    */
   @Override
   XTypeNode getTypeNode();

   /**
    * Check if is date type.
    *
    * @return true if is, false otherwise
    */
   boolean isDate();

   /**
    * Check if is empty.
    *
    * @return true if is, false otherwise
    */
   @Override
   boolean isEmpty();

   /**
    * Set whether the field is processed in query generator.
    */
   void setProcessed(boolean processed);

   /**
    * Check if the field is processed in query generator.
    */
   boolean isProcessed();

   /**
    * Create an embedded field.
    * @return the created embedded field.
    */
   Field createEmbeddedField();

   /**
    * Check if is group field.
    */
   boolean isGroupField();

   /**
    * Set whether is a group field.
    */
   void setGroupField(boolean gfld);
}
