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
package inetsoft.web.admin.server;

import java.io.Serializable;
import java.util.List;

public class GetClusterCacheUsageResponse implements Serializable {
   public GetClusterCacheUsageResponse() {
   }

   public GetClusterCacheUsageResponse(List<CacheUsageRow> rows) {
      this.rows = rows;
   }

   public List<CacheUsageRow> getRows() {
      return rows;
   }

   public void setRows(List<CacheUsageRow> rows) {
      this.rows = rows;
   }

   @Override
   public String toString() {
      return "GetClusterCacheUsageResponse{" +
         "rows=" + rows +
         '}';
   }

   private List<CacheUsageRow> rows;

   public static class CacheUsageRow implements Serializable {
      public CacheUsageRow() {
      }

      public CacheUsageRow(String host, String cacheName, long localEntries, long totalEntries,
                           long avgEntrySize, long localEstimatedSize, long estimatedTotalSize)
      {
         this.host = host;
         this.cacheName = cacheName;
         this.localEntries = localEntries;
         this.totalEntries = totalEntries;
         this.avgEntrySize = avgEntrySize;
         this.localEstimatedSize = localEstimatedSize;
         this.estimatedTotalSize = estimatedTotalSize;
      }

      public String host() {
         return host;
      }

      public void setHost(String host) {
         this.host = host;
      }

      public String cacheName() {
         return cacheName;
      }

      public void setCacheName(String cacheName) {
         this.cacheName = cacheName;
      }

      public long localEntries() {
         return localEntries;
      }

      public void setLocalEntries(long localEntries) {
         this.localEntries = localEntries;
      }

      public long totalEntries() {
         return totalEntries;
      }

      public void setTotalEntries(long totalEntries) {
         this.totalEntries = totalEntries;
      }

      public long avgEntrySize() {
         return avgEntrySize;
      }

      public void setAvgEntrySize(long avgEntrySize) {
         this.avgEntrySize = avgEntrySize;
      }

      public long localEstimatedSize() {
         return localEstimatedSize;
      }

      public void setLocalEstimatedSize(long localEstimatedSize) {
         this.localEstimatedSize = localEstimatedSize;
      }

      public long estimatedTotalSize() {
         return estimatedTotalSize;
      }

      public void setEstimatedTotalSize(long estimatedTotalSize) {
         this.estimatedTotalSize = estimatedTotalSize;
      }

      @Override
      public String toString() {
         return "CacheUsageRow{" +
            "host='" + host + '\'' +
            ", cacheName='" + cacheName + '\'' +
            ", localEntries=" + localEntries +
            ", totalEntries=" + totalEntries +
            ", avgEntrySize=" + avgEntrySize +
            ", localEstimatedSize=" + localEstimatedSize +
            ", estimatedTotalSize=" + estimatedTotalSize +
            '}';
      }

      private String host;
      private String cacheName;
      private long localEntries;
      private long totalEntries;
      private long avgEntrySize;
      private long localEstimatedSize;
      private long estimatedTotalSize;
   }
}
