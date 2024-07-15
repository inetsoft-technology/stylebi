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
import inetsoft.sree.security.IdentityID;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableUsersModel.class)
@JsonDeserialize(as = ImmutableUsersModel.class)
public abstract class UsersModel {
   @Nullable
   public abstract String adminName();

   @Value.Default
   public IdentityID[] owners() {
      return new IdentityID[0];
   }

   @Value.Default
   public IdentityID[] groups() {
      return new IdentityID[0];
   }

   @Value.Default
   public String[] groupBaseNames() {
      return new String[0];
   }

   @Value.Default
   public List<IdentityID> emailUsers() {
      return new ArrayList<>(0);
   }

   @Value.Default
   public List<IdentityID> emailGroups() {
      return new ArrayList<>(0);
   }

   @Value.Default
   public String[] emailGroupBaseNames() {
      return new String[0];
   }

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableUsersModel.Builder {}
}
