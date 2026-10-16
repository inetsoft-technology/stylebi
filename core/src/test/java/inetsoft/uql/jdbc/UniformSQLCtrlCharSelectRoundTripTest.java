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
import java.util.Arrays;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78128: a control character in a string literal of a select item, a derived table,
 * a group by or order by item, or the select list of a subquery was written raw into the
 * CDATA of the parsed structure UniformSQL saves next to its sql string, so the saved
 * SQL-bound worksheet could not be read back. Each case writes with the real writeXML,
 * reads it with the storage reader ({@code Tool.parseXML(in, "UTF-8", false, false)}) and
 * a coalescing reader, parses it back and compares the structure and the regenerated sql.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  UniformSQLCtrlCharSelectRoundTripTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLCtrlCharSelectRoundTripTest {
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
   void reportedSelectLiteral() throws Exception {
      String sql = "select 'a]]>b' as x, 'c\u001fd' as y from orders";
      UniformSQL u = usql(sql);
      String xml = xml(u::writeXML);

      assertTrue(xml.contains("<column ctrlEncoded=\"true\">"), xml);
      UniformSQL back = roundTrip(u);

      assertEquals("'a]]>b'", back.getSelection().getColumn(0));
      assertEquals("'c\u001fd'", back.getSelection().getColumn(1));
      assertEquals("y", ((JDBCSelection) back.getSelection()).getAlias(1));
   }

   @Test
   void reportedDerivedTableLiteral() throws Exception {
      String sql = "select a.c from (select 'v' as c from orders where 'k' <> '\u001f') a";
      UniformSQL u = usql(sql);
      String xml = xml(u::writeXML);

      assertTrue(xml.contains("<name ctrlEncoded=\"true\">"), xml);
      UniformSQL back = roundTrip(u);

      assertTrue(back.getSelectTable(0).getName() instanceof UniformSQL);
      assertTrue(back.getSelectTable(0).getName().toString().contains("'\u001f'"),
                 back.getSelectTable(0).getName().toString());
   }

   @Test
   void otherStructureLiterals() throws Exception {
      for(String sql : new String[] {
         "select 'c\u001fd' from orders",
         "select upper('f\u001f') as u from orders o",
         "select o.id from orders o where o.id in (select '\u001f' from t)",
         "select o.id from orders o group by o.id, 'x\u001f'",
         "select o.id from orders o order by 'y\u001f'",
         "select o.id as \"al\u001fx\" from orders o",
         // a literal in where and having was already encoded (Bug #77795)
         "select max(o.n) from orders o where o.name = 'z\u001f' having max(o.n) <> 'h\u001f'",
         // encoded text next to a backslash and ]]>
         "select 'c:\\\\u001f\\x\u0001]]>' as v from orders o order by 'q\\\u0002'"
      })
      {
         UniformSQL u = usql(sql);
         assertEquals(UniformSQL.PARSE_SUCCESS, u.getParseResult(), sql);
         String xml = xml(u::writeXML);
         assertTrue(xml.contains("ctrlEncoded=\"true\""), xml);
         roundTrip(u);
      }
   }

   @Test
   void derivedTableOverQuotedTableWithBackslashAndControlChar() throws Exception {
      // the quotedSql attribute and the text are encoded under the one marker on <name>
      String sql = "select a.c from (select 'v\\w' as c from \"Orders\" " +
         "where 'k' <> 'a\u001fb' and 'p' <> '\\') a";
      UniformSQL u = usql(sql);
      String xml = xml(u::writeXML);

      assertTrue(xml.contains("quotedSql=\""), xml);
      assertTrue(xml.contains("ctrlEncoded=\"true\"><![CDATA["), xml);
      UniformSQL back = roundTrip(u);
      String name = back.getSelectTable(0).getName().toString();

      assertEquals(u.getSelectTable(0).getName().toString().replaceAll(BREAKS, " "),
                   name.replaceAll(BREAKS, " "));
      assertTrue(name.contains("\"Orders\""), name);
      assertTrue(name.contains("'v\\w'") && name.contains("'a\u001fb'") &&
                 name.contains("'\\'"), name);
   }

   @Test
   void backslashWithoutControlCharIsNotEncoded() throws Exception {
      for(String sql : new String[] {
         "select a.c from (select 'v' as c from orders where 'k' <> '\\') a",
         "select a.c from (select 'v\\\\u0001' as c from \"Orders\" where 'k' <> '\\') a",
         "select '\\\\u001f' as y from orders o group by '\\x' order by '\\\\'"
      })
      {
         UniformSQL u = usql(sql);
         String xml = xml(u::writeXML);
         assertFalse(xml.contains("ctrlEncoded"), xml);
         roundTrip(u);
      }
   }

   @Test
   void ordinaryTextIsWrittenUnchanged() throws Exception {
      // text without an illegal char is written exactly as before Bug #78128
      String sql = "select 'a\\b' as x, o.id from orders o, " +
         "(select 'v' as c from \"Orders\" where 'k' <> '\\u0001') a " +
         "group by o.id order by 'y'";
      UniformSQL u = usql(sql);
      String xml = xml(u::writeXML).replace("\r", "");

      assertFalse(xml.contains("ctrlEncoded"), xml);
      assertTrue(xml.contains("<column>\n<![CDATA['a\\b']]>\n<alias quoted=\"false\"><![CDATA[x]]></alias>\n" +
                              "<type><![CDATA[]]></type>\n"), xml);
      assertTrue(xml.contains("<name quotedSql=\""), xml);
      assertTrue(xml.contains("><![CDATA[select 'v' as c \nfrom   Orders \nwhere  'k' <> '\\u0001'    ]]></name>"),
                 xml);
      assertTrue(xml.contains("<groupby><field"), xml);
      assertTrue(xml.contains("><![CDATA[o.id]]></field></groupby>"), xml);
      assertTrue(xml.contains("><![CDATA['y']]></field></sortby>"), xml);
      roundTrip(u);
   }

   @Test
   void legacyUnmarkedTextIsReadAsStored() throws Exception {
      // text that looks encoded is not decoded without the marker
      String xml = "<uniform_sql parse=\"true\">" +
         "<table><alias><![CDATA[a]]></alias>" +
         "<name quotedSql=\"select 'x\\\\u0001' from &quot;T&quot;\"><![CDATA[select 'x\\\\u0001' from T]]></name>" +
         "<issql><![CDATA[true]]></issql></table>" +
         "<column><![CDATA['c\\u001Fd']]><alias><![CDATA[y\\u0001]]></alias></column>" +
         "<sortby><field><![CDATA['s\\u0001']]></field></sortby>" +
         "<groupby><field><![CDATA['g\\\\']]></field></groupby>" +
         "</uniform_sql>";
      UniformSQL back = new UniformSQL();
      back.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());

      assertEquals("'c\\u001Fd'", back.getSelection().getColumn(0));
      assertEquals("y\\u0001", ((JDBCSelection) back.getSelection()).getAlias(0));
      assertEquals("'s\\u0001'", back.getOrderByFields()[0].toString());
      assertEquals("'g\\\\'", back.getGroupBy()[0].toString());
      assertEquals("select 'x\\\\u0001' from \"T\"", back.getSelectTable(0).getName().toString());
   }

   @Test
   void worksheetStorageRoundTrip() throws Exception {
      // the two SQL texts of the report, saved in a worksheet and read with the storage reader
      for(String sql : new String[] {
         "select 'a]]>b' as x, 'c\u001fd' as y from orders",
         "select a.c from (select 'v' as c from orders where 'k' <> '\u001f') a"
      })
      {
         Worksheet ws = new Worksheet();
         JDBCQuery query = new JDBCQuery();
         query.setName("bug78128");
         query.setUserQuery(true);
         query.setDataSource(ds());
         query.setSQLDefinition(usql(sql));
         SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
         SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
         info.setQuery(query);
         info.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "bug78128", "bug78128"));
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
         assertEquals(regenerate(usql(sql)), regenerate(usql));
      }
   }

   private static JDBCDataSource ds() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug78128");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:memory:bug78128");
      ds.setRequireLogin(false);
      return ds;
   }

   private static UniformSQL usql(String sql) throws Exception {
      UniformSQL u = new UniformSQL();
      u.setDataSource(ds());
      u.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      u.setSQLString(sql, false);
      return u;
   }

   private static String regenerate(UniformSQL u) {
      UniformSQL copy = (UniformSQL) u.clone();
      copy.clearSQLString();
      return copy.getSQLString();
   }

   // an attribute value (quotedSql) reads its line breaks back as spaces, so the
   // derived table text is compared without them. A control character is not one
   private static final String BREAKS = "[ \r\n]+";

   /**
    * The items of a group by or order by, none for a missing one.
    */
   private static String text(Object[] items) {
      return items == null ? "[]" : Arrays.toString(items);
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
      String sql = u.getSQLString();

      for(Element elem : read(xml(u::writeXML))) {
         UniformSQL back = new UniformSQL();
         back.parseXML(elem);

         assertEquals(sql, back.getSQLString());
         assertEquals(u.getParseResult(), back.getParseResult());
         assertEquals(u.getTableCount(), back.getTableCount(), sql);

         for(int i = 0; i < u.getTableCount(); i++) {
            assertEquals(u.getSelectTable(i).getAlias(), back.getSelectTable(i).getAlias(), sql);
            assertEquals(String.valueOf(u.getSelectTable(i).getName()).replaceAll(BREAKS, " "),
                         String.valueOf(back.getSelectTable(i).getName()).replaceAll(BREAKS, " "),
                         sql);
         }

         JDBCSelection sel = (JDBCSelection) u.getSelection();
         JDBCSelection bsel = (JDBCSelection) back.getSelection();
         assertEquals(sel.getColumnCount(), bsel.getColumnCount(), sql);

         for(int i = 0; i < sel.getColumnCount(); i++) {
            assertEquals(sel.getColumn(i), bsel.getColumn(i), sql);
            assertEquals(sel.getAlias(i), bsel.getAlias(i), sql);
         }

         assertEquals(String.valueOf(u.getWhere()), String.valueOf(back.getWhere()), sql);
         assertEquals(String.valueOf(u.getHaving()), String.valueOf(back.getHaving()), sql);
         assertEquals(text(u.getGroupBy()), text(back.getGroupBy()), sql);
         assertEquals(text(u.getOrderByFields()), text(back.getOrderByFields()), sql);

         // the sql generated from the structure, as VPM and clearSQLString do
         String regen = regenerate(back);
         assertEquals(regenerate(u).replaceAll(BREAKS, " "), regen.replaceAll(BREAKS, " "), sql);
         result = back;
      }

      return result;
   }
}
