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
package inetsoft.report.composition.execution;

import inetsoft.uql.XTable;

import java.util.*;

/**
 * SelectionVSAQuery, the selection viewsheet assembly query.
 *
 * @author InetSoft Technology Corp
 * @version 8.5
 */
public interface SelectionVSAQuery {
   /**
    * Refresh the selection value of a selection viewsheet assembly.
    *
    * @param data               the specified data.
    * @param allSelections      all selections including excluded selected values.
    * @param appliedSelections  selected values not including excluded values.
    * @param values             the specified associated value map.
    * @param measureAggregation selection measure aggregation.
    */
   void refreshSelectionValue(XTable data,
                              Map<String, Map<String, Collection<Object>>> allSelections,
                              Map<String, Map<String, Collection<Object>>> appliedSelections,
                              Map<String, Set<Object>> values,
                              SelectionMeasureAggregation measureAggregation)
      throws Exception;

   /**
    * Refresh the view selection value.
    */
   void refreshViewSelectionValue() throws Exception;

   /**
    * Evaluate the query hits mv or not.
    */
   boolean hitsMV() throws Exception;
}
