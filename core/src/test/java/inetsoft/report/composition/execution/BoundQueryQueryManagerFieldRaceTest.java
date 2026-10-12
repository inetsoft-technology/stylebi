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
package inetsoft.report.composition.execution;

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.QueryManager;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Bug #78033 (reopened), P5 verifier's {@code 05-verify.md} &sect;4d finding: the identical
 * shared-{@code QueryManager}-field shadowing that {@code AssetQuery.getRuntimeTableLens()}
 * (TableLens-level {@code addPending}) was fixed for also existed, unfixed, one layer deeper, at
 * the JDBC/XMLA live-statement-cancellation registration -- {@code BoundQuery.java}'s own
 * {@code getPostBaseTableLens(VariableTable)}, plus the analogous {@code CubeQuery} sites.
 *
 * <p>{@code AssetQuerySandbox} (the {@code box}/{@code wbox} shared by every assembly of one
 * {@code ViewsheetSandbox}) has a single, plain, unsynchronized {@code queryMgr} field
 * ({@code getQueryManager()}/{@code setQueryManager()}). {@code VSAQuery.getTableLens()} writes
 * it unconditionally on every entry with the correct, per-assembly manager. Before this PR's
 * extension, {@code BoundQuery.getPostBaseTableLens()} read that shared field directly
 * ({@code xquery.setProperty("queryManager", box.getQueryManager())}) into the property
 * {@code JDBCHandler} later reads to register the live JDBC {@code Statement} for cancellation --
 * with no fallback to this {@code AssetQuery} instance's own, correctly-set {@code qmgr} field
 * (set via {@code setQueryManager()} by callers like {@code AssetDataCache.Processor.run0()}, the
 * same per-instance field {@code AssetQuery.getRuntimeTableLens()} already prefers). If a sibling
 * assembly's own, routine {@code VSAQuery.getTableLens()} call overwrites the shared field in the
 * window between this victim's own write of that field and this read, the victim's live JDBC
 * statement is registered on the sibling's manager instead of its own -- so the sibling's own
 * ordinary {@code cancelForQuery()} cancels the victim's live statement too, a materially more
 * direct/severe cancellation than the TableLens-level one the original fix addresses.
 *
 * <p>This test drives that exact mechanism, synchronously rather than with real threads: it
 * gives a {@code SQLBoundQuery} its own, correct manager (as {@code AssetDataCache.Processor
 * .run0()} would), then overwrites the shared sandbox-level field with a different one (as a
 * sibling assembly's own concurrent {@code VSAQuery.getTableLens()} call would, landing in the
 * race window), then calls {@code getPostBaseTableLens(VariableTable)} directly -- the same
 * package gives this test access to the protected method and to the protected {@code xquery}
 * field it sets the property on -- and asserts the resulting {@code "queryManager"} property is
 * the victim's own manager, not the clobbering sibling's. No live JDBC connection is set up (the
 * test's minimal Spring context has no {@code XSessionManager} bean, so the actual query
 * execution past the property-set line throws and is ignored): the defect and its fix are
 * entirely in the property write itself, which already runs before that point. Fails against the
 * pre-fix code (the property would hold the sibling's manager); passes against the fix (the
 * property holds the victim's own).</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  BoundQueryQueryManagerFieldRaceTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BoundQueryQueryManagerFieldRaceTest {
   // JDBCDataSource's constructor resolves a CredentialService bean (not provided by
   // BaseTestConfiguration); no other JDBC plumbing (Drivers/Plugins/ConnectionPoolFactory) is
   // needed, since SQLBoundQuery's table constructor takes the query directly off the table's
   // own info and never looks a data source up by name.
   @Configuration
   static class JdbcConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }

   @Test
   void postBaseTableLensPrefersItsOwnQueryManagerOverTheSharedSandboxField() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = newTable(ws);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      SQLBoundQuery victim =
         new SQLBoundQuery(AssetQuerySandbox.RUNTIME_MODE, box, table, false, false);

      QueryManager ownQmgr = new QueryManager();
      QueryManager siblingQmgr = new QueryManager();

      // the victim's own VSAQuery.getTableLens() would have called setQueryManager() with its
      // own, correct manager before dispatching the fetch -- the same per-instance field
      // AssetQuery.getRuntimeTableLens() already prefers for the TableLens-level registration
      victim.setQueryManager(ownQmgr);

      // simulate a sibling assembly's own, routine VSAQuery.getTableLens() call landing in the
      // race window and overwriting the shared AssetQuerySandbox-level field after the victim
      // snapshotted its own manager but before this read -- the exact Mechanism B shape
      // 02-root-cause.md and 05-verify.md section 4d describe, one layer deeper
      box.setQueryManager(siblingQmgr);

      try {
         victim.getPostBaseTableLens(new VariableTable());
      }
      catch(Exception ignore) {
         // this minimal context has no XSessionManager bean, so the real query execution past
         // the property-set line throws; irrelevant here -- the defect and its fix are entirely
         // in the "queryManager" property write at BoundQuery.getPostBaseTableLens(), which
         // already ran before this point is reached
      }

      Object registered = victim.xquery.getProperty("queryManager");
      assertSame(ownQmgr, registered,
                 "the victim's live JDBC statement must be registered for cancellation on its " +
                 "own query manager, not a sibling assembly's -- a sibling's routine " +
                 "cancelForQuery() would otherwise cancel the victim's live statement too " +
                 "(Bug #78033, same shadowing shape as AssetQuery.getRuntimeTableLens(), one " +
                 "layer deeper at the JDBC live-statement registration)");
   }

   // a minimal sql-edited table: SQLBoundQuery's table constructor takes the query directly off
   // the table's own info, with no DataSourceRegistry/XRepository lookup needed
   private static SQLBoundTableAssembly newTable(Worksheet ws) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug78033r2");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:memory:bug78033r2");
      ds.setRequireLogin(false);

      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      usql.setParseSQL(false);
      usql.setSQLString("select 1 as k from t", false);

      JDBCQuery query = new JDBCQuery();
      query.setName("bug78033r2");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);

      ColumnSelection columns = new ColumnSelection();
      ColumnRef ref = new ColumnRef(new AttributeRef("k"));
      ref.setDataType(XSchema.INTEGER);
      columns.addAttribute(ref);
      table.setColumnSelection(columns, false);
      table.setColumnSelection((ColumnSelection) columns.clone(), true);
      ws.addAssembly(table);
      return table;
   }
}
