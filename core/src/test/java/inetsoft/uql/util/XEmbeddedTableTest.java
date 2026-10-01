/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.uql.util;

import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class XEmbeddedTableTest {
   @Test
   public void testSerialize() throws Exception {
      XEmbeddedTable originalTable = new XEmbeddedTable(XTableUtil.getDefaultTableLens());
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(XEmbeddedTable.class, deserializedTable.getClass());
   }

   // Bug #77412: a corrupt column count must be rejected without an OutOfMemoryError
   @Test
   public void rejectsPieceWithHugeColumnCount() throws Exception {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(buf);
      out.writeBoolean(true);
      out.writeInt(1);
      out.writeInt(Integer.MAX_VALUE);

      assertRejected(buf.toByteArray(), true);
   }

   @Test
   public void rejectsFullDataWithHugeColumnCount() throws Exception {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(buf);
      out.writeInt(1);
      out.writeInt(Integer.MAX_VALUE);

      assertRejected(buf.toByteArray(), false);
   }

   @Test
   public void rejectsCountsWhoseProductOverflows() throws Exception {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(buf);
      out.writeBoolean(false);
      out.writeInt(Integer.MAX_VALUE);
      out.writeInt(Integer.MAX_VALUE);

      assertRejected(buf.toByteArray(), true);
   }

   @Test
   public void rejectsNegativeCounts() throws Exception {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(buf);
      out.writeBoolean(true);
      out.writeInt(-1);
      out.writeInt(-5);

      assertRejected(buf.toByteArray(), true);
   }

   @Test
   public void rejectsHugeRowCountWithNoColumns() throws Exception {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(buf);
      out.writeBoolean(true);
      out.writeInt(Integer.MAX_VALUE);
      out.writeInt(0);
      out.writeBoolean(true);

      assertRejected(buf.toByteArray(), true);
   }

   @Test
   public void parsesValidPieceData() throws Exception {
      XEmbeddedTable original = new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.INTEGER },
         new Object[][] { { "name", "value" }, { "a", 1 }, { "b", 2 } });
      original.reset();
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      original.writeData(new DataOutputStream(buf), true);

      XEmbeddedTable table = new XEmbeddedTable();
      table.parseData(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())),
                      true, true);

      Assertions.assertEquals(2, table.getColCount());
      Assertions.assertEquals(3, table.getRowCount());
      Assertions.assertEquals("b", table.getObject(2, 0));
      Assertions.assertEquals(2, table.getObject(2, 1));
   }

   @Test
   public void parsesValidFullDataWithNullCell() throws Exception {
      XEmbeddedTable original = new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.INTEGER },
         new Object[][] { { "name", "value" }, { "a", null }, { null, 2 } });
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      original.writeData(new DataOutputStream(buf), false);

      XEmbeddedTable table = new XEmbeddedTable();
      table.parseData(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())),
                      false, true);

      Assertions.assertEquals(2, table.getColCount());
      Assertions.assertEquals(3, table.getRowCount());
      Assertions.assertEquals("a", table.getObject(1, 0));
      Assertions.assertNull(table.getObject(1, 1));
      Assertions.assertEquals(2, table.getObject(2, 1));
   }

   // Bug #77445: writeXML must produce well-formed XML that parseXML can read back
   @Test
   public void writeXmlRoundTripsThroughParser() throws Exception {
      XEmbeddedTable original = new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.INTEGER },
         new Object[][] { { "name", "value" }, { "a", 1 }, { "b", 2 } });
      StringWriter xml = new StringWriter();
      original.writeXML(new PrintWriter(xml));

      Element elem = Tool.parseXML(new StringReader(xml.toString())).getDocumentElement();
      XEmbeddedTable table = new XEmbeddedTable();
      table.parseXML(elem);

      Assertions.assertEquals("3", Tool.getAttribute(elem, "row"));
      Assertions.assertEquals("2", Tool.getAttribute(elem, "col"));
      Assertions.assertTrue(table.isStrictNull());
      Assertions.assertEquals(2, table.getColCount());
      Assertions.assertEquals(3, table.getRowCount());
      Assertions.assertEquals("b", table.getObject(2, 0));
      Assertions.assertEquals(2, table.getObject(2, 1));
   }

   private static void assertRejected(byte[] data, boolean piece) {
      XEmbeddedTable table = new XEmbeddedTable();
      DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
      Assertions.assertDoesNotThrow(() -> table.parseData(input, piece, true));
      // the default table is left unchanged
      Assertions.assertEquals(1, table.getColCount());
      Assertions.assertEquals(2, table.getRowCount());
   }
}
