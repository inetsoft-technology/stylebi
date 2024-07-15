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
package inetsoft.web.graph.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.viewsheet.model.table.DrillLevel;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = ImmutablePlot.class)
@JsonDeserialize(as = ImmutablePlot.class)
public abstract class Plot extends ChartObject {
   public abstract List<Double> xboundaries();

   public abstract List<Double> yboundaries();

   /**
    * The levels of the fields bound to this axis
    */
   public abstract List<DrillLevel> drillLevels();

   /**
    * If the axis has drill filter.
    */
   public abstract Optional<Boolean> drillFilter();

   public abstract boolean showReferenceLine();

   @Nullable
   public abstract Double geoPadding();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutablePlot.Builder {
   }
}
