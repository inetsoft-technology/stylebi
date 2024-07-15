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
package inetsoft.web.admin.schedule;

import inetsoft.web.admin.server.ServerMetrics;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;

public class ScheduleMetrics implements Serializable {
   public int getCycleCount() {
      return cycleCount;
   }

   public void setCycleCount(int cycleCount) {
      this.cycleCount = cycleCount;
   }

   public int getTaskCount() {
      return taskCount;
   }

   public void setTaskCount(int taskCount) {
      this.taskCount = taskCount;
   }

   public Long getUpTime() {
      return upTime;
   }

   public void setUpTime(Long upTime) {
      this.upTime = upTime;
   }

   public Date getStartDate() {
      return startDate;
   }

   public void setStartDate(Date startDate) {
      this.startDate = startDate;
   }

   public DataCycleInfo[] getCycleInfo() {
      return cycleInfo;
   }

   public void setCycleInfo(DataCycleInfo[] cycleInfo) {
      this.cycleInfo = cycleInfo;
   }

   public ScheduleTaskInfo[] getTaskInfo() {
      return taskInfo;
   }

   public void setTaskInfo(ScheduleTaskInfo[] taskInfo) {
      this.taskInfo = taskInfo;
   }

   public ServerMetrics getServerMetrics() {
      return serverMetrics;
   }

   public void setServerMetrics(ServerMetrics serverMetrics) {
      this.serverMetrics = serverMetrics;
   }

   public ScheduleViewsheetsStatus getViewsheets() {
      return viewsheets;
   }

   public void setViewsheets(ScheduleViewsheetsStatus viewsheets) {
      this.viewsheets = viewsheets;
   }

   public ScheduleQueriesStatus getQueries() {
      return queries;
   }

   public void setQueries(ScheduleQueriesStatus queries) {
      this.queries = queries;
   }

   @Override
   public String toString() {
      return "ScheduleMetrics{" +
         "cycleCount=" + cycleCount +
         ", taskCount=" + taskCount +
         ", upTime=" + upTime +
         ", startDate=" + startDate +
         ", cycleInfo=" + Arrays.toString(cycleInfo) +
         ", taskInfo=" + Arrays.toString(taskInfo) +
         ", serverMetrics=" + serverMetrics +
         ", viewsheets=" + viewsheets +
         ", queries=" + queries +
         '}';
   }

   private int cycleCount;
   private int taskCount;
   private Long upTime;
   private Date startDate;
   private DataCycleInfo[] cycleInfo;
   private ScheduleTaskInfo[] taskInfo;
   private ServerMetrics serverMetrics;
   private ScheduleViewsheetsStatus viewsheets;
   private ScheduleQueriesStatus queries;
}
