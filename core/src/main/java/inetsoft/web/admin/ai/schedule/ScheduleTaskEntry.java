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

import inetsoft.web.api.schedule.ScheduleTask;

/**
 * One entry of the {@code list_schedule_tasks} response: the public {@link ScheduleTask} plus the
 * folder the task sits in. The folder lives here, not on the public DTO, because that DTO is also
 * the public REST API's request and response body. {@code path} is root-normalized (root is
 * {@code "/"}, a subfolder has no leading slash), the same form the schedule folder tools use.
 */
public class ScheduleTaskEntry extends ScheduleTask {
   public ScheduleTaskEntry(ScheduleTask task, String path) {
      super(task.getName(), task.getOwner(), task.isEnabled(), task.isDeleteIfNotScheduledToRun(),
            task.getStartDate(), task.getEndDate(), task.getDescription(), task.getLocale(),
            task.getExecuteAsID());
      setStatus(task.getStatus());
      this.path = path;
   }

   public String getPath() {
      return path;
   }

   private final String path;
}
