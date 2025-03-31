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

import java.io.Serializable;

/**
 * Data transfer object that represents the {@link GaugePropertyDialogModel} for the
 * gauge property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NumberRangePaneModel implements Serializable {
   public String getMinorIncrement() {
      return minorIncrement;
   }

   public void setMinorIncrement(String minorIncrement) {
      this.minorIncrement = minorIncrement;
   }

   public String getMajorIncrement() {
      return majorIncrement;
   }

   public void setMajorIncrement(String majorIncrement) {
      this.majorIncrement = majorIncrement;
   }

   public String getMin() {
      return min;
   }

   public void setMin(String min) {
      this.min = min;
   }

   public String getMax() {
      return max;
   }

   public void setMax(String max) {
      this.max = max;
   }

   @Override
   public String toString() {
      return "NumberRangePaneModel{}";
   }

   private String minorIncrement;
   private String majorIncrement;
   private String min;
   private String max;
}
