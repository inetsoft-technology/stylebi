/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

class AutoAliasTest {

   private AutoAlias autoAlias;

   @BeforeEach
   void setUp() {
      autoAlias = new AutoAlias();
   }

   // ---- AutoAlias.addIncomingJoin ----

   @Test
   void addIncomingJoinAppendsToList() {
      AutoAlias.IncomingJoin join = makeJoin("orders", "orders_alias", "pfx_", true);
      autoAlias.addIncomingJoin(join);

      assertEquals(1, autoAlias.getIncomingJoinCount());
      assertSame(join, autoAlias.getIncomingJoin(0));
   }

   @Test
   void addMultipleJoinsPreservesOrder() {
      autoAlias.addIncomingJoin(makeJoin("tableA", "aliasA", null, false));
      autoAlias.addIncomingJoin(makeJoin("tableB", "aliasB", null, false));
      autoAlias.addIncomingJoin(makeJoin("tableC", "aliasC", null, false));

      assertEquals(3, autoAlias.getIncomingJoinCount());
      assertEquals("tableA", autoAlias.getIncomingJoin(0).getSourceTable());
      assertEquals("tableB", autoAlias.getIncomingJoin(1).getSourceTable());
      assertEquals("tableC", autoAlias.getIncomingJoin(2).getSourceTable());
   }

   // ---- AutoAlias.removeIncomingJoin(int) ----

   @Test
   void removeIncomingJoinByIndexRemovesCorrectJoin() {
      autoAlias.addIncomingJoin(makeJoin("first", "a1", null, false));
      autoAlias.addIncomingJoin(makeJoin("second", "a2", null, false));
      autoAlias.addIncomingJoin(makeJoin("third", "a3", null, false));

      autoAlias.removeIncomingJoin(1);

      assertEquals(2, autoAlias.getIncomingJoinCount());
      assertEquals("first", autoAlias.getIncomingJoin(0).getSourceTable());
      assertEquals("third", autoAlias.getIncomingJoin(1).getSourceTable());
   }

   @Test
   void removeIncomingJoinByIndexDecreasesCount() {
      autoAlias.addIncomingJoin(makeJoin("t1", "a1", null, false));
      autoAlias.addIncomingJoin(makeJoin("t2", "a2", null, false));

      autoAlias.removeIncomingJoin(0);

      assertEquals(1, autoAlias.getIncomingJoinCount());
   }

   // ---- AutoAlias.removeIncomingJoin(String) ----

   @Test
   void removeIncomingJoinByTableNameRemovesAllMatchingJoins() {
      autoAlias.addIncomingJoin(makeJoin("orders", "orders1", null, false));
      autoAlias.addIncomingJoin(makeJoin("orders", "orders2", null, false));
      autoAlias.addIncomingJoin(makeJoin("customers", "cust1", null, false));

      autoAlias.removeIncomingJoin("orders");

      assertEquals(1, autoAlias.getIncomingJoinCount());
      assertEquals("customers", autoAlias.getIncomingJoin(0).getSourceTable());
   }

   @Test
   void removeIncomingJoinByTableNameIsNoopWhenNotFound() {
      autoAlias.addIncomingJoin(makeJoin("orders", "o1", null, false));

      autoAlias.removeIncomingJoin("nonexistent");

      assertEquals(1, autoAlias.getIncomingJoinCount());
   }

   // ---- AutoAlias.removeAllIncomingJoins ----

   @Test
   void removeAllIncomingJoinsClearsTheList() {
      autoAlias.addIncomingJoin(makeJoin("t1", "a1", null, false));
      autoAlias.addIncomingJoin(makeJoin("t2", "a2", null, false));

      autoAlias.removeAllIncomingJoins();

      assertEquals(0, autoAlias.getIncomingJoinCount());
   }

   // ---- AutoAlias.getIncomingJoinCount ----

   @Test
   void getIncomingJoinCountReturnsZeroForEmpty() {
      assertEquals(0, autoAlias.getIncomingJoinCount());
   }

   // ---- AutoAlias.isEmpty (static) ----

   @Test
   void isEmptyReturnsTrueForNull() {
      assertTrue(AutoAlias.isEmpty(null));
   }

