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
import inetsoft.sree.schedule.TaskActivity;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * ScheduleTaskStatusModel describes the current status of a schedule task.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableScheduleTaskStatusModel.class)
@JsonDeserialize(as = ImmutableScheduleTaskStatusModel.class)
public abstract class ScheduleTaskStatusModel {
   /**
    * The status of the last run of the task.
    */
   @Nullable
   public abstract String lastRunStatus();

   /**
    * The status of the next run of the task.
    */
   @Nullable
   public abstract String nextRunStatus();

   /**
    * The date and time at which the last run started.
    */
   public abstract long lastRunStart();

   /**
    * The date and time at which the last run ended.
    */
   public abstract long lastRunEnd();

   /**
    * The date and time at which the next run is scheduled.
    */

   public abstract long nextRunStart();

   /**
    * The clean message.
    */
   @Nullable
   public abstract String cleanMessage();

   /**
    * The error message.
    */
   @Nullable
   public abstract String errorMessage();

   /**
    * Creates a new ScheduleTaskStatusModel builder.
    *
    * @return a new builder instance.
    */
   public static ScheduleTaskStatusModel.Builder builder() {
      return new ScheduleTaskStatusModel.Builder();
   }

   /**
    * Builder for {@link ScheduleTaskStatusModel} instances.
    */
   public static final class Builder extends ImmutableScheduleTaskStatusModel.Builder {
      public ScheduleTaskStatusModel.Builder from(TaskActivity activity) {
         if(activity != null) {
            lastRunStatus(activity.getLastRunStatus());
            nextRunStatus(activity.getNextRunStatus());
            lastRunStart(activity.getLastRunStart());
            lastRunEnd(activity.getLastRunEnd());
            nextRunStart(activity.getNextRunStart());
            errorMessage(activity.getError());
            cleanMessage(activity.getCleanMessage());
         }

         return this;
      }
   }
}
