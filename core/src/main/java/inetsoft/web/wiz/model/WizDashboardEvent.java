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
 * Request body for POST /api/wiz/visualization/dashboard.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WizDashboardEvent {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public List<String> getIdentifiers() {
      return identifiers;
   }

   public void setIdentifiers(List<String> identifiers) {
      this.identifiers = identifiers;
   }

   /** When set, overwrite this existing dashboard asset (update-in-place); else create new. */
   public String getExistingIdentifier() {
      return existingIdentifier;
   }

   public void setExistingIdentifier(String existingIdentifier) {
      this.existingIdentifier = existingIdentifier;
   }

   /** Optional grid tile placements (identifier + column span); when absent, layout is unmanaged. */
   public List<TileSpec> getTiles() {
      return tiles;
   }

   public void setTiles(List<TileSpec> tiles) {
      this.tiles = tiles;
   }

   /** Optional number of grid columns for the dashboard layout. */
   public Integer getLayoutColumns() {
      return layoutColumns;
   }

   public void setLayoutColumns(Integer layoutColumns) {
      this.layoutColumns = layoutColumns;
   }

   /** Optional filter specs to apply to the dashboard's viewsheets. */
   public List<FilterSpec> getFilters() {
      return filters;
   }

   public void setFilters(List<FilterSpec> filters) {
      this.filters = filters;
   }

   public List<PerChartFilterSpec> getPerChartFilters() {
      return perChartFilters;
   }

   public void setPerChartFilters(List<PerChartFilterSpec> perChartFilters) {
      this.perChartFilters = perChartFilters;
   }

   private String name;
   private List<String> identifiers;
   private String existingIdentifier;
   private List<TileSpec> tiles;
   private Integer layoutColumns;
   private List<FilterSpec> filters;
   private List<PerChartFilterSpec> perChartFilters;

   /** A single tile's placement within the dashboard grid layout. */
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class TileSpec {
      public String getIdentifier() {
         return identifier;
      }

      public void setIdentifier(String identifier) {
         this.identifier = identifier;
      }

      public int getSpanCols() {
         return spanCols;
      }

      public void setSpanCols(int spanCols) {
         this.spanCols = spanCols;
      }

      public int getSpanRows() {
         return spanRows;
      }

      public void setSpanRows(int spanRows) {
         this.spanRows = spanRows;
      }

      private String identifier;
      private int spanCols = 1;   // default: one cell
      private int spanRows = 1;   // default: one cell
   }

   /** A single filter control's target field within the dashboard filter bar. */
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class FilterSpec {
      public String getField() {
         return field;
      }

      public void setField(String field) {
         this.field = field;
      }

      public String getDataType() {
         return dataType;
      }

      public void setDataType(String dataType) {
         this.dataType = dataType;
      }

      public String getLabel() {
         return label;
      }

      public void setLabel(String label) {
         this.label = label;
      }

      /** When true, bind this filter PRE-aggregation: to the raw source table(s) carrying the
       *  column (a WHERE evaluated before any group-by), so it filters an aggregated chart's
       *  underlying rows and the aggregate re-computes over the subset. Enables filtering by an
       *  orthogonal column that never survives to the chart's final table (e.g. order `state` on a
       *  revenue-by-quarter chart). Default false = bind to the chart's final table (post-agg),
       *  the original behavior. The caller (wiz) is responsible for only setting this for
       *  structurally-safe charts (a simple per-group aggregate) — see WizDashboardFilterBuilder. */
      public boolean isPreAggregation() {
         return preAggregation;
      }

      public void setPreAggregation(boolean preAggregation) {
         this.preAggregation = preAggregation;
      }

      private String field;
      private String dataType;
      private String label;
      private boolean preAggregation;
   }

   /** A single chart-scoped filter, identified by which saved chart it targets. This list is
    *  sparse (NOT positional like {@link TileSpec}) — only charts that need one have an entry;
    *  {@link #identifier} must match one of {@link WizDashboardEvent#getIdentifiers()}. */
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class PerChartFilterSpec {
      public String getIdentifier() {
         return identifier;
      }

      public void setIdentifier(String identifier) {
         this.identifier = identifier;
      }

      public String getField() {
         return field;
      }

      public void setField(String field) {
         this.field = field;
      }

      public String getDataType() {
         return dataType;
      }

      public void setDataType(String dataType) {
         this.dataType = dataType;
      }

      public String getLabel() {
         return label;
      }

      public void setLabel(String label) {
         this.label = label;
      }

      /** When true, bind this filter PRE-aggregation: to the raw source table(s) carrying the
       *  column that are reachable from THIS chart's own final table (a WHERE evaluated before any
       *  group-by), so it filters the chart's underlying rows and its aggregate re-computes over
       *  the subset. Enables filtering by an orthogonal column that never survives to the chart's
       *  final table (e.g. order `state` on a revenue-by-quarter chart). Default false = bind to
       *  the chart's final table (post-agg), the original behavior. Unlike the shared filter bar's
       *  {@link FilterSpec#isPreAggregation()}, the binding is scoped to this one chart's own raw
       *  source, so its structural safety is independent of every other chart on the board — see
       *  WizDashboardFilterBuilder. */
      public boolean isPreAggregation() {
         return preAggregation;
      }

      public void setPreAggregation(boolean preAggregation) {
         this.preAggregation = preAggregation;
      }

      private String identifier;
      private String field;
      private String dataType;
      private String label;
      private boolean preAggregation;
   }
}
