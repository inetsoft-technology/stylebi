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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.web.admin.schedule.model.ScheduleConditionModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Data transfer object that represents the {@link TimeConditionModel} for the
 * schedule dialog
 */
@Value.Immutable
@JsonSerialize(as = ImmutableTimeConditionModel.class)
@JsonDeserialize(as = ImmutableTimeConditionModel.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class TimeConditionModel extends ScheduleConditionModel {
   @Nullable
   public abstract Integer hour();

   @Nullable
   public abstract Integer minute();

   @Nullable
   public abstract Integer second();

   @Nullable
   public abstract Integer hourEnd();

   @Nullable
   public abstract Integer minuteEnd();

   @Nullable
   public abstract Integer secondEnd();

   @Nullable
   public abstract Integer dayOfMonth();

   @Nullable
   public abstract Integer dayOfWeek();

   @Nullable
   public abstract Integer weekOfMonth();

   @Nullable
   public abstract Long date();

   @Nullable
   public abstract String timeZone();

   @Nullable
   public abstract Integer timeZoneOffset();

   @Nullable
   public abstract Long dateEnd();

   @Value.Default
   public int type() {
      return TimeCondition.EVERY_DAY;
   }

   @Nullable
   public abstract Integer interval();

   @Nullable
   public abstract Float hourlyInterval();

   @Value.Default
   public boolean weekdayOnly() {
      return false;
   }

   @Nullable
   public abstract Boolean monthlyDaySelected();

   @Value.Default
   public int[] daysOfWeek() {
      return new int[0];
   }

   @Value.Default
   public int[] monthsOfYear() {
      return new int[0];
   }

   @Nullable
   public abstract TimeRangeModel timeRange();

   public static Builder builder() {
      return new Builder();
   }

   public static final class Builder extends ImmutableTimeConditionModel.Builder {
   }
}
