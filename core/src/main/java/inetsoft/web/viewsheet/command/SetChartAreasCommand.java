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
package inetsoft.web.viewsheet.command;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.graph.model.*;
import inetsoft.web.viewsheet.AllowNulls;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.awt.geom.RectangularShape;
import java.util.List;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonSerialize(as = ImmutableSetChartAreasCommand.class)
@JsonDeserialize(as = ImmutableSetChartAreasCommand.class)
public abstract class SetChartAreasCommand implements ViewsheetCommand {
   public abstract boolean invalid();
   public abstract boolean verticallyResizable();
   public abstract boolean horizontallyResizable();
   public abstract double maxHorizontalResize();
   public abstract double maxVerticalResize();
   @Nullable
   public abstract Plot plot();
   public abstract List<Title> titles();
   public abstract List<Axis> axes();
   public abstract List<LegendContainer> legends();
   public abstract List<Facet> facets();
   @AllowNulls
   public abstract List<String> stringDictionary();
   @AllowNulls
   public abstract List<RegionMeta> regionMetaDictionary();
   @Nullable
   public abstract RectangularShape legendsBounds();
   @Nullable
   public abstract RectangularShape contentBounds();
   public abstract int legendOption();
   public abstract double initialWidthRatio();
   public abstract double initialHeightRatio();
   public abstract double widthRatio();
   public abstract double heightRatio();
   public abstract boolean resized();
   public abstract boolean changedByScript();
   public abstract boolean completed();
   public abstract boolean noData();

   public static Builder builder() {
      return new Builder();
   }

   public static final class Builder extends ImmutableSetChartAreasCommand.Builder {
   }
}
