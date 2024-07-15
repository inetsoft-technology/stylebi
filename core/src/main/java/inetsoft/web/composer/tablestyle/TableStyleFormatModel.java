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
package inetsoft.web.composer.tablestyle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.style.XTableStyle;

import java.util.ArrayList;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TableStyleFormatModel {
   public TableStyleFormatModel() {
   }

   public TableStyleFormatModel(XTableStyle tableStyle) {
      super();

      setTopBorderFormat(new BorderFormat(tableStyle, "top-border"));
      setBottomBorderFormat(new BorderFormat(tableStyle, "bottom-border"));
      setRightBorderFormat(new BorderFormat(tableStyle, "right-border"));
      setLeftBorderFormat(new BorderFormat(tableStyle, "left-border"));
      setHeaderRowFormat(new RowRegionFormat(tableStyle, "header-row"));
      setTrailerRowFormat(new RowRegionFormat(tableStyle, "trailer-row"));
      setHeaderColFormat(new ColRegionFormat(tableStyle, "header-col"));
      setTrailerColFormat(new ColRegionFormat(tableStyle, "trailer-col"));
      setBodyRegionFormat(new BodyRegionFormat(tableStyle, "body"));

      for(int i = 0; i < tableStyle.getSpecificationCount(); i++) {
         specList.add(new SpecificationModel(tableStyle.getSpecification(i), i));
      }
   }

   public void updateTableStyle(XTableStyle tableStyle) {
      topBorderFormat.updateTableStyle(tableStyle);
      bottomBorderFormat.updateTableStyle(tableStyle);
      rightBorderFormat.updateTableStyle(tableStyle);
      leftBorderFormat.updateTableStyle(tableStyle);
      headerRowFormat.updateTableStyle(tableStyle);
      trailerRowFormat.updateTableStyle(tableStyle);
      headerColFormat.updateTableStyle(tableStyle);
      trailerColFormat.updateTableStyle(tableStyle);
      bodyRegionFormat.updateTableStyle(tableStyle);
      tableStyle.clearSpecification();

      for(int i = 0; i < getSpecList().size(); i++) {
         XTableStyle.Specification specification = tableStyle.new Specification();
         getSpecList().get(i).updateSpecification(specification);
         tableStyle.addSpecification(specification);
      }
   }

   public BorderFormat getTopBorderFormat() {
      return topBorderFormat;
   }

   public void setTopBorderFormat(BorderFormat topBorderFormat) {
      this.topBorderFormat = topBorderFormat;
   }

   public BorderFormat getBottomBorderFormat() {
      return bottomBorderFormat;
   }

   public void setBottomBorderFormat(BorderFormat bottomBorderFormat) {
      this.bottomBorderFormat = bottomBorderFormat;
   }

   public BorderFormat getRightBorderFormat() {
      return rightBorderFormat;
   }

   public void setRightBorderFormat(BorderFormat rightBorderFormat) {
      this.rightBorderFormat = rightBorderFormat;
   }

   public BorderFormat getLeftBorderFormat() {
      return leftBorderFormat;
   }

   public void setLeftBorderFormat(BorderFormat leftBorderFormat) {
      this.leftBorderFormat = leftBorderFormat;
   }

   public BodyRegionFormat getBodyRegionFormat() {
      return bodyRegionFormat;
   }

   public void setBodyRegionFormat(BodyRegionFormat bodyRegionFormat) {
      this.bodyRegionFormat = bodyRegionFormat;
   }

   public RowRegionFormat getHeaderRowFormat() {
      return headerRowFormat;
   }

   public void setHeaderRowFormat(RowRegionFormat headerRowFormat) {
      this.headerRowFormat = headerRowFormat;
   }

   public RowRegionFormat getTrailerRowFormat() {
      return trailerRowFormat;
   }

   public void setTrailerRowFormat(RowRegionFormat trailerRowFormat) {
      this.trailerRowFormat = trailerRowFormat;
   }

   public ColRegionFormat getHeaderColFormat() {
      return headerColFormat;
   }

   public void setHeaderColFormat(ColRegionFormat headerColFormat) {
      this.headerColFormat = headerColFormat;
   }

   public ColRegionFormat getTrailerColFormat() {
      return trailerColFormat;
   }

   public void setTrailerColFormat(ColRegionFormat trailerColFormat) {
      this.trailerColFormat = trailerColFormat;
   }

   public ArrayList<SpecificationModel> getSpecList() {
      return specList;
   }

   public void setSpecList(ArrayList<SpecificationModel> specList) {
      this.specList = specList;
   }

   private BorderFormat topBorderFormat;
   private BorderFormat bottomBorderFormat;
   private BorderFormat rightBorderFormat;
   private BorderFormat leftBorderFormat;
   private BodyRegionFormat bodyRegionFormat;
   private RowRegionFormat headerRowFormat;
   private RowRegionFormat trailerRowFormat;
   private ColRegionFormat headerColFormat;
   private ColRegionFormat trailerColFormat;
   private ArrayList<SpecificationModel> specList = new ArrayList<SpecificationModel>();
}
