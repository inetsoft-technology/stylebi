package inetsoft.uql.viewsheet.graph;

import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import javax.xml.parsers.*;
import org.xml.sax.InputSource;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class TextLayoutItemTest {
   @Test void fieldItemRoundTrips() throws Exception {
      TextLayoutItem item = TextLayoutItem.ofField(0);
      assertRoundTrip(item);
   }

   @Test
   void fieldIndexRoundTripsThroughXml() throws Exception {
      TextLayoutItem item = TextLayoutItem.ofField(2);
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      item.writeXML(pw);
      pw.flush();

      Document doc = Tool.parseXML(new java.io.StringReader(sw.toString()));
      TextLayoutItem parsed = TextLayoutItem.parseXML(doc.getDocumentElement());

      assertEquals(TextLayoutItem.FIELD, parsed.getType());
      assertEquals(2, parsed.getFieldIndex());
   }

   @Test void staticItemRoundTrips() throws Exception {
      TextLayoutItem item = TextLayoutItem.ofStatic(": ");
      assertRoundTrip(item);
   }

   @Test void staticItemWithSpecialCharsRoundTrips() throws Exception {
      TextLayoutItem item = TextLayoutItem.ofStatic("a & b < c > d \" e");
      assertRoundTrip(item);
   }

   private void assertRoundTrip(TextLayoutItem item) throws Exception {
      StringWriter sw = new StringWriter();
      item.writeXML(new PrintWriter(sw));
      var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new InputSource(new StringReader(sw.toString())));
      assertEquals(item, TextLayoutItem.parseXML(doc.getDocumentElement()));
   }
}
