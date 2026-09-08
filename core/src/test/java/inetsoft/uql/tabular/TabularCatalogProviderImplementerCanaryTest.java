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
package inetsoft.uql.tabular;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers charter assertions A9 and B5.
 *
 * <p>A9's reflection check, honestly scoped: {@code core}'s test classpath cannot see ANY of the 13
 * production {@link TabularCatalogProvider} implementers — not just the 4 that live in the separate
 * enterprise reactor, but all 9 community connectors too, because the dependency direction is
 * connector to {@code core}, never the reverse. Running this from {@code core} therefore verifies
 * zero implementers by reflection today; what makes A9 true for all 13 is the constructive argument
 * in the design doc (the two methods are new, and no connector file changes this round), not this
 * test.
 *
 * <p>This test exists so that fact is asserted, not silently assumed:
 * {@code knownImplementersAreNotOnClasspath_documentedLimitOfThisModule} pins the 13 names and
 * fails loudly the day one becomes loadable from {@code core} — at which point
 * {@code resolvesToInterfaceDefaultWhenLoadable} (which already loops over the same list and skips
 * whatever it cannot load) starts actually checking that one, with no code change needed here.
 */
@Tag("core")
class TabularCatalogProviderImplementerCanaryTest {

   private static final List<String> KNOWN_PRODUCTION_IMPLEMENTERS = List.of(
      "inetsoft.uql.aerospike.AerospikeRuntime",
      "inetsoft.uql.cassandra.CassandraRuntime",
      "inetsoft.uql.elasticrest.ElasticRestRuntime",
      "inetsoft.uql.gdata.GDataRuntime",
      "inetsoft.uql.hive.HiveRuntime",
      "inetsoft.uql.mongodb.MongoRuntime",
      "inetsoft.uql.odata.ODataRuntime",
      "inetsoft.uql.orientdb.OrientDBRuntime",
      "inetsoft.uql.sharepoint.SharepointOnlineRuntime",
      "inetsoft.uql.facebook.marketing.FBAdInsightsRuntime",
      "inetsoft.uql.googleanalyticsga4.AnalyticsRuntime",
      "inetsoft.uql.sforce.SForceRuntime",
      "inetsoft.uql.sapjco2.table.SAPTableRuntime",
      "inetsoft.uql.rest.datasource.graphql.GraphQLRuntime");

   @Test
   void knownImplementersAreNotOnClasspath_documentedLimitOfThisModule() {
      for(String fqcn : KNOWN_PRODUCTION_IMPLEMENTERS) {
         assertThrows(ClassNotFoundException.class, () -> Class.forName(fqcn),
            () -> fqcn + " is now loadable from core -- promote it into the assertion below, " +
                  "this is the signal this canary exists to give.");
      }
   }

   @Test
   void resolvesToInterfaceDefaultWhenLoadable() throws Exception {
      for(String fqcn : KNOWN_PRODUCTION_IMPLEMENTERS) {
         Class<?> implClass;

         try {
            implClass = Class.forName(fqcn);
         }
         catch(ClassNotFoundException e) {
            continue;   // Not on this module's classpath -- covered by construction, see design doc.
         }

         Method listDatasetsPaged = implClass.getMethod(
            "listDatasets", TabularDataSource.class, TabularCatalogRequest.class);
         assertEquals(TabularCatalogProvider.class, listDatasetsPaged.getDeclaringClass(),
            fqcn + " overrides the new paged listDatasets -- re-verify A1/A2/A4 equivalence for it.");

         Method listRelationships = implClass.getMethod(
            "listRelationships", TabularDataSource.class, Collection.class);
         assertEquals(TabularCatalogProvider.class, listRelationships.getDeclaringClass(),
            fqcn + " overrides listRelationships -- re-verify A5/A6 equivalence for it.");
      }
   }

   // ----- B5 -----

   @Test
   void bothNewMethods_areDefault_notAbstract() throws Exception {
      Method listDatasetsPaged = TabularCatalogProvider.class.getMethod(
         "listDatasets", TabularDataSource.class, TabularCatalogRequest.class);
      Method listRelationships = TabularCatalogProvider.class.getMethod(
         "listRelationships", TabularDataSource.class, Collection.class);

      assertTrue(listDatasetsPaged.isDefault(),
         "listDatasets(TabularDataSource, TabularCatalogRequest) must be default, or it would " +
         "break every existing implementer at compile time");
      assertTrue(listRelationships.isDefault(),
         "listRelationships must be default, or it would break every existing implementer at " +
         "compile time");
   }
}
