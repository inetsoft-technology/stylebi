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
package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XRepository;
import inetsoft.uql.XTableNode;
import inetsoft.uql.tabular.*;
import inetsoft.web.wiz.model.osi.OsiRelationship;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Item 2, {@code TabularCatalogService.listRelationships} + its private
 * {@code filterAndConvertSubsetRelationships} validator -- charter B1, B2, B6, and the reconcile
 * Δ4 duplicate-name-across-out-of-scope-edges case the verify plan could not have anticipated
 * (it was written blind to this method, which did not exist yet).
 */
@Tag("core")
class TabularCatalogServiceSubsetRelationshipsTest {

   private static final String DS_NAME = "Fake Subset Source";

   /** B1: both-endpoints-in-subset edges are converted to the wire type. */
   @Test
   void bothEndpointsInSubset_areReturned() throws Exception {
      TabularCatalog fullCatalog = new TabularCatalog(
         List.of(new TabularDatasetRef("A"), new TabularDatasetRef("B"), new TabularDatasetRef("C")),
         List.of(new TabularRelationship("R_A_B", "A", "B", List.of("x"), List.of("y"))));

      TabularCatalogService service = serviceFor(new FakeCatalogRuntime(fullCatalog, Map.of()));

      List<OsiRelationship> result = service.listRelationships(DS_NAME, Set.of("A", "B"));

      assertEquals(1, result.size());
      assertEquals("R_A_B", result.get(0).getName());
      assertEquals("A", result.get(0).getFrom());
      assertEquals("B", result.get(0).getTo());
   }

   /**
    * B2: an edge with one endpoint outside the subset is dropped, not thrown -- and this must
    * hold even when the connector's OWN {@code listRelationships} override violates the
    * both-endpoints contract (the default implementation cannot; this exercises the defensive
    * path in {@code filterAndConvertSubsetRelationships} directly, per 03-reconcile.md Δ3/Δ4).
    */
   @Test
   void oneEndpointOutsideSubset_isDroppedWithoutThrowing() throws Exception {
      TabularRuntime runtime = new OverridingRelationshipsRuntime(List.of(
         new TabularRelationship("R_A_B", "A", "B", List.of("x"), List.of("y")),
         new TabularRelationship("R_A_C", "A", "C", List.of("x"), List.of("y"))));

      TabularCatalogService service = serviceFor(runtime);

      List<OsiRelationship> result = service.listRelationships(DS_NAME, Set.of("A", "B"));

      assertEquals(1, result.size());
      assertEquals("R_A_B", result.get(0).getName());
   }

   /**
    * B6: the subset path must reach {@code provider.listRelationships(tds, ids)} itself, not
    * re-derive the answer by calling {@code listDatasets} and filtering client-side. A runtime
    * whose {@code listRelationships} override returns something a client-side filter of
    * {@code listDatasets()} could never produce (an edge {@code listDatasets} does not declare at
    * all) proves the service used the override.
    */
   @Test
   void callsProviderListRelationships_notAClientSideFilterOfListDatasets() throws Exception {
      TabularCatalog catalogWithNoRelationships = new TabularCatalog(
         List.of(new TabularDatasetRef("A"), new TabularDatasetRef("B")), List.of());

      TabularRuntime runtime = new OverridingRelationshipsRuntime(
         catalogWithNoRelationships,
         List.of(new TabularRelationship("R_ONLY_VIA_OVERRIDE", "A", "B", List.of("x"), List.of("y"))));

      TabularCatalogService service = serviceFor(runtime);

      List<OsiRelationship> result = service.listRelationships(DS_NAME, Set.of("A", "B"));

      // listDatasets() alone declares NO relationships -- if the service filtered that instead of
      // calling the override, this would come back empty.
      assertEquals(1, result.size());
      assertEquals("R_ONLY_VIA_OVERRIDE", result.get(0).getName());
   }

   /**
    * Reconcile Δ4: the duplicate-name check must run over EVERY returned relationship, including
    * ones later dropped for being out of scope -- proving the uniqueness check is not accidentally
    * moved after the scope {@code continue}. Both edges here are entirely out-of-scope for the
    * requested subset {A}.
    */
   @Test
   void duplicateNameAmongTwoOutOfScopeEdges_stillThrows() throws Exception {
      TabularRuntime runtime = new OverridingRelationshipsRuntime(List.of(
         new TabularRelationship("DUP", "X", "Y", List.of("x"), List.of("y")),
         new TabularRelationship("DUP", "Y", "Z", List.of("x"), List.of("y"))));

      TabularCatalogService service = serviceFor(runtime);

      Exception ex = assertThrows(Exception.class,
         () -> service.listRelationships(DS_NAME, Set.of("A")));
      assertTrue(ex.getMessage().contains("DUP"),
         "the duplicate name must be named in the failure even though both edges are out of scope");
   }

