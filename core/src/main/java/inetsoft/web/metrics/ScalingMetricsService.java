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

import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.config.MetricsConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

@Component
@Lazy(false)
public class ScalingMetricsService {

   @PostConstruct
   public void initEnabledMetrics() {
      MetricsConfig config = InetsoftConfig.getInstance().getMetrics();
      boolean movingAverage = config != null && config.isMovingAverage();
      long movingAveragePeriodSeconds = config == null ? 15 : config.getMovingAveragePeriodSeconds();
      int movingAverageCount = config == null ? 0 : config.getMovingAverageCount();

      jvmCpu = new JvmCpuScalingMetric(movingAverage, movingAverageCount);
      jvmMemory = new JvmMemoryScalingMetric(movingAverage, movingAverageCount);
      systemCpu = new SystemCpuScalingMetric(movingAverage, movingAverageCount);
      systemMemory = new SystemMemoryScalingMetric(movingAverage, movingAverageCount);
      scheduler = new SchedulerScalingMetric(movingAverage, movingAverageCount);
      cacheSwapMemory = new CacheSwapMemoryScalingMetric(movingAverage, movingAverageCount);
      cacheSwapWait = new CacheSwapWaitScalingMetric(movingAverage, movingAverageCount);

      if(config == null) {
         enabledMetrics = List.of(jvmCpu, scheduler, cacheSwapMemory, cacheSwapWait);
      }
      else {
         enabledMetrics = new ArrayList<>();

         if(config.isJvmCpu()) {
            enabledMetrics.add(jvmCpu);
         }

         if(config.isJvmMemory()) {
            enabledMetrics.add(jvmMemory);
         }

         if(config.isContainerCpu()) {
            enabledMetrics.add(systemCpu);
         }

         if(config.isContainerMemory()) {
            enabledMetrics.add(systemMemory);
         }

         if(config.isScheduler()) {
            enabledMetrics.add(scheduler);
         }

         if(config.isCacheSwapMemory()) {
            enabledMetrics.add(cacheSwapMemory);
         }

         if(config.isCacheSwapWait()) {
            enabledMetrics.add(cacheSwapWait);
         }

         for(ScalingMetricPublisherFactory factory :
             ServiceLoader.load(ScalingMetricPublisherFactory.class))
         {
            if(factory.isSupported(config)) {
               publisher = factory.createMetricPublisher(config);
               break;
            }
         }
      }

      executor = Executors.newSingleThreadScheduledExecutor(
         r -> new Thread(r, "ScalingMetricsService"));
      executor.scheduleAtFixedRate(this::update, 0L, movingAveragePeriodSeconds, TimeUnit.SECONDS);
   }

   @PreDestroy
   public void stopPolling() {
      if(executor != null) {
         executor.shutdownNow();
      }
   }

   private void update() {
      jvmCpu.update();
      jvmMemory.update();
      systemCpu.update();
      systemMemory.update();
      scheduler.update();
      cacheSwapMemory.update();
      cacheSwapWait.update();

      if(publisher != null) {
         ScalingMetricData data = new ScalingMetricData(
            jvmCpu.get(), jvmCpu.calculate(),
            jvmMemory.get(), jvmMemory.calculate(),
            systemCpu.get(), systemMemory.calculate(),
            systemMemory.get(), systemMemory.calculate(),
            scheduler.get(), scheduler.calculate(),
            cacheSwapMemory.get(), cacheSwapMemory.calculate(),
            cacheSwapWait.get(), cacheSwapWait.calculate(),
            getServerUtilization()
         );
         publisher.publish(data);
      }
   }

   public double getJvmCpuUtilization() {
      return jvmCpu.get();
   }

   public double getJvmCpuDetail() {
      return jvmCpu.calculate();
   }

   public double getJvmMemoryUtilization() {
      return jvmMemory.get();
   }

   public double getJvmMemoryDetail() {
      return jvmMemory.calculate();
   }

   public double getSystemCpuUtilization() {
      return systemCpu.get();
   }

   public double getSystemCpuDetail() {
      return systemCpu.calculate();
   }

   public double getSystemMemoryUtilization() {
      return systemMemory.get();
   }

   public double getSystemMemoryDetail() {
      return systemMemory.calculate();
   }

   public double getSchedulerUtilization() {
      return scheduler.get();
   }

   public double getSchedulerDetail() {
      return scheduler.calculate();
   }

   public double getCacheSwapMemoryUtilization() {
      return cacheSwapMemory.get();
   }

   public double getCacheSwapMemoryDetail() {
      return cacheSwapMemory.calculate();
   }

   public double getCacheSwapWaitUtilization() {
      return cacheSwapWait.get();
   }

   public double getCacheSwapWaitDetail() {
      return cacheSwapWait.calculate();
   }

   public double getServerUtilization() {
      if(enabledMetrics == null || enabledMetrics.isEmpty()) {
         return 0D;
      }

      return enabledMetrics.stream()
         .map(ScalingMetric::get)
         .max(Double::compare)
         .orElse(0D);
   }

   private JvmCpuScalingMetric jvmCpu;
   private JvmMemoryScalingMetric jvmMemory;
   private SystemCpuScalingMetric systemCpu;
   private SystemMemoryScalingMetric systemMemory;
   private SchedulerScalingMetric scheduler;
   private CacheSwapMemoryScalingMetric cacheSwapMemory;
   private CacheSwapWaitScalingMetric cacheSwapWait;
   private List<ScalingMetric> enabledMetrics;
   private ScheduledExecutorService executor;
   private ScalingMetricPublisher publisher;
}
