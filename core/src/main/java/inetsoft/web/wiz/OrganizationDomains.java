/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.web.wiz;

import inetsoft.util.Tool;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@code AppDomains} contains the organization application domains.
 */
@Validated
public class OrganizationDomains {
   private String id;
   private List<String> subDomainIds = Collections.emptyList();

   /**
    * Get the application domain.
    *
    * @return the application domain.
    */
   @NotNull
   public String getId() {
      return id;
   }

   /**
    * Set the application domain.
    *
    * @param id the application domain.
    */
   public void setId(String id) {
      this.id = id;
   }

   /**
    * Get the application sub-domains list.
    *
    * @return the application sub-domains list.
    */
   public List<String> getSubDomainIds() {
      return subDomainIds;
   }

   /**
    * Set the application sub-domains list.
    *
    * @param subDomainIds the application sub-domains list.
    */
   public void setSubDomainIds(List<String> subDomainIds) {
      this.subDomainIds = subDomainIds != null ? subDomainIds : Collections.emptyList();
   }

   /**
    * Parse application domains from a property string.
    * Format: "domain;sub1,sub2,sub3"
    *
    * @param value the property string to parse
    */
   public void parseFromProperty(String value) {
      if(value == null || value.isEmpty()) {
         return;
      }

      int idx = value.indexOf(";");
      if(idx == -1) {
         this.id = value;
         return;
      }

      this.id = value.substring(0, idx);

      if(idx + 1 >= value.length()) {
         return;
      }

      String subs = value.substring(idx + 1);

      if(!subs.isEmpty()) {
         String[] arr = subs.split(",");
         this.subDomainIds = Arrays.asList(arr);
      }
      else {
         this.subDomainIds = Collections.emptyList();
      }
   }

   public String toString() {
      if(Tool.isEmptyString(id)) {
         return "";
      }

      StringBuilder sb = new StringBuilder();
      sb.append(id);

      if(subDomainIds != null && !subDomainIds.isEmpty()) {
         sb.append(";");

         for(int i = 0; i < subDomainIds.size(); i++) {
            if(i > 0) {
               sb.append(",");
            }

            sb.append(subDomainIds.get(i));
         }
      }

      return sb.toString();
   }
}