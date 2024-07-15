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
package inetsoft.uql.asset;

import inetsoft.uql.erm.DataRef;

/**
 * Attached assembly, which might be attached to a data source,
 * column or data type.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface AttachedAssembly extends AssetObject {
   /**
    * Source attached.
    */
   int SOURCE_ATTACHED = 1;
   /**
    * Column attached.
    */
   int COLUMN_ATTACHED = 2 | SOURCE_ATTACHED;
   /**
    * Data type attached.
    */
   int DATA_TYPE_ATTACHED = 4;

   /**
    * Get the attached type.
    * @return the attached type.
    */
   int getAttachedType();

   /**
    * Set the attached type.
    * @param type the specified type.
    */
   void setAttachedType(int type);

   /**
    * Get the attached source.
    * @return the attached source.
    */
   SourceInfo getAttachedSource();

   /**
    * Set the attached source.
    * @param info the specified source.
    */
   void setAttachedSource(SourceInfo info);

   /**
    * Get the attached attribute.
    * @return the attached attribute.
    */
   DataRef getAttachedAttribute();

   /**
    * Set the attached attribute.
    * @param ref the specified attribute.
    */
   void setAttachedAttribute(DataRef ref);

   /**
    * Get the attached data type.
    * @return the attached data type.
    */
   String getAttachedDataType();

   /**
    * Set the attached data type.
    */
   void setAttachedDataType(String dtype);

   /**
    * Check if the attached assembly is valid.
    */
   void isAttachedValid() throws Exception;

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   Object clone();
}
