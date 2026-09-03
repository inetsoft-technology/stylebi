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

/** One requested field that could not be resolved on the chart's bound table, in {@link AddFiltersResponse}. */
public class SkippedFilter {
   public SkippedFilter() {
   }

   public SkippedFilter(String field, String reason) {
      this.field = field;
      this.reason = reason;
   }

   public String getField() {
      return field;
   }

   public void setField(String field) {
      this.field = field;
   }

   public String getReason() {
      return reason;
   }

   public void setReason(String reason) {
      this.reason = reason;
   }

   private String field;
   private String reason;
}
