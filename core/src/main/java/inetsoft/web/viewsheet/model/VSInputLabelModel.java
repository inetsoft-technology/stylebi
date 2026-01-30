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

package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSInputLabelModel {
   public boolean isShowLabel() {
      return showLabel;
   }

   public void setShowLabel(boolean showLabel) {
      this.showLabel = showLabel;
   }

   public String getLabelText() {
      return labelText;
   }

   public void setLabelText(String labelText) {
      this.labelText = labelText;
   }

   public String getLabelPosition() {
      return labelPosition;
   }

   public void setLabelPosition(String labelPosition) {
      this.labelPosition = labelPosition;
   }

   public int getLabelGap() {
      return labelGap;
   }

   public void setLabelGap(int labelGap) {
      this.labelGap = labelGap;
   }

   private boolean showLabel;
   private String labelText = "";
   private String labelPosition = "left";
   private int labelGap = 5;
}
