/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.security;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;

public class ResourceKey implements Serializable, Comparable<ResourceKey> {
   public ResourceKey() {
   }
   public ResourceKey(ResourceType type, String path, String orgID) {
      this.type = type;
      this.path = path;
      this.orgID = fixOrgID(orgID);
   }

   public ResourceType getType() {
      return type;
   }

   public void setType(ResourceType type) {
      this.type = type;
   }

   public String getOrgID() {
      return orgID;
   }

   public void setOrgID(String orgID) {
      this.orgID = fixOrgID(orgID);
   }

   public String getPath() {
      return path;
   }

   public void setPath(String path) {
      this.path = path;
   }

   private String fixOrgID(String orgID) {
      return Organization.getSelfOrganizationID().equals(orgID) ?
         Organization.getDefaultOrganizationID() : orgID;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ResourceKey key = (ResourceKey) o;
      return type == key.type && Objects.equals(orgID, key.orgID) && Objects.equals(path, key.path);
   }

   @Override
   public int hashCode() {
      return Objects.hash(type, path);
   }

   @Override
   public String toString() {
      return "Resource{" +
         "type=" + type +
         ", path='" + path + '\'' +
         ", orgID='" + orgID + '\'' +
         '}';
   }

   @Override
   public int compareTo(ResourceKey o) {
      if(o == null) {
         return 1;
      }

      return Comparator.comparing(ResourceKey::getType)
         .thenComparing(ResourceKey::getOrgID)
         .thenComparing(ResourceKey::getPath).compare(this, o);
   }

   private ResourceType type;
   private String path;
   private String orgID;
}
