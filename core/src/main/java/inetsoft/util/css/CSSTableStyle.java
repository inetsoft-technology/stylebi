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
package inetsoft.util.css;

import inetsoft.report.TableLens;
import inetsoft.report.style.XTableStyle;

/**
 * This class defines a table style created through css
 *
 * @version 12.1
 * @author InetSoft Technology
 */
public class CSSTableStyle extends XTableStyle {
   public CSSTableStyle(String id, String type, String cls, TableLens base,
      String location, CSSParameter sheetParam)
   {
      super(base);
      this.id = id;
      this.type = type;
      this.cls = cls;
      this.cssParam = new CSSParameter(type, id, cls, null);
      this.sheetParam = sheetParam;

      if(location != null) {
         this.cssDict = CSSDictionary.getDictionary(location);
      }

      setApplyFont(false);
      setApplyForeground(false);
      setApplyBackground(false);
      setApplyAlignment(false);

      applyHeaderRow();
      applyHeaderCol();
      applyBody();
      applyTable();
      applyTrailerRow();
      applyTrailerCol();
      applyGroupings(); // GroupHeader + GroupFooter
      applyRowPatterns();
      applyColPatterns();
   }

   public CSSTableStyle(CSSParameter cssParam, TableLens base) {
      super(base);
      this.id = cssParam.getCSSID();
      this.type = cssParam.getCSSType();
      this.cls = cssParam.getCSSClass();
      this.cssParam = cssParam;
      this.cssDict = CSSDictionary.getDictionary();
   }

   /**
    * Apply CSS to the HeaderRow.
    * Format: .class TableStyle[region=HeaderRow]{}
    */
   private void applyHeaderRow() {
      if(cssDict == null) {
         return;
      }

      CSSParameter parameters = new CSSParameter("TableStyle", id, cls,
                                                 new CSSAttr("region", "HeaderRow"));
      CSSStyle style = cssDict.getStyle(sheetParam, cssParam, parameters);

      if(style == null) {
         return;
      }

      if(super.getHeaderRowCount() <= 0) {
         return;
      }

      setAttributes(style, null, "header-row.");
   }

   /**
    * Apply CSS to the HeaderColumn.
    * Format: .class TableStyle[region=HeaderCol]{}
    */
   private void applyHeaderCol() {
      if(cssDict == null) {
         return;
      }

      CSSParameter parameters = new CSSParameter("TableStyle", id, cls,
                                                 new CSSAttr("region", "HeaderCol"));
      CSSStyle style = cssDict.getStyle(sheetParam, cssParam, parameters);

      if(style == null) {
         return;
      }

      if(super.getHeaderColCount() <= 0) {
         return;
      }

      setAttributes(style, null, "header-col.");
   }

   /**
    * Apply CSS to the TrailerRow.
    * Format: .class TableStyle[region=TrailerRow]{}
    */
   private void applyTrailerRow() {
      if(cssDict == null) {
         return;
      }

      CSSParameter parameters = new CSSParameter("TableStyle", id, cls,
                                                 new CSSAttr("region", "TrailerRow"));
      CSSStyle style = cssDict.getStyle(sheetParam, cssParam, parameters);

      if(style == null) {
         return;
      }

      if(super.getTrailerRowCount() < 1) {
         return;
      }

      setAttributes(style, null, "trailer-row.");
   }

   /**
    * Apply CSS to the TrailerColumn.
    * Format: .class TableStyle[region=TrailerCol]{}
    */
   private void applyTrailerCol() {
      if(cssDict == null) {
         return;
      }

      CSSParameter parameters = new CSSParameter("TableStyle", id, cls,
                                                 new CSSAttr("region", "TrailerCol"));
      CSSStyle style = cssDict.getStyle(sheetParam, cssParam, parameters);

      if(style == null) {
         return;
      }

      if(super.getTrailerColCount() <= 0) {
         return;
      }

      setAttributes(style, null, "trailer-col.");
   }

   /**
    * Apply CSS to the Table Body.
    * Format: .class TableStyle[region=Body]{}
    */
   private void applyBody() {
      if(cssDict == null) {
         return;
      }

      CSSParameter parameters = new CSSParameter("TableStyle", id, cls,
                                                 new CSSAttr("region", "Body"));
      CSSStyle style = cssDict.getStyle(sheetParam, cssParam, parameters);

      if(style == null) {
         return;
      }

      setAttributes(style, null, "body.");
   }

