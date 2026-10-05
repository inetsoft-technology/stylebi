/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.report;

import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.*;

import java.io.*;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77794: a calc-table hregion with a huge rows attribute must not hang the parse
 * thread through the real viewsheet parse and storage read, and a count-valid region with
 * many rows must parse in one step.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class Bug77794LayoutRowsVerifyTest {
   // the reported shapes: rows with no <rowHeight> children, through Viewsheet.parseXML
   @ParameterizedTest
   @ValueSource(strings = { "20000", "40000", "2147483647" })
   void forgedRowsFailFastThroughViewsheetParse(String rows) throws Exception {
      Element elem = parse(productViewsheetXml());
      firstRegion(elem).setAttribute("rows", rows);
      assertFails(() -> new Viewsheet().parseXML(elem), "invalid region rows");
   }

   // the real storage read fails with an ordinary Exception
   @Test
   void forgedRowsFailFastThroughStorageRead() throws Exception {
      Element elem = parse(productViewsheetXml());
      firstRegion(elem).setAttribute("rows", "2147483647");
      RawViewsheet.xml = toXml(elem);
      String key = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                  "/bug77794forged", null).toIdentifier();
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      storage.putXMLSerializable(key, new RawViewsheet());

      assertFails(() -> storage.getXMLSerializable(key, null), "invalid region rows");
      // not in a finally: a parse that hangs keeps holding the blob's lock, so remove would
      // wait forever instead of letting the test fail
      storage.remove(key);
   }

   // a count-valid single region at the cell cap, sized through one allocation
   @ParameterizedTest
   @ValueSource(ints = { 1, 3 })
   void countValidSingleRegionAtCapParsesFast(int cols) throws Exception {
      int rows = 100_000 / cols;
      String xml = singleRegionXml(cols, rows);
      TableLayout layout = new TableLayout();
      assertTimeoutPreemptively(Duration.ofSeconds(10), () -> layout.parseXML(parse(xml)));
      assertEquals(rows, layout.getRowCount());
      assertEquals(cols, layout.getColCount());
      TableLayout again = new TableLayout();
      assertTimeoutPreemptively(Duration.ofSeconds(10),
                                () -> again.parseXML(parse(toXml(layout))));
      assertEquals(rows, again.getRowCount());
   }

   // a count-valid region with many rows through the real viewsheet parse
   @Test
   void countValidManyRowsParsesFastThroughViewsheetParse() throws Exception {
      Element elem = parse(productViewsheetXml());
      Element layoutE = (Element) elem.getElementsByTagName("tableLayout").item(0);
      int cols = Integer.parseInt(layoutE.getAttribute("columns"));
      Element region = firstRegion(elem);
      int other = 0;
      NodeList regions = Tool.getChildNodeByTagName(layoutE, "hregions")
         .getElementsByTagName("region");

      for(int i = 1; i < regions.getLength(); i++) {
         other += Integer.parseInt(((Element) regions.item(i)).getAttribute("rows"));
      }

      int rows = 100_000 / cols - other;
      int existing = region.getElementsByTagName("rowHeight").getLength();

      for(int r = existing; r < rows; r++) {
         Element h = elem.getOwnerDocument().createElement("rowHeight");
         h.setAttribute("row", "" + r);
         h.setAttribute("height", "-1");
         region.appendChild(h);
      }

      region.setAttribute("rows", "" + rows);
      Viewsheet vs = new Viewsheet();
      assertTimeoutPreemptively(Duration.ofSeconds(10), () -> vs.parseXML(elem));
      CalcTableVSAssembly calc = (CalcTableVSAssembly) vs.getAssembly("Calc1");
      TableLayout layout = ((CalcTableVSAssemblyInfo) calc.getInfo()).getTableLayout();
      assertEquals(rows + other, layout.getRowCount());
   }

   public static class RawViewsheet extends Viewsheet {
      static String xml;

      public RawViewsheet() {
      }

      @Override
      public void writeXML(PrintWriter writer) {
         writer.print(xml);
      }
   }

   private static String singleRegionXml(int cols, int rows) {
      StringBuilder buf = new StringBuilder();
      buf.append("<tableLayout columns=\"").append(cols).append("\" mode=\"")
         .append(TableLayout.CALC).append("\">\n<hregions>\n<layoutRegion>\n")
         .append(toXml(new TableDataPath(-1, TableDataPath.DETAIL)))
         .append("<region rows=\"").append(rows)
         .append("\" visible=\"true\" virtual=\"false\">\n");

      for(int r = 0; r < rows; r++) {
         buf.append("<rowHeight row=\"").append(r).append("\" height=\"-1\"/>\n");
         buf.append("<rowBinding row=\"").append(r).append("\" binding=\"-1\"/>\n");
      }

      buf.append("</region>\n</layoutRegion>\n</hregions>\n<vregions>\n</vregions>\n")
         .append("<cwidths>\n</cwidths>\n<spans>\n</spans>\n</tableLayout>\n");
      return buf.toString();
   }

   private static String productViewsheetXml() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new CalcTableVSAssembly(vs, "Calc1"));
      return toXml(vs);
   }

   private static Element firstRegion(Element root) {
      Element layout = (Element) root.getElementsByTagName("tableLayout").item(0);
      Element hregions = Tool.getChildNodeByTagName(layout, "hregions");
      Element layoutRegion = Tool.getChildNodeByTagName(hregions, "layoutRegion");
      return Tool.getChildNodeByTagName(layoutRegion, "region");
   }

   private static String toXml(XMLSerializable obj) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      obj.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static String toXml(Element elem) throws Exception {
      javax.xml.transform.Transformer t =
         javax.xml.transform.TransformerFactory.newInstance().newTransformer();
      t.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
      StringWriter buf = new StringWriter();
      t.transform(new javax.xml.transform.dom.DOMSource(elem),
                  new javax.xml.transform.stream.StreamResult(buf));
      return buf.toString();
   }

   private static Element parse(String xml) throws Exception {
      return Tool.parseXML(new StringReader(xml)).getDocumentElement();
   }

   private static Throwable assertFails(Executable call, String message) {
      Throwable thrown = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
         try {
            call.execute();
         }
         catch(Throwable ex) {
            return ex;
         }

         return null;
      });

      assertTrue(thrown instanceof Exception, "expected an Exception, got " + thrown);
      assertTrue(thrown.getMessage() != null && thrown.getMessage().contains(message),
                 thrown.toString());
      return thrown;
   }
}
