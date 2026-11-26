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
package inetsoft.web.admin.ai.schedule;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.web.api.schedule.ScheduleAction;
import inetsoft.web.api.schedule.ScheduleCondition;

import java.util.List;

/**
 * The fields of an existing schedule task an {@code update} change replaces. A {@code null} field
 * leaves the stored value alone; {@code conditions} and {@code actions}, when present, replace the
 * whole stored list. The task's name and owner are not part of a patch, changing either is a
 * rename that carries its own dependency checks in Enterprise Manager.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleTaskPatch {
   public String getDescription() { return description; }
   public void setDescription(String v) { this.description = v; }

   public String getLocale() { return locale; }
   public void setLocale(String v) { this.locale = v; }

   public Boolean getEnabled() { return enabled; }
   public void setEnabled(Boolean v) { this.enabled = v; }

   public Boolean getDeleteIfNotScheduledToRun() { return deleteIfNotScheduledToRun; }
   public void setDeleteIfNotScheduledToRun(Boolean v) { this.deleteIfNotScheduledToRun = v; }

   /** Epoch millis, {@code 0} clears the start date. */
   public Long getStartDate() { return startDate; }
   public void setStartDate(Long v) { this.startDate = v; }

   /** Epoch millis, {@code 0} clears the end date. */
   public Long getEndDate() { return endDate; }
   public void setEndDate(Long v) { this.endDate = v; }

   public List<ScheduleCondition> getConditions() { return conditions; }
   public void setConditions(List<ScheduleCondition> v) { this.conditions = v; }

   public List<ScheduleAction> getActions() { return actions; }
   public void setActions(List<ScheduleAction> v) { this.actions = v; }

   /**
    * The task-level time zone (the portal Options tab's Time Zone). {@code null} leaves it alone,
    * a blank value clears it back to the server default, anything else sets it.
    */
   public String getTimeZone() { return timeZone; }
   public void setTimeZone(String v) { this.timeZone = v; }

   private String description;
   private String locale;
   private Boolean enabled;
   private Boolean deleteIfNotScheduledToRun;
   private Long startDate;
   private Long endDate;
   private List<ScheduleCondition> conditions;
   private List<ScheduleAction> actions;
   private String timeZone;
}