   /**
    * Apply CSS to the Table's outer borders.
    * Format: .class TableStyle[region=Table]{}
    */
   private void applyTable() {
      if(cssDict == null) {
         return;
      }

      CSSParameter parameters = new CSSParameter("TableStyle", id, cls,
                                                 new CSSAttr("region", "Table"));
      CSSStyle style = cssDict.getStyle(sheetParam, cssParam, parameters);

      if(style == null) {
         return;
      }

      // Border
      if(style.isBorderDefined()) {
         super.put("top-border.border", style.getBorders().top);
         super.put("bottom-border.border", style.getBorders().bottom);
         super.put("right-border.border", style.getBorders().right);
         super.put("left-border.border", style.getBorders().left);
      }

      if(style.isBorderColorDefined()) {
         super.put("top-border.color", style.getBorderColors().topColor);
         super.put("bottom-border.color", style.getBorderColors().bottomColor);
         super.put("right-border.color", style.getBorderColors().rightColor);
         super.put("left-border.color", style.getBorderColors().leftColor);
      }
   }

   /**
    * Apply CSS to the GroupHeaders and GroupFooters.
    * Format: .class TableStyle[region=GroupHeader][level='#']{}
    * Format: .class TableStyle[region=GroupFooter][level='#']{}
    */
   private void applyGroupings() {
      CSSStyle style;

      // Get CSSStyle for each possible Grouping level
      // (up to 10 for header, 10 for footer)
      for(int i = 0; i < 20; i++) {
         if(i < 10) {
            style = getStyle(i, "RowGroupTotal"); // find groupheader levels 1-10
         }
         else {
            // find groupfooter levels 1-10.
            style = getStyle(i - 10, "ColumnGroupTotal");
         }

         if(style == null) {
            continue;
         }

         Specification spec = new Specification();

         if(i < 10) {
            spec.setIndex(i + 1);
            spec.setType(1);
         }
         else {
            spec.setIndex(i - 9);
            spec.setType(2);
         }

         setAttributes(style, spec, null);
      }
   }

   private void applyRowPatterns() {
      CSSParameter parameters = new CSSParameter("TableStyle", id, cls,
                                                 new CSSAttr("region", "Body",
                                                             "pattern", "EvenRow"));
      CSSStyle styleEven, styleOdd;

      styleEven = cssDict.getStyle(sheetParam, cssParam, parameters);

      if(styleEven != null) {
         Specification specEvenRow = new Specification();
         specEvenRow.setType(0);
         specEvenRow.setIndex(2);
         specEvenRow.setRepeat(true);
         setAttributes(styleEven, specEvenRow, null);
      }

      // reset attributes and parameters
      parameters = new CSSParameter("TableStyle", id, cls,
                                    new CSSAttr("region", "Body", "pattern", "OddRow"));
      styleOdd = cssDict.getStyle(sheetParam, cssParam, parameters);

      if(styleOdd != null) {
         Specification specOddRow = new Specification();
         specOddRow.setType(0);
         specOddRow.setIndex(1);
         specOddRow.setRepeat(true);
         setAttributes(styleOdd, specOddRow, null);
      }
   }

   private void applyColPatterns() {
      CSSParameter parameters = new CSSParameter("TableStyle", id, cls,
                                                 new CSSAttr("region", "Body",
                                                             "pattern", "EvenCol"));
      CSSStyle styleEven, styleOdd;

      styleEven = cssDict.getStyle(sheetParam, cssParam, parameters);

      if(styleEven != null) {
         Specification specEvenCol = new Specification();
         specEvenCol.setType(0);
         specEvenCol.setRow(false);
         specEvenCol.setIndex(2);
         specEvenCol.setRepeat(true);
         setAttributes(styleEven, specEvenCol, null);
      }

      // reset attributes and parameters
      parameters = new CSSParameter("TableStyle", id, cls, new CSSAttr("region", "Body",
                                                                       "pattern", "OddCol"));
      styleOdd = cssDict.getStyle(sheetParam, cssParam, parameters);

      if(styleOdd != null) {
         Specification specOddCol = new Specification();
         specOddCol.setType(0);
         specOddCol.setRow(false);
         specOddCol.setIndex(1);
         specOddCol.setRepeat(true);
         setAttributes(styleOdd, specOddCol, null);
      }
   }

