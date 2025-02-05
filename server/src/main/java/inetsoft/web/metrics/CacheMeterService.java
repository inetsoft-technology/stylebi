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
package inetsoft.web.metrics;

import inetsoft.report.internal.paging.PageGroup;
import inetsoft.uql.table.XTableColumn;
import inetsoft.uql.table.XTableFragment;
import inetsoft.util.swap.*;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CacheMeterService implements XSwappableMonitor, MeterBinder {
   private Counter reportHits;
   private Counter reportMisses;
   private Counter reportRead;
   private Counter reportWritten;
   private Counter dataHits;
   private Counter dataMisses;
   private Counter dataRead;
   private Counter dataWritten;

   private final AtomicInteger reportMemory = new AtomicInteger(0);
   private final AtomicInteger reportDisk = new AtomicInteger(0);
   private final AtomicInteger dataMemory = new AtomicInteger(0);
   private final AtomicInteger dataDisk = new AtomicInteger(0);

   @Override
   public void bindTo(MeterRegistry registry) {
      reportHits = Counter
         .builder("inetsoft.cache.requests")
         .description("The number of cache requests")
         .tag("result", "hit")
         .tag("cache", "report")
         .register(registry);
      reportMisses = Counter
         .builder("inetsoft.cache.requests")
         .description("The number of cache requests")
         .tag("result", "miss")
         .tag("cache", "report")
         .register(registry);
      reportRead = Counter
         .builder("inetsoft.cache.transfer")
         .description("The number of bytes transferred to and from disk cache")
         .baseUnit(BaseUnits.BYTES)
         .tag("direction", "read")
         .tag("cache", "report")
         .register(registry);
      reportWritten = Counter
         .builder("inetsoft.cache.transfer")
         .description("The number of bytes transferred to and from disk cache")
         .baseUnit(BaseUnits.BYTES)
         .tag("direction", "write")
         .tag("cache", "report")
         .register(registry);
      dataHits = Counter
         .builder("inetsoft.cache.requests")
         .description("The number of cache requests")
         .tag("result", "hit")
         .tag("cache", "data")
         .register(registry);
      dataMisses = Counter
         .builder("inetsoft.cache.requests")
         .description("The number of cache requests")
         .tag("result", "miss")
         .tag("cache", "data")
         .register(registry);
      dataRead = Counter
         .builder("inetsoft.cache.transfer")
         .description("The number of bytes transferred to and from disk cache")
         .baseUnit(BaseUnits.BYTES)
         .tag("direction", "read")
         .tag("cache", "data")
         .register(registry);
      dataWritten = Counter
         .builder("inetsoft.cache.transfer")
         .description("The number of bytes transferred to and from disk cache")
         .baseUnit(BaseUnits.BYTES)
         .tag("direction", "write")
         .tag("cache", "data")
         .register(registry);

      Gauge.builder("inetsoft.cache.size", reportMemory, AtomicInteger::doubleValue)
         .description("The number of items in the cache")
         .tag("cache", "report")
         .tag("location", "memory")
         .register(registry);
      Gauge.builder("inetsoft.cache.size", reportDisk, AtomicInteger::doubleValue)
         .description("The number of items in the cache")
         .tag("cache", "report")
         .tag("location", "disk")
         .register(registry);
      Gauge.builder("inetsoft.cache.size", dataMemory, AtomicInteger::doubleValue)
         .description("The number of items in the cache")
         .tag("cache", "data")
         .tag("location", "memory")
         .register(registry);
      Gauge.builder("inetsoft.cache.size", dataDisk, AtomicInteger::doubleValue)
         .description("The number of items in the cache")
         .tag("cache", "data")
         .tag("location", "disk")
         .register(registry);
   }

   @PostConstruct
   public void registerMonitor() {
      XSwapper.registerMonitor(this);
   }

   @PreDestroy
   public void deregisterMonitor() {
      XSwapper.deregisterMonitor(this);
   }

   @Scheduled(fixedRate = 5000L, initialDelay = 0L)
   public void updateGauges() {
      int reportMemoryCount = 0;
      int reportDiskCount = 0;
      int dataMemoryCount = 0;
      int dataDiskCount = 0;

      for(XSwappable swap : XSwapper.getAllSwappables()) {
         if(swap instanceof PageGroup) {
            PageGroup group = (PageGroup) swap;

            if(group.isSwappable()) {
               if(group.isValid()) {
                  ++reportMemoryCount;
               }
               else {
                  ++reportDiskCount;
               }
            }
         }
         else if((swap instanceof XIntFragment) || (swap instanceof XObjectFragment)) {
            if(swap.isSwappable()) {
               if(swap.isValid()) {
                  ++dataMemoryCount;
               }
               else {
                  ++dataDiskCount;
               }
            }
         }
         else if(swap instanceof XTableFragment) {
            XTableFragment table = (XTableFragment) swap;

            if(!table.isDisposed()) {
               if(table.isValid()) {
                  dataMemoryCount += table.getColumns().length;
               }
               else {
                  for(XTableColumn column : table.getColumns()) {
                     if(column.isSerializable()) {
                        ++dataDiskCount;
                     }
                     else {
                        ++dataMemoryCount;
                     }
                  }
               }
            }
         }
      }

      reportMemory.set(reportMemoryCount);
      reportDisk.set(reportDiskCount);
      dataMemory.set(dataMemoryCount);
      dataDisk.set(dataDiskCount);
   }

   @Override
   public void countHits(int type, int hits) {
      if(type == XSwappableMonitor.REPORT) {
         reportHits.increment(hits);
      }
      else {
         dataHits.increment(hits);
      }
   }

   @Override
   public void countMisses(int type, int misses) {
      if(type == XSwappableMonitor.REPORT) {
         reportMisses.increment(misses);
      }
      else {
         dataMisses.increment(misses);
      }
   }

   @Override
   public void countRead(long num, int type) {
      if(type == XSwappableMonitor.REPORT) {
         reportRead.increment(num);
      }
      else {
         dataRead.increment(num);
      }
   }

   @Override
   public void countWrite(long num, int type) {
      if(type == XSwappableMonitor.REPORT) {
         reportWritten.increment(num);
      }
      else {
         dataWritten.increment(num);
      }
   }

   @Override
   public boolean isLevelQualified(String attr) {
      return true;
   }
}
