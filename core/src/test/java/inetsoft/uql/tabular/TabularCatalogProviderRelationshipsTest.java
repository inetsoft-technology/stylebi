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

import inetsoft.web.wiz.service.FakeTabularDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers charter assertions A5 and A6 for
 * {@link TabularCatalogProvider#listRelationships(TabularDataSource, java.util.Collection)}'s
 * default implementation. Uses {@link FixedCatalogProvider}, which overrides neither new default
 * method (see charter A9).
 */
@Tag("core")
class TabularCatalogProviderRelationshipsTest {

   private static final TabularDataSource<?> DS = new FakeTabularDataSource();

   // Four datasets, one edge per endpoint shape, selected ids = {A, B}. Names double as the
   // expected/rejected assertion labels.
   private static final List<TabularDatasetRef> DATASETS =
      List.of(new TabularDatasetRef("A"), new TabularDatasetRef("B"),
         new TabularDatasetRef("C"), new TabularDatasetRef("D"));

   private static final TabularRelationship R_BOTH_IN =
      edge("R_both_in", "A", "B");   // both endpoints in {A, B} -- must be kept
   private static final TabularRelationship R_FROM_IN =
      edge("R_from_in", "A", "C");   // from in, to out -- must be dropped
   private static final TabularRelationship R_TO_IN =
      edge("R_to_in", "C", "A");     // from out, to in -- the "at-least-one-endpoint" trap
   private static final TabularRelationship R_BOTH_OUT =
      edge("R_both_out", "C", "D");  // neither endpoint in -- must be dropped

   private static final FixedCatalogProvider PROVIDER = new FixedCatalogProvider(
      DATASETS, List.of(R_BOTH_IN, R_FROM_IN, R_TO_IN, R_BOTH_OUT));

   @Test
   void listRelationships_keepsOnlyTheEdgeWithBothEndpointsInSelection() throws Exception {
      List<TabularRelationship> result = PROVIDER.listRelationships(DS, Set.of("A", "B"));

      // Exact set equality, not "contains at least" -- an implementation wrongly written with
      // ids.contains(from) || ids.contains(to) would keep R_from_in and R_to_in too (both have A
      // as one endpoint) and this would report 3 edges instead of 1.
      assertEquals(Set.of("R_both_in"), nameSet(result));
   }

   @Test
   void listRelationships_emptyDatasetIds_yieldsEmptyList_notEveryEdge() throws Exception {
      List<TabularRelationship> result = PROVIDER.listRelationships(DS, Set.of());

      assertNotNull(result);
      assertTrue(result.isEmpty());
   }

   @Test
   void listRelationships_nullDatasetIds_treatedIdenticallyToEmpty() throws Exception {
      List<TabularRelationship> result = PROVIDER.listRelationships(DS, null);

      assertNotNull(result);
      assertTrue(result.isEmpty());
   }

   private static TabularRelationship edge(String name, String from, String to) {
      return new TabularRelationship(name, from, to, List.of("x"), List.of("y"));
   }

   private static Set<String> nameSet(List<TabularRelationship> relationships) {
      return relationships.stream().map(TabularRelationship::name).collect(Collectors.toSet());
   }
}
