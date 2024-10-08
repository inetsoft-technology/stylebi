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
package inetsoft.web.viewsheet.model.dialog.schedule;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Data transfer object that represents the {@link ScheduleDialogModel}
 */
@Value.Immutable
@JsonSerialize(as = ImmutableScheduleDialogModel.class)
@JsonDeserialize(as = ImmutableScheduleDialogModel.class)
public abstract class ScheduleDialogModel {
   @Nullable
   public abstract Boolean currentBookmark();

   @Nullable
   public abstract String bookmark();

   @Nullable
   public abstract Boolean bookmarkEnabled();

   @Value.Default
   public SimpleScheduleDialogModel simpleScheduleDialogModel() {
      return SimpleScheduleDialogModel.builder().build();
   }

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableScheduleDialogModel.Builder {
   }
}
