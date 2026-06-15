package inetsoft.uql.viewsheet.graph;

import org.w3c.dom.Element;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Objects;

public class TextLayoutItem implements Serializable, Cloneable {
   private static final long serialVersionUID = 1L;

   public static final int FIELD = 0;
   public static final int STATIC = 1;
   public static final int SPACING = 2;  // blank space between items

   private int type = FIELD;
   private double spacingAmount = 10.0;  // For SPACING items: amount of space in points
   private int fieldIndex = -1;  // index into ChartInfo.textLayoutFields (when type == FIELD)
   private String text;       // literal delimiter/label (when type == STATIC)

   // Applicable only when type == STATIC
   private java.awt.Color color;       // text color; null = inherit element TextSpec
   private String fontFamily;          // font family; null = inherit
   private int fontSize = -1;          // font size in pt; -1 = inherit
   private boolean bold;
   private boolean italic;

   public TextLayoutItem() {}

   public static TextLayoutItem ofField(int fieldIndex) {
      TextLayoutItem item = new TextLayoutItem();
      item.type = FIELD;
      item.fieldIndex = fieldIndex;
      return item;
   }

   public static TextLayoutItem ofStatic(String text) {
      TextLayoutItem item = new TextLayoutItem();
      item.type = STATIC;
      item.text = text;
      return item;
   }

   public static TextLayoutItem ofSpacing(double amount) {
      TextLayoutItem item = new TextLayoutItem();
      item.type = SPACING;
      item.spacingAmount = amount;
      return item;
   }

   public int getType() { return type; }
   public void setType(int type) { this.type = type; }
   public int getFieldIndex() { return fieldIndex; }
   public void setFieldIndex(int fieldIndex) { this.fieldIndex = fieldIndex; }
   public String getText() { return text; }
   public void setText(String text) { this.text = text; }

   public java.awt.Color getColor() { return color; }
   public void setColor(java.awt.Color color) { this.color = color; }
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

   /** True if this (STATIC) item carries any panel-set inline styling. */
   public boolean hasInlineFormat() {
      return color != null || fontFamily != null || fontSize > 0 || bold || italic;
   }

   public void writeXML(PrintWriter writer) {
      writer.print("<textLayoutItem type=\"" + type + "\"");
      if(type == FIELD && fieldIndex >= 0) writer.print(" fieldIndex=\"" + fieldIndex + "\"");
      if(type == SPACING) writer.print(" spacingAmount=\"" + spacingAmount + "\"");
      if(text != null) writer.print(" text=\"" + escapeXml(text) + "\"");
      if(color != null) writer.print(" color=\"" + Integer.toString(color.getRGB()) + "\"");
      if(fontFamily != null) writer.print(" fontFamily=\"" + escapeXml(fontFamily) + "\"");
      if(fontSize != -1) writer.print(" fontSize=\"" + fontSize + "\"");
      if(bold) writer.print(" bold=\"true\"");
      if(italic) writer.print(" italic=\"true\"");
      writer.println("/>");
   }

   public static TextLayoutItem parseXML(Element elem) {
      TextLayoutItem item = new TextLayoutItem();
      try { item.type = Integer.parseInt(elem.getAttribute("type")); }
      catch(NumberFormatException ignored) { item.type = FIELD; }
      String fi = elem.getAttribute("fieldIndex");
      if(!fi.isEmpty()) {
         try { item.fieldIndex = Integer.parseInt(fi); }
         catch(NumberFormatException ignored) { item.fieldIndex = -1; }
      }
      String t = elem.getAttribute("text");
      if(!t.isEmpty()) item.text = t;
      String colorAttr = elem.getAttribute("color");
      if(!colorAttr.isEmpty()) {
         try { item.color = new java.awt.Color(Integer.parseInt(colorAttr), true); }
         catch(NumberFormatException ignored) { /* leave color null */ }
      }
      String fontFamilyAttr = elem.getAttribute("fontFamily");
      if(!fontFamilyAttr.isEmpty()) item.fontFamily = fontFamilyAttr;
      String fontSizeAttr = elem.getAttribute("fontSize");
      if(!fontSizeAttr.isEmpty()) {
         try { item.fontSize = Integer.parseInt(fontSizeAttr); }
         catch(NumberFormatException ignored) { /* leave default -1 */ }
      }
      String boldAttr = elem.getAttribute("bold");
      if(!boldAttr.isEmpty()) item.bold = Boolean.parseBoolean(boldAttr);
      String italicAttr = elem.getAttribute("italic");
      if(!italicAttr.isEmpty()) item.italic = Boolean.parseBoolean(italicAttr);
      String saAttr = elem.getAttribute("spacingAmount");
      if(!saAttr.isEmpty()) {
         try { item.spacingAmount = Double.parseDouble(saAttr); }
         catch(NumberFormatException ignored) {}
      }
      return item;
   }

   private static String escapeXml(String s) {
      return s.replace("&","&amp;").replace("\"","&quot;")
              .replace("<","&lt;").replace(">","&gt;");
   }

   @Override
   public boolean equals(Object o) {
      if(!(o instanceof TextLayoutItem)) return false;
      TextLayoutItem other = (TextLayoutItem) o;
      if(type != other.type || fieldIndex != other.fieldIndex ||
         !Objects.equals(text, other.text)) return false;
      if(type == STATIC) {
         if(!Objects.equals(color, other.color)) return false;
         if(!Objects.equals(fontFamily, other.fontFamily)) return false;
         if(fontSize != other.fontSize) return false;
         if(bold != other.bold) return false;
         if(italic != other.italic) return false;
      }
      if(type == SPACING && other.type == SPACING) {
         if(Double.compare(spacingAmount, other.spacingAmount) != 0) return false;
      }
      return true;
   }

   @Override
   public int hashCode() { return Objects.hash(type, fieldIndex, text); }

   @Override
   public TextLayoutItem clone() {
      try { return (TextLayoutItem) super.clone(); }
      catch(CloneNotSupportedException e) { throw new RuntimeException(e); }
   }
}
