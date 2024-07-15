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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Data transfer object that represents the {@link GaugePropertyDialogModel} for the
 * gauge property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RangePaneModel {
   public boolean isGradient() {
      return gradient;
   }

   public void setGradient(boolean gradient) {
      this.gradient = gradient;
   }

   public String[] getRangeValues() {
      if(rangeValues == null) {
         rangeValues = new String[5];
      }

      return rangeValues;
   }

   public void setRangeValues(String[] rangeValues) {
      this.rangeValues = rangeValues;
   }

   public String[] getRangeColorValues() {
      if(rangeColorValues == null) {
         rangeColorValues = new String[6];
      }

      return rangeColorValues;
   }

   public void setRangeColorValues(String[] rangeColorValues) {
      this.rangeColorValues = rangeColorValues;
   }

   public String getTargetValue() {
      return targetValue;
   }

   public void setTargetValue(String targetValue) {
      this.targetValue = targetValue;
   }

   @Override
   public String toString() {
      return "RangePaneModel{" +
         "gradientColor='" + gradient + '\'' +
         '}';
   }

   private boolean gradient;
   private String[] rangeValues = new String[5];
   private String[] rangeColorValues = new String[6];
   private String targetValue;
}
