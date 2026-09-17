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
 * SecurityGroupList contains a list of security groups.
 */
@Validated
@Schema(description = "A list of security groups.")
public class SecurityGroupList {
   /**
    * Gets the list of groups.
    *
    * @return the group list.
    */
   @NotNull
   @Schema(description = "The security groups.")
   public List<SecurityGroup> getGroups() {
      if(groups == null) {
         groups = new ArrayList<>();
      }

      return groups;
   }

   /**
    * Sets the list of groups.
    *
    * @param groups the group list.
    */
   public void setGroups(List<SecurityGroup> groups) {
      this.groups = groups;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      SecurityGroupList that = (SecurityGroupList) o;
      return Objects.equals(groups, that.groups);
   }

   @Override
   public int hashCode() {
      return Objects.hash(groups);
   }

   @Override
   public String toString() {
      return "SecurityGroupList{" +
         "groups=" + groups +
         '}';
   }

   private List<SecurityGroup> groups;
}
