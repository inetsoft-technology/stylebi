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
package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Layer {
   public String getField() {
      return field;
   }

   public void setField(String field) {
      this.field = field;
   }

   /** "U.S" | "Asia" | "Canada" | "Europe" | "Mexico" */
   public String getMap() {
      return map;
   }

   public void setMap(String map) {
      this.map = map;
   }

   /** "State" | "City" | "Zip" | "Country" | "Province" */
   public String getLayer() {
      return layer;
   }

   public void setLayer(String layer) {
      this.layer = layer;
   }

   private String field;
   /** "U.S" | "Asia" | "Canada" | "Europe" | "Mexico" */
   private String map;
   /** "State" | "City" | "Zip" | "Country" | "Province" */
   private String layer;
}
