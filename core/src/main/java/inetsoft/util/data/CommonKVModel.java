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
package inetsoft.util.data;

import java.io.Serializable;

public class CommonKVModel<T, V> implements Serializable {

   public CommonKVModel() {
   }

   public CommonKVModel(T key, V value) {
      this.key = key;
      this.value = value;
   }

   public T getKey() {
      return key;
   }

   public void setKey(T key) {
      this.key = key;
   }

   public V getValue() {
      return value;
   }

   public void setValue(V value) {
      this.value = value;
   }

   @Override
   public String toString() {
      return "CommonKVModel [key=" + key + ", value=" + value + "]";
   }

   private T key;
   private V value;
}