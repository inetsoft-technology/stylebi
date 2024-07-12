/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.util.data;

public class CommonKVModel<T, V> {

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