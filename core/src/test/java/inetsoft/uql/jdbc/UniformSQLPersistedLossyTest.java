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

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.SQLBoundQuery;
import inetsoft.test.*;
import inetsoft.uql.asset.SQLBoundTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77477, a lossy flag saved with a UniformSQL must not hide a parser refusal added after
 * the asset was saved, and must not keep a query whose sql string was cleared unmergeable.
 * The saved parse result is never re-derived on load.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLPersistedLossyTest {
   /**
    * Core before #6016 stored "a left join b on a.x < b.y" with the same structure as the "="
    * form (an outer XJoin), parse result success and, once isLossy() had run, lossy="false".
    * The current grammar refuses the "<" form, so the asset must not be merged.
    */
   @Test
   void savedNonLossyRefusedSqlIsLossyAfterLoad() throws Exception {
      String refused = "select a.x, b.y from a left join b on a.x < b.y";
      Element xml = save("select a.x, b.y from a left join b on a.x = b.y", true);
      setSQLText(xml, refused);
      assertEquals("false", xml.getAttribute("lossy"));
      assertEquals("0", sqlstring(xml).getAttribute("parseResult"));

      UniformSQL sql = load(xml);

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertTrue(XUtil.isParsedSQL(sql));
      assertTrue(sql.isLossy());
      assertFalse(XUtil.isQueryMergeable(query(sql)));
      assertEquals(refused, sql.getSQLString());
      // the lazy check must not change the parse result either
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
   }

   @Test
   void savedLossyWithoutSqlStringIsMergeable() throws Exception {
      UniformSQL original = parse("select a.x from a where a.x = 1");
      original.clearSQLString();
      Element xml = save(original);
      assertEquals(0, xml.getElementsByTagName("sqlstring").getLength());
      xml.setAttribute("lossy", "true");

      UniformSQL sql = load(xml);

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertFalse(sql.hasSQLString());
      assertFalse(sql.isLossy());
      assertTrue(XUtil.isQueryMergeable(query(sql)));
   }

   @Test
   void savedLossyWithParseOffStaysLossy() throws Exception {
      Element xml = save("select a.x from a where a.x = 1", true);
      xml.setAttribute("parse", "false");
      xml.setAttribute("lossy", "true");

      UniformSQL sql = load(xml);

      assertFalse(sql.isParseSQL());
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertTrue(sql.isLossy());
      assertFalse(XUtil.isQueryMergeable(query(sql)));
   }

   @Test
   void savedNonLossyWithParseOffStaysNonLossy() throws Exception {
      Element xml = save("select top 5 a.x from a", false);
      xml.setAttribute("parse", "false");
      xml.setAttribute("lossy", "false");

      UniformSQL sql = load(xml);

      // isLossy() can't re-derive with parsing off, so the saved value is kept
      assertFalse(sql.isLossy());
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
   }

   @Test
   void savedLossyTopQueryStaysLossy() throws Exception {
      Element xml = save("select top 5 a.x from a", true);
      assertEquals("true", xml.getAttribute("lossy"));

      UniformSQL sql = load(xml);

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertTrue(sql.isLossy());
      assertFalse(XUtil.isQueryMergeable(query(sql)));
   }

   @Test
   void savedNonLossyParseableSqlStaysMergeable() throws Exception {
      String text = "select a.x, b.y from a left join b on a.x = b.y where a.k = 1";
      Element xml = save(text, true);
      assertEquals("false", xml.getAttribute("lossy"));

      UniformSQL sql = load(xml);

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals(text, sql.getSQLString());
      assertFalse(sql.isLossy());
      assertTrue(XUtil.isQueryMergeable(query(sql)));
      assertEquals(1, sql.getJoins().length);
   }

   @Test
   void savedFailedParseResultIsNotReDerived() throws Exception {
      // #6016 made this shape parse; a copy saved as failed before that must stay failed
      String text = "select a.x from a left join b on a.id = b.id and a.k = b.k";
      Element xml = save(text, false);
      sqlstring(xml).setAttribute("parseResult", String.valueOf(UniformSQL.PARSE_FAILED));
      xml.setAttribute("lossy", "true");

      UniformSQL sql = load(xml);

      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
      assertFalse(XUtil.isParsedSQL(sql));
      assertFalse(sql.isLossy());
      assertFalse(XUtil.isQueryMergeable(query(sql)));
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
   }

   /**
    * The same old asset loaded the way a worksheet SQL table is: the Worksheet XML round trip
    * through SQLBoundTableAssemblyInfo and JDBCQuery.parseXML, then the merge decision of the
    * SQLBoundQuery built on it. A parseable sql string in the same worksheet stays mergeable.
    */
   @Test
   void savedWorksheetSqlTableWithRefusedSqlIsNotMerged() throws Exception {
      String refused = "select a.x, b.y from a left join b on a.x < b.y";
      SQLBoundTableAssembly table = loadWorksheetSqlTable(
         "select a.x, b.y from a left join b on a.x = b.y", refused);
      UniformSQL sql = sql(table);

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertTrue(XUtil.isParsedSQL(sql));
      assertFalse(isSourceMergeable(table));
      assertEquals(refused, sql.getSQLString());
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());

      String text = "select a.x, b.y from a left join b on a.x = b.y where a.k = 1";
      table = loadWorksheetSqlTable(text, null);
      sql = sql(table);

      assertTrue(isSourceMergeable(table));
      assertEquals(text, sql.getSQLString());
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
   }

   /**
    * Save a worksheet with a sql-edited SQL table, optionally swap the saved sql text, and load
    * it back the way the worksheet is read from storage.
    */
   private static SQLBoundTableAssembly loadWorksheetSqlTable(String text, String savedText)
      throws Exception
   {
      UniformSQL sql = parse(text);
      sql.isLossy();
      JDBCQuery query = new JDBCQuery();
      query.setName("q");
      query.setSQLDefinition(sql);

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "t1");
      ((SQLBoundTableAssemblyInfo) table.getTableInfo()).setQuery(query);
      table.setSQLEdited(true);
      ws.addAssembly(table);
      ws.setPrimaryAssembly(table);

      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         ws.writeXML(writer);
      }

      Element xml = Tool.parseXML(new StringReader(buffer.toString())).getDocumentElement();
      Element usql = (Element) xml.getElementsByTagName("uniform_sql").item(0);
      assertEquals("false", usql.getAttribute("lossy"));

      if(savedText != null) {
         setSQLText(usql, savedText);
      }

      Worksheet loaded = new Worksheet();
      loaded.parseXML(xml);
      SQLBoundTableAssembly loadedTable = (SQLBoundTableAssembly) loaded.getAssembly("t1");
      JDBCQuery loadedQuery = ((SQLBoundTableAssemblyInfo) loadedTable.getTableInfo()).getQuery();
      // what SQLBoundTableAssemblyInfo.parseContents() does for a <datasource> node
      JDBCDataSource ds = dataSource();
      loadedQuery.setDataSource(ds);
      ((UniformSQL) loadedQuery.getSQLDefinition()).setDataSource(ds);
      return loadedTable;
   }

   private static UniformSQL sql(SQLBoundTableAssembly table) {
      return (UniformSQL) ((SQLBoundTableAssemblyInfo) table.getTableInfo()).getQuery()
         .getSQLDefinition();
   }

   // the merge decision of the SQLBoundQuery the worksheet builds on the table
   private static boolean isSourceMergeable(SQLBoundTableAssembly table) throws Exception {
      Worksheet ws = table.getWorksheet();
      SQLBoundQuery query = new SQLBoundQuery(
         AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), table, false, false);
      return query.isSourceMergeable0();
   }

   // parse the way setSQLString() does, synchronously, so the sql string is kept
   private static UniformSQL parse(String text) {
      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      return sql;
   }

   /**
    * Parse and save a query. When checkLossy is set, isLossy() runs first, as the query editor
    * and merge do, so the lossy attribute is written.
    */
   private static Element save(String text, boolean checkLossy) throws Exception {
      UniformSQL sql = parse(text);

      if(checkLossy) {
         sql.isLossy();
      }

      return save(sql);
   }

   private static Element save(UniformSQL sql) throws Exception {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return Tool.parseXML(new StringReader(buffer.toString())).getDocumentElement();
   }

   private static UniformSQL load(Element xml) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parseXML(xml);
      return sql;
   }

   private static Element sqlstring(Element xml) {
      NodeList list = Tool.getChildNodesByTagName(xml, "sqlstring");
      assertEquals(1, list.getLength());
      return (Element) list.item(0);
   }

   private static void setSQLText(Element xml, String text) {
      Element tag = sqlstring(xml);

      while(tag.getFirstChild() != null) {
         tag.removeChild(tag.getFirstChild());
      }

      tag.appendChild(xml.getOwnerDocument().createCDATASection(text));
   }

   private static JDBCQuery query(UniformSQL sql) {
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(dataSource());
      query.setSQLDefinition(sql);
      return query;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getDatabaseType()).thenReturn(JDBCDataSource.JDBC_ODBC);
      when(ds.getRuntimeProductName()).thenReturn("h2");
      // the merge decision runs on a clone of the query, which clones its data source
      when(ds.clone()).thenReturn(ds);
      return ds;
   }
}
