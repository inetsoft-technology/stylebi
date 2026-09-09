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

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTaskMetaData;
import inetsoft.sree.security.IdentityID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

/**
 * {@code ScheduleCompletionCondition} describes a condition that is satisfied when another schedule
 * task finishes.
 */
@Validated
@Schema(description = "A condition that is satisfied when another schedule task finishes.")
public class CompletionCondition extends ScheduleCondition {
   /**
    * Creates a new instance of {@code CompletionCondition}.
    */
   public CompletionCondition() {
      setConditionType(ConditionType.COMPLETION);
   }

   /**
    * Creates a new instance of {@code CompletionCondition}.
    *
    * @param condition the schedule condition being represented.
    */
   public CompletionCondition(inetsoft.sree.schedule.CompletionCondition condition) {
      ScheduleTaskMetaData taskMetaData = ScheduleManager.getTaskMetaData(condition.getTaskName());
      setTaskName(taskMetaData.getTaskName());

      if(taskMetaData.getTaskOwnerId() != null) {
         setOwner(IdentityID.getIdentityIDFromKey(taskMetaData.getTaskOwnerId()).name);
      }

      setConditionType(ConditionType.COMPLETION);
   }

   /**
    * Gets the name of the task on which the condition depends.
    *
    * @return the task name.
    */
   @NotNull
   @Schema(
      description = "The name of the task on  which the condition depends.",
      example = "Monthly Task")
   public String getTaskName() {
      return taskName;
   }

   /**
    * Sets the name of the task on which the condition depends.
    *
    * @param task the task name.
    */
   public void setTaskName(String task) {
      this.taskName = task;
   }

   /**
    * Gets he name of the task on which the condition depends.
    *
    * @return the task name.
    */
   @NotNull
   @Schema(
      description = "The owner of the task on  which the condition depends.",
      example = "user0")
   public String getOwner() {
      return owner;
   }

   /**
    * Sets the owner of the task on which the condition depends.
    *
    * @param owner the task owner.
    */
   public void setOwner(String owner) {
      this.owner = owner;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      CompletionCondition that = (CompletionCondition) o;
      return Objects.equals(taskName, that.taskName) && Objects.equals(owner, that.owner);
   }

   @Override
   public int hashCode() {
      return Objects.hash(taskName, owner);
   }

   @Override
   public String toString() {
      return "CompletionCondition{" +
         "conditionType=" + getConditionType() +
         ", taskName='" + taskName + '\'' +
         ", owner='" + owner + '\'' +
         '}';
   }

   private String taskName;
   private String owner;
}
