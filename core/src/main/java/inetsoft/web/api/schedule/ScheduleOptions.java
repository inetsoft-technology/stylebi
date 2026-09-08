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

import inetsoft.util.Tool;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

@Validated
@Schema(description = "Options that determines when a schedule task runs.")
public class ScheduleOptions {
   /**
    * Gets the enabled of task.
    *
    * @return the enabled of task.
    */
   @Schema(description = "The enable of schedule.")
   public boolean isEnabled() {
      return enabled;
   }

   /**
    * Sets the enabled of task.
    *
    * @param enabled the enabled or not.
    */
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   /**
    * Gets the deleteIfNotScheduledToRun of task.
    *
    * @return the deleteIfNotScheduledToRun of task.
    */
   @Schema(description = "The deleteIfNotScheduledToRun of schedule.")
   public boolean isDeleteIfNotScheduledToRun() {
      return deleteIfNotScheduledToRun;
   }

   /**
    * Sets the deleteIfNotScheduledToRun of task.
    *
    * @param deleteIfNotScheduledToRun the deleteIfNotScheduledToRun or not.
    */
   public void setDeleteIfNotScheduledToRun(boolean deleteIfNotScheduledToRun) {
      this.deleteIfNotScheduledToRun = deleteIfNotScheduledToRun;
   }

   /**
    * Gets the description of task.
    *
    * @return the description of task.
    */
   @Schema(description = "The deleteIfNotScheduledToRun of schedule.")
   public String getDescription() {
      return description;
   }

   /**
    * Sets the description of task.
    *
    * @param description the description or not.
    */
   public void setDescription(String description) {
      this.description = description;
   }

   /**
    * Gets the locale of task.
    *
    * @return the locale of task.
    */
   @Schema(description = "The locale of schedule.")
   public String getLocale() {
      return locale;
   }

   /**
    * Sets the locale of task.
    *
    * @param locale the description or not.
    */
   public void setLocale(String locale) {
      this.locale = locale;
   }

   /**
    * Gets the locale of task.
    *
    * @return the locale of task.
    */
   @Schema(description = "The startDate of schedule.")
   public long getStartDate() {
      return startDate;
   }

   /**
    * Sets the locale of task.
    *
    * @param startDate the description or not.
    */
   public void setStartDate(long startDate) {
      this.startDate = startDate;
   }

   /**
    * Gets the locale of task.
    *
    * @return the locale of task.
    */
   @Schema(description = "The startDate of schedule.")
   public long getEndDate() {
      return endDate;
   }

   /**
    * Sets the locale of task.
    *
    * @param endDate the description or not.
    */
   public void setEndDate(long endDate) {
      this.endDate = endDate;
   }

   /**
    * Gets the executeAsID of task.
    *
    * @return the executeAsID of task.
    */
   @Schema(description = "The executeAsID of schedule.")
   public ScheduleTask.ExecuteAsID getExecuteAsID() {
      return executeAsID;
   }

   /**
    * Sets the locale of task.
    *
    * @param executeAsID the description or not.
    */
   public void setExecuteAsID(ScheduleTask.ExecuteAsID executeAsID) {
      this.executeAsID = executeAsID;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ScheduleOptions that = (ScheduleOptions) o;
      return enabled == that.enabled && deleteIfNotScheduledToRun == that.deleteIfNotScheduledToRun
         && Tool.equals(description, that.description);
   }

   @Override
   public int hashCode() {
      return Objects.hash(enabled);
   }

   @Override
   public String toString() {
      return "ScheduleOptions{" +
         "enabled=" + enabled + " deleteIfNotScheduledToRun=" + deleteIfNotScheduledToRun +
         " description=" + description +
         '}';
   }

   private boolean enabled;
   private boolean deleteIfNotScheduledToRun;
   private String description;
   private String locale;
   private ScheduleTask.ExecuteAsID executeAsID;
   private long startDate;
   private long endDate;
}
