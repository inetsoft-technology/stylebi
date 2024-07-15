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
package inetsoft.web.admin.schedule.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.uql.asset.AssetEntry;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableBatchActionModel.class)
@JsonDeserialize(as = ImmutableBatchActionModel.class)
public abstract class BatchActionModel extends ScheduleActionModel {
   public abstract String taskName();

   @Nullable
   public abstract AssetEntry queryEntry();

   @Nullable
   public abstract AddParameterDialogModel[] queryParameters();

   @Nullable
   public abstract AddParameterDialogModel[][] embeddedParameters();

   @Value.Default
   public boolean queryEnabled() {
      return false;
   }

   @Value.Default
   public boolean embeddedEnabled() {
      return false;
   }

   public static BatchActionModel.Builder builder() {
      return new BatchActionModel.Builder();
   }

   public static class Builder extends ImmutableBatchActionModel.Builder {
   }
}
