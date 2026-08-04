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

import java.util.ArrayList;
import java.util.List;

/**
 * Response for {@code POST /api/wiz/chart/aestheticModel} — what the chart's COLOR aesthetic can
 * currently accept. Read-only.
 *
 * A caller of {@code /viewsheet/colors} cannot choose between staticColor / measureColors /
 * paletteName / colorList / categoryColors without this: which of them applies is fixed by whether a
 * field sits on the color aesthetic and whether that field is a dimension or a measure, and a parameter
 * that does not fit the binding is rejected.
 */
public class ChartAestheticModel {
   /**
    * The live runtime id. Reading the model restores a reaped runtime when it has to, which changes the
    * id, so this is echoed unconditionally and the caller must adopt it before applying anything —
    * otherwise the apply targets the dead instance. Same contract as {@link WizBrowseDataResponse}.
    */
   public String getRuntimeId() { return runtimeId; }
   public void setRuntimeId(String runtimeId) { this.runtimeId = runtimeId; }

   /** null when nothing is on the color aesthetic (the staticColor / measureColors case). */
   public ColorField getColorField() { return colorField; }
   public void setColorField(ColorField colorField) { this.colorField = colorField; }

   /** null unless {@link #getColorField()} is a dimension. */
   public ColorValues getColorValues() { return colorValues; }
   public void setColorValues(ColorValues colorValues) { this.colorValues = colorValues; }

   /** The measures that can carry a color aesthetic; the only valid {@code measureColors} keys. */
   public List<AestheticAggregate> getAestheticAggregates() { return aestheticAggregates; }
   public void setAestheticAggregates(List<AestheticAggregate> aestheticAggregates) {
      this.aestheticAggregates = aestheticAggregates;
   }

   /** The field on the color aesthetic, as {@code VSChartInfo.getColorField()} resolves it. */
   public static class ColorField {
      public ColorField() {
      }

      public ColorField(String fullName, String role) {
         this.fullName = fullName;
         this.role = role;
      }

      public String getFullName() { return fullName; }
      public void setFullName(String fullName) { this.fullName = fullName; }

      /** "dimension" (categorical) or "measure" (continuous scale — gradient only). */
      public String getRole() { return role; }
      public void setRole(String role) { this.role = role; }

      private String fullName;
      private String role;
   }

   /**
    * The color dimension's values, as the strings {@code categoryColors} must be keyed by.
    *
    * These are produced with {@code GTool.toString}, which is the SAME function
    * {@code CategoricalColorFrame.setColor}/{@code getColor} key the color map with. A key copied from
    * this list therefore matches by construction, and no type or format conversion happens on the way
    * back in — which is what keeps a date-grouped color dimension ("Month(order_date)") working without
    * either side having to agree on a display pattern.
    */
   public static class ColorValues {
      public ColorValues() {
      }

      public ColorValues(List<String> values, boolean truncated) {
         this.values = values;
         this.truncated = truncated;
      }

      public List<String> getValues() { return values; }
      public void setValues(List<String> values) { this.values = values; }

      /** true when the value set was cut off by the cap, so the list is not exhaustive. */
      public boolean isTruncated() { return truncated; }
      public void setTruncated(boolean truncated) { this.truncated = truncated; }

      private List<String> values = new ArrayList<>();
      private boolean truncated;
   }

   /** A measure that can carry a color / marker aesthetic. */
   public static class AestheticAggregate {
      public AestheticAggregate() {
      }

      public AestheticAggregate(String fullName) {
         this.fullName = fullName;
      }

      /**
       * The aggregate's full name ("Sum(sales)") — {@code VSAggregateRef.getFullName()}, the same
       * convention the wiz API already uses for {@code binding.measures[]}, {@code slots}, and
       * {@code rankingCol}, so one spelling serves every side.
       */
      public String getFullName() { return fullName; }
      public void setFullName(String fullName) { this.fullName = fullName; }

      private String fullName;
   }

   private String runtimeId;
   private ColorField colorField;
   private ColorValues colorValues;
   private List<AestheticAggregate> aestheticAggregates = new ArrayList<>();
}