   /** A null relationships list from the provider is a connector defect, not an empty answer. */
   @Test
   void nullRelationshipsListFromProvider_throws() {
      TabularRuntime runtime = new OverridingRelationshipsRuntime((List<TabularRelationship>) null);

      TabularCatalogService service = serviceFor(runtime);

      assertThrows(Exception.class, () -> service.listRelationships(DS_NAME, Set.of("A")));
   }

   /** A blank relationship name is rejected the same way the full-listing validator rejects it. */
   @Test
   void blankRelationshipName_throws() {
      TabularRuntime runtime = new OverridingRelationshipsRuntime(List.of(
         new TabularRelationship("", "A", "B", List.of("x"), List.of("y"))));

      TabularCatalogService service = serviceFor(runtime);

      assertThrows(Exception.class, () -> service.listRelationships(DS_NAME, Set.of("A", "B")));
   }

   /** Mismatched fromColumns/toColumns sizes on an in-scope edge is a malformed record. */
   @Test
   void mismatchedColumnSizesOnInScopeEdge_throws() {
      TabularRuntime runtime = new OverridingRelationshipsRuntime(List.of(
         new TabularRelationship("R", "A", "B", List.of("x", "y"), List.of("z"))));

      TabularCatalogService service = serviceFor(runtime);

      assertThrows(Exception.class, () -> service.listRelationships(DS_NAME, Set.of("A", "B")));
   }

   /**
    * Charter B4, regression pin: the PAGED listing route must still carry no relationships after
    * this round -- the risk is a builder "helpfully" wiring relationships into the paged route
    * while adding the new subset one. {@code TabularCatalogProviderPagingTest} already pins this
    * structurally at the {@code TabularCatalogPage} record level (no relationships field exists at
    * all); this pins it at the {@code TabularCatalogService.listTables} call this round did not
    * touch.
    */
   @Test
   void pagedListing_stillCarriesNoRelationshipsAfterThisRound() throws Exception {
      TabularCatalog catalog = new TabularCatalog(
         List.of(new TabularDatasetRef("A"), new TabularDatasetRef("B")),
         List.of(new TabularRelationship("R_A_B", "A", "B", List.of("x"), List.of("y"))));

      TabularCatalogService service = serviceFor(new FakeCatalogRuntime(catalog, Map.of()));

      inetsoft.web.wiz.model.DatasourceTablesResponse page =
         service.listTables(DS_NAME, null, 10, null);

      assertTrue(page.getRelationships().isEmpty(),
         "the paged route must carry no relationships even though the same source declares one");
   }

   private static TabularCatalogService serviceFor(TabularRuntime runtime) {
      XRepository xrepository = mock(XRepository.class);
      try {
         when(xrepository.getDataSource(DS_NAME)).thenReturn(new FakeTabularDataSource());
      }
      catch(java.rmi.RemoteException e) {
         throw new RuntimeException(e);
      }
      return new TabularCatalogService(xrepository, new ObjectMapper(), dsName -> runtime);
   }

   /**
    * A runtime whose {@code listRelationships} is overridden directly, bypassing the default
    * both-endpoints filter entirely -- lets these tests feed {@code TabularCatalogService} an
    * answer the default implementation could never itself produce, exercising the SERVICE's own
    * defensive checks rather than the SPI default's.
    */
   private static class OverridingRelationshipsRuntime extends TabularRuntime
      implements TabularCatalogProvider
   {
      OverridingRelationshipsRuntime(List<TabularRelationship> relationships) {
         this(new TabularCatalog(List.of(), List.of()), relationships);
      }

      OverridingRelationshipsRuntime(TabularCatalog listDatasetsCatalog,
                                      List<TabularRelationship> relationshipsOverride)
      {
         this.listDatasetsCatalog = listDatasetsCatalog;
         this.relationshipsOverride = relationshipsOverride;
      }

      @Override
      public TabularCatalog listDatasets(TabularDataSource<?> dataSource) {
         return listDatasetsCatalog;
      }

      @Override
      public TabularDatasetSchema describeDataset(TabularDataSource<?> dataSource, String datasetId) {
         throw new UnsupportedOperationException("not used by these tests");
      }

      @Override
      public List<TabularRelationship> listRelationships(TabularDataSource<?> dataSource,
                                                           Collection<String> datasetIds)
      {
         return relationshipsOverride;
      }

      @Override
      public XTableNode runQuery(TabularQuery query, VariableTable params) {
         throw new UnsupportedOperationException("not used by these tests");
      }

      @Override
      public void testDataSource(TabularDataSource<?> ds, VariableTable params) {
         throw new UnsupportedOperationException("not used by these tests");
      }

      private final TabularCatalog listDatasetsCatalog;
      private final List<TabularRelationship> relationshipsOverride;
   }
}
