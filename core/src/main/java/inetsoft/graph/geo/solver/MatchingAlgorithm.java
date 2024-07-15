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
package inetsoft.graph.geo.solver;

import inetsoft.graph.data.DataSet;

import java.io.Serializable;
import java.util.List;

/**
 * Interface for classes that find the best matches of source data to map
 * features.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
public interface MatchingAlgorithm extends Serializable {
   /**
    * Find the map feature id that best matches the source data.
    *
    * @param source        the source data.
    * @param sourceColumns the columns in the source data to match.
    * @param names         the map feature names.
    * @param nameColumns   the columns in the name data to match.
    * @param row           the row of the source data to match.
    *
    * @return the ID of the map feature that best matches the source data.
    */
   String findBestMatchID(DataSet source, int[] sourceColumns, NameTable names,
                          int[] nameColumns, int row);

   /**
    * Finds the map feature ids that best match the source data. The returned
    * map features will be sorted in order of the likelihood of the match, from
    * the most likely to the least likely.
    *
    * @param source        the source data.
    * @param sourceColumns the columns in the source data to match.
    * @param names         the map feature names.
    * @param nameColumns   the columns in the name data to match.
    * @param row           the row of the source data to match.
    *
    * @return the IDs of the map features that match the source data.
    */
   List<String> findMatchIDs(DataSet source, int[] sourceColumns,
                             NameTable names, int[] nameColumns, int row);
}
