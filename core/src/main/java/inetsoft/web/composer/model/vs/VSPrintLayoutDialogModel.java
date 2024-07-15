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

public class VSPrintLayoutDialogModel {
   public String getPaperSize() {
      return paperSize;
   }

   public void setPaperSize(String paperSize) {
      this.paperSize = paperSize;
   }

   public double getMarginTop() {
      return marginTop;
   }

   public void setMarginTop(double marginTop) {
      this.marginTop = marginTop;
   }

   public double getMarginLeft() {
      return marginLeft;
   }

   public void setMarginLeft(double marginLeft) {
      this.marginLeft = marginLeft;
   }

   public double getMarginBottom() {
      return marginBottom;
   }

   public void setMarginBottom(double marginBottom) {
      this.marginBottom = marginBottom;
   }

   public double getMarginRight() {
      return marginRight;
   }

   public void setMarginRight(double marginRight) {
      this.marginRight = marginRight;
   }

   public float getFooterFromEdge() {
      return footerFromEdge;
   }

   public void setFooterFromEdge(float footerFromEdge) {
      this.footerFromEdge = footerFromEdge;
   }

   public float getHeaderFromEdge() {
      return headerFromEdge;
   }

   public void setHeaderFromEdge(float headerFromEdge) {
      this.headerFromEdge = headerFromEdge;
   }

   public boolean isLandscape() {
      return landscape;
   }

   public void setLandscape(boolean landscape) {
      this.landscape = landscape;
   }

   public float getScaleFont() {
      return scaleFont;
   }

   public void setScaleFont(float scaleFont) {
      this.scaleFont = scaleFont;
   }

   public int getNumberingStart() {
      return numberingStart;
   }

   public void setNumberingStart(int numberingStart) {
      this.numberingStart = numberingStart;
   }

   public double getCustomWidth() {
      return customWidth;
   }

   public void setCustomWidth(double customWidth) {
      this.customWidth = customWidth;
   }

   public double getCustomHeight() {
      return customHeight;
   }

   public void setCustomHeight(double customHeight) {
      this.customHeight = customHeight;
   }

   public String getUnits() {
      return units;
   }

   public void setUnits(String units) {
      this.units = units;
   }

   private String paperSize;
   private double marginTop;
   private double marginLeft;
   private double marginBottom;
   private double marginRight;
   private float footerFromEdge;
   private float headerFromEdge;
   private boolean landscape;
   private float scaleFont;
   private int numberingStart;
   private double customWidth;
   private double customHeight;
   private String units;
}
