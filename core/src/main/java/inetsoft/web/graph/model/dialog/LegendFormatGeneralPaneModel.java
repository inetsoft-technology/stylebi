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

import inetsoft.graph.guide.legend.LegendItem;

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

   public int getSymbolSize() {
      return symbolSize;
   }

   public void setSymbolSize(int symbolSize) {
      this.symbolSize = symbolSize;
   }

   /** The gap between the legend column and the plot, in pixels. */
   public int getGap() {
      return gap;
   }

   public void setGap(int gap) {
      this.gap = gap;
   }

   /**
    * Whether the gap follows the modern card default. Null on an unmarked chart, where there is no
    * default to follow and the checkbox is not shown.
    */
   public Boolean getGapFollowsDefault() {
      return gapFollowsDefault;
   }

   public void setGapFollowsDefault(Boolean gapFollowsDefault) {
      this.gapFollowsDefault = gapFollowsDefault;
   }

   public boolean isRoundCorners() {
      return roundCorners;
   }

   public void setRoundCorners(boolean roundCorners) {
      this.roundCorners = roundCorners;
   }

   public boolean isSymbolRoundCorners() {
      return symbolRoundCorners;
   }

   public void setSymbolRoundCorners(boolean symbolRoundCorners) {
      this.symbolRoundCorners = symbolRoundCorners;
   }

   public boolean isSymbolRoundCornersVisible() {
      return symbolRoundCornersVisible;
   }

   public void setSymbolRoundCornersVisible(boolean symbolRoundCornersVisible) {
      this.symbolRoundCornersVisible = symbolRoundCornersVisible;
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
   private int symbolSize = LegendItem.DEFAULT_SYMBOL_SIZE;
   private int gap;
   private Boolean gapFollowsDefault;
   private boolean roundCorners;
   private boolean symbolRoundCorners;
   private boolean symbolRoundCornersVisible;
}
