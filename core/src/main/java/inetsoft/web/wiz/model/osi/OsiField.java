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

package inetsoft.web.wiz.model.osi;

import com.fasterxml.jackson.annotation.*;

import java.util.List;

/**
 * Row-level field for grouping, filtering, and metric expressions (Field in osi-schema.json).
 * {@code name} and {@code expression} are required.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OsiField {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public OsiExpression getExpression() {
      return expression;
   }

   public void setExpression(OsiExpression expression) {
      this.expression = expression;
   }

   public OsiDimension getDimension() {
      return dimension;
   }

   public void setDimension(OsiDimension dimension) {
      this.dimension = dimension;
   }

   public String getLabel() {
      return label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public Object getAiContext() {
      return aiContext;
   }

   public void setAiContext(Object aiContext) {
      this.aiContext = aiContext;
   }

   public List<OsiCustomExtension> getCustomExtensions() {
      return customExtensions;
   }

   public void setCustomExtensions(List<OsiCustomExtension> customExtensions) {
      this.customExtensions = customExtensions;
   }

   /**
    * Whether this column is a dimension, as the data source itself declares it (three-valued:
    * null means the source did not say). Not to be confused with {@link #getDimension()}, which
    * carries only the time-dimension flag ({@code is_time}) and is a different JSON key.
    *
    * <p>Deliberately named {@code getIsDimension}, not {@code isDimension} — Jackson's is-getter
    * rule applies to {@code Boolean} the same as {@code boolean}, and {@code isDimension()} would
    * derive the property name {@code dimension}, colliding with {@link #getDimension()}'s own JSON
    * key. The explicit {@code @JsonProperty} pins the key regardless of naming strategy.
    */
   @JsonProperty("isDimension")
   public Boolean getIsDimension() {
      return isDimension;
   }

   public void setIsDimension(Boolean isDimension) {
      this.isDimension = isDimension;
   }

   private String name;
   private OsiExpression expression;
   private OsiDimension dimension;
   private String label;
   private String description;
   private Boolean isDimension;

   @JsonProperty("ai_context")
   private Object aiContext;

   @JsonProperty("custom_extensions")
   private List<OsiCustomExtension> customExtensions;
}
