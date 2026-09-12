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
   public ProxyParameter(String name, String type, String getter, boolean transferred) {
      this.name = name;
      this.type = type;
      this.getter = getter;
      this.transferred = transferred;
   }

   public String getName() {
      return name;
   }

   public String getType() {
      return type;
   }

   public String getGetter() {
      return getter;
   }

   public boolean isTransferred() {
      return transferred;
   }

   @Override
   public boolean equals(Object o) {
      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ProxyParameter that = (ProxyParameter) o;
      return transferred == that.transferred && Objects.equals(name, that.name) &&
         Objects.equals(type, that.type) && Objects.equals(getter, that.getter);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, type, getter, transferred);
   }

   @Override
   public String toString() {
      return "ProxyParameter{" +
         "name='" + name + '\'' +
         ", type='" + type + '\'' +
         ", getter='" + getter + '\'' +
         ", transferred=" + transferred +
         '}';
   }

   private final String name;
   private final String type;
   private final String getter;
   private final boolean transferred;
}
