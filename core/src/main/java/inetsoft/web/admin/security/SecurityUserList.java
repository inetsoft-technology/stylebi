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
package inetsoft.web.admin.security;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.*;

/**
 * SecurityUserList contains a list of security users.
 */
@Validated
@Schema(description = "A list of security users.")
public class SecurityUserList {
   /**
    * Gets the list of users.
    *
    * @return the user list.
    */
   @NotNull
   @Schema(description = "The security users.")
   public List<SecurityUser> getUsers() {
      if(users == null) {
         users = new ArrayList<>();
      }

      return users;
   }

   /**
    * Sets the list of users.
    *
    * @param users the user list.
    */
   public void setUsers(List<SecurityUser> users) {
      this.users = users;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      SecurityUserList that = (SecurityUserList) o;
      return Objects.equals(users, that.users);
   }

   @Override
   public int hashCode() {
      return Objects.hash(users);
   }

   @Override
   public String toString() {
      return "SecurityUserList{" +
         "users=" + users +
         '}';
   }

   private List<SecurityUser> users;
}
