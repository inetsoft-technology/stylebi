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
package inetsoft.web.composer.model.ws;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.binding.drm.ColumnRefModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableReorderColumnsDialogModel.class)
@JsonDeserialize(as = ImmutableReorderColumnsDialogModel.class)
public interface ReorderColumnsDialogModel {
   @Value.Default
   @Nullable
   default List<ColumnRefModel> columns() {
      return new ArrayList<>();
   }

   @Nullable int[] indexes();

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableReorderColumnsDialogModel.Builder {
   }
}