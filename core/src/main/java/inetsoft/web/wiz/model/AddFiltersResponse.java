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

import java.util.List;

/** Response body for {@code POST /api/wiz/viewsheet/filters}. */
public class AddFiltersResponse {
   public AddFiltersResponse() {
   }

   public AddFiltersResponse(String runtimeId, List<AppliedFilter> applied, List<SkippedFilter> skipped) {
      this.runtimeId = runtimeId;
      this.applied = applied;
      this.skipped = skipped;
   }

   public String getRuntimeId() {
      return runtimeId;
   }

   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   public List<AppliedFilter> getApplied() {
      return applied;
   }

   public void setApplied(List<AppliedFilter> applied) {
      this.applied = applied;
   }

   public List<SkippedFilter> getSkipped() {
      return skipped;
   }

   public void setSkipped(List<SkippedFilter> skipped) {
      this.skipped = skipped;
   }

   private String runtimeId;
   private List<AppliedFilter> applied;
   private List<SkippedFilter> skipped;
}
