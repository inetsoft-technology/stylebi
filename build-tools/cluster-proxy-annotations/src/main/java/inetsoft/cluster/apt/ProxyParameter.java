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

package inetsoft.cluster.apt;

import java.util.Objects;

public final class ProxyParameter {
   public ProxyParameter(String name, String type) {
      this.name = name;
      this.type = type;
   }

   public String getName() {
      return name;
   }

   public String getType() {
      return type;
   }

   @Override
   public boolean equals(Object o) {
      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ProxyParameter that = (ProxyParameter) o;
      return Objects.equals(name, that.name) && Objects.equals(type, that.type);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, type);
   }

   @Override
   public String toString() {
      return "ProxyParameter{" +
         "name='" + name + '\'' +
         ", type='" + type + '\'' +
         '}';
   }

   private final String name;
   private final String type;
}
