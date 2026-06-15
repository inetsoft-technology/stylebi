package inetsoft.uql.viewsheet.graph;

import org.w3c.dom.*;
import java.io.*;
import java.util.*;

public class TextLayoutRow implements Serializable, Cloneable {
   private List<TextLayoutItem> items = new ArrayList<>();
   /** Horizontal alignment of items within this row: "left" (default), "center", or "right". */
   private String alignment;

   public List<TextLayoutItem> getItems() { return items; }
   public void setItems(List<TextLayoutItem> items) { this.items = items; }
   public void addItem(TextLayoutItem item) { items.add(item); }

   public String getAlignment() { return alignment != null ? alignment : "left"; }
   public void setAlignment(String alignment) { this.alignment = alignment; }

   /**
    * True when this row exists only to provide vertical space (contains a single SPACING item).
    */
   public boolean isSpacingRow() {
      return items.size() == 1 && items.get(0).getType() == TextLayoutItem.SPACING;
   }

   public void writeXML(PrintWriter writer) {
      // only emit alignment when it differs from the default ("left")
      if(alignment != null && !"left".equals(alignment)) {
         writer.println("<textLayoutRow alignment=\"" + alignment + "\">");
      }
      else {
         writer.println("<textLayoutRow>");
      }
      for(TextLayoutItem item : items) {
         item.writeXML(writer);
      }
      writer.println("</textLayoutRow>");
   }

   public static TextLayoutRow parseXML(Element elem) {
      TextLayoutRow row = new TextLayoutRow();
      String align = elem.getAttribute("alignment");
      if(!align.isEmpty()) {
         row.alignment = align;
      }
      NodeList children = elem.getChildNodes();
      for(int i = 0; i < children.getLength(); i++) {
         Node node = children.item(i);
         if(node instanceof Element && "textLayoutItem".equals(((Element) node).getNodeName())) {
            row.items.add(TextLayoutItem.parseXML((Element) node));
         }
      }
      return row;
   }

   @Override
   public boolean equals(Object o) {
      if(!(o instanceof TextLayoutRow)) return false;
      TextLayoutRow other = (TextLayoutRow) o;
      return Objects.equals(items, other.items) && Objects.equals(getAlignment(), other.getAlignment());
   }

   @Override
   public int hashCode() { return Objects.hash(items, getAlignment()); }

   @Override
   public TextLayoutRow clone() {
      try {
         TextLayoutRow copy = (TextLayoutRow) super.clone();
         copy.items = new ArrayList<>();
         for(TextLayoutItem item : this.items) copy.items.add(item.clone());
         return copy;
      }
      catch(CloneNotSupportedException e) { throw new RuntimeException(e); }
   }

   private static final long serialVersionUID = 1L;
}
