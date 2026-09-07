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
import inetsoft.uql.XRepository;
import inetsoft.uql.tabular.*;
import inetsoft.web.wiz.model.DatasourceTablesResponse;
import inetsoft.web.wiz.model.osi.OsiRelationship;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers charter assertion A7: filtering relationships to both-endpoints-in-selection, per
 * {@link TabularCatalogProvider#listRelationships}'s default implementation, makes the untouched
 * {@code TabularCatalogService.validateRelationshipEndpoints} pass by construction for a caller
 * that annotates a chosen subset of a source's datasets.
 *
 * <p>New file rather than an addition to {@code TabularCatalogServiceContractValidationTest} —
 * charter B1 requires that file (and {@code TabularCatalogService.java} itself) to see zero changed
 * lines this round; its own two existing negative-path tests
 * ({@code listTables_relationshipFromDatasetMissing_throwsNamingTheViolation}/
 * {@code _ToDatasetMissing_}) already carry the B1 regression evidence — see charter B1 and
 * {@code 03-reconcile.md} §D5 for why no new negative-path test is added here.
 */
@Tag("core")
class TabularCatalogServiceRelationshipEndpointFilterTest {

   private static final String DS_NAME = "Fake Paging Source";

   @Test
   void filteredRelationships_feedIntoListTables_withoutTrippingEndpointValidation() throws Exception {
      // A three-dataset, two-edge full catalog: A->B has both endpoints in the caller's chosen
      // subset {A, B}; A->C does not (C is never selected).
      TabularCatalog fullCatalog = new TabularCatalog(
         List.of(new TabularDatasetRef("A"), new TabularDatasetRef("B"), new TabularDatasetRef("C")),
         List.of(
            new TabularRelationship("R_A_B", "A", "B", List.of("x"), List.of("y")),
            new TabularRelationship("R_A_C", "A", "C", List.of("x"), List.of("y"))));

      FakeCatalogRuntime fullProvider = new FakeCatalogRuntime(fullCatalog, Map.of());

      // This is the real default listRelationships call, not a hand-copied edge list -- if A5's
      // implementation had a bug, this test would fail along with it rather than masking it.
      List<TabularRelationship> filtered = fullProvider.listRelationships(
         new FakeTabularDataSource(), Set.of("A", "B"));
      assertEquals(1, filtered.size());
      assertEquals("R_A_B", filtered.get(0).name());

      TabularCatalog selectedCatalog = new TabularCatalog(
         List.of(new TabularDatasetRef("A"), new TabularDatasetRef("B")), filtered);
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(selectedCatalog, Map.of());

      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(DS_NAME)).thenReturn(new FakeTabularDataSource());
      TabularCatalogService service =
         new TabularCatalogService(xrepository, new ObjectMapper(), dsName -> runtime);

      DatasourceTablesResponse response = service.listTables(DS_NAME);

      List<OsiRelationship> relationships = response.getRelationships();
      assertEquals(1, relationships.size());
      assertEquals("A", relationships.get(0).getFrom());
      assertEquals("B", relationships.get(0).getTo());
   }
}
