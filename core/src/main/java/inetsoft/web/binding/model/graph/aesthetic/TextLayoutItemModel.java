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
package inetsoft.web.binding.model.graph.aesthetic;

public class TextLayoutItemModel {
   private int type;          // 0 = FIELD, 1 = STATIC, 2 = SPACING
   private int fieldIndex = -1;  // valid when type == FIELD: index into textFields
   private String text;       // valid when type == STATIC

   // Applicable only when type == STATIC.
   // color is an opaque RGB hex string ("#RRGGBB"); alpha is intentionally not carried on the
   // binding-model round-trip (text colors are effectively never translucent). The XML form does
   // preserve alpha, so a translucent color survives a save/reload but not a model round-trip.
   private String color;       // hex color string e.g. "#FF0000", null = not set
   private String fontFamily;
   private int fontSize = -1;
   private boolean bold;
   private boolean italic;

   // Applicable only when type == SPACING
   private double spacingAmount = 10.0;

   public int getType() { return type; }
   public void setType(int type) { this.type = type; }
   public int getFieldIndex() { return fieldIndex; }
   public void setFieldIndex(int fieldIndex) { this.fieldIndex = fieldIndex; }
   public String getText() { return text; }
   public void setText(String text) { this.text = text; }

   public String getColor() { return color; }
   public void setColor(String color) { this.color = color; }
   public String getFontFamily() { return fontFamily; }
   public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
   public int getFontSize() { return fontSize; }
   public void setFontSize(int fontSize) { this.fontSize = fontSize; }
   public boolean isBold() { return bold; }
   public void setBold(boolean bold) { this.bold = bold; }
   public boolean isItalic() { return italic; }
   public void setItalic(boolean italic) { this.italic = italic; }

   public double getSpacingAmount() { return spacingAmount; }
   public void setSpacingAmount(double spacingAmount) { this.spacingAmount = spacingAmount; }

   public static TextLayoutItemModel fromDomain(inetsoft.uql.viewsheet.graph.TextLayoutItem item) {
      TextLayoutItemModel model = new TextLayoutItemModel();
      model.type = item.getType();
      model.fieldIndex = item.getFieldIndex();
      model.text = item.getText();
      java.awt.Color c = item.getColor();
      if(c != null) {
         model.color = String.format("#%06X", (c.getRGB() & 0xFFFFFF));
      }
      model.fontFamily = item.getFontFamily();
      model.fontSize = item.getFontSize();
      model.bold = item.isBold();
      model.italic = item.isItalic();
      model.setSpacingAmount(item.getSpacingAmount());
      return model;
   }

   public inetsoft.uql.viewsheet.graph.TextLayoutItem toDomain() {
      inetsoft.uql.viewsheet.graph.TextLayoutItem item =
         new inetsoft.uql.viewsheet.graph.TextLayoutItem();
      item.setType(type);
      item.setFieldIndex(fieldIndex);
      item.setText(text);
      if(color != null && !color.isEmpty()) {
         try {
            item.setColor(java.awt.Color.decode(color));
         }
         catch(NumberFormatException ignored) {
         }
      }
      item.setFontFamily(fontFamily);
      // Normalize any non-positive size to the domain's "inherit" sentinel (-1) so a stray 0 from
      // the model isn't treated as a real font size.
      item.setFontSize(fontSize > 0 ? fontSize : -1);
      item.setBold(bold);
      item.setItalic(italic);
      item.setSpacingAmount(spacingAmount);
      return item;
   }
}
