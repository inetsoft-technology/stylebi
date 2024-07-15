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
package inetsoft.graph.geo;

import inetsoft.graph.data.DataSet;

import java.io.Serializable;

/**
 * Interface for classes that map input data to a specific map feature ID.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
public interface NameMatcher extends Serializable {
   /**
    * Get the map feature ID that matches the specified row of a data set.
    * @param input   the data set that contains the input data.
    * @param row     the input data row to process.
    * @return the matching map feature ID or <tt>null</tt> if no match is found.
    */
   public String getFeatureId(DataSet input, int row);
}
