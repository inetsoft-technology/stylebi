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
package inetsoft.graph.geo.solver;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.geo.NameMatcher;

/**
 * <tt>NameMatcher</tt> that uses a <tt>MatchingAlgorithm</tt> to find the
 * best map feature for the source data.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
public class MatchingNameMatcher implements NameMatcher {
   /**
    * Creates a new instance of <tt>MatchingNameMatcher</tt>.
    *
    * @param algorithm     the algorithm used to rank matches.
    * @param names         the map feature names.
    * @param sourceColumns the columns in the source data to match.
    * @param nameColumns   the columns in the name data to match.
    */
   public MatchingNameMatcher(MatchingAlgorithm algorithm, NameTable names,
                              int[] sourceColumns, int[] nameColumns)
   {
      this.algorithm = algorithm;
      this.names = names;
      this.sourceColumns = sourceColumns;
      this.nameColumns = nameColumns;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getFeatureId(DataSet input, int row) {
      return algorithm.findBestMatchID(input, sourceColumns, names, nameColumns,
                                     row);
   }

   private final MatchingAlgorithm algorithm;
   private final NameTable names;
   private final int[] sourceColumns;
   private final int[] nameColumns;
}
