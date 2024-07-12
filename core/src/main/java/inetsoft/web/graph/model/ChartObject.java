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

import org.immutables.value.Value;

import java.awt.geom.RectangularShape;
import java.util.List;
import java.util.Optional;

/**
 * Represents a chart object (Axis, Plot, Title, etc.)
 */
public abstract class ChartObject {
   public abstract String areaName();
   public abstract RectangularShape bounds();
   public abstract RectangularShape layoutBounds();
   public abstract List<ChartTile> tiles();
   public abstract Optional<List<ChartRegion>> regions();

   @Value.Default
   public boolean containsCustomDcRangeRef() {
      return false;
   }

   @Value.Default
   public boolean containsCustomDcMergeRef() {
      return false;
   }
}