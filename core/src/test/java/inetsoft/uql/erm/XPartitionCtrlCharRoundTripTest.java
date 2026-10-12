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
package inetsoft.uql.erm;

import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.awt.Rectangle;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78327: an inline view's sql, and the description and alias table of a partition,
 * were written into CDATA without handling characters XML 1.0 can't carry, so the saved
 * data model could not be read back.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XPartitionCtrlCharRoundTripTest {
   private static final String BS = String.valueOf((char) 92);

   @Test
   void plainSqlIsUnmarkedAndUnchanged() throws Exception {
      String sql = "select 'ab' from t";
      String xml = xml(partition(sql, null, null));

      assertFalse(xml.contains("ctrlEncoded"));
      assertTrue(xml.contains("<sql><![CDATA[" + Tool.encodeCDATA(sql) + "]]></sql>"));
      assertSql(sql, xml);
   }

   @Test
   void controlCharInLiteral() throws Exception {
      String sql = "select 'a" + (char) 0x1f + "b' from t";
      String xml = xml(partition(sql, null, null));

      assertTrue(xml.contains("ctrlEncoded"));
      assertSql(sql, xml);
   }

   @Test
   void nulFollowedByX0() throws Exception {
      assertSql("select '" + (char) 0 + "x0' from t", xml(partition(
         "select '" + (char) 0 + "x0' from t", null, null)));
   }

   // Guards round-trip losslessness only: the parser accepts a raw U+FFFE, so this case
   // passes on the unfixed writer too and does not prove parseability.
   @Test
   void nonCharacterFffe() throws Exception {
      assertSql("select '" + (char) 0xfffe + "' from t", xml(partition(
         "select '" + (char) 0xfffe + "' from t", null, null)));
   }

   @Test
   void cdataEndWithControlChar() throws Exception {
      String sql = "select 'a]]>b" + (char) 1 + "' from t";
      assertSql(sql, xml(partition(sql, null, null)));
   }

   @Test
   void markedSqlKeepsLegacyMappingText() throws Exception {
      String sql = "select 'a]] b" + (char) 2 + "' from t";
      assertSql(sql, xml(partition(sql, null, null)));
   }

   @Test
   void cdataEndWithoutControlChar() throws Exception {
      String sql = "select 'a]]>b' from t";
      assertSql(sql, xml(partition(sql, null, null)));
   }

   @Test
   void literalBackslashUText() throws Exception {
      // unmarked: the text must not be decoded
      String plain = "select '" + BS + "u001f' from t";
      String xml = xml(partition(plain, null, null));
      assertFalse(xml.contains("ctrlEncoded"));
      assertSql(plain, xml);

      // marked: the backslash is escaped and restored
      String marked = "select '" + BS + "u001f" + (char) 3 + BS + "' from t";
      assertSql(marked, xml(partition(marked, null, null)));
   }

   @Test
   void legacyUnmarkedMappingStillDecodes() throws Exception {
      String xml = xml(partition("select 1 from t", null, null))
         .replace("select 1 from t", "select 'a]] b' from t");

      assertSql("select 'a]]b' from t", xml);
   }

   @Test
   void descriptionAndAliasTable() throws Exception {
      for(String text : new String[] {
         "d" + (char) 0x1f + "e", "d]]>e", "d]]>e" + (char) 1, "plain", "d" + BS + "u001f" })
      {
         XPartition back = roundTrip(partition(null, text, text));

         assertEquals(text, back.getDescription());
         assertEquals(text, back.getAliasTable("v2"));
      }
   }

   @Test
   void extendedViewSqlAndDescription() throws Exception {
      String sql = "select 'a" + (char) 0x1f + "]]>b' from t";
      String desc = "d" + (char) 2 + "]]>e";
      XPartition base = new XPartition("base");
      base.addTable("bv", PartitionTable.VIEW, "select 1 from t",
                    new Rectangle(0, 0, 10, 10), null, null);
      XPartition ext = new XPartition("ext", base);
      ext.addTable("ev", PartitionTable.VIEW, sql, new Rectangle(0, 0, 10, 10), null, null);
      ext.setDescription(desc);

      String xml = xml(ext);
      assertFalse(xml.contains("select 1 from t"), "base table must not be written");
      assertTrue(xml.contains("ctrlEncoded"));

      XPartition back = new XPartition();
      back.parseXML(parseElement(xml));
      // getDescription() delegates to the base once one is set, so read it first
      assertEquals(desc, back.getDescription());
      back.setBaseParitition(base);

      assertEquals(sql, back.getPartitionTable("ev").getSql());
      assertEquals("select 1 from t", back.getPartitionTable("bv").getSql());
   }

   @Test
   void catalogSchemaAndAuthors() throws Exception {
      for(String text : new String[] { "c" + (char) 0x1f + "d", "c]]>d", "c]]>d" + (char) 1 }) {
         XPartition part = new XPartition("p");
         part.addTable("t", PartitionTable.PHYSICAL, "", new Rectangle(0, 0, 10, 10),
                       text, text);
         part.setCreatedBy(text);
         part.setLastModifiedBy(text);

         XPartition back = roundTrip(part);

         assertEquals(text, back.getPartitionTable("t").getCatalog());
         assertEquals(text, back.getPartitionTable("t").getSchema());
         assertEquals(text, back.getCreatedBy());
         assertEquals(text, back.getLastModifiedBy());
      }
   }

   private static XPartition partition(String sql, String description, String alias) {
      XPartition part = new XPartition("p");

      if(sql != null) {
         part.addTable("v", PartitionTable.VIEW, sql, new Rectangle(0, 0, 10, 10), null, null);
      }

      if(description != null) {
         part.setDescription(description);
         part.addTable("v2", PartitionTable.PHYSICAL, "", new Rectangle(0, 0, 10, 10), null, null);
         part.setAliasTable("v2", alias);
      }

      return part;
   }

   private static void assertSql(String sql, String xml) throws Exception {
      XPartition back = parse(xml);
      assertEquals(sql, back.getPartitionTable("v").getSql());
      assertEquals(sql, ((XPartition) back.clone()).getPartitionTable("v").getSql());
   }

   private static XPartition roundTrip(XPartition part) throws Exception {
      return parse(xml(part));
   }

   private static Element parseElement(String xml) throws Exception {
      byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
      return Tool.parseXML(new ByteArrayInputStream(bytes), "UTF-8", false, false)
         .getDocumentElement();
   }

   private static XPartition parse(String xml) throws Exception {
      XPartition back = new XPartition();
      back.parseXML(parseElement(xml));
      return back;
   }

   private static String xml(XPartition part) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      part.writeXML(pw);
      pw.flush();
      return sw.toString();
   }
}
