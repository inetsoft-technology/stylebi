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

import inetsoft.sree.security.IdentityID;

import java.util.List;

public class ExecuteAsIdentitiesModel {
   public ExecuteAsIdentitiesModel() {}

   public List<IdentityID> getUsers() {
      return users;
   }

   public void setUsers(List<IdentityID> users) {
      this.users = users;
   }

   public List<IdentityID> getGroups() {
      return groups;
   }

   public void setGroups(List<IdentityID> groups) {
      this.groups = groups;
   }

   private List<IdentityID> users;
   private List<IdentityID> groups;
}
