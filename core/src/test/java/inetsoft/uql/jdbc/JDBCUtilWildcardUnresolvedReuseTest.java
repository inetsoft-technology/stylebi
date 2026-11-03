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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80023, fix detail beyond the diagnosis: {@code UniformSQL.wildcardUnresolved} is written
 * unconditionally on every {@link JDBCUtil#fixUniformSQLInfo} call (true or false), not only when
 * {@code true}, because {@code edit_sql_query} works on the same persisted {@link UniformSQL}
 * instance across repeated edits of the same SQL-bound table (a rejected edit never replaces it,
 * so the next attempt resumes from whatever state it was left in). A set-once-true
 * implementation would leak a stale {@code true} from an earlier refused edit into a later,
 * successful one. This test calls {@code fixUniformSQLInfo} twice on the same {@link UniformSQL}
 * object -- first with a wildcard over a table without metadata (the "table doesn't exist"
 * shape), then with valid SQL on the same instance -- confirming the flag is reset.
 * <p>
 * Mirrors {@link JDBCUtilWildcardExpansionTest}'s harness (Bug #77617) for the same
 * {@code fixUniformSQLInfo} entry point, including its Spring bean setup; kept in a separate
 * file because the tester role commits test files only and does not edit that existing file.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  JDBCUtilWildcardUnresolvedReuseTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JDBCUtilWildcardUnresolvedReuseTest {
   private static final String[] T4 = { "ID", "K:s", "C1", "C2" };

   @Configuration
   static class JdbcConfig {
      // JDBCDataSource's constructor needs CredentialService, whose constructor is
      // package-private.
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster,
                            eventPublisher);
      }

      // no query is ever executed in this file -- fixUniformSQLInfo's column lookup goes
      // through the mocked XRepository bean below, not a real connection -- so the pool can
      // just hand back an unused mock DataSource.
      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenReturn(mock(DataSource.class));
         return factory;
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory) {
         return new Drivers(plugins, connectionPoolFactory);
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   /**
    * The same {@link UniformSQL} instance is reused for a second edit -- as {@code
    * edit_sql_query}'s scratch clone carries over whatever the previous attempt left on the
    * persisted sql when that attempt was refused and never replaced it. A stale
    * {@code wildcardUnresolved == true} from the first, refused edit must not survive into the
    * second, successful one.
    */
   @Test
   void wildcardUnresolvedDoesNotLeakAcrossReusedEdits() throws Exception {
      JDBCDataSource ds = dataSource();

      String badSql = "select T.*, T.A from T";
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      usql.parse(badSql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(badSql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), badSql);

      JDBCUtil.fixUniformSQLInfo(usql, repository(table -> null), null, ds);

      assertEquals(UniformSQL.PARSE_FAILED, usql.getParseResult(), badSql);
      assertTrue(usql.isWildcardUnresolved(),
                "a wildcard over a table without metadata must be flagged unresolved");
      assertFalse(XUtil.isParsedSQL(usql), badSql);

      // the same UniformSQL object is reused for a later, successful edit
      String goodSql = "select T4.* from T4";
      usql.parse(goodSql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(goodSql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), goodSql);

      JDBCUtil.fixUniformSQLInfo(usql, repository(table -> Map.of("T4", T4).get(table)), null, ds);

      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), goodSql);
      assertFalse(usql.isWildcardUnresolved(),
                 "a stale wildcardUnresolved=true from the earlier refused edit must not leak "
                    + "into this later, successful edit on the same object");
      assertTrue(XUtil.isParsedSQL(usql), goodSql);
   }

   /**
    * Control: a fresh {@link UniformSQL} (no reuse) for the same "good" sql is never flagged --
    * confirms the prior test's assertion is actually exercising the reset, not a tautology.
    */
   @Test
   void freshUniformSqlForValidWildcardIsNotFlaggedUnresolved() throws Exception {
      JDBCDataSource ds = dataSource();
      String goodSql = "select T4.* from T4";
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      usql.parse(goodSql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(goodSql, false);

      JDBCUtil.fixUniformSQLInfo(usql, repository(table -> Map.of("T4", T4).get(table)), null, ds);

      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), goodSql);
      assertFalse(usql.isWildcardUnresolved(), goodSql);
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds80023reuse");
      ds.setDriver("org.h2.Driver");
      ds.setURL("jdbc:h2:mem:bug80023reuse");
      ds.setRequireLogin(false);
      ds.setRuntimeProductName("h2");
      ds.setProductVersion("10.0");
      return ds;
   }

   /**
    * @param columns the columns of a table by its (upper-cased) name, "name:s" for a string
    *                column, {@code null} for a table without metadata (simulating a table that
    *                doesn't exist -- the metadata lookup returns an empty, non-null result rather
    *                than throwing, per the bug's root-cause trace).
    */
   private static XRepository repository(java.util.function.Function<String, String[]> columns)
      throws Exception
   {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
         }

         String name = mtype.getName();
         name = name.substring(name.lastIndexOf('.') + 1).replace("\"", "").toUpperCase();
         String[] list = columns.apply(name);
         XTypeNode result = new XTypeNode("Result");

         for(String column : list == null ? new String[0] : list) {
            boolean str = column.endsWith(":s");
            column = str ? column.substring(0, column.length() - 2) : column;
            result.addChild(XSchema.createPrimitiveType(column, str ? String.class : Integer.class));
         }

         XTypeNode meta = new XTypeNode("meta");
         meta.addChild(result);
         return meta;
      });

      return repository;
   }
}
