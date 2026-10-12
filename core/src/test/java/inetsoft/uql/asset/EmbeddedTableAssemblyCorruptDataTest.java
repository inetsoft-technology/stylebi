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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77584: corrupt Base64 in the embedded data of an {@link EmbeddedTableAssembly} must be
 * logged and skipped when the data is loaded on demand, not thrown from the getter, and the
 * resulting table must still be complete so that it can be saved.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class EmbeddedTableAssemblyCorruptDataTest {
   // header + 2500 data rows, written as 3 blocks of at most 1000 rows
   private static final int DATA_ROWS = 2500;
   private static final int BLOCK_ROWS = 1000;

   @Test
   void validFragmentsLoadAllRows() throws Exception {
      EmbeddedTableAssembly assembly = parse(fragments(), -1, null);
      assertLoadedAndSavable(assembly, DATA_ROWS + 1, 2);
   }

   @Test
   void corruptFirstFragmentLeavesPlaceholder() throws Exception {
      EmbeddedTableAssembly assembly = parse(fragments(), 0, "AAAA~");
      XEmbeddedTable data = assertDoesNotThrow(assembly::getEmbeddedData);
      // the first block creates the table, without it the default placeholder remains
      assertEquals(1, data.getColCount());
      assertTrue(data.getRowCount() >= 0);
      assertSavable(assembly, data.getRowCount());
   }

   @Test
   void corruptMiddleFragmentDropsItsRows() throws Exception {
      EmbeddedTableAssembly assembly = parse(fragments(), 1, "AAAA~");
      XEmbeddedTable data = assertDoesNotThrow(assembly::getEmbeddedData);
      assertEquals(2, data.getColCount());
      assertEquals(DATA_ROWS + 1 - BLOCK_ROWS, data.getRowCount());
      assertEquals("r" + (2 * BLOCK_ROWS), data.getObject(BLOCK_ROWS, 0));
      assertSavable(assembly, DATA_ROWS + 1 - BLOCK_ROWS);
   }

   // every IllegalArgumentException variant of the basic decoder, in the last fragment, which
   // is the one that normally completes the table
   @ParameterizedTest
   @ValueSource(strings = { "AAAA~", "AAAAA", " AAAAAAAA", "AAAA\nAAAA", "AA=A", "AAA=A",
                            "é" })
   void corruptLastFragmentStillCompletesTable(String corrupt) throws Exception {
      EmbeddedTableAssembly assembly = parse(fragments(), 2, corrupt);
      XEmbeddedTable data = assertDoesNotThrow(assembly::getEmbeddedData);
      assertEquals(2, data.getColCount());
      assertEquals(2 * BLOCK_ROWS, data.getRowCount());
      assertEquals("r" + (2 * BLOCK_ROWS - 1), data.getObject(2 * BLOCK_ROWS - 1, 0));

      // the save loop fails with "Embedded table is not completely loaded!" if the inner
      // table was not completed
      XEmbeddedTable copy = data.clone();
      copy.reset();
      int blocks = 0;

      while(copy.hasNextBlock()) {
         copy.writeData(new DataOutputStream(new ByteArrayOutputStream()), true);
         blocks++;
      }

      assertEquals(2, blocks);
      assertSavable(assembly, 2 * BLOCK_ROWS);
   }

   @Test
   void corruptDataIsNotDecodedAgain() throws Exception {
      EmbeddedTableAssembly assembly = parse(fragments(), 2, "AAAA~");
      EmbeddedTableAssembly clone = (EmbeddedTableAssembly) assembly.clone();
      XEmbeddedTable first = assertDoesNotThrow(assembly::getEmbeddedData);
      assertSame(first, assembly.getEmbeddedData());
      assertEquals(2 * BLOCK_ROWS, assertDoesNotThrow(clone::getEmbeddedData).getRowCount());
   }

   @Test
   void corruptFullFragmentLeavesPlaceholder() throws Exception {
      Element elem = Tool.parseXML(new StringReader(
         "<assembly><embeddedData><![CDATA[\nAAAA~\n]]></embeddedData></assembly>"))
         .getDocumentElement();
      EmbeddedTableAssembly assembly = new EmbeddedTableAssembly();
      assembly.parseEmbeddedData(elem);

      XEmbeddedTable data = assertDoesNotThrow(assembly::getEmbeddedData);
      assertEquals(1, data.getColCount());
      assertTrue(data.getRowCount() >= 0);
      assertSavable(assembly, data.getRowCount());
   }

   @Test
   void validFullFragmentLoads() throws Exception {
      XEmbeddedTable original = table();
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      original.writeData(new DataOutputStream(buf), false);
      String encoded = Base64.getEncoder().encodeToString(buf.toByteArray());
      Element elem = Tool.parseXML(new StringReader(
         "<assembly><embeddedData><![CDATA[\n" + encoded + "\n]]></embeddedData></assembly>"))
         .getDocumentElement();
      EmbeddedTableAssembly assembly = new EmbeddedTableAssembly();
      assembly.parseEmbeddedData(elem);
      assertLoadedAndSavable(assembly, DATA_ROWS + 1, 2);
   }

   @Test
   void metadataModeWithCorruptFirstFragment() throws Exception {
      EmbeddedTableAssembly assembly = parse(fragments(), 0, "AAAA~");
      assembly.setForMetadata(true);
      XEmbeddedTable data = assertDoesNotThrow(assembly::getEmbeddedData);
      assertEquals(1, data.getColCount());
      assertTrue(data.getRowCount() >= 0);
      assertSavable(assembly, data.getRowCount());
   }

   @Test
   void metadataModeLoadsOnlyFirstFragment() throws Exception {
      EmbeddedTableAssembly assembly = parse(fragments(), 2, "AAAA~");
      assembly.setForMetadata(true);
      XEmbeddedTable data = assertDoesNotThrow(assembly::getEmbeddedData);
      assertEquals(2, data.getColCount());
      assertEquals(BLOCK_ROWS, data.getRowCount());
   }

   private static void assertLoadedAndSavable(EmbeddedTableAssembly assembly, int rows, int cols)
      throws Exception
   {
      XEmbeddedTable data = assembly.getEmbeddedData();
      assertEquals(cols, data.getColCount());
      assertEquals(rows, data.getRowCount());
      assertSavable(assembly, rows);
   }

   // writes the embedded data as the worksheet save does and parses it back
   private static void assertSavable(EmbeddedTableAssembly assembly, int rows) throws Exception {
      StringWriter xml = new StringWriter();
      PrintWriter writer = new PrintWriter(xml);
      assertDoesNotThrow(() -> assembly.writeEmbeddedData0(writer));
      writer.flush();

      Element elem = Tool.parseXML(new StringReader("<assembly>" + xml + "</assembly>"))
         .getDocumentElement();
      EmbeddedTableAssembly reparsed = new EmbeddedTableAssembly();
      reparsed.parseEmbeddedData(elem);
      assertEquals(rows, reparsed.getEmbeddedData().getRowCount());
   }

   private static EmbeddedTableAssembly parse(List<String> fragments, int corruptIndex,
                                              String corrupt) throws Exception
   {
      StringBuilder xml = new StringBuilder("<assembly>");

      for(int i = 0; i < fragments.size(); i++) {
         String fragment = i == corruptIndex ? corrupt : fragments.get(i);
         xml.append("<embeddedDatas><![CDATA[\n").append(fragment)
            .append("\n]]></embeddedDatas>");
      }

      xml.append("</assembly>");
      Element elem = Tool.parseXML(new StringReader(xml.toString())).getDocumentElement();
      EmbeddedTableAssembly assembly = new EmbeddedTableAssembly();
      assembly.parseEmbeddedData(elem);
      return assembly;
   }

   private static List<String> fragments() {
      XEmbeddedTable table = table();
      List<String> fragments = new ArrayList<>();
      table.reset();

      while(table.hasNextBlock()) {
         ByteArrayOutputStream buf = new ByteArrayOutputStream();
         table.writeData(new DataOutputStream(buf), true);
         fragments.add(Base64.getEncoder().encodeToString(buf.toByteArray()));
      }

      assertEquals(3, fragments.size());
      return fragments;
   }

   private static XEmbeddedTable table() {
      Object[][] rows = new Object[DATA_ROWS + 1][];
      rows[0] = new Object[] { "name", "value" };

      for(int i = 1; i <= DATA_ROWS; i++) {
         rows[i] = new Object[] { "r" + i, i };
      }

      return new XEmbeddedTable(new String[] { XSchema.STRING, XSchema.INTEGER }, rows);
   }
}
