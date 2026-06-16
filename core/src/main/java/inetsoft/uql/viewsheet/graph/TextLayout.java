package inetsoft.uql.viewsheet.graph;

import org.w3c.dom.*;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class TextLayout implements Serializable, Cloneable {
   private List<TextLayoutRow> rows = new ArrayList<>();

   public List<TextLayoutRow> getRows() { return rows; }
   public void setRows(List<TextLayoutRow> rows) { this.rows = rows; }
   public void addRow(TextLayoutRow row) { rows.add(row); }

   /** Flattens all items from all rows in document order. */
   public List<TextLayoutItem> getAllItems() {
      return rows.stream()
         .flatMap(r -> r.getItems().stream())
         .collect(Collectors.toList());
   }

   /** All FIELD item indices across all rows, in row-major order (first-appearance deduped). */
   public List<Integer> getFieldIndices() {
      java.util.LinkedHashSet<Integer> idx = new java.util.LinkedHashSet<>();
      for(TextLayoutItem item : getAllItems()) {
         if(item.getType() == TextLayoutItem.FIELD && item.getFieldIndex() >= 0) {
            idx.add(item.getFieldIndex());
         }
      }
      return new ArrayList<>(idx);
   }

   /** Remove FIELD items with no valid field index (orphan cleanup). */
   public void pruneOrphans() {
      for(TextLayoutRow row : rows) {
         row.getItems().removeIf(
            i -> i.getType() == TextLayoutItem.FIELD && i.getFieldIndex() < 0);
      }
      rows.removeIf(r -> r.getItems().isEmpty());
   }

   /** True when the layout is trivially single-field with no static/spacing items. */
   public boolean isTrivial() {
      List<TextLayoutItem> all = getAllItems();
      long fieldCount = all.stream().filter(i -> i.getType() == TextLayoutItem.FIELD).count();
      long otherCount = all.stream().filter(i -> i.getType() != TextLayoutItem.FIELD).count();
      return fieldCount <= 1 && otherCount == 0 && rows.size() <= 1;
   }

   /**
    * Carry panel-set inline formatting of STATIC items from one layout onto another, matched by
    * item text. Only items in {@code from} that actually carry inline format participate; a matched
    * STATIC item in {@code to} is overwritten with the source's full inline format. This is the
    * single source of truth for preserving Format-panel static styling across a binding-model
    * round-trip (the frontend layout model carries item text but not its inline color/font), so an
    * unformatted source item never clobbers a value the incoming layout may carry.
    */
   public static void carryStaticItemFormatting(TextLayout from, TextLayout to) {
      if(from == null || to == null) {
         return;
      }

      Map<String, TextLayoutItem> byText = new HashMap<>();

      for(TextLayoutItem item : from.getAllItems()) {
         if(item.getType() == TextLayoutItem.STATIC && item.hasInlineFormat()) {
            byText.putIfAbsent(item.getText() != null ? item.getText() : "", item);
         }
      }

      if(byText.isEmpty()) {
         return;
      }

      for(TextLayoutItem item : to.getAllItems()) {
         if(item.getType() != TextLayoutItem.STATIC) {
            continue;
         }

         TextLayoutItem src = byText.get(item.getText() != null ? item.getText() : "");

         if(src != null) {
            item.setColor(src.getColor());
            item.setFontFamily(src.getFontFamily());
            item.setFontSize(src.getFontSize());
            item.setBold(src.isBold());
            item.setItalic(src.isItalic());
         }
      }
   }

   public void writeXML(PrintWriter writer) {
      writer.println("<textLayout>");
      for(TextLayoutRow row : rows) {
         row.writeXML(writer);
      }
      writer.println("</textLayout>");
   }

   public static TextLayout parseXML(Element elem) {
      TextLayout layout = new TextLayout();
      NodeList children = elem.getChildNodes();
      for(int i = 0; i < children.getLength(); i++) {
         Node node = children.item(i);
         if(!(node instanceof Element)) continue;
         String name = node.getNodeName();
         if("textLayoutRow".equals(name)) {
            layout.rows.add(TextLayoutRow.parseXML((Element) node));
         }
         else if("textLayoutItem".equals(name)) {
            // Backward compat: old flat-list format — wrap each item in its own row
            TextLayoutItem item = TextLayoutItem.parseXML((Element) node);
            TextLayoutRow row = new TextLayoutRow();
            row.addItem(item);
            layout.rows.add(row);
         }
      }
      return layout;
   }

   @Override
   public boolean equals(Object o) {
      if(!(o instanceof TextLayout)) return false;
      return Objects.equals(rows, ((TextLayout) o).rows);
   }

   @Override
   public int hashCode() { return Objects.hash(rows); }

   @Override
   public TextLayout clone() {
      try {
         TextLayout copy = (TextLayout) super.clone();
         copy.rows = new ArrayList<>();
         for(TextLayoutRow row : rows) copy.rows.add(row.clone());
         return copy;
      }
      catch(CloneNotSupportedException e) { throw new RuntimeException(e); }
   }

   private static final long serialVersionUID = 1L;
}
