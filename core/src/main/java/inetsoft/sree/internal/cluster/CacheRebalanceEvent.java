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

package inetsoft.sree.internal.cluster;

import java.util.EventObject;

public class CacheRebalanceEvent extends EventObject {
   public CacheRebalanceEvent(Object source, String cacheName, long timestamp, int partition) {
      super(source);
      this.cacheName = cacheName;
      this.timestamp = timestamp;
      this.partition = partition;
   }

   public String getCacheName() {
      return cacheName;
   }

   public long getTimestamp() {
      return timestamp;
   }

   public int getPartition() {
      return partition;
   }

   private final String cacheName;
   private final long timestamp;
   private final int partition;
}
