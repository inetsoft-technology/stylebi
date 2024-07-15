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
package inetsoft.web.admin.security.user;

import inetsoft.web.admin.security.PropertyModel;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableEditOrganizationPaneModel.class)
@JsonDeserialize(as = ImmutableEditOrganizationPaneModel.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface EditOrganizationPaneModel extends EntityModel {
   @Value.Default
   default boolean status() {
      return true;
   }

   @Value.Default
   @Nullable
   default String id() { return ""; }

   @Value.Default
   @Nullable
   default String locale() {
      return "";
   }

   @Value.Default
   default boolean currentUser() {
      return false;
   }

   @Value.Default
   default String currentUserName() {
      return "";
   }

   @Value.Default
   default List<String> localesList(){ return new ArrayList<>(); }

   @Value.Default
   default List<PropertyModel> properties() {
      return new ArrayList<>();
   }

   static EditOrganizationPaneModel.Builder builder() {
      return new EditOrganizationPaneModel.Builder();
   }

   final class Builder extends ImmutableEditOrganizationPaneModel.Builder {

   }
}
