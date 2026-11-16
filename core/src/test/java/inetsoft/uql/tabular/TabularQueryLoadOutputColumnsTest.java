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
package inetsoft.uql.tabular;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.uql.util.XTableTableNode;
import inetsoft.util.credential.CredentialType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Column discovery reads as many rows as the query itself reads (up to a ceiling) rather than a
 * fixed 100, restores exactly the row limit the query had, and carries a connector's report that it
 * stopped with rows left unread.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, TabularQueryLoadOutputColumnsTest.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TabularQueryLoadOutputColumnsTest {
   @Configuration
   static class TestConfig {
      @Bean
      public Config config() throws Exception {
         Config config = mock(Config.class);
         when(config.getResourceBundle(ArgumentMatchers.any())).thenReturn(null);
         when(config.getRuntime(TYPE)).thenReturn(RecordingRuntime.class.getName());
         when(config.getClass(eq(TYPE), ArgumentMatchers.anyString()))
            .thenAnswer(inv -> RecordingRuntime.class);
         return config;
      }

      @Bean
      public Drivers drivers() {
         return mock(Drivers.class);
      }
   }

   @BeforeEach
   void setUp() {
      RecordingRuntime.reset();
      globalLimit = SreeEnv.getProperty("query.runtime.maxrow", null);
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty("query.runtime.maxrow", globalLimit);
   }

   @Test
   void queryWithoutALimitReadsTheDefaultAndStaysWithoutOne() throws Exception {
      TestQuery query = query(0);

      query.loadOutputColumns(new VariableTable());

      assertEquals(100, RecordingRuntime.effectiveMaxRows);
      assertEquals(0, query.getStoredMaxRows());
      assertEquals(0, query.getColumnDiscoveryCappedAt());
   }

   @Test
   void queryWithALimitAboveTheDefaultReadsAllOfIt() throws Exception {
      TestQuery query = query(300);

      query.loadOutputColumns(new VariableTable());

      assertEquals(300, RecordingRuntime.effectiveMaxRows);
      assertEquals(300, query.getStoredMaxRows());
   }

   @Test
   void queryWithALimitBelowTheDefaultStillReadsTheDefault() throws Exception {
      TestQuery query = query(50);

      query.loadOutputColumns(new VariableTable());

      assertEquals(100, RecordingRuntime.effectiveMaxRows);
      assertEquals(50, query.getStoredMaxRows());
   }

   @Test
   void aVeryLargeLimitIsCappedAtTheCeiling() throws Exception {
      TestQuery query = query(500000);

      query.loadOutputColumns(new VariableTable());

      assertEquals(TabularQuery.MAX_DISCOVERY_ROWS, RecordingRuntime.effectiveMaxRows);
      assertEquals(500000, query.getStoredMaxRows());
   }

   @Test
   void theServerWideLimitBoundsTheReadButIsNotStoredOnTheQuery() throws Exception {
      SreeEnv.setProperty("query.runtime.maxrow", "40");
      TestQuery query = query(300);

      query.loadOutputColumns(new VariableTable());

      assertEquals(40, RecordingRuntime.effectiveMaxRows);
      assertEquals(300, query.getStoredMaxRows());

      TestQuery unlimited = query(0);
      unlimited.loadOutputColumns(new VariableTable());

      assertEquals(0, unlimited.getStoredMaxRows(),
                   "the server-wide limit must not be written into the query");
   }

   @Test
   void aConnectorStoppingAtTheLimitIsReportedWithTheRowsRead() throws Exception {
      RecordingRuntime.reportLimitReached = true;
      TestQuery query = query(300);

      query.loadOutputColumns(new VariableTable());

      assertEquals(300, query.getColumnDiscoveryCappedAt());
      assertFalse(query.isRowLimitProbe(), "only column discovery asks for the report");
   }

   @Test
   void aConnectorThatReportsNothingLeavesNoCap() throws Exception {
      TestQuery query = query(300);

      query.loadOutputColumns(new VariableTable());

      assertEquals(0, query.getColumnDiscoveryCappedAt());
   }

   @Test
   void aLaterDiscoveryThatReadEverythingClearsTheCap() throws Exception {
      TestQuery query = query(300);
      RecordingRuntime.reportLimitReached = true;
      query.loadOutputColumns(new VariableTable());
      assertEquals(300, query.getColumnDiscoveryCappedAt());

      RecordingRuntime.reportLimitReached = false;
      query.loadOutputColumns(new VariableTable());

      assertEquals(0, query.getColumnDiscoveryCappedAt());
   }

   @Test
   void cloneStartsWithoutTheReportsOfAnEarlierRunButKeepsTheRequestForOne() throws Exception {
      RecordingRuntime.reportLimitReached = true;
      TestQuery query = query(300);
      query.loadOutputColumns(new VariableTable());
      query.setRowLimitProbe(true);

      TabularQuery copy = query.clone();

      assertEquals(0, copy.getColumnDiscoveryCappedAt());
      assertFalse(copy.isRowLimitReached());
      assertTrue(copy.isRowLimitProbe());
   }

   @Test
   void discoveryRowsFollowTheLimitBetweenTheDefaultAndTheCeiling() {
      assertEquals(100, TabularQuery.getDiscoveryRows(-1));
      assertEquals(100, TabularQuery.getDiscoveryRows(0));
      assertEquals(100, TabularQuery.getDiscoveryRows(99));
      assertEquals(100, TabularQuery.getDiscoveryRows(100));
      assertEquals(101, TabularQuery.getDiscoveryRows(101));
      assertEquals(10000, TabularQuery.getDiscoveryRows(10000));
      assertEquals(10000, TabularQuery.getDiscoveryRows(10001));
   }

   private static TestQuery query(int storedMaxRows) {
      TestDataSource ds = new TestDataSource();
      ds.setName("LoadOutputColumnsDS");
      TestQuery query = new TestQuery();
      query.setName("loadOutputColumnsQuery");
      query.setDataSource(ds);
      query.setMaxRows(storedMaxRows);
      return query;
   }

   /** Records the row limit it ran under and optionally reports having stopped at it. */
   public static final class RecordingRuntime extends TabularRuntime {
      static void reset() {
         effectiveMaxRows = -1;
         reportLimitReached = false;
      }

      @Override
      public XTableNode runQuery(TabularQuery query, VariableTable params) {
         effectiveMaxRows = query.getMaxRows();

         if(reportLimitReached && query.isRowLimitProbe()) {
            query.setRowLimitReached(true);
         }

         return new XTableTableNode(new DefaultTableLens(new Object[][] { { "a" }, { "1" } }));
      }

      @Override
      public void testDataSource(TabularDataSource<?> ds, VariableTable params) {
      }

      static volatile int effectiveMaxRows = -1;
      static volatile boolean reportLimitReached;
   }

   private static final class TestDataSource extends TabularDataSource<TestDataSource> {
      TestDataSource() {
         super(TYPE, TestDataSource.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return null;
      }
   }

   private static final class TestQuery extends TabularQuery {
      TestQuery() {
         super(TYPE);
      }
   }

   private static final String TYPE = "LoadOutputColumnsTest";
   private String globalLimit;
}
