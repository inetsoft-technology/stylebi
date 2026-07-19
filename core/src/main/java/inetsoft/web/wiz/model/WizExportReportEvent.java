/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Request body for POST /api/wiz/viewsheet/export-report.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WizExportReportEvent {
   public String getDashboardId() {
      return dashboardId;
   }

   public void setDashboardId(String dashboardId) {
      this.dashboardId = dashboardId;
   }

   public String getFormat() {
      return format;
   }

   public void setFormat(String format) {
      this.format = format;
   }

   public String getPageSize() {
      return pageSize;
   }

   public void setPageSize(String pageSize) {
      this.pageSize = pageSize;
   }

   public String getTitle() {
      return title;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public String getRecap() {
      return recap;
   }

   public void setRecap(String recap) {
      this.recap = recap;
   }

   public List<ChartEntry> getCharts() {
      return charts;
   }

   public void setCharts(List<ChartEntry> charts) {
      this.charts = charts;
   }

   private String dashboardId;
   private String format;
   private String pageSize;
   private String title;
   private String recap;
   private List<ChartEntry> charts;

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class ChartEntry {
      public String getSavedId() {
         return savedId;
      }

      public void setSavedId(String savedId) {
         this.savedId = savedId;
      }

      public String getTitle() {
         return title;
      }

      public void setTitle(String title) {
         this.title = title;
      }

      public String getCaption() {
         return caption;
      }

      public void setCaption(String caption) {
         this.caption = caption;
      }

      public int getOrder() {
         return order;
      }

      public void setOrder(int order) {
         this.order = order;
      }

      public String getInsightsMarkdown() {
         return insightsMarkdown;
      }

      public void setInsightsMarkdown(String insightsMarkdown) {
         this.insightsMarkdown = insightsMarkdown;
      }

      private String savedId;
      private String title;
      private String caption;
      private int order;
      private String insightsMarkdown;
   }
}
