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

/**
 * Placeholder for a schedule condition or action with no enterprise API DTO (e.g. a built-in
 * system task's {@code TaskBalancerCondition}/{@code NeverRunCondition}/{@code
 * AssetFileBackupAction}) -- returned by {@link ScheduleApiService#getTaskConditionsLenient}/
 * {@link ScheduleApiService#getTaskActionsLenient} instead of propagating the conversion
 * failure. Deliberately not a {@link ScheduleCondition}/{@link ScheduleAction} subtype: those
 * hierarchies are shared with the public REST API and cluster client services, and must stay
 * closed.
 */
@Schema(description = "A condition or action with no representation in this API -- present so " +
   "the task can still be read in full, but cannot itself be edited through this API.")
public class UnsupportedScheduleItem {
   public UnsupportedScheduleItem(String internalType) {
      this.internalType = internalType;
   }

   @Schema(description = "Always true; marks this entry as a placeholder.")
   public boolean isUnsupported() {
      return true;
   }

   @Schema(description = "The internal (non-API) class name of the condition or action.")
   public String getInternalType() {
      return internalType;
   }

   private final String internalType;
}
