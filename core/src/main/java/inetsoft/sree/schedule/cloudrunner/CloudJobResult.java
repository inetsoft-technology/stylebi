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
package inetsoft.sree.schedule.cloudrunner;

import java.io.Serializable;

public class CloudJobResult implements Serializable {
   public CloudJobResult(String taskName, boolean success, String message) {
      this.taskName = taskName;
      this.success = success;
      this.message = message;
   }

   public String getTaskName() {
      return taskName;
   }

   public boolean isSuccess() {
      return success;
   }

   public String getMessage() {
      return message;
   }

   private final String taskName;
   private final boolean success;
   private final String message;
}
