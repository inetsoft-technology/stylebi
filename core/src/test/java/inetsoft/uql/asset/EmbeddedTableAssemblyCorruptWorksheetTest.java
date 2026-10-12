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
package inetsoft.uql.asset;

import inetsoft.test.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.Tool;
import inetsoft.util.TransformerManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77584: a whole worksheet whose embedded table holds corrupt Base64 must load, and the
 * save loop (Worksheet.writeXML, parse, getEmbeddedData) must keep working and be stable.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class EmbeddedTableAssemblyCorruptWorksheetTest {
   private static final int DATA_ROWS = 2500;
   private static final int BLOCK_ROWS = 1000;
   private static final Pattern FRAGMENT = Pattern.compile(
      "(<embeddedDatas><!\\[CDATA\\[\\s*)([^\\]]*?)(\\s*\\]\\]></embeddedDatas>)");

   // the reporter's minimal worksheet
   @Test
   void reporterWorksheetLoadsAndSaves() throws Exception {
      String xml = "<worksheet><assemblies><oneAssembly>" +
         "<assembly class=\"inetsoft.uql.asset.EmbeddedTableAssembly\"><assemblyInfo>" +
         "<normalColumnSelection></normalColumnSelection>" +
         "<crosstabColumnSelection></crosstabColumnSelection></assemblyInfo>" +
         "<embeddedDatas><![CDATA[\nAAAA~\n]]></embeddedDatas>" +
         "</assembly></oneAssembly></assemblies></worksheet>";
      Worksheet ws = parse(xml);
      EmbeddedTableAssembly table = onlyEmbedded(ws);
      XEmbeddedTable data = assertDoesNotThrow(table::getEmbeddedData);
      assertTrue(data.getRowCount() >= 0);

      String saved = write(ws);
      XEmbeddedTable reloaded = onlyEmbedded(parse(saved)).getEmbeddedData();
      assertEquals(data.getRowCount(), reloaded.getRowCount());
      assertEquals(data.getColCount(), reloaded.getColCount());
   }

   @Test
   void validWorksheetRoundTripsUnchanged() throws Exception {
      String saved = write(worksheet());
      Worksheet ws = parse(saved);
      XEmbeddedTable data = onlyEmbedded(ws).getEmbeddedData();
      assertEquals(DATA_ROWS + 1, data.getRowCount());
      assertEquals("name", data.getObject(0, 0));
      assertEquals("r" + DATA_ROWS, data.getObject(DATA_ROWS, 0));
      assertEquals(DATA_ROWS, data.getObject(DATA_ROWS, 1));
      assertEquals(saved, write(ws));
   }

   // a corrupt middle or last block in a saved worksheet: load, save, reload, save again
   @ParameterizedTest
   @ValueSource(ints = { 1, 2 })
   void corruptBlockSurvivesSaveLoop(int corruptIndex) throws Exception {
      String corrupt = corrupt(write(worksheet()), corruptIndex, "AAAA~");
      Worksheet ws = parse(corrupt);
      XEmbeddedTable data = assertDoesNotThrow(onlyEmbedded(ws)::getEmbeddedData);
      int expected = corruptIndex == 2 ? 2 * BLOCK_ROWS : DATA_ROWS + 1 - BLOCK_ROWS;
      assertEquals(expected, data.getRowCount());

      String saved = assertDoesNotThrow(() -> write(ws));
      Worksheet reloaded = parse(saved);
      XEmbeddedTable reloadedData = onlyEmbedded(reloaded).getEmbeddedData();
      assertEquals(expected, reloadedData.getRowCount());
      assertEquals("name", reloadedData.getObject(0, 0));
      assertEquals(saved, write(reloaded));
   }

   private static Worksheet worksheet() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "T1");
      Object[][] rows = new Object[DATA_ROWS + 1][];
      rows[0] = new Object[] { "name", "value" };

      for(int i = 1; i <= DATA_ROWS; i++) {
         rows[i] = new Object[] { "r" + i, i };
      }

      table.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.STRING, XSchema.INTEGER },
                                               rows));
      ws.addAssembly(table);
      return ws;
   }

   private static String corrupt(String xml, int index, String value) {
      Matcher matcher = FRAGMENT.matcher(xml);
      StringBuilder out = new StringBuilder();
      int i = 0;

      while(matcher.find()) {
         String body = i++ == index ? value : matcher.group(2);
         matcher.appendReplacement(out, Matcher.quoteReplacement(
            matcher.group(1) + body + matcher.group(3)));
      }

      matcher.appendTail(out);
      assertEquals(3, i, "expected 3 embedded data blocks");
      return out.toString();
   }

   private static EmbeddedTableAssembly onlyEmbedded(Worksheet ws) {
      EmbeddedTableAssembly found = null;

      for(Assembly assembly : ws.getAssemblies()) {
         if(assembly instanceof EmbeddedTableAssembly table) {
            assertNull(found);
            found = table;
         }
      }

      assertNotNull(found);
      return found;
   }

   private static Worksheet parse(String xml) throws Exception {
      Document doc = Tool.parseXML(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      TransformerManager.getManager(TransformerManager.WORKSHEET).transform(doc);
      Worksheet ws = new Worksheet();
      ws.parseXML(doc.getDocumentElement(), false);
      return ws;
   }

   private static String write(Worksheet ws) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      ws.writeXML(writer);
      writer.flush();
      return buffer.toString();
   }
}
