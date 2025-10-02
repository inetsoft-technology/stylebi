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
package inetsoft.sree.internal.cluster;

/**
 * Listener for Cluster map events.
 */
public interface MapChangeListener<K, V>  {
   void entryAdded(EntryEvent<K, V> event);

   void entryUpdated(EntryEvent<K, V> event);

   void entryRemoved(EntryEvent<K, V> event);

   default void entryExpired(EntryEvent<K, V> event) {
      // no-op
   }
}
