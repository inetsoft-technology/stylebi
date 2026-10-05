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
package inetsoft.uql.jdbc;

import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77795: SQL text with {@code ]]>} or an XML 1.0 illegal control character made the
 * XML written by UniformSQL and XExpression unreadable, so a saved SQL-bound worksheet
 * could not be opened. Each case writes with the real writeXML, reads the XML with the
 * storage reader ({@code Tool.parseXML(in, "UTF-8", false, false)}, as
 * AbstractIndexedStorage.parseData does) and with a coalescing reader, and parses it back.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  UniformSQLCdataRoundTripTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLCdataRoundTripTest {
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }

      @Bean
      DataSourceRegistry dataSourceRegistry() {
         return mock(DataSourceRegistry.class);
      }
   }

   @Test
   void sqlStringWithCdataEndUnparsed() throws Exception {
      String sql = "select * from notes where note like '%]]>%'";
      UniformSQL back = roundTrip(usql(sql, false));

      assertEquals(sql, back.getSQLString());
   }

   @Test
   void sqlStringAndWhereLiteralWithCdataEndParsed() throws Exception {
      String sql = "select * from notes where note like '%]]>%'";
      UniformSQL u = usql(sql, true);
      UniformSQL back = roundTrip(u);

      assertEquals(sql, back.getSQLString());
      assertEquals(u.getWhere().toString(), back.getWhere().toString());
      back.clearSQLString();
      assertTrue(back.getSQLString().contains("'%]]>%'"), back.getSQLString());
   }

   @Test
   void whereLiteralWithCdataEndWithoutSqlString() throws Exception {
      UniformSQL u = usql("select * from notes where note like '%]]>%'", true);
      u.setSQLString(null, false);
      String xml = xml(u::writeXML);

      assertFalse(xml.contains("<sqlstring"), xml);

      for(Element elem : read(xml)) {
         UniformSQL back = new UniformSQL();
         back.parseXML(elem);
         assertEquals(u.getWhere().toString(), back.getWhere().toString());
      }
   }

   @Test
   void expressionValuesWithCdataEnd() throws Exception {
      for(String type : new String[] { XExpression.VALUE, XExpression.EXPRESSION,
                                       XExpression.FIELD })
      {
         for(String value : new String[] { "x]]>y", "]]>", "]]>]]>]]", "a\n]]>\nb" }) {
            XExpression back = roundTrip(new XExpression(value, type));
            assertEquals(type.equals(XExpression.FIELD) ? value.trim() : value,
                         back.getValue(), type);
            assertEquals(type, back.getType());
         }
      }

      XExpression array = roundTrip(new XExpression(new Object[] { "a", "b]]>c" },
                                                    XExpression.VALUE));
      assertArrayEquals(new Object[] { "a", "b]]>c" }, (Object[]) array.getValue());
   }

   @Test
   void quotedIdentifiersWithCdataEnd() throws Exception {
      UniformSQL alias = roundTrip(usql("select a.k as \"x]]>y\" from a", true));
      assertEquals("x]]>y", ((JDBCSelection) alias.getSelection()).getAlias(0));

      UniformSQL column = roundTrip(usql("select \"x]]>y\" from a order by \"x]]>y\"", true));
      assertEquals("x]]>y", column.getSelection().getColumn(0));
      assertEquals("x]]>y", column.getOrderByFields()[0].toString());

      UniformSQL table = roundTrip(usql("select t.k from \"x]]>y\" t", true));
      assertEquals("x]]>y", String.valueOf(table.getSelectTable(0).getName()).replace("\"", ""));
   }

   @Test
   void controlCharsInSqlString() throws Exception {
      for(boolean parse : new boolean[] { false, true }) {
         for(String sql : new String[] {
            "select * from a where a.name = 'a\u0001b'",
            "select *\ffrom a",
            // a backslash next to an encoded char, a literal escape-like text and ]]>
            "select * from a where a.name = 'c:\\\\u0001\\x\u001f]]>' and a.k = '\\'"
         })
         {
            UniformSQL u = usql(sql, parse);
            String xml = xml(u::writeXML);

            assertTrue(xml.contains("<sqlstring parseResult=\"" + u.getParseResult() +
                                    "\" ctrlEncoded=\"true\">"), xml);
            assertEquals(sql, roundTrip(u).getSQLString());
         }
      }
   }

   @Test
   void controlCharsInWhereLiteral() throws Exception {
      UniformSQL u = usql("select * from a where a.name = 'a\u0001b\\c'", true);
      String where = u.getWhere().toString();
      u.setSQLString(null, false);
      String xml = xml(u::writeXML);

      assertFalse(xml.contains("<sqlstring"), xml);
      assertTrue(xml.contains("ctrlEncoded=\"true\""), xml);

      for(Element elem : read(xml)) {
         UniformSQL back = new UniformSQL();
         back.parseXML(elem);
         assertEquals(where, back.getWhere().toString());
      }

      for(String value : new String[] { "a\u0001b", "\u0000", "x\\y\u000b]]>z", "\uFFFE" }) {
         assertEquals(value, roundTrip(new XExpression(value, XExpression.VALUE)).getValue());
         assertEquals(value, roundTrip(new XExpression(value, XExpression.EXPRESSION)).getValue());
      }
   }

   @Test
   void ordinaryTextIsWrittenUnchanged() throws Exception {
      // text without ]]> or an illegal char is written exactly as before Bug #77795
      String sql = "select a.k as \"x]]y\" from notes a where a.note like 'c:\\\\u0001\\x'";
      UniformSQL u = usql(sql, false);
      String xml = xml(u::writeXML);

      assertTrue(xml.contains("<sqlstring parseResult=\"" + u.getParseResult() + "\"><![CDATA[" +
                              sql + "]]></sqlstring>"), xml);
      assertEquals(sql, roundTrip(u).getSQLString());

      UniformSQL parsed = usql(sql, true);
      String pxml = xml(parsed::writeXML);
      assertFalse(pxml.contains("ctrlEncoded"), pxml);
      assertFalse(pxml.contains("]]]]><![CDATA[>"), pxml);
      assertEquals(sql, roundTrip(parsed).getSQLString());

      assertEquals("<expression type=\"" + XExpression.VALUE + "\">\n<![CDATA['c:\\u0001\\x']]>\n</expression>\n",
                   xml(new XExpression("'c:\\u0001\\x'", XExpression.VALUE)::writeXML)
                      .replace("\r", ""));

      // legacy text that looks encoded is not decoded without the marker
      Element legacy = Tool.parseXML(new StringReader(
         "<expression type=\"" + XExpression.VALUE + "\"><![CDATA[a\\u0001b\\\\c]]></expression>"))
         .getDocumentElement();
      XExpression exp = new XExpression();
      exp.parseXML(legacy);
      assertEquals("a\\u0001b\\\\c", exp.getValue());
   }

   @Test
   void worksheetStorageRoundTrip() throws Exception {
      String sql = "select * from notes where note like '%]]>%' and k = '\u0001'";
      Worksheet ws = new Worksheet();
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77795");
      query.setUserQuery(true);
      query.setDataSource(ds());
      query.setSQLDefinition(usql(sql, false));
      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "bug77795", "bug77795"));
      table.setSQLEdited(true);
      ws.addAssembly(table);

      byte[] data = AbstractIndexedStorage.encodeXMLSerializable(ws, "1^2^__NULL__^ws1");
      // the read AbstractIndexedStorage.parseData makes
      Element root = Tool.parseXML(new ByteArrayInputStream(data), "UTF-8", false, false)
         .getDocumentElement();
      Worksheet back = new Worksheet();
      back.parseXML(root);
      SQLBoundTableAssembly t = (SQLBoundTableAssembly) back.getAssembly("T1");
      UniformSQL usql = (UniformSQL) ((SQLBoundTableAssemblyInfo) t.getTableInfo())
         .getQuery().getSQLDefinition();

      assertEquals(sql, usql.getSQLString());
   }

   @Test
   void toolHelpers() {
      assertNull(Tool.splitCDATAEnd(null));
      assertEquals("a]]b", Tool.splitCDATAEnd("a]]b"));
      assertEquals("a]]]]><![CDATA[>b", Tool.splitCDATAEnd("a]]>b"));

      assertFalse(Tool.hasXMLIllegalChars(null));
      assertFalse(Tool.hasXMLIllegalChars("a\tb\nc\rd\u007f\u0085\ud83d\ude00"));
      assertTrue(Tool.hasXMLIllegalChars("a\u0000"));
      assertTrue(Tool.hasXMLIllegalChars("\u001f"));
      assertTrue(Tool.hasXMLIllegalChars("\uffff"));

      for(String value : new String[] { "", "\\", "\\\\u0001", "\u0001\\u0001", "\\u00",
                                        "a\u0008b\u000bc\u000cd\u000ee\uFFFE\uFFFF\\" })
      {
         String encoded = Tool.encodeXMLIllegalChars(value);
         assertFalse(Tool.hasXMLIllegalChars(encoded), encoded);
         assertEquals(value, Tool.decodeXMLIllegalChars(encoded), encoded);
      }

      assertEquals("\\u0001", Tool.encodeXMLIllegalChars("\u0001"));
      // not an encoded sequence, kept as is
      assertEquals("\\uZZ12\\q\\", Tool.decodeXMLIllegalChars("\\uZZ12\\q\\"));
   }

   private static JDBCDataSource ds() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77795");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:memory:bug77795");
      ds.setRequireLogin(false);
      return ds;
   }

   private static UniformSQL usql(String sql, boolean parse) throws Exception {
      UniformSQL u = new UniformSQL();
      u.setDataSource(ds());

      if(parse) {
         u.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      }
      else {
         u.setParseSQL(false);
      }

      u.setSQLString(sql, false);
      return u;
   }

   private static String xml(Consumer<PrintWriter> write) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      write.accept(pw);
      pw.flush();
      return sw.toString();
   }

   /**
    * Read the XML with the storage reader (non-coalescing) and with a coalescing reader.
    */
   private static Element[] read(String xml) throws Exception {
      byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
      return new Element[] {
         Tool.parseXML(new ByteArrayInputStream(bytes), "UTF-8", false, false)
            .getDocumentElement(),
         CoreTool.parseXML(new StringReader(xml), false, Boolean.TRUE).getDocumentElement()
      };
   }

   private static UniformSQL roundTrip(UniformSQL u) throws Exception {
      UniformSQL result = null;

      for(Element elem : read(xml(u::writeXML))) {
         UniformSQL back = new UniformSQL();
         back.parseXML(elem);
         assertEquals(u.getSQLString(), back.getSQLString());
         assertEquals(u.getParseResult(), back.getParseResult());
         result = back;
      }

      return result;
   }

   private static XExpression roundTrip(XExpression exp) throws Exception {
      XExpression result = null;

      for(Element elem : read(xml(exp::writeXML))) {
         XExpression back = new XExpression();
         back.parseXML(elem);

         // both readers give the same value
         if(result != null) {
            assertEquals(text(result.getValue()), text(back.getValue()));
         }

         result = back;
      }

      return result;
   }

   private static String text(Object value) {
      return value instanceof Object[] ? String.join(",", (String[]) value) : String.valueOf(value);
   }
}
