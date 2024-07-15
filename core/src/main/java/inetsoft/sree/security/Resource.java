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
package inetsoft.sree.security;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;

public final class Resource implements Serializable, Comparable<Resource> {
   public Resource() {
   }

   public Resource(ResourceType type, String path) {
      this.type = type;
      this.path = path;
   }

   public ResourceType getType() {
      return type;
   }

   public void setType(ResourceType type) {
      this.type = type;
   }

   public String getPath() {
      return path;
   }

   public void setPath(String path) {
      this.path = path;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      Resource resource = (Resource) o;
      return type == resource.type && Objects.equals(path, resource.path);
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
         '}';
   }

   @Override
   public int compareTo(Resource o) {
      if(o == null) {
         return 1;
      }

      return Comparator.comparing(Resource::getType)
         .thenComparing(Resource::getPath).compare(this, o);
   }

   private ResourceType type;
   private String path;
}
