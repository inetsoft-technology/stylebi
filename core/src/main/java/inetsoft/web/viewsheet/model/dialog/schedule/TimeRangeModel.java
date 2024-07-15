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
package inetsoft.web.viewsheet.model.dialog.schedule;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.schedule.TimeRange;
import inetsoft.util.Catalog;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Value.Immutable
@JsonSerialize(as = ImmutableTimeRangeModel.class)
@JsonDeserialize(as = ImmutableTimeRangeModel.class)
public interface TimeRangeModel {
   String name();
   @Nullable String label();
   String startTime();
   String endTime();
   boolean defaultRange();
   @Nullable Boolean modified();
   @Nullable ResourcePermissionModel permissions();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableTimeRangeModel.Builder {
      public Builder from(TimeRange timeRange, Catalog catalog) {
         name(timeRange.getName());
         label(catalog.getString(timeRange.getName()));
         startTime(timeRange.getStartTime()
                      .truncatedTo(ChronoUnit.MINUTES)
                      .format(DateTimeFormatter.ISO_LOCAL_TIME));
         endTime(timeRange.getEndTime()
                    .truncatedTo(ChronoUnit.MINUTES)
                    .format(DateTimeFormatter.ISO_LOCAL_TIME));
         defaultRange(timeRange.isDefault());
         return this;
      }
   }
}
