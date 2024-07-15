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

import inetsoft.report.StyleConstants;
import inetsoft.report.style.XTableStyle;
import inetsoft.web.adhoc.model.AlignmentInfo;
import inetsoft.web.adhoc.model.FontInfo;

import java.awt.*;

public class SpecificationModel {
   public SpecificationModel() {}

   public SpecificationModel(XTableStyle.Specification specification, int id) {
      this.id = id;
      this.label = specification.toString();

      if(specification.getType() == 0) {
         setStart(specification.getIndex());
         setCustomType(specification.isRow() ? "Row" : "Column");
         setAll(specification.getRange() == null);
         setFrom(this.all ? 0 : specification.getRange()[0]);
         setTo(this.all ? 0 : specification.getRange()[1]);
         setRepeat(specification.isRepeat());
      }
      else {
         setCustomType(specification.getType() == 1 ? "Row Group Total" :
            "Column Group Total");
         setLevel(specification.getIndex());
      }

      Object background = specification.get("background");
      Object foreground = specification.get("foreground");
      Object rcolor = specification.get("rcolor");
      Object ccolor = specification.get("ccolor");
      Object rowborder = specification.get("row-border");
      Object colborder = specification.get("col-border");
      Object font = specification.get("font");
      Object alignment = specification.get("alignment");

      specFormat = new BodyRegionFormat();
      specFormat.setBackground(specFormat.getColorString(background));
      specFormat.setForeground(specFormat.getColorString(foreground));
      specFormat.setRowBorderColor(specFormat.getColorString(rcolor));
      specFormat.setFont(font instanceof Font ? new FontInfo((Font) font) :
         new FontInfo(BodyRegionFormat.defFont));
      specFormat.setRowBorder(rowborder != null && rowborder instanceof  Integer ?
         (Integer) rowborder : -1);
      specFormat.setColBorderColor(specFormat.getColorString(ccolor));
      specFormat.setColBorder(colborder != null && colborder instanceof Integer ?
         (Integer) colborder : -1);
      specFormat.setAlignment(alignment != null && alignment instanceof Integer ?
         new AlignmentInfo((Integer) alignment) : new AlignmentInfo(StyleConstants.FILL));
   }

   public void updateSpecification(XTableStyle.Specification specification) {
      if(specFormat != null) {
         if(this.customType.equals("Row Group Total") ||
            this.customType.equals("Column Group Total"))
         {
            specification.setType(this.customType.equals("Row Group Total") ? 1 : 2);
            specification.setIndex(this.level);
         }
         else {
            specification.setType(0);
            specification.setIndex(this.start);
            specification.setRow(this.customType.equals("Row"));

            if(this.from == 0 && this.to == 0 || this.all) {
               specification.setRange(null);
            }
            else {
               specification.setRange(new int[]{this.from, this.to});
            }

            specification.setRepeat(this.isRepeat());
         }

         Object background = specFormat.getColor(specFormat.getBackground());
         Object foreground = specFormat.getColor(specFormat.getForeground());
         int rowBorder = specFormat.getRowBorder();
         int colBorder = specFormat.getColBorder();
         Object rowBorderColor = specFormat.getColor(specFormat.getRowBorderColor());
         Object colBorderColor = specFormat.getColor(specFormat.getColBorderColor());
         specification.put("background", background);
         specification.put("foreground", foreground);
         specification.put("row-border", rowBorder >= 0 ? rowBorder : null);
         specification.put("ccolor", colBorderColor);
         specification.put("rcolor", rowBorderColor);
         specification.put("col-border", colBorder >= 0 ? colBorder : null);
         specification.put("font", specFormat.getFont() == null ||
            BodyRegionFormat.defFont.equals(specFormat.getFont().toFont()) ? null :
            specFormat.getFont().toFont());
         specification.put("alignment", specFormat.getAlignment().toAlign() == 0 ?
            null : specFormat.getAlignment().toAlign());
      }
   }

   public BodyRegionFormat getSpecFormat() {
      return specFormat;
   }

   public void setSpecFormat(BodyRegionFormat specFormat) {
      this.specFormat = specFormat;
   }

   public int getId() {
      return id;
   }

   public void setId(int id) {
      this.id = id;
   }

   public String getLabel() {
      return label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public int getStart() {
      return start;
   }

   public void setStart(int start) {
      this.start = start;
   }

   public boolean isRepeat() {
      return repeat;
   }

   public void setRepeat(boolean repeat) {
      this.repeat = repeat;
   }

   public int getFrom() {
      return from;
   }

   public void setFrom(int from) {
      this.from = from;
   }

   public int getTo() {
      return to;
   }

   public void setTo(int to) {
      this.to = to;
   }

   public boolean isAll() {
      return all;
   }

   public void setAll(boolean all) {
      this.all = all;
   }

   public int getLevel() {
      return level;
   }

   public void setLevel(int level) {
      this.level = level;
   }

   public String getCustomType() {
      return customType;
   }

   public void setCustomType(String customType) {
      this.customType = customType;
   }

   private String label;
   private int id; //speci model list of the index
   private BodyRegionFormat specFormat;
   private int start;
   private boolean repeat;
   private int from;
   private int to;
   private boolean all;
   private String customType;
   private int level = 1;
}
