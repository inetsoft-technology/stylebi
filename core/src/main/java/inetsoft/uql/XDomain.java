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

import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Enumeration;

/**
 * A domain is a container for OLAP cubes.
 *
 * @author  InetSoft Technology
 * @since   10.1
 */
public interface XDomain extends Cloneable, Serializable, XMLSerializable {
   /**
    * Get the name of the data source to which this domain is associated.
    *
    * @return the name of the associated data source.
    */
   String getDataSource();

   /**
    * Set the name of the data source to which this domain is associated.
    *
    * @param datasource the name of the associated data source.
    */
   void setDataSource(String datasource);

   /**
    * Get the cubes contained in this domain.
    *
    * @return an Enumeration of XCube objects.
    */
   Enumeration getCubes();

   /**
    * Get the specified cube.
    *
    * @param name the name of the cube.
    *
    * @return an XCube object, or <code>null</code> if no cube with the
    *         specified name exists.
    */
   XCube getCube(String name);

   /**
    * Read in the definition of this object from an XML tag.
    *
    * @param tag the XML Element representing this object.
    */
   @Override
   void parseXML(Element tag) throws Exception;

   /**
    * Clears all cached cube result sets for this data model.
    */
   void clearCache();

   /**
    * Write an XML element representation of this object.
    *
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   void writeXML(PrintWriter writer);

   /**
    * Create a copy of this object.
    *
    * @return a copy of this object.
    */
   Object clone();
}
