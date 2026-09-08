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

import java.util.*;

/**
 * ScheduleTaskList contains a list of schedule tasks.
 */
@Validated
@Schema(description = "A list of scheduled tasks.")
public class ScheduleTaskList {
   /**
    * Gets the list of tasks.
    *
    * @return the task list.
    */
   @NotNull
   @Schema(description = "The scheduled tasks.")
   public List<ScheduleTask> getTasks() {
      if(tasks == null) {
         tasks = new ArrayList<>();
      }

      return tasks;
   }

   /**
    * Sets the list of tasks.
    *
    * @param tasks the task list.
    */
   public void setTasks(List<ScheduleTask> tasks) {
      this.tasks = tasks;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ScheduleTaskList that = (ScheduleTaskList) o;
      return Objects.equals(tasks, that.tasks);
   }

   @Override
   public int hashCode() {
      return Objects.hash(tasks);
   }

   @Override
   public String toString() {
      return "ScheduleTaskList{" +
         "tasks=" + tasks +
         '}';
   }

   private List<ScheduleTask> tasks;
}
