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
package inetsoft.uql;

import inetsoft.uql.erm.DataRef;
import inetsoft.util.XMLSerializable;

import java.io.Serializable;

/**
 * An XCubeMember represents either a dimension level or measure in an OLAP
 * cube.
 *
 * @author  InetSoft Technology
 * @since   10.1
 */
public interface XCubeMember extends Cloneable, Serializable, XMLSerializable {
   /**
    * Get the name of this cube member.
    *
    * @return the name of this cube member.
    */
   String getName();

   /**
    * Get the data reference of this cube member.
    *
    * @return an XDataRef object.
    */
   DataRef getDataRef();

   /**
    * Get the data type of this cube member. This will be one of the data type
    * constants defined in <code>inetsoft.uql.schema.XSchema</code>
    *
    * @return the data type of this cube member.
    */
   String getType();

   /**
    * Get the folder of this cube member.
    *
    * @return folder of the cube member.
    */
   String getFolder();
   
   /**
    * Get the XMetaInfo of this cube member.
    *
    * @return the XMetaInfo of this cube member.
    */
   XMetaInfo getXMetaInfo();


   /**
    * Get the type of original ref.
    */
   String getOriginalType();

   /**
    * Set the type of original ref.
    */
   void setOriginalType(String originalType);

   /**
    * Get the type of original ref.
    */
   String getDateLevel();

   /**
    * Set the type of original ref.
    */
   void setDateLevel(String originalType);
}
