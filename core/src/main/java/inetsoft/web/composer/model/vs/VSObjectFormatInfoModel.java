/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.model.vs;

import inetsoft.web.adhoc.model.FormatInfoModel;

/**
 * Class that encapsulates the parameters for changing an object format.
 *
 * @since 12.3
 */
public class VSObjectFormatInfoModel extends FormatInfoModel {
   public boolean isShape() {
      return shape;
   }

   public void setShape(boolean shape) {
      this.shape = shape;
   }

   public boolean isImage() {
      return image;
   }

   public void setImage(boolean image) {
      this.image = image;
   }

   public boolean isChart() {
      return chart;
   }

   public void setChart(boolean chart) {
      this.chart = chart;
   }

   public String getColorType() {
      return colorType;
   }

   public void setColorType(String colorType) {
      this.colorType = colorType;
   }

   public String getBackgroundColorType() {
      return backgroundColorType;
   }

   public void setBackgroundColorType(String backgroundColorType) {
      this.backgroundColorType = backgroundColorType;
   }

   public int getBackgroundAlpha() {
      return backgroundAlpha;
   }

   public void setBackgroundAlpha(int backgroundAlpha) {
      this.backgroundAlpha = backgroundAlpha;
   }

   public int getRoundCorner() {
      return roundCorner;
   }

   public void setRoundCorner(int roundCorner) {
      this.roundCorner = roundCorner;
   }

   public boolean isWrapText() {
      return wrapText;
   }

   public void setWrapText(boolean wrapText) {
      this.wrapText = wrapText;
   }

   public String getCssID() {
      return cssID;
   }

   public void setCssID(String cssID) {
      this.cssID = cssID;
   }

   public String getCssClass() {
      return cssClass;
   }

   public void setCssClass(String cssClass) {
      this.cssClass = cssClass;
   }

   public String[] getCssIDs() {
      return cssIDs != null ? cssIDs : new String[0];
   }

   public void setCssIDs(String[] cssIDs) {
      this.cssIDs = cssIDs;
   }

   public String[] getCssClasses() {
      return cssClasses != null ? cssClasses : new String[0];
   }

   public void setCssClasses(String[] cssClasses) {
      this.cssClasses = cssClasses;
   }

   public String getCssType() {
      return cssType;
   }

   public void setCssType(String cssType) {
      this.cssType = cssType;
   }

   public String getPresenterLabel() {
      return presenterLabel;
   }

   public void setPresenterLabel(String presenterLabel) {
      this.presenterLabel = presenterLabel;
   }

   public String getPresenter() {
      return presenter;
   }

   public void setPresenter(String presenter) {
      this.presenter = presenter;
   }

   public boolean getBorderDisabled() {
      return borderDisabled;
   }

   public void setBorderDisabled(boolean borderDisabled) {
      this.borderDisabled = borderDisabled;
   }

   public boolean getDynamicColorDisabled() {
      return dynamicColorDisabled;
   }

   public void setDynamicColorDisabled(boolean dynamicColorDisabled) {
      this.dynamicColorDisabled = dynamicColorDisabled;
   }

   public boolean getAlignEnabled() {
      return alignEnabled;
   }

   public void setAlignEnabled(boolean alignEnabled) {
      this.alignEnabled = alignEnabled;
   }

   public boolean isPresenterHasDescriptors() {
      return presenterHasDescriptors;
   }

   public void setPresenterHasDescriptors(boolean presenterHasDescriptors) {
      this.presenterHasDescriptors = presenterHasDescriptors;
   }

   public void setValueFillColor(String valueFillColor) {
      this.valueFillColor = valueFillColor;
   }

   public String getValueFillColor() {
      return valueFillColor;
   }

   public boolean isRoundTopCornersOnly() {
      return roundTopCornersOnly;
   }

   public void setRoundTopCornersOnly(boolean roundTopCornersOnly) {
      this.roundTopCornersOnly = roundTopCornersOnly;
   }

   private boolean shape;
   private boolean image;
   private boolean chart;
   private String colorType;
   private String backgroundColorType;
   private int backgroundAlpha = 100;
   private int roundCorner = 0;
   private boolean roundTopCornersOnly;
   private boolean wrapText;
   private String cssID;
   private String cssClass;
   private String[] cssIDs;
   private String[] cssClasses;
   private String cssType;
   private String presenterLabel;
   private String presenter;
   private boolean borderDisabled;
   private boolean dynamicColorDisabled;
   private boolean alignEnabled;
   private boolean presenterHasDescriptors;
   private String valueFillColor;
}
