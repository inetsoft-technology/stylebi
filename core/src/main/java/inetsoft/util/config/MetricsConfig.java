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

package inetsoft.util.config;

import inetsoft.util.config.crd.CRDProperty;

import java.io.Serializable;

@InetsoftConfigBean
public class MetricsConfig implements Serializable {
   /**
    * A flag that indicates if the JVM heap memory utilization should be used as a scaling metric.
    */
   public boolean isJvmMemory() {
      return jvmMemory;
   }

   public void setJvmMemory(boolean jvmMemory) {
      this.jvmMemory = jvmMemory;
   }

   /**
    * A flag that indicates if the container memory utilization should be used as a scaling metric.
    */
   public boolean isContainerMemory() {
      return containerMemory;
   }

   public void setContainerMemory(boolean containerMemory) {
      this.containerMemory = containerMemory;
   }

   /**
    * A flag that indicates if the JVM CPU utilization should be used as a scaling metric.
    */
   public boolean isJvmCpu() {
      return jvmCpu;
   }

   public void setJvmCpu(boolean jvmCpu) {
      this.jvmCpu = jvmCpu;
   }

   /**
    * A flag that indicates if the container CPU utilization should be used as a scaling metric.
    */
   public boolean isContainerCpu() {
      return containerCpu;
   }

   public void setContainerCpu(boolean containerCpu) {
      this.containerCpu = containerCpu;
   }

   public boolean isScheduler() {
      return scheduler;
   }

   public void setScheduler(boolean scheduler) {
      this.scheduler = scheduler;
   }

   public boolean isCacheSwapMemory() {
      return cacheSwapMemory;
   }

   public void setCacheSwapMemory(boolean cacheSwapMemory) {
      this.cacheSwapMemory = cacheSwapMemory;
   }

   public boolean isCacheSwapWait() {
      return cacheSwapWait;
   }

   public void setCacheSwapWait(boolean cacheSwapWait) {
      this.cacheSwapWait = cacheSwapWait;
   }

   public boolean isMovingAverage() {
      return movingAverage;
   }

   public void setMovingAverage(boolean movingAverage) {
      this.movingAverage = movingAverage;
   }

   public int getMovingAveragePeriodSeconds() {
      return movingAveragePeriodSeconds;
   }

   public void setMovingAveragePeriodSeconds(int movingAveragePeriodSeconds) {
      this.movingAveragePeriodSeconds = movingAveragePeriodSeconds;
   }

   public int getMovingAverageCount() {
      return movingAverageCount;
   }

   public void setMovingAverageCount(int movingAverageCount) {
      this.movingAverageCount = movingAverageCount;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public CloudWatchMetricsConfig getCloudwatch() {
      return cloudwatch;
   }

   public void setCloudwatch(CloudWatchMetricsConfig cloudwatch) {
      this.cloudwatch = cloudwatch;
   }

   @CRDProperty(description = "A flag that indicates if the JVM heap memory utilization should be used as a scaling metric.")
   private boolean jvmMemory = false;
   @CRDProperty(description = "A flag that indicates if the container memory utilization should be used as a scaling metric.")
   private boolean containerMemory = false;
   @CRDProperty(description = "A flag that indicates if the JVM CPU utilization should be used as a scaling metric.")
   private boolean jvmCpu = true;
   @CRDProperty(description = "A flag that indicates if the container CPU utilization should be used as a scaling metric.")
   private boolean containerCpu = false;
   @CRDProperty(description = "A flag that indicates if the scheduler thread utilization should be used as a scaling metric.")
   private boolean scheduler = true;
   @CRDProperty(description = "A flag that indicates if the cache swapping memory state should be used as a scaling metric.")
   private boolean cacheSwapMemory = true;
   @CRDProperty(description = "A flag that indicates if the number of threads waiting for cache swapping should be used as a scaling metric.")
   private boolean cacheSwapWait = true;
   private boolean movingAverage = false;
   @CRDProperty(description = "The interval at which the metric values are recorded for the moving average")
   private int movingAveragePeriodSeconds = 15;
   @CRDProperty(description = "The number of metric values to include in the moving average")
   private int movingAverageCount = 8;
   private String type;
   private CloudWatchMetricsConfig cloudwatch;
}