   /**
    * Returns the CSSStyle associated with the grouping type + level
    *
    * @param i Group level
    * @param type GroupHeader or GroupFooter
    * @return
    */
   private CSSStyle getStyle(int i, String type) {
      CSSParameter parameters = new CSSParameter("TableStyle", id, cls,
                                                 new CSSAttr("region", "Body",
                                                             "type", type,
                                                             // int values 1-10 instead of 0-9
                                                             "level", Integer.toString(i + 1)));
      CSSStyle style = cssDict.getStyle(sheetParam, cssParam, parameters);

      if(style == null) {
         return null;
      }

      return style;
   }

   /**
    * Set up the XTableStyle attributes.
    *
    * @param style CSSStyle
    * @param spec Specification for patterns and GroupHeader/Footer, null otherwise
    * @param area The region for the CSSStyle to be applied
    */
   private void setAttributes(CSSStyle style, Specification spec, String area) {
      if(spec != null) {
         if(style.isBackgroundDefined()) {
            spec.put("background", style.getBackground());
            setApplyBackground(true);
         }

         // Foreground
         if(style.isForegroundDefined()) {
            spec.put("foreground", style.getForeground());
            setApplyForeground(true);
         }

         // Font
         if(style.isFontDefined()) {
            spec.put("font", style.getFont());
            setApplyFont(true);
         }

         // Border
         if(style.isBorderDefined()) {
            spec.put("row-border", style.getBorders().top);
            spec.put("col-border", style.getBorders().right);
            super.setApplyRowBorder(true);
            super.setApplyColBorder(true);
         }

         if(style.isBorderColorDefined()) {
            spec.put("rcolor", style.getBorderColors().topColor);
            spec.put("ccolor", style.getBorderColors().rightColor);
            super.setApplyRowBorder(true);
            super.setApplyColBorder(true);
         }

         // Alignment
         if(style.isAlignmentDefined()) {
            spec.put("alignment", style.getAlignment());
            super.setApplyAlignment(true);
         }

         // padding
         if(style.isPaddingDefined()) {
            spec.put("padding", style.getPadding());
            super.setApplyInsets(true);
         }

         // height
         if(style.isHeightDefined()) {
            spec.put("height", style.getHeight());
            super.setApplyRowHeight(true);
         }

         super.addSpecification(spec);
      }
      else {
         if(style.isBackgroundDefined()) {
            super.put(area + "background", style.getBackground());
            setApplyBackground(true);
         }

         // Foreground
         if(style.isForegroundDefined()) {
            super.put(area + "foreground", style.getForeground());
            setApplyForeground(true);
         }

         // Font
         if(style.isFontDefined()) {
            super.put(area + "font", style.getFont());
            setApplyFont(true);
         }

         // Border
         if(style.isBorderDefined()) {
            if(area.contains("row")) {
               super.put(area + "col-border", style.getBorders().right);
               super.put(area + "border", style.getBorders().top);
            }
            else if(area.contains("col")) {
               super.put(area + "border", style.getBorders().right);
               super.put(area + "row-border", style.getBorders().top);
            }
            else {
               super.put(area + "col-border", style.getBorders().right);
               super.put(area + "row-border", style.getBorders().top);
            }

            super.setApplyRowBorder(true);
            super.setApplyColBorder(true);
         }

         if(style.isBorderColorDefined()) {
            if(area.contains("row")) {
               super.put(area + "ccolor", style.getBorderColors().rightColor);
               super.put(area + "bcolor", style.getBorderColors().topColor);
            }
            else if(area.contains("col")) {
               super.put(area + "bcolor", style.getBorderColors().rightColor);
               super.put(area + "rcolor", style.getBorderColors().topColor);
            }
            else {
               super.put(area + "ccolor", style.getBorderColors().rightColor);
               super.put(area + "rcolor", style.getBorderColors().topColor);
            }
            super.setApplyRowBorder(true);
            super.setApplyColBorder(true);
         }

         // Alignment
         if(style.isAlignmentDefined()) {
            super.put(area + "alignment", style.getAlignment());
            super.setApplyAlignment(true);
         }

         // padding
         if(style.isPaddingDefined()) {
            super.put(area + "padding", style.getPadding());
            super.setApplyInsets(true);
         }

         // height
         if(style.isHeightDefined()) {
            super.put(area + "height", style.getHeight());
            super.setApplyRowHeight(true);
         }
      }
   }

   public CSSParameter getSheetParam() {
      return sheetParam;
   }

   private String id;
   private String type;
   private String cls;

   private CSSParameter sheetParam = null;
   private CSSParameter cssParam = null;
   // for viewsheet ONLY
   private transient CSSDictionary cssDict = CSSDictionary.getDictionary();
}
