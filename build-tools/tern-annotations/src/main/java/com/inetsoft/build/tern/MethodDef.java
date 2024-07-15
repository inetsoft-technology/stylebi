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
package com.inetsoft.build.tern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class MethodDef {
   public MethodDef(String name, String type, String url) {
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

   private final String name;
   private final String type;
   private final String url;
}
