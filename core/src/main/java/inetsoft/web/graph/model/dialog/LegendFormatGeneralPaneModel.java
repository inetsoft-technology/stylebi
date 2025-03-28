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
package inetsoft.web.graph.model.dialog;

import java.io.Serializable;

public class LegendFormatGeneralPaneModel implements Serializable {
   public String getTitle() {
      return title;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public String getTitleValue() {
      return titleValue;
   }

   public void setTitleValue(String titleValue) {
      this.titleValue = titleValue;
   }

   public int getStyle() {
      return style;
   }

   public void setStyle(int style) {
      this.style = style;
   }

   public String getFillColor() {
      return fillColor;
   }

   public void setFillColor(String fillColor) {
      this.fillColor = fillColor;
   }

   public String getPosition() {
      return position;
   }

   public void setPosition(String position) {
      this.position = position;
   }

   public boolean isVisible() {
      return visible;
   }

   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   public boolean isNotShowNull() {
      return notShowNull;
   }

   public void setNotShowNull(boolean notShowNull) {
      this.notShowNull = notShowNull;
   }

   public boolean isNotShowNullVisible() {
      return notShowNullVisible;
   }

   public void setNotShowNullVisible(boolean notShowNullVisible) {
      this.notShowNullVisible = notShowNullVisible;
   }

   // title dvalue
   private String title;
   // title rvalue
   private String titleValue;
   private int style;
   private String fillColor;
   private String position;
   private boolean visible;
   private boolean notShowNull;
   private boolean notShowNullVisible;
}
