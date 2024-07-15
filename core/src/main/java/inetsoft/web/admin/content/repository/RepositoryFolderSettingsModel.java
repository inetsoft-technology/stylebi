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
package inetsoft.web.admin.content.repository;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableRepositoryFolderSettingsModel.class)
@JsonDeserialize(as = ImmutableRepositoryFolderSettingsModel.class)
public interface RepositoryFolderSettingsModel {
   String folderName();

   @Nullable
   String oname();

   @Nullable
   String alias();

   String parentFolder();

   String parentFolderLabel();

   @Nullable
   String description();

   @Value.Default
   default List<String> folders() {
      return new ArrayList<>();
   }

   @Nullable
   ResourcePermissionModel permissionTableModel();

   @Value.Default
   default boolean editable() {
      return true;
   }

   static Builder builder() {
      return new RepositoryFolderSettingsModel.Builder();
   }

   final class Builder extends ImmutableRepositoryFolderSettingsModel.Builder {
   }
}
