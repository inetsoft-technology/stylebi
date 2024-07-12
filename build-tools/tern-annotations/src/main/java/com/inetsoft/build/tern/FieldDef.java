/*
 * tern-annotations - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.inetsoft.build.tern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

final class FieldDef {
   FieldDef(String name, String type, String url) {
      this.name = name;
      this.type = type;
      this.url = url;
   }

   public String getName() {
      return name;
   }

   public String getType() {
      return type;
   }

   public String getUrl() {
      return url;
   }

   public ObjectNode toJson(ObjectMapper mapper) {
      ObjectNode node = mapper.createObjectNode();
      node.put("!type", type);

      if(url != null) {
         node.put("!url", url);
      }

      return node;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      FieldDef fieldDef = (FieldDef) o;
      return Objects.equals(name, fieldDef.name) && Objects.equals(type, fieldDef.type) &&
         Objects.equals(url, fieldDef.url);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, type, url);
   }

   @Override
   public String toString() {
      return "FieldDef{" +
         "name='" + name + '\'' +
         ", type='" + type + '\'' +
         ", url='" + url + '\'' +
         '}';
   }

   private final String name;
   private final String type;
   private final String url;
}
