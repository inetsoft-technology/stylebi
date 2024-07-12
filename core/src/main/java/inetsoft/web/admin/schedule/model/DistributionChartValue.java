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
package inetsoft.web.admin.schedule.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.report.internal.Region;
import org.immutables.value.Value;

import java.awt.*;

@Value.Immutable
@JsonSerialize(as = ImmutableDistributionChartValue.class)
@JsonDeserialize(as = ImmutableDistributionChartValue.class)
public interface DistributionChartValue {
   int x();
   int y();
   int width();
   int height();
   int index();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableDistributionChartValue.Builder {
      public Builder from(Region region, Rectangle plotBounds) {
         Rectangle bounds = region.getBounds();
         x(bounds.x + plotBounds.x);
         y(bounds.y + plotBounds.y);
         width(bounds.width);
         height(bounds.height);
         return this;
      }
   }
}