   @Test
   void isEmptyReturnsTrueForEmptyAutoAlias() {
      assertTrue(AutoAlias.isEmpty(autoAlias));
   }

   @Test
   void isEmptyReturnsFalseWhenJoinsPresent() {
      autoAlias.addIncomingJoin(makeJoin("t1", "a1", null, false));
      assertFalse(AutoAlias.isEmpty(autoAlias));
   }

   // ---- AutoAlias.clone ----

   @Test
   void cloneProducesIndependentCopy() {
      autoAlias.addIncomingJoin(makeJoin("orders", "orders_alias", "pfx_", true));

      AutoAlias cloned = (AutoAlias) autoAlias.clone();
      assertNotNull(cloned);
      assertEquals(1, cloned.getIncomingJoinCount());

      // Adding to clone should not affect original
      cloned.addIncomingJoin(makeJoin("extra", "extra_alias", null, false));
      assertEquals(1, autoAlias.getIncomingJoinCount());
      assertEquals(2, cloned.getIncomingJoinCount());
   }

   @Test
   void cloneJoinsAreIndependentObjects() {
      AutoAlias.IncomingJoin original = makeJoin("orders", "orders_alias", "pfx_", true);
      autoAlias.addIncomingJoin(original);

      AutoAlias cloned = (AutoAlias) autoAlias.clone();
      AutoAlias.IncomingJoin clonedJoin = cloned.getIncomingJoin(0);

      // The cloned join should not be the same object reference
      assertNotSame(original, clonedJoin);
      // But should have same content
      assertEquals("orders", clonedJoin.getSourceTable());
      assertEquals("orders_alias", clonedJoin.getAlias());
   }

   // ---- IncomingJoin getters/setters ----

   @Test
   void incomingJoinGettersReturnCorrectValues() {
      AutoAlias.IncomingJoin join = makeJoin("src_table", "my_alias", "my_prefix_", true);

      assertEquals("src_table", join.getSourceTable());
      assertEquals("my_alias", join.getAlias());
      assertEquals("my_prefix_", join.getPrefix());
      assertTrue(join.isKeepOutgoing());
   }

   @Test
   void incomingJoinDefaultKeepOutgoingIsFalse() {
      AutoAlias.IncomingJoin join = new AutoAlias.IncomingJoin();
      assertFalse(join.isKeepOutgoing());
   }

   // ---- IncomingJoin XML round-trip ----

   @Test
   void incomingJoinXmlRoundTripPreservesAllFields() throws Exception {
      AutoAlias.IncomingJoin original = makeJoin("source_tbl", "alias_name", "prefix_", true);

      // Write to XML
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      original.writeXML(pw);
      pw.flush();
      String xml = sw.toString();

      // Parse back
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(new org.xml.sax.InputSource(new StringReader(xml)));
      Element element = doc.getDocumentElement();

      AutoAlias.IncomingJoin parsed = new AutoAlias.IncomingJoin();
      parsed.parseXML(element);

      assertEquals("source_tbl", parsed.getSourceTable());
      assertEquals("alias_name", parsed.getAlias());
      assertEquals("prefix_", parsed.getPrefix());
      assertTrue(parsed.isKeepOutgoing());
   }

   @Test
   void incomingJoinXmlRoundTripWithKeepOutgoingFalse() throws Exception {
      AutoAlias.IncomingJoin original = makeJoin("tbl", "a", null, false);

      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      original.writeXML(pw);
      pw.flush();

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(new org.xml.sax.InputSource(new StringReader(sw.toString())));

      AutoAlias.IncomingJoin parsed = new AutoAlias.IncomingJoin();
      parsed.parseXML(doc.getDocumentElement());

      assertFalse(parsed.isKeepOutgoing());
      assertEquals("tbl", parsed.getSourceTable());
      assertEquals("a", parsed.getAlias());
      assertNull(parsed.getPrefix());
   }

   // ---- helper ----

   private AutoAlias.IncomingJoin makeJoin(String table, String alias,
                                            String prefix, boolean keepOutgoing)
   {
      AutoAlias.IncomingJoin join = new AutoAlias.IncomingJoin();
      join.setSourceTable(table);
      join.setAlias(alias);
      join.setPrefix(prefix);
      join.setKeepOutgoing(keepOutgoing);
      return join;
   }
}
