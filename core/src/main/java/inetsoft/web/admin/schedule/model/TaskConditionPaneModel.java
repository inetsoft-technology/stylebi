/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.schedule.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.util.Tool;
import org.immutables.value.Value;

import java.util.*;

/**
 * Data transfer object that represents the {@link TaskConditionPaneModel} for the
 * image property dialog
 */
@Value.Immutable
@JsonSerialize(as = ImmutableTaskConditionPaneModel.class)
@JsonDeserialize(as = ImmutableTaskConditionPaneModel.class)
public interface TaskConditionPaneModel {
   @Value.Default
   default String timeProp() {
      return Tool.DEFAULT_TIME_PATTERN;
   }
   List<ScheduleConditionModel> conditions();
   List<String> userDefinedClasses();
   List<String> userDefinedClassLabels();
   boolean twelveHourSystem();

   @Value.Default
   default long timeZoneOffset() {
      return TimeZone.getDefault().getOffset(System.currentTimeMillis());
   }

   @Value.Default
   default String serverTimeZoneId() {
      return TimeZone.getDefault().getID();
   }

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableTaskConditionPaneModel.Builder {
   }
}
