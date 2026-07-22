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
 * Request body for {@code POST /api/wiz/viewsheet/highlight}.
 *
 * <p>The wiz-services caller POSTs:
 * {@code { runtimeId, viewsheetIdentifier?, assemblyName?, copy?, sampleMaxRows?,
 * highlightModel: { highlights: [ { name, field?, foreground?, background?, applyArea?, applyRow?,
 * fontInfo?, conditions: ConditionNode[] } ] } } }.
 *
 * <p>Each highlight rule's {@code conditions} reuse the same {@link VisualizationConditionModel.ConditionNode}
 * tree as {@code apply_filter}; the service converts them to a StyleBI {@code ConditionList} with the same
 * converter the filter path uses, so a rule always applies (a flat leaf would otherwise silently no-op).
 * Highlight is viz styling only — it colors marks/labels (chart) or cells/rows (table) that meet a
 * condition; it does NOT drop rows.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplyHighlightModel {
   public String getRuntimeId() {
      return runtimeId;
   }

   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   /** Optional; resolve the sole/primary wiz assembly if absent. */
   public String getAssemblyName() {
      return assemblyName;
   }

   public void setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;
   }

   /** When true, duplicate the target assembly first and apply the highlight to the COPY instead of
    *  mutating {@link #assemblyName} in place — the original chart is left untouched. Mirrors
    *  {@code ChartColorsRequest}/{@code ChartFormatRequest}'s {@code copy} flag. Default false
    *  (in-place, the existing behavior). */
   public boolean isCopy() {
      return copy;
   }

   public void setCopy(boolean copy) {
      this.copy = copy;
   }

   public String getViewsheetIdentifier() {
      return viewsheetIdentifier;
   }

   public void setViewsheetIdentifier(String viewsheetIdentifier) {
      this.viewsheetIdentifier = viewsheetIdentifier;
   }

   public HighlightModel getHighlightModel() {
      return highlightModel;
   }

   public void setHighlightModel(HighlightModel highlightModel) {
      this.highlightModel = highlightModel;
   }

   public Integer getSampleMaxRows() {
      return sampleMaxRows;
   }

   public void setSampleMaxRows(Integer sampleMaxRows) {
      this.sampleMaxRows = sampleMaxRows;
   }

   private String runtimeId;
   private String assemblyName;
   private boolean copy;
   private String viewsheetIdentifier;
   private HighlightModel highlightModel;
   private Integer sampleMaxRows;

   /** The set of highlight rules to apply (REPLACES any existing highlight on the assembly). */
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class HighlightModel {
      public List<Highlight> getHighlights() {
         return highlights;
      }

      public void setHighlights(List<Highlight> highlights) {
         this.highlights = highlights;
      }

      private List<Highlight> highlights;
   }

   /**
    * One highlight rule: a condition tree plus the styling to apply when it matches. Styling fields are
    * type-specific (chart uses {@code foreground} + {@code applyArea}; table/crosstab add {@code background}
    * + {@code applyRow}; output uses {@code foreground}/{@code background}); unused fields are ignored.
    */
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class Highlight {
      public String getName() {
         return name;
      }

      public void setName(String name) {
         this.name = name;
      }

      /** The measure/dimension the rule reads (chart/table highlights). */
      public String getField() {
         return field;
      }

      public void setField(String field) {
         this.field = field;
      }

      /** Foreground/text color as a hex string, e.g. {@code "#FF0000"}. */
      public String getForeground() {
         return foreground;
      }

      public void setForeground(String foreground) {
         this.foreground = foreground;
      }

      /** Background/fill color as a hex string (table/crosstab/output only). */
      public String getBackground() {
         return background;
      }

      public void setBackground(String background) {
         this.background = background;
      }

      /** Chart only: {@code "dataPoint"} (color the mark) or {@code "dataLabel"} (style the label). */
      public String getApplyArea() {
         return applyArea;
      }

      public void setApplyArea(String applyArea) {
         this.applyArea = applyArea;
      }

      /** Table/crosstab only: apply the styling to the whole row rather than the single cell. */
      public boolean isApplyRow() {
         return applyRow;
      }

      public void setApplyRow(boolean applyRow) {
         this.applyRow = applyRow;
      }

      public FontInfo getFontInfo() {
         return fontInfo;
      }

      public void setFontInfo(FontInfo fontInfo) {
         this.fontInfo = fontInfo;
      }

      /** The condition tree that triggers the styling — same shape as an apply_filter conditionModel. */
      public List<VisualizationConditionModel.ConditionNode> getConditions() {
         return conditions;
      }

      public void setConditions(List<VisualizationConditionModel.ConditionNode> conditions) {
         this.conditions = conditions;
      }

      private String name;
      private String field;
      private String foreground;
      private String background;
      private String applyArea;
      private boolean applyRow;
      private FontInfo fontInfo;
      private List<VisualizationConditionModel.ConditionNode> conditions;
   }

   /** Minimal font styling for a highlight rule. {@code fontSize} is a point size (e.g. "12"). */
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class FontInfo {
      public String getFontFamily() {
         return fontFamily;
      }

      public void setFontFamily(String fontFamily) {
         this.fontFamily = fontFamily;
      }

      public String getFontSize() {
         return fontSize;
      }

      public void setFontSize(String fontSize) {
         this.fontSize = fontSize;
      }

      public boolean isBold() {
         return bold;
      }

      public void setBold(boolean bold) {
         this.bold = bold;
      }

      public boolean isItalic() {
         return italic;
      }

      public void setItalic(boolean italic) {
         this.italic = italic;
      }

      private String fontFamily;
      private String fontSize;
      private boolean bold;
      private boolean italic;
   }
}
