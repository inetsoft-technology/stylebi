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
package inetsoft.graph.geo.solver;

import inetsoft.graph.data.DataSet;

import java.util.List;

/**
 * Base class for implementations of <tt>MatchingAlgorithm</tt>.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
public abstract class AbstractMatchingAlgorithm implements MatchingAlgorithm {
   /**
    * Creates a new instance of <tt>AbstractMatchingAlgorithm</tt>.
    */
   public AbstractMatchingAlgorithm() {
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String findBestMatchID(DataSet source, int[] sourceColumns,
                                 NameTable names, int[] nameColumns, int row)
   {
      List<String> matches =
         findMatchIDs(source, sourceColumns, names, nameColumns, row);
      return matches.isEmpty() ? null : matches.get(0);
   }
}
