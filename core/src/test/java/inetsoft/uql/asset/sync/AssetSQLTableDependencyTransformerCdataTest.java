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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77795: a saved SQL-bound worksheet whose SQL text and WHERE literals hold ]]> or an
 * XML 1.0 illegal control character stays readable after a rename transform edits its
 * stored XML and saves it back with the transformer's writer (XMLTool.writeAssets).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  AssetSQLTableDependencyTransformerCdataTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AssetSQLTableDependencyTransformerCdataTest {
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
   void encodedTextSurvivesRenameAndResave() throws Exception {
      String sql = "select * from notes where notes.note like '%]]>%' and notes.k = 'a\u0001b\\c'";
      String like = "'%]]>%'";
      String ctrl = "'a\u0001b\\c'";
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds());
      usql.setParseSQL(false);
      XSet where = new XSet(XSet.AND);
      where.addChild(new XBinaryCondition(new XExpression("notes.note", XExpression.FIELD),
                                          new XExpression(like, XExpression.VALUE), "LIKE"));
      where.addChild(new XBinaryCondition(new XExpression("notes.k", XExpression.FIELD),
                                          new XExpression(ctrl, XExpression.VALUE), "="));
      usql.setWhere(where);
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
      Document renamed = Tool.parseXML(new ByteArrayInputStream(out.toByteArray()),
                                       "UTF-8", false, false);

      Worksheet ws = new Worksheet();
      ws.parseXML(renamed.getDocumentElement());
      SQLBoundTableAssembly table = (SQLBoundTableAssembly) ws.getAssembly("T1");
      UniformSQL back = (UniformSQL) ((SQLBoundTableAssemblyInfo) table.getTableInfo())
         .getQuery().getSQLDefinition();

      assertEquals(sql, back.getSQLString());
      XFilterNode backWhere = back.getWhere();
      XBinaryCondition c1 = (XBinaryCondition) backWhere.getChild(0);
      XBinaryCondition c2 = (XBinaryCondition) backWhere.getChild(1);
      // the transform rewrote the field names, so it edited this where clause
      assertEquals("ds2.notes.note", c1.getExpression1().getValue());
      assertEquals("ds2.notes.k", c2.getExpression1().getValue());
      assertEquals(like, c1.getExpression2().getValue());
      assertEquals(ctrl, c2.getExpression2().getValue());
   }

   private static Worksheet worksheet(UniformSQL usql) {
      Worksheet ws = new Worksheet();
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77795");
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
      ds.setURL("jdbc:derby:memory:bug77795rename");
      ds.setRequireLogin(false);
      return ds;
   }
}
