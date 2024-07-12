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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.composer.model.vs.HyperlinkModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;

/**
 * A visual object region contained within a ChartObject
 */
@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonSerialize(as = ImmutableChartRegion.class)
public interface ChartRegion {
   // if segType is RECT or ELLIPSE, the pts contains [[x, y], [w, h]]
   public static final int RECT_PATH = 8;
   public static final int ELLIPSE_PATH = 9;
   public static final int LINE_PATH = 10;

   // The type of segment to draw associated with the coordinates
   @Nullable
   short[][] segTypes();

   // A GeoJSON multi-polygon coordinates array
   @Nullable
   double[][][][] pts();

   // The center point of the region
   @Nullable
   Point centroid();

   // An index used to enumerate this region
   @Nullable
   Integer index();

   // The index in the string dictionary that has the tooltip for this region
   int tipIdx();

   // For interactive areas, the row number in the underlying table data
   int rowIdx();

   // index in the regionMetaDictionary for getting region meta information
   @Nullable
   Integer metaIdx();

   // The index in the string dictionary that has the value for this region
   @Nullable
   Integer valIdx();

   @Nullable
   Integer axisFldIdx();

   // Hyperlink models associated with the region
   @Nullable
   List<HyperlinkModel> hyperlinks();

   // A boolean to determine if a region is selectable
   @Nullable
   Boolean noselect();

   // A boolean to determine if a region contained grouped fields
   @Nullable
   Boolean grouped();

   // true if the field bound to this region (axis) is an aggregate
   @Nullable
   Boolean isAggr();

   // The size of the boundary that is referred to by this region
   @Nullable
   Integer boundaryIdx();

   @Nullable
   List<Integer> parentVals();

   @Nullable
   Integer legendItemIdx();

   // Check if this region is period dimension.
   @Nullable
   Boolean period();

   // rows that are used to select intersecting vo.
   @Nullable
   int[] selectRows();

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableChartRegion.Builder {}
}
