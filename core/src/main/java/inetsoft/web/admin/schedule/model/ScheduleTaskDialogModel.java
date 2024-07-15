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
package inetsoft.web.admin.schedule.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeRangeModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Data transfer object that represents the {@link ScheduleTaskDialogModel} for the
 * schedule task dialog model
 */
@Value.Immutable
@JsonSerialize(as = ImmutableScheduleTaskDialogModel.class)
@JsonDeserialize(as = ImmutableScheduleTaskDialogModel.class)
public interface ScheduleTaskDialogModel {
   String name();
   String label();

   @Value.Default
   default boolean taskDefaultTime() {
      return true;
   }

   TaskOptionsPaneModel taskOptionsPaneModel();
   TaskConditionPaneModel taskConditionPaneModel();
   TaskActionPaneModel taskActionPaneModel();
   String timeZone();
   List<TimeRangeModel> timeRanges();
   @Nullable List<TimeZoneModel> timeZoneOptions();
   boolean timeRangeEnabled();
   boolean startTimeEnabled();

   @Value.Default
   default boolean internalTask() {
      return false;
   }


   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableScheduleTaskDialogModel.Builder {
   }
}