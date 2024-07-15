/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import inetsoft.report.internal.Common;
import inetsoft.uql.viewsheet.SelectionValue;
import inetsoft.uql.viewsheet.internal.SelectionBaseVSAssemblyInfo;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SelectionValueModel {
   public SelectionValueModel() {
   }

   public SelectionValueModel(SelectionValue svalue, SelectionListModel slist,
                              SelectionBaseVSAssemblyInfo info, Font font, boolean wrapping)
   {
      if(svalue != null) {
         this.label = svalue.getLabel();
         this.value = svalue.getValue();
         this.state = svalue.getState() == SelectionValue.STATE_COMPATIBLE ? null
            : svalue.getState();
         this.level = svalue.getLevel() == 0 ? null : svalue.getLevel();
         this.measureLabel = svalue.getMeasureLabel();
         double measureValue = svalue.getMeasureValue();

         // optimization, ignore default values, filled in on client size
         if(Objects.equals(this.label, this.value)) {
            this.label = null;
         }

         if(!Double.isNaN(measureValue) && (measureLabel != null || measureValue != 0)) {
            this.measureValue = measureValue;
         }

         if(svalue.getFormat() != null) {
            if(font != null && wrapping) {
               FontMetrics fm = Common.getFontMetrics(font);
               int maxLines = info.getCellHeight() / (fm.getAscent() + fm.getDescent());

               if(maxLines > 1) {
                  this.maxLines = maxLines;
               }
            }

            int formatIndex = slist == null ? -1 : slist.getFormatIndex(svalue.getFormat());

            if(formatIndex != 0) {
               this.formatIndex = formatIndex;
            }
         }
      }
   }

   @Nullable
   public String getLabel() {
      return label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public String getValue() {
      return value;
   }

   public void setValue(String value) {
      this.value = value;
   }

   @Nullable
   public Integer getState() {
      return state;
   }

   public void setState(Integer state) {
      this.state = state;
   }

   @Nullable
   public Integer getLevel() {
      return level;
   }

   public void setLevel(Integer level) {
      this.level = level;
   }

   @Nullable
   public String getMeasureLabel() {
      return measureLabel;
   }

   public void setMeasureLabel(String measureLabel) {
      this.measureLabel = measureLabel;
   }

   @Nullable
   public Double getMeasureValue() {
      return measureValue;
   }

   public void setMeasureValue(Double measureValue) {
      this.measureValue = measureValue;
   }

   @Nullable
   public Integer getMaxLines() {
      return maxLines;
   }

   public void setMaxLines(Integer maxLines) {
      this.maxLines = maxLines;
   }

   @Nullable
   public Integer getFormatIndex() {
      return formatIndex;
   }

   public void setFormatIndex(Integer formatIndex) {
      this.formatIndex = formatIndex;
   }

   @Override
   public String toString() {
      return "{label:" + label + " " +
         "value:" + value + " " +
         "state:" + state + " " +
         "level:" + level + " " +
         "measureLabel:" + measureLabel + " " +
         "measureValue:" + measureValue + " " +
         "maxLines:" + maxLines + " " +
         "formatIndex:" + formatIndex + "} ";
   }

   private String label;
   private String value;
   private Integer state;
   private Integer level;
   private String measureLabel;
   private Double measureValue;
   private Integer maxLines;
   private Integer formatIndex;
}
