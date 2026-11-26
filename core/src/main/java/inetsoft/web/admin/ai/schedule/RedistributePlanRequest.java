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

import java.util.List;

/**
 * Body of {@code POST /api/wiz/v1/admin/schedule/redistribute-plan}: what Enterprise Manager's
 * Balance Tasks dialog asks for, the window the tasks' time conditions are spread over, how many
 * may start at the same time and the tasks. Nothing is changed by the request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RedistributePlanRequest {
   /** The ids of the tasks to redistribute, in the order the conditions are placed. */
   public List<String> getTaskIds() { return taskIds; }
   public void setTaskIds(List<String> v) { this.taskIds = v; }

   /** The start of the window, {@code HH:mm} (24-hour). */
   public String getStartTime() { return startTime; }
   public void setStartTime(String v) { this.startTime = v; }

   /** The end of the window, {@code HH:mm} (24-hour), after the start. */
   public String getEndTime() { return endTime; }
   public void setEndTime(String v) { this.endTime = v; }

   /** The most conditions that may start at the same time, at least 1; {@code null} means 1. */
   public Integer getMaxConcurrency() { return maxConcurrency; }
   public void setMaxConcurrency(Integer v) { this.maxConcurrency = v; }

   private List<String> taskIds;
   private String startTime;
   private String endTime;
   private Integer maxConcurrency;
}
