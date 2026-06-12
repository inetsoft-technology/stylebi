package inetsoft.uql.viewsheet.graph;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import javax.xml.parsers.*;
import org.xml.sax.InputSource;
import java.io.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class TextLayoutTest {
   @Test void roundTripsXml() throws Exception {
      TextLayout layout = new TextLayout();
      TextLayoutRow row0 = new TextLayoutRow();
      row0.addItem(TextLayoutItem.ofField(0));
      row0.addItem(TextLayoutItem.ofStatic(": "));
      row0.addItem(TextLayoutItem.ofField(1));
      layout.addRow(row0);
      StringWriter sw = new StringWriter();
      layout.writeXML(new PrintWriter(sw));
      var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new InputSource(new StringReader(sw.toString())));
      assertEquals(layout, TextLayout.parseXML(doc.getDocumentElement()));
   }

   @Test
   void collectsDistinctFieldIndicesInRowMajorOrder() {
      TextLayout layout = new TextLayout();
      TextLayoutRow row = new TextLayoutRow();
      row.addItem(TextLayoutItem.ofField(1));
      row.addItem(TextLayoutItem.ofStatic("-"));
      row.addItem(TextLayoutItem.ofField(0));
      row.addItem(TextLayoutItem.ofField(1)); // duplicate -> deduped
      layout.addRow(row);

      assertEquals(java.util.List.of(1, 0), layout.getFieldIndices());
   }

   @Test void trivialLayoutWhenSingleField() {
      TextLayout layout = new TextLayout();
      TextLayoutRow row = new TextLayoutRow();
      row.addItem(TextLayoutItem.ofField(0));
      layout.addRow(row);
      assertTrue(layout.isTrivial());
   }

   @Test void notTrivialWhenMultipleFields() {
      TextLayout layout = new TextLayout();
      TextLayoutRow row = new TextLayoutRow();
      row.addItem(TextLayoutItem.ofField(0));
      row.addItem(TextLayoutItem.ofField(1));
      layout.addRow(row);
      assertFalse(layout.isTrivial());
   }

   @Test void parseXmlWithLegacyFieldIndexAttributeDoesNotThrow() throws Exception {
      // Old format used fieldIndex (integer) — must parse without error
      String xml = "<textLayout><textLayoutRow><textLayoutItem type=\"0\" fieldIndex=\"0\"/></textLayoutRow></textLayout>";
      var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new InputSource(new StringReader(xml)));
      TextLayout layout = TextLayout.parseXML(doc.getDocumentElement());
      assertNotNull(layout);
      assertEquals(1, layout.getRows().size());
   }

   @Test void cloneIsDeep() {
      TextLayout layout = new TextLayout();
      TextLayoutRow row = new TextLayoutRow();
      row.addItem(TextLayoutItem.ofField(0));
      layout.addRow(row);
      TextLayout copy = layout.clone();
      copy.getRows().clear();
      assertEquals(1, layout.getRows().size(), "original must not be affected by clone mutation");
   }

   private static TextLayout staticLayout(String text, java.awt.Color color, String family,
                                          int size, boolean bold)
   {
      TextLayout tl = new TextLayout();
      TextLayoutRow row = new TextLayoutRow();
      TextLayoutItem item = TextLayoutItem.ofStatic(text);
      item.setColor(color);
      item.setFontFamily(family);
      item.setFontSize(size);
      item.setBold(bold);
      row.addItem(item);
      tl.addRow(row);
      return tl;
   }

   @Test void carryStaticItemFormattingCopiesFromFormattedSource() {
      TextLayout from = staticLayout("Total:", java.awt.Color.CYAN, "Roboto", 24, true);
      TextLayout to = staticLayout("Total:", null, null, 10, false);

      TextLayout.carryStaticItemFormatting(from, to);

      TextLayoutItem merged = to.getAllItems().get(0);
      assertEquals(java.awt.Color.CYAN, merged.getColor());
      assertEquals("Roboto", merged.getFontFamily());
      assertEquals(24, merged.getFontSize());
      assertTrue(merged.isBold());
   }

   @Test void carryStaticItemFormattingMatchesByText() {
      TextLayout from = staticLayout("Total:", java.awt.Color.CYAN, "Roboto", 24, true);
      TextLayout to = staticLayout("Sum:", null, null, 10, false);

      TextLayout.carryStaticItemFormatting(from, to);

      TextLayoutItem merged = to.getAllItems().get(0);
      assertNull(merged.getColor());
      assertNull(merged.getFontFamily());
   }

   @Test void carryStaticItemFormattingUnformattedSourceDoesNotClobber() {
      // The unified (filtered) semantics: a source item with no inline format must NOT wipe a
      // value the target already carries.
      TextLayout from = staticLayout("Total:", null, null, -1, false);
      TextLayout to = staticLayout("Total:", java.awt.Color.RED, "Arial", 18, true);

      TextLayout.carryStaticItemFormatting(from, to);

      TextLayoutItem kept = to.getAllItems().get(0);
      assertEquals(java.awt.Color.RED, kept.getColor());
      assertEquals("Arial", kept.getFontFamily());
      assertEquals(18, kept.getFontSize());
      assertTrue(kept.isBold());
   }

   @Test void carryStaticItemFormattingNullLayoutsAreNoOps() {
      TextLayout layout = staticLayout("Total:", null, null, 10, false);
      assertDoesNotThrow(() -> TextLayout.carryStaticItemFormatting(null, layout));
      assertDoesNotThrow(() -> TextLayout.carryStaticItemFormatting(layout, null));
   }
}
