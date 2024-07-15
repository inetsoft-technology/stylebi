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
package inetsoft.uql;

import java.io.Serializable;
import java.util.Enumeration;

/**
 * An XCube represents a cube in an OLAP server.
 *
 * @author  InetSoft Technology
 * @since   10.1
 */
public interface XCube extends Cloneable, Serializable {
   /**
    * SQLServer cube.
    */
   public static final String SQLSERVER = "SQLServer";
   /**
    * Essbase cube.
    */
   public static final String ESSBASE = "Essbase";
   /**
    * Logical model cube.
    */
   public static final String MODEL = "Model";
   /**
    * Mondrian cube.
    */
   public static final String MONDRIAN = "Mondrian";
   /**
    * SAP cube.
    */
   public static final String SAP = "SAP";

   /**
    * Get the name of this cube.
    *
    * @return the name of this cube.
    */
   public String getName();

   /**
    * Get all the dimensions contained in this cube.
    *
    * @return an Enumeration of XDimension objects.
    */
   public Enumeration<XDimension> getDimensions();

   /**
    * Get the specified dimension.
    *
    * @param name the name of the dimension.
    *
    * @return an XDimension object or <code>null</code> if a dimension with the
    *         specified name does not exist.
    */
   public XDimension getDimension(String name);

   /**
    * Get all the measures contained in this cube.
    *
    * @return an Enumeration of XCubeMember objects.
    */
   public Enumeration getMeasures();

   /**
    * Get the specified measure.
    *
    * @param name the name of the measure.
    *
    * @return an XCubeMember object or <code>null</code> if a measure with the
    *         specified name does not exist.
    */
   public XCubeMember getMeasure(String name);

   /**
    * Get cube type.
    * @return cube type.
    */
   public String getType();
}
