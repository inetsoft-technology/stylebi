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
@JsonSerialize(as = ImmutableRequiredAssetModel.class)
@JsonDeserialize(as = ImmutableRequiredAssetModel.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface RequiredAssetModel {
   @Value.Default
   default String name() { return ""; }

   @Value.Default
   default String label() { return ""; }

   @Value.Default
   default String type() { return ""; }

   @Value.Default
   default String typeLabel() {
      return "";
   }

   @Nullable
   String requiredBy();

   @Nullable
   IdentityID user();

   @Nullable
   String detailDescription();

   @Nullable
   String typeDescription();

   @Nullable
   String assetDescription();

   @Nullable
   String deviceLabel();

   @Value.Default
   default int index() {
      return -1;
   }

   long lastModifiedTime();

   @Nullable
   String dateFormat();

   @Nullable
   String appliedTargetLabel();

   static Builder builder() {
      return new RequiredAssetModel.Builder();
   }

   final class Builder extends ImmutableRequiredAssetModel.Builder {
   }
}
