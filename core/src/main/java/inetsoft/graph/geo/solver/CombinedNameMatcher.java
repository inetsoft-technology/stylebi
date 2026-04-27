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

import java.util.Collection;

/**
 * <tt>CombinedNameMatcher</tt> finds the first NameMatcher that can map feature
 * in the NameMatcher array to map input data to a specific map feature ID. If
 * no NameMatcher found, return null.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public class CombinedNameMatcher implements NameMatcher {
   /**
    * Constructure.
    * @param matchers combined name matchers.
    * @param col if not null, the value from the column is used if no matching
    * is found.
    */
   public CombinedNameMatcher(NameMatcher[] matchers, String col) {
      this(matchers, col, null);
   }

   /**
    * Creates a new instance of <tt>CombinedNameMatcher</tt> with a set of
    * valid feature IDs. When provided, a matcher result that is not in the
    * valid set is skipped so the next matcher can try a different column
    * (e.g. ISO2 instead of FIPS when both share the same code).
    *
    * @param matchers  combined name matchers.
    * @param col       if not null, the raw column value is returned as a
    *                  last resort when no matcher succeeds.
    * @param validIds  the feature IDs present in the target GeoMap, or null
    *                  to disable validation (legacy behaviour).
    */
   public CombinedNameMatcher(NameMatcher[] matchers, String col,
                               Collection<String> validIds)
   {
      this.matchers = matchers;
      this.col = col;
      this.validIds = validIds;
   }

   /**
    * Get the map feature ID that matches the specified row of a data set.
    * @param input   the data set that contains the input data.
    * @param row     the input data row to process.
    * @return the matching map feature ID or <tt>null</tt> if no match is found.
    */
   @Override
   public String getFeatureId(DataSet input, int row) {
      String firstMatch = null;

      for(int i = 0; i < matchers.length; i++) {
         String id = matchers[i].getFeatureId(input, row);

         if(id != null) {
            if(validIds == null || validIds.contains(id)) {
               return id;
            }

            // keep the first match as a fallback in case no matcher produces
            // a valid-map result (preserves pre-existing behaviour)
            if(firstMatch == null) {
               firstMatch = id;
            }
         }
      }

      if(firstMatch != null) {
         return firstMatch;
      }

      Object val = (col == null) ? null : input.getData(col, row);
      return (val == null) ? null : val.toString();
   }

   private NameMatcher[] matchers;
   private String col;
   private Collection<String> validIds;
}
