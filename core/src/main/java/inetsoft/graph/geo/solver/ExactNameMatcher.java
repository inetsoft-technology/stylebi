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
import inetsoft.graph.geo.NameMatcher;

/**
 * <tt>ExactNameMatcher</tt> that uses a <tt>ExactAlgorithm</tt> and other
 * NameMatcher to find the best map feature for the source data.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public class ExactNameMatcher implements NameMatcher {
   /**
    * Creates a new instance of <tt>ExactNameMatcher</tt>.
    *
    * @param names         the map feature names.
    * @param sourceColumns the columns in the source data to match.
    * @param nameColumns   the columns in the name data to match.
    * @param matchers      the name matchers.
    */
   public ExactNameMatcher(NameTable[] names, int[] sourceColumns,
                           int[] nameColumns, NameMatcher[] matchers)
   {
      this.names = names;
      this.sourceColumns = sourceColumns;
      this.nameColumns = nameColumns;
      this.matchers = matchers;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getFeatureId(DataSet input, int row) {
      return algorithm.findBestMatchID(input, sourceColumns, names,
         nameColumns, row, matchers);
   }

   private final NameTable[] names;
   private final int[] sourceColumns;
   private final int[] nameColumns;
   private NameMatcher[] matchers;
   private ExactAlgorithm algorithm = new ExactAlgorithm();
}
