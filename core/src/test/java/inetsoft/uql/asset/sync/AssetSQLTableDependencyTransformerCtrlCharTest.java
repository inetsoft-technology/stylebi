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
package inetsoft.uql.asset.sync;

import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78128: a saved SQL-bound worksheet whose select item and derived table hold an XML
 * 1.0 illegal control character in a string literal stays readable after a rename transform
 * edits its stored XML and saves it back with the transformer's writer
 * (XMLTool.writeAssets). The transform rewrites the text of {@code <column>} and keeps the
 * attributes of {@code <column>} and {@code <name>}, so the encoding marker survives.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  AssetSQLTableDependencyTransformerCtrlCharTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AssetSQLTableDependencyTransformerCtrlCharTest {
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
   void encodedSelectAndDerivedTableSurviveRenameAndResave() throws Exception {
      // a select item and a derived table with a control character in a string literal,
      // the item also with a backslash, which the encoding doubles
      String column = "ds1.orders.n || 'c\u001fd\\e'";
      String derived = "select 'v' as c from orders where 'k' <> '\u001f'";
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds());
      usql.setParseSQL(false);
      usql.addTable("ds1.orders", "ds1.orders");
      usql.addTable("a", new UniformSQL(derived, false));
      JDBCSelection selection = (JDBCSelection) usql.getSelection();
      selection.addColumn(column);
      selection.setAlias(0, "y");
      selection.setTable(column, "ds1.orders");
      selection.addColumn("a.c");
      selection.setTable("a.c", "a");
      XSet where = new XSet(XSet.AND);
      where.addChild(new XBinaryCondition(new XExpression("orders.k", XExpression.FIELD),
                                          new XExpression("'z'", XExpression.VALUE), "="));
      usql.setWhere(where);
      String sql = "select " + column + " as y, a.c from ds1.orders, (" + derived + ") a " +
         "where orders.k = 'z'";
      usql.setSQLString(sql, false);

      byte[] data = AbstractIndexedStorage.encodeXMLSerializable(worksheet(usql), "1^2^__NULL__^ws1");
      // the read AbstractIndexedStorage.parseData makes
      Document doc = Tool.parseXML(new ByteArrayInputStream(data), "UTF-8", false, false);

      new AssetSQLTableDependencyTransformer(null).renameSQLSources(
         doc.getDocumentElement(),
         new RenameInfo("ds1.", "ds2.", RenameInfo.SOURCE | RenameInfo.SQL_TABLE));

      // the writer AbstractIndexedStorage.putDocument uses for a transformed document
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      XMLTool.writeAssets(doc, out, Worksheet.class.getName(), "1^2^__NULL__^ws1");
      String xml = out.toString(StandardCharsets.UTF_8);
      Document renamed = Tool.parseXML(new ByteArrayInputStream(out.toByteArray()),
                                       "UTF-8", false, false);

      // the markers on <column> and on the derived table <name> are kept
      assertTrue(xml.matches("(?s).*<column ctrlEncoded=\"true\">\\s*<!\\[CDATA\\[ds2\\.orders.*"),
                 xml);
      assertTrue(xml.matches("(?s).*<name ctrlEncoded=\"true\">\\s*<!\\[CDATA\\[select 'v'.*"), xml);

      Worksheet ws = new Worksheet();
      ws.parseXML(renamed.getDocumentElement());
      SQLBoundTableAssembly table = (SQLBoundTableAssembly) ws.getAssembly("T1");
      UniformSQL back = (UniformSQL) ((SQLBoundTableAssemblyInfo) table.getTableInfo())
         .getQuery().getSQLDefinition();

      assertEquals(sql, back.getSQLString());
      // the transform renamed the table in the encoded column text, which reads back decoded
      assertEquals("ds2.orders.n || 'c\u001fd\\e'", back.getSelection().getColumn(0));
      assertEquals("a.c", back.getSelection().getColumn(1));
      assertEquals("y", ((JDBCSelection) back.getSelection()).getAlias(0));
      assertEquals(derived, back.getSelectTable("a").getName().toString());
      XBinaryCondition cond = (XBinaryCondition) back.getWhere().getChild(0);
      assertEquals("ds2.orders.k", cond.getExpression1().getValue());
   }

   private static Worksheet worksheet(UniformSQL usql) {
      Worksheet ws = new Worksheet();
      JDBCQuery query = new JDBCQuery();
      query.setName("bug78128");
      query.setUserQuery(true);
      query.setDataSource(ds());
      query.setSQLDefinition(usql);
      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds1", "ds1"));
      table.setSQLEdited(true);
      ws.addAssembly(table);
      return ws;
   }

   private static JDBCDataSource ds() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds1");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:memory:bug78128rename");
      ds.setRequireLogin(false);
      return ds;
   }
}
