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
package inetsoft.web.admin.content.repository.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.security.IdentityID;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableSelectedAssetModel.class)
@JsonDeserialize(as = ImmutableSelectedAssetModel.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface SelectedAssetModel {
   @Value.Default
   default String path() { return ""; }

   @Value.Default
   default String label() { return ""; }

   int type();

   String typeName();

   String typeLabel();

   @Nullable
   IdentityID user();

   @Nullable
   String icon();

   @Nullable
   String description();

   @Nullable
   String lastModifiedTime();

   @Nullable
   String appliedTargetLabel();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableSelectedAssetModel.Builder {
   }
}
