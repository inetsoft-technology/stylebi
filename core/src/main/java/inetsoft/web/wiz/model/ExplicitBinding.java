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
package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * An explicit field-to-slot assignment from the LLM, used to steer recommendation
 * selection toward a ChartInfo whose slot assignment matches the user intent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExplicitBinding {
   public String getRole() {
      return role;
   }

   public void setRole(String role) {
      this.role = role;
   }

   public String getField() {
      return field;
   }

   public void setField(String field) {
      this.field = field;
   }

   /** Chart slot name: "x", "y", "color", "shape", "size", "text", "group", "rows", "cols", "aggregates", "details" */
   private String role;
   /** Field name exactly as it appears in the data source. */
   private String field;
}
