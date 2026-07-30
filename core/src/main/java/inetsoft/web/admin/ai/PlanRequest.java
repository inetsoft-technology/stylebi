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
package inetsoft.web.admin.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Request body for {@code POST /preview}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanRequest {
   public String getTask() { return task; }
   public void setTask(String v) { this.task = v; }
   public List<Change> getChanges() { return changes; }
   public void setChanges(List<Change> v) { this.changes = v; }

   /** One requested change. A {@code null} value resets the property to its default. */
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class Change {
      public String getProperty() { return property; }
      public void setProperty(String v) { this.property = v; }
      public String getValue() { return value; }
      public void setValue(String v) { this.value = v; }

      private String property, value;
   }

   private String task;
   private List<Change> changes;
}
