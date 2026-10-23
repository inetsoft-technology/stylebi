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
package inetsoft.web.portal.controller.database;

import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SQLBoundTableAssembly;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.util.ColumnCache;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79176 (item 3), editing a worksheet SQL query (no alias mapping) must carry a column's
 * rename alias over by the column's output name, not only by the tail of its full name.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryManagerServiceRenameAliasCarryOverTest {
   private QueryManagerService qms;

   @BeforeEach
   void setUp() {
      qms = new QueryManagerService(mock(RuntimeQueryService.class), mock(XRepository.class),
                                    mock(DataSourceService.class), mock(SecurityEngine.class),
                                    mock(ColumnCache.class));
   }

   @Test
   void physicalColumnWithSqlAliasKeepsRename() throws Exception {
      ColumnSelection old = oldSelection("cid", "CUST");
      ColumnSelection result = edit("select orders.customer_id as cid, orders.qty from orders", old);

      assertEquals(2, result.getAttributeCount());
      assertEquals("cid", ((ColumnRef) result.getAttribute(0)).getAttribute());
      assertEquals("CUST", ((ColumnRef) result.getAttribute(0)).getAlias());
   }

   @Test
   void computedColumnKeepsRename() throws Exception {
      ColumnSelection old = oldSelection("amount", "REVENUE");
      ColumnSelection result = edit(
         "select orders.qty * orders.price as amount, orders.id from orders", old);

      assertEquals(2, result.getAttributeCount());
      assertEquals("amount", ((ColumnRef) result.getAttribute(0)).getAttribute());
      assertEquals("REVENUE", ((ColumnRef) result.getAttribute(0)).getAlias());
   }

   @Test
   void unaliasedPhysicalColumnStillKeepsRename() throws Exception {
      ColumnSelection old = oldSelection("customer_id", "CUST");
      ColumnSelection result = edit("select orders.customer_id, orders.qty from orders", old);

      assertEquals("CUST", ((ColumnRef) result.getAttribute(0)).getAlias());
   }

   @Test
   void tailOfFullNameStillCarriesRenameWhenSqlAliasIsNew() throws Exception {
      // the old column was the bare physical column, renamed; the edit adds a SQL alias to it
      ColumnSelection old = oldSelection("customer_id", "CUST");
      ColumnSelection result = edit("select orders.customer_id as cid, orders.qty from orders", old);

      assertEquals("CUST", ((ColumnRef) result.getAttribute(0)).getAlias());
   }

   @Test
   void unrelatedColumnGetsNoAlias() throws Exception {
      ColumnSelection old = oldSelection("other", "CUST");
      ColumnSelection result = edit("select orders.qty * orders.price as amount from orders", old);

      assertNull(((ColumnRef) result.getAttribute(0)).getAlias());
   }

   private static ColumnSelection oldSelection(String attribute, String alias) {
      ColumnSelection old = new ColumnSelection();
      ColumnRef ref = new ColumnRef(new AttributeRef(attribute));
      ref.setAlias(alias);
      old.addAttribute(ref);
      return old;
   }

   private ColumnSelection edit(String text, ColumnSelection old) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(true);

      synchronized(sql) {
         sql.setSQLString(text);
         sql.wait();
      }

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      JDBCQuery query = new JDBCQuery();
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getRuntimeProductName()).thenReturn("hsql");
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      SQLBoundTableAssembly assembly = mock(SQLBoundTableAssembly.class);
      when(assembly.getColumnSelection()).thenReturn(old);

      // the worksheet edit path passes a null alias mapping
      return qms.getColumnSelection(query, new VariableTable(), assembly, null, null);
   }
}