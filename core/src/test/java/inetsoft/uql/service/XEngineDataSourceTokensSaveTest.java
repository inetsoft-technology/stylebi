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
package inetsoft.uql.service;

import inetsoft.test.*;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.tabular.oauth.Tokens;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77692: a connector that refreshed the OAuth tokens of the data source it was given at
 * query time must save the tokens onto the stored definition, not the runtime instance, whose
 * variables have been replaced with the values of the query. The URL stands in for a field with
 * a $(var) template, the default database for the token, since the save layer does not look at
 * the type. The registry and the repository are the real ones.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  XEngineAdditionalConnectionSaveTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XEngineDataSourceTokensSaveTest {
   private static final String TEMPLATE = "jdbc:derby:memory:$(tenant);create=true";
   private static final String SUBSTITUTED = "jdbc:derby:memory:acme;create=true";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
   }

   // the tokens are stored, the template is kept
   @Test
   void tokensOfATopLevelDataSourceAreSavedOntoTheStoredDefinition() throws Exception {
      registry.setDataSource(source("tkDs"), false);
      JDBCDataSource runtime = substitute((JDBCDataSource) registry.getDataSource("tkDs").clone());

      repository.updateDataSourceTokens(
         runtime, ds -> ((JDBCDataSource) ds).setDefaultDatabase("tok-B"));

      registry.clearCache();
      JDBCDataSource stored = (JDBCDataSource) registry.getDataSource("tkDs");
      assertEquals("tok-B", stored.getDefaultDatabase());
      assertEquals(TEMPLATE, stored.getURL());
      // the runtime instance is not changed by the save
      assertEquals(SUBSTITUTED, runtime.getURL());
   }

   // an additional connection's tokens land on parent/name, its template is kept, no top-level
   // copy is created and the parent and the other additional connections are not changed
   @Test
   void tokensOfAnAdditionalConnectionAreSavedUnderTheParent() throws Exception {
      addParent("tkP", "tkAdd", "tkKeep");
      long parentModified = storedLastModified("tkP");
      JDBCDataSource parent = (JDBCDataSource) registry.getDataSource("tkP");
      JDBCDataSource runtime = substitute((JDBCDataSource) parent.getDataSource("tkAdd").clone());
      assertNotNull(runtime.getBaseDatasource());
      assertEquals("tkAdd", runtime.getFullName());

      repository.updateDataSourceTokens(
         runtime, ds -> ((JDBCDataSource) ds).setDefaultDatabase("tok-B"));

      registry.clearCache();
      assertNull(registry.getDataSource("tkAdd"), "stray top-level copy");
      JDBCDataSource stored = (JDBCDataSource) registry.getDataSource("tkP/tkAdd");
      assertEquals("tok-B", stored.getDefaultDatabase());
      assertEquals(TEMPLATE, stored.getURL());
      assertNotEquals("tok-B",
                      ((JDBCDataSource) registry.getDataSource("tkP/tkKeep")).getDefaultDatabase());
      assertEquals(parentModified, storedLastModified("tkP"));
      String[] children = ((JDBCDataSource) registry.getDataSource("tkP")).getDataSourceNames();
      Arrays.sort(children);
      assertArrayEquals(new String[] { "tkAdd", "tkKeep" }, children);
   }

   // tokens equal to the stored ones are not written again
   @Test
   void unchangedTokensAreNotWritten() throws Exception {
      JDBCDataSource source = source("ucTkDs");
      source.setDefaultDatabase("tok-A");
      registry.setDataSource(source, false);
      long modified = storedLastModified("ucTkDs");
      JDBCDataSource runtime = substitute((JDBCDataSource) registry.getDataSource("ucTkDs").clone());

      repository.updateDataSourceTokens(
         runtime, ds -> ((JDBCDataSource) ds).setDefaultDatabase("tok-A"));

      assertEquals(modified, storedLastModified("ucTkDs"));
      assertEquals(TEMPLATE, registry.getDataSource("ucTkDs") instanceof JDBCDataSource jdbc ?
         jdbc.getURL() : null);
   }

   // a data source removed since the query started is not brought back
   @Test
   void removedDataSourceIsNotSaved() throws Exception {
      registry.setDataSource(source("rmTkDs"), false);
      JDBCDataSource runtime = substitute((JDBCDataSource) registry.getDataSource("rmTkDs").clone());
      registry.removeDataSource("rmTkDs");

      repository.updateDataSourceTokens(
         runtime, ds -> ((JDBCDataSource) ds).setDefaultDatabase("tok-B"));

      registry.clearCache();
      assertNull(registry.getDataSource("rmTkDs"));
   }

   // the tokens overload needs a data source that can take OAuth tokens
   @Test
   void tokensOfANonOAuthDataSourceAreRejected() {
      Tokens tokens = Tokens.builder().accessToken("a").refreshToken("r").issued(0L)
         .expiration(0L).build();
      assertThrows(IllegalArgumentException.class,
                   () -> repository.updateDataSourceTokens(source("noOAuth"), tokens));
   }

   // what TabularUtil.replaceVariables does to the query-time clone
   private static JDBCDataSource substitute(JDBCDataSource ds) {
      ds.setURL(SUBSTITUTED);
      ds.setCustomUrl(SUBSTITUTED);
      return ds;
   }

   private long storedLastModified(String path) {
      registry.clearCache();
      return registry.getDataSource(path).getLastModified();
   }

   private void addParent(String path, String... additionals) {
      registry.setDataSource(source(path), false);
      JDBCDataSource parent = (JDBCDataSource) registry.getDataSource(path);

      for(String name : additionals) {
         JDBCDataSource additional = source(name);
         additional.setLastModified(System.currentTimeMillis());
         parent.addDatasource(additional);
      }
   }

   private static JDBCDataSource source(String name) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(name);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      dataSource.setURL(TEMPLATE);
      dataSource.setCustomUrl(TEMPLATE);
      return dataSource;
   }
}
