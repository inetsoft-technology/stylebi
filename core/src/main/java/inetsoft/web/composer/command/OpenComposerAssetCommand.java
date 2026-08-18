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
package inetsoft.web.composer.command;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableOpenComposerAssetCommand.class)
public abstract class OpenComposerAssetCommand {
   @Nullable
   public abstract String assetId();

   @Nullable
   public abstract String folderId();

   @Nullable
   public abstract String baseDataSource();

   public abstract boolean viewsheet();

   @Value.Default
   public boolean wsWizard() {
      return false;
   }

   @Nullable
   public abstract String parentId();

   @Value.Default
   public int baseDataSourceType() {
      return -1;
   }

   /**
    * The server-opened runtime id to attach to, or {@code null} for the normal flow where the
    * browser opens its own runtime. Set when an agent tool (e.g. {@code open_base_worksheet})
    * already opened the runtime server-side -- the browser must attach to that runtime instead
    * of opening a second one of the same asset.
    */
   @Nullable
   public abstract String runtimeId();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableOpenComposerAssetCommand.Builder {
   }
}
