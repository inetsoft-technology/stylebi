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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.wiz.model;

/**
 * Request body for {@code POST /api/wiz/viewsheet/format} — applies chart-level FORMAT properties
 * (axis titles, y-axis scale, legend placement) to an existing runtime chart and re-renders it.
 * All format fields are optional; only the non-null ones are applied (a no-op field is left as-is).
 */
public class ChartFormatRequest {
   public String getWizRuntimeId() { return wizRuntimeId; }
   public void setWizRuntimeId(String wizRuntimeId) { this.wizRuntimeId = wizRuntimeId; }

   public String getAssemblyName() { return assemblyName; }
   public void setAssemblyName(String assemblyName) { this.assemblyName = assemblyName; }

   public String getViewsheetIdentifier() { return viewsheetIdentifier; }
   public void setViewsheetIdentifier(String viewsheetIdentifier) { this.viewsheetIdentifier = viewsheetIdentifier; }

   public String getXAxisTitle() { return xAxisTitle; }
   public void setXAxisTitle(String xAxisTitle) { this.xAxisTitle = xAxisTitle; }

   public String getYAxisTitle() { return yAxisTitle; }
   public void setYAxisTitle(String yAxisTitle) { this.yAxisTitle = yAxisTitle; }

   public Double getYAxisMin() { return yAxisMin; }
   public void setYAxisMin(Double yAxisMin) { this.yAxisMin = yAxisMin; }

   public Double getYAxisMax() { return yAxisMax; }
   public void setYAxisMax(Double yAxisMax) { this.yAxisMax = yAxisMax; }

   public Double getYAxisIncrement() { return yAxisIncrement; }
   public void setYAxisIncrement(Double yAxisIncrement) { this.yAxisIncrement = yAxisIncrement; }

   public Boolean getYAxisLogarithmic() { return yAxisLogarithmic; }
   public void setYAxisLogarithmic(Boolean yAxisLogarithmic) { this.yAxisLogarithmic = yAxisLogarithmic; }

   /** none | top | right | bottom | left | in_place (case-insensitive); null leaves the legend unchanged. */
   public String getLegendPosition() { return legendPosition; }
   public void setLegendPosition(String legendPosition) { this.legendPosition = legendPosition; }

   /** The runtime viewsheet that holds the live chart (the plugin's active-chart runtimeId). */
   private String wizRuntimeId;
   /** The chart assembly name within that runtime. */
   private String assemblyName;
   /** Optional — carried back on the result so a subsequent save keeps the same identifier. */
   private String viewsheetIdentifier;

   private String xAxisTitle;
   private String yAxisTitle;
   private Double yAxisMin;
   private Double yAxisMax;
   private Double yAxisIncrement;
   private Boolean yAxisLogarithmic;
   private String legendPosition;
}
