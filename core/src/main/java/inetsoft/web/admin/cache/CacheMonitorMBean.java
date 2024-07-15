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
package inetsoft.web.admin.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import javax.management.openmbean.*;
import java.util.Date;

@Component
@ManagedResource
public class CacheMonitorMBean {
   @Autowired
   public CacheMonitorMBean(CacheService cacheService) {
      this.cacheService = cacheService;
   }

   @ManagedAttribute
   public int getDataMemoryCount() {
      return cacheService.getDataMemoryCount();
   }

   @ManagedAttribute
   public int getDataDiskCount() {
      return cacheService.getDataDiskCount();
   }

   @ManagedAttribute
   public long getDataBytesRead() {
      return cacheService.getDataBytesRead();
   }

   @ManagedAttribute
   public long getDataBytesWritten() {
      return cacheService.getDataBytesWritten();
   }

   @ManagedAttribute
   public TabularData getMemoryCacheHistory() throws OpenDataException {
      String[] names = { "Time", "Data" };
      CompositeType rowType = new CompositeType(
         "MemoryCacheHistoryItem", "Information about the memory cache at a point in time",
         names, names, new OpenType[] { SimpleType.DATE, SimpleType.INTEGER });
      TabularType tableType = new TabularType(
         "MemoryCacheHistory", "Historical information about the memory cache", rowType, names);
      TabularDataSupport data = new TabularDataSupport(tableType);

      for(CacheState state : cacheService.getCacheHistory()) {
         CompositeData row = new CompositeDataSupport(
            rowType, names,
            new Object[] { new Date(state.time()),  state.dataMemoryCount() });
         data.put(row);
      }

      return data;
   }

   @ManagedAttribute
   public TabularData getDiskCacheHistory() throws OpenDataException {
      String[] names = { "Time", "Data" };
      CompositeType rowType = new CompositeType(
         "DiskCacheHistoryItem", "Information about the disk cache at a point in time",
         names, names, new OpenType[] { SimpleType.DATE, SimpleType.INTEGER });
      TabularType tableType = new TabularType(
         "DiskCacheHistory", "Historical information about the disk cache", rowType, names);
      TabularDataSupport data = new TabularDataSupport(tableType);

      for(CacheState state : cacheService.getCacheHistory()) {
         CompositeData row = new CompositeDataSupport(
            rowType, names,
            new Object[] { new Date(state.time()), state.dataDiskCount() });
         data.put(row);
      }

      return data;
   }

   @ManagedAttribute
   public TabularData getSwappingHistory() throws OpenDataException {
      String[] names = { "Time", "Read", "Written" };
      CompositeType rowType = new CompositeType(
         "SwappingHistoryItem", "Information about swapping at a point in time",
         names, names, new OpenType[] { SimpleType.DATE, SimpleType.LONG, SimpleType.LONG });
      TabularType tableType = new TabularType(
         "SwappingHistory", "Historical information about swapping", rowType, names);
      TabularDataSupport data = new TabularDataSupport(tableType);

      for(CacheState state : cacheService.getCacheHistory()) {
         CompositeData row = new CompositeDataSupport(
            rowType, names,
            new Object[] {
               new Date(state.time()),
               state.dataBytesRead(),
               state.dataBytesWritten()
            });
         data.put(row);
      }

      return data;
   }

   @ManagedAttribute
   public TabularData getDataCacheInfo() throws OpenDataException {
      String[] names = { "Location", "DataObjects", "HitsOrMisses", "Swapped" };
      CompositeType rowType = new CompositeType(
         "DataCacheLocationInfo", "Summary information about the data memory or disk cache",
         names, new String[] { "Location", "Data Objects", "Hits/Misses", "Swapped" },
         new OpenType[] { SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING });
      TabularType tableType = new TabularType(
         "DataCacheInfo", "Summary information about the data cache", rowType, names);
      TabularDataSupport data = new TabularDataSupport(tableType);

      for(CacheMonitoringTableModel model : cacheService.getDataGrid(null)) {
         CompositeData row = new CompositeDataSupport(
            rowType, names,
            new Object[] { model.location(), model.count(), model.hits(), model.read() });
         data.put(row);
      }

      return data;
   }

   private final CacheService cacheService;
}
