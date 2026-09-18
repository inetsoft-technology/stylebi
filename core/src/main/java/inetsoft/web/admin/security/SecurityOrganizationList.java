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
 * SecurityOrganizationList contains a list of security organizations.
 */
@Validated
@Schema(description = "A list of security organizations.")
public class SecurityOrganizationList {
   /**
    * Gets the list of organizations.
    *
    * @return the organization list.
    */
   @NotNull
   @Schema(description = "The security organizations.")
   public List<SecurityOrganization> getOrganizations() {
      if(organizations == null) {
         organizations = new ArrayList<>();
      }

      return organizations;
   }

   /**
    * Sets the list of organizations.
    *
    * @param organizations the organization list.
    */
   public void setOrganizations(List<SecurityOrganization> organizations) {
      this.organizations = organizations;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      SecurityOrganizationList that = (SecurityOrganizationList) o;
      return Objects.equals(organizations, that.organizations);
   }

   @Override
   public int hashCode() {
      return Objects.hash(organizations);
   }

   @Override
   public String toString() {
      return "SecurityOrganizationList{" +
         "organizations=" + organizations +
         '}';
   }

   private List<SecurityOrganization> organizations;
}
