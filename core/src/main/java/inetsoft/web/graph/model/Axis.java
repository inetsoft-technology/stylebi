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
package inetsoft.web.graph.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.viewsheet.model.table.DrillLevel;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = ImmutableAxis.class)
@JsonDeserialize(as = ImmutableAxis.class)
public abstract class Axis extends ChartObject {
   /**
    * @return 'x' or 'y' if this is a horizontal/vertical axis
    */
   public abstract Optional<String> axisType();

   /**
    * The sort order of the axis. null, "", "Asc", or "Desc"
    */
   public abstract Optional<String> sortOp();

   /**
    * The drill ops on this axis
    */
   public abstract Optional<String[]> axisOps();

   /**
    * The names of the fields bound to this axis
    */
   public abstract Optional<String[]> axisFields();

   /**
    * The size of each level of this axis
    */
   public abstract Optional<int[]> axisSizes();

   /**
    * If the axis is secondary.
    */
   public abstract Optional<Boolean> secondary();

   /**
    * The sort field of this axis
    */
   public abstract Optional<String> sortField();

   /**
    * The levels of the fields bound to this axis
    */
   public abstract Optional<DrillLevel[]> drillLevels();

   /**
    * Whether the sort filed is calc agg.
    * @return
    */
   @Value.Default
   public boolean sortFieldIsCalc() {
      return false;
   }

   /**
    * If the axis has drill filter.
    */
   public abstract Optional<Boolean> drillFilter();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableAxis.Builder {}
}
