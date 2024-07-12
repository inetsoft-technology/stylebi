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

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = ImmutableLegend.class)
@JsonDeserialize(as = ImmutableLegend.class)
public abstract class Legend extends ChartObject {
   /**
    * @return the legend title label
    */
   @Nullable
   public abstract String titleLabel();

   @Nullable
   public abstract String field();

   /**
    * @return the legend aesthetic type
    */
   @Nullable
   public abstract String aestheticType();

   public abstract boolean titleVisible();

   public abstract String[] targetFields();

   public abstract String background();

   /**
    * The levels of the fields bound to this axis
    */
   public abstract List<DrillLevel> drillLevels();

   /**
    * If the axis has drill filter.
    */
   public abstract Optional<Boolean> drillFilter();

   /**
    * Whether is the node Aesthetic.
    */
   public abstract Optional<Boolean> nodeAesthetic();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableLegend.Builder {}
}
