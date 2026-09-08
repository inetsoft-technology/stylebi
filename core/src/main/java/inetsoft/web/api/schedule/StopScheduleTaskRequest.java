/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.api.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

/**
 * StopScheduleTaskRequest contains the parameters used to stop a schedule task.
 */
@Validated
@Schema(description = "The parameters used to stop a schedule task.")
public class StopScheduleTaskRequest {
   /**
    * Gets the name of the task to stop.
    *
    * @return the task name.
    */
   @NotNull
   @Schema(description = "The name of the task.", example = "admin~;~host-org:Monthly Summary")
   public String getTask() {
      return task;
   }

   /**
    * Sets the name of the task to stop.
    *
    * @param task the task name.
    */
   public void setTask(String task) {
      this.task = task;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      StopScheduleTaskRequest that = (StopScheduleTaskRequest) o;
      return Objects.equals(task, that.task);
   }

   @Override
   public int hashCode() {
      return Objects.hash(task);
   }

   @Override
   public String toString() {
      return "StopScheduleTaskRequest{" +
         "task='" + task + '\'' +
         '}';
   }

   private String task;
}
