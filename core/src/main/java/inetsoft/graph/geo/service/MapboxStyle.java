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
package inetsoft.graph.geo.service;

import javax.json.JsonValue;

/**
 * Mapbox style information.
 */
public class MapboxStyle {
   public MapboxStyle() {
   }

   public MapboxStyle(JsonValue jobj) {
      this(jobj.asJsonObject().getString("name"), jobj.asJsonObject().getString("id"));
   }

   private MapboxStyle(String name, String id) {
      this.name = name;
      this.id = id;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   // default style
   public static final MapboxStyle DEFAULT = new MapboxStyle("(default)", null);

   private String name;
   private String id;
}
