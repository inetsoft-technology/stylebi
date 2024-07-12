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
package inetsoft.sree.internal.cluster;

public class EntryEvent<K, V> {
   public EntryEvent(String mapName, K key, V oldValue, V value) {
      this.mapName = mapName;
      this.key = key;
      this.oldValue = oldValue;
      this.value = value;
   }

   public String getMapName() {
      return mapName;
   }

   public void setMapName(String mapName) {
      this.mapName = mapName;
   }

   public K getKey() {
      return key;
   }

   public void setKey(K key) {
      this.key = key;
   }

   public V getOldValue() {
      return oldValue;
   }

   public void setOldValue(V oldValue) {
      this.oldValue = oldValue;
   }

   public V getValue() {
      return value;
   }

   public void setValue(V value) {
      this.value = value;
   }

   private String mapName;
   private K key;
   private V oldValue;
   private V value;
}
