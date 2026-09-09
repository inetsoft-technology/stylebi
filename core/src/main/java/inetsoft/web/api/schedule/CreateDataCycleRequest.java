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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.*;

/**
 * Cycle contains the properties of a cycle.
 */
@Schema(description = "The properties of a cycle.")
public class CreateDataCycleRequest {
   /**
    * Gets the name of the cycle.
    *
    * @return the cycle name.
    */
   @NotNull
   @Schema(description = "The name of the cycle.", example = "Cycle1")
   public String getName() {
      return name;
   }

   /**
    * Sets the name of the cycle.
    *
    * @param name the cycle name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Gets the list of conditions for the cycle.
    *
    * @return the condition list.
    */
   @NotNull
   @NotEmpty
   @Schema(description = "The list of conditions for the cycle. At least one condition is required.")
   public List<TimeCondition> getConditions() {
      if(conditions == null) {
         conditions = new ArrayList<>();
      }

      return conditions;
   }

   /**
    * Sets the list of conditions for the cycle.
    *
    * @param conditions the condition list.
    */
   public void setConditions(List<TimeCondition> conditions) {
      this.conditions = conditions;
   }

   /**
    * Gets whether to send a notification when the cycle starts.
    *
    * @return whether to notify on cycle start.
    */
   @Schema(description = "Whether to send a notification on cycle start.", example = "false")
   public boolean isStartNotify() {
      return startNotify;
   }

   /**
    * Sets whether to send a notification email when the cycle starts.
    *
    * @param startNotify whether to notify on cycle start.
    */
   public void setStartNotify(boolean startNotify) {
      this.startNotify = startNotify;
   }

   /**
    * Gets the email to notify when the cycle starts.
    *
    * @return the email to notify.
    */
   @Schema(description = "The email to notify.")
   public String getStartEmail() {
      return startEmail;
   }

   /**
    * Sets the email to notify when the cycle starts.
    *
    * @param startEmail the email to notify.
    */
   public void setStartEmail(String startEmail) {
      this.startEmail = startEmail;
   }

   /**
    * Gets whether to send a notification email when the cycle ends.
    *
    * @return whether to notify on cycle end.
    */
   @Schema(description = "Whether to send a notification on cycle end.", example = "false")
   public boolean isEndNotify() {
      return endNotify;
   }

   /**
    * Sets whether to send a notification when the cycle ends.
    *
    * @param endNotify whether to notify on cycle end.
    */
   public void setEndNotify(boolean endNotify) {
      this.endNotify = endNotify;
   }

   /**
    * Gets the email to notify when the cycle ends.
    *
    * @return the email to notify.
    */
   @Schema(description = "The email to notify.")
   public String getEndEmail() {
      return endEmail;
   }

   /**
    * Sets the email to notify when the cycle ends.
    *
    * @param endEmail the email to notify.
    */
   public void setEndEmail(String endEmail) {
      this.endEmail = endEmail;
   }

   /**
    * Gets whether to send a notification email when the cycle fails.
    *
    * @return whether to notify on cycle failure.
    */
   @Schema(description = "Whether to send a notification on cycle failure.", example = "false")
   public boolean isFailureNotify() {
      return failureNotify;
   }

   /**
    * Sets whether to send a notification when the cycle fails.
    *
    * @param failureNotify whether to notify on cycle failure.
    */
   public void setFailureNotify(boolean failureNotify) {
      this.failureNotify = failureNotify;
   }

   /**
    * Gets the email to notify when the cycle fails.
    *
    * @return the email to notify.
    */
   @Schema(description = "The email to notify.")
   public String getFailureEmail() {
      return failureEmail;
   }

   /**
    * Sets the email to notify when the cycle fails.
    *
    * @param failureEmail the email to notify.
    */
   public void setFailureEmail(String failureEmail) {
      this.failureEmail = failureEmail;
   }

   /**
    * Gets whether to send a notification email when the cycle threshold is exceeded.
    *
    * @return whether to notify when the threshold is exceeded.
    */
   @Schema(description = "Whether to send a notification when the threshold is exceeded.", example = "false")
   public boolean isExceedNotify() {
      return exceedNotify;
   }

   /**
    * Sets whether to send a notification when the cycle threshold is exceeded.
    *
    * @param exceedNotify whether to notify when the threshold is exceeded.
    */
   public void setExceedNotify(boolean exceedNotify) {
      this.exceedNotify = exceedNotify;
   }

   /**
    * Gets the email to notify when the cycle threshold is exceeded.
    *
    * @return the email to notify.
    */
   @Schema(description = "The email to notify.")
   public String getExceedEmail() {
      return exceedEmail;
   }

   /**
    * Sets the email to notify when the cycle threshold is exceeded.
    *
    * @param exceedEmail the email to notify.
    */
   public void setExceedEmail(String exceedEmail) {
      this.exceedEmail = exceedEmail;
   }

   /**
    * Gets the cycle threshold required for the notification.
    *
    * @return the cycle threshold.
    */
   @Schema(description = "The cycle threshold in seconds.")
   public int getThreshold() {
      return threshold;
   }

   /**
    * Sets the cycle threshold required for the notification.
    *
    * @param threshold the cycle threshold in seconds.
    */
   public void setThreshold(int threshold) {
      this.threshold = threshold;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      CreateDataCycleRequest that = (CreateDataCycleRequest) o;
      return startNotify == that.startNotify &&
         endNotify == that.endNotify &&
         failureNotify == that.failureNotify &&
         exceedNotify == that.exceedNotify &&
         threshold == that.threshold &&
         Objects.equals(name, that.name) &&
         Objects.equals(conditions, that.conditions) &&
         Objects.equals(startEmail, that.startEmail) &&
         Objects.equals(endEmail, that.endEmail) &&
         Objects.equals(failureEmail, that.failureEmail) &&
         Objects.equals(exceedEmail, that.exceedEmail);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, startNotify, startEmail, endNotify, endEmail,
                          failureNotify, failureEmail, exceedNotify, exceedEmail, threshold);
   }

   @Override
   public String toString() {
      return "CreateScheduleTaskRequest{" +
         "name='" + name + '\'' +
         ", conditions=" + conditions +
         ", startNotify=" + startNotify +
         ", startEmail='" + startEmail + '\'' +
         ", endNotify=" + endNotify +
         ", endEmail='" + endEmail + '\'' +
         ", failureNotify=" + failureNotify +
         ", failureEmail='" + failureEmail + '\'' +
         ", exceedNotify=" + exceedNotify +
         ", exceedEmail='" + exceedEmail + '\'' +
         ", threshold=" + threshold +
         '}';
   }

   private String name;
   private List<TimeCondition> conditions;
   private boolean startNotify;
   private String startEmail;
   private boolean endNotify;
   private String endEmail;
   private boolean failureNotify;
   private String failureEmail;
   private boolean exceedNotify;
   private String exceedEmail;
   private int threshold;
}
