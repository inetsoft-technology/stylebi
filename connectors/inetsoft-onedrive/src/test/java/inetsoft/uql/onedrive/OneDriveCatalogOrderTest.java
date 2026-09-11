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
package inetsoft.uql.onedrive;

import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.BrowsableQuery.BrowseEntry;
import inetsoft.uql.tabular.BrowsableQuery.BrowseListing;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B2/B3/D1/B9/D8: enumeration goes through {@code browseChildren} (never a reimplemented walk),
 * imposes its own stable order, reports truncation faithfully, and does not port forward
 * {@code MongoCatalog}'s obsolete "exclude ids containing '.'" filter.
 *
 * <p>No {@code OneDriveDataSource} is constructed anywhere in this file (unlike
 * {@code OneDriveTabularContractTest}/{@code OneDriveRuntimeTests}, which need one): the
 * package-private seam this exercises, {@code OneDriveCatalog.listDatasets(BrowsableQuery)}, needs
 * no data-source identity at all -- there is no configured root to validate against (see the design
 * doc's §6/B8), so nothing here needs the Spring context a real {@code OneDriveDataSource}'s
 * constructor would otherwise require.
 */
@Tag("core")
class OneDriveCatalogOrderTest {
   /** A stub {@link BrowsableQuery} whose answer (and invocation count) a test controls directly. */
   private static class StubBrowsable implements BrowsableQuery {
      private final List<BrowseListing> answers;
      private int calls = 0;

      StubBrowsable(BrowseListing... answers) {
         this.answers = List.of(answers);
      }

      int invocationCount() {
         return calls;
      }

      @Override
      public String getBrowsablePropertyName() {
         return "path";
      }

      @Override
      public List<String> getAcceptedExtensions() {
         return List.of(".txt", ".csv", ".xls", ".xlsx");
      }

      @Override
      public BrowseListing browseChildren(String path, boolean recursive, List<String> acceptTypes,
                                           int maxEntries)
      {
         BrowseListing answer = answers.get(Math.min(calls, answers.size() - 1));
         calls++;
         return answer;
      }
   }

   private static BrowseEntry file(String path) {
      return new BrowseEntry(path, path, false);
   }

   private static BrowseEntry folder(String path) {
      return new BrowseEntry(path, path, true);
   }

   // ----- B2/D1: enumeration goes through browseChildren, not a reimplemented walk -----

   @Test
   void listDatasetsInvokesBrowseChildrenAtLeastOnce() throws Exception {
      StubBrowsable stub = new StubBrowsable(
         new BrowseListing(List.of(file("a.csv")), false));

      OneDriveCatalog.listDatasets(stub);

      assertTrue(stub.invocationCount() >= 1,
         "listDatasets must actually call browseChildren, not reimplement a walk of its own");
   }

   @Test
   void foldersAreExcludedFromTheEmittedDatasets() throws Exception {
      StubBrowsable stub = new StubBrowsable(new BrowseListing(
         List.of(folder("Reports"), file("Reports/a.csv")), false));

      TabularCatalog catalog = OneDriveCatalog.listDatasets(stub);

      assertEquals(List.of("Reports/a.csv"),
         catalog.datasets().stream().map(TabularDatasetRef::id).toList());
   }

   // ----- B17: filtered by the READER's predicate, not by browseChildren's acceptTypes result -----

   @Test
   void anUppercaseExtensionFileIsEnumeratedByBrowseChildrenButExcludedFromTheCatalog()
      throws Exception
   {
      // collectChildren's own accepts() lowercases both sides, so Graph could hand this back even
      // though OneDriveFileUtil.isExcel/isText (both case-sensitive) would read it down the WRONG
      // branch if it were ever described. B17: listDatasets must filter with the reader's own
      // predicate so the advertised set and the describable set are the same set by construction.
      StubBrowsable stub = new StubBrowsable(new BrowseListing(
         List.of(file("REPORT.XLSX"), file("normal.xlsx")), false));

      TabularCatalog catalog = OneDriveCatalog.listDatasets(stub);

      assertEquals(List.of("normal.xlsx"),
         catalog.datasets().stream().map(TabularDatasetRef::id).toList());
   }

   // ----- D8: no ".'-containing id is excluded (MongoCatalog's obsolete filter, not ported) -----

   @Test
   void aPathContainingADotBeyondItsExtensionIsEnumeratedNormally() throws Exception {
      StubBrowsable stub = new StubBrowsable(new BrowseListing(
         List.of(file("logs-2026.09.04.csv")), false));

      TabularCatalog catalog = OneDriveCatalog.listDatasets(stub);

      assertEquals(List.of("logs-2026.09.04.csv"),
         catalog.datasets().stream().map(TabularDatasetRef::id).toList());
   }

   // ----- B3: the connector imposes its OWN stable order; Graph's own order is not a promise -----

   /**
    * The predecessor round's hard-won lesson, applied from the start: a test that merely checks
    * "two consecutive calls against an UNCHANGED stub return the same order" would pass identically
    * with the production sort deleted, if the stub itself always answers in the same order. This
    * drives a DIFFERENTLY SHUFFLED native order on each call and asserts the OUTPUT is identical
    * regardless -- the only shape that actually proves the connector, not the stub, is what
    * stabilizes the order.
    */
   @Test
   void enumerationIsSortedById_evenWhenTheNativeListingIsShuffledBetweenCalls() throws Exception {
      StubBrowsable stub = new StubBrowsable(
         new BrowseListing(List.of(file("c.csv"), file("a.csv"), file("b.csv")), false),
         new BrowseListing(List.of(file("b.csv"), file("c.csv"), file("a.csv")), false));

      TabularCatalog first = OneDriveCatalog.listDatasets(stub);
      TabularCatalog second = OneDriveCatalog.listDatasets(stub);

      List<String> expected = List.of("a.csv", "b.csv", "c.csv");
      assertEquals(expected, first.datasets().stream().map(TabularDatasetRef::id).toList());
      assertEquals(expected, second.datasets().stream().map(TabularDatasetRef::id).toList());
   }

   /**
    * Mutation check, per the SKILL's own rule ("a regression guard nobody has watched fail is not
    * yet a regression guard"): calling the sort-less private collection logic is not possible from
    * here without editing production code, so this test instead asserts the CONTRACT the sort
    * provides directly against a native order that is NOT already sorted -- if a future edit
    * deleted the {@code refs.sort(...)} call, this is the assertion that would go red.
    */
   @Test
   void unsortedNativeOrderStillProducesASortedResult() throws Exception {
      StubBrowsable stub = new StubBrowsable(
         new BrowseListing(List.of(file("z.csv"), file("m.csv"), file("a.csv")), false));

      TabularCatalog catalog = OneDriveCatalog.listDatasets(stub);

      assertEquals(List.of("a.csv", "m.csv", "z.csv"),
         catalog.datasets().stream().map(TabularDatasetRef::id).toList());
   }

   // ----- B9/G1: truncation is reported, never silently absorbed into a short/complete-looking result -----

   @Test
   void truncatedListingIsReportedOnTheCatalog_andTheEntriesItGotAreStillReturned() throws Exception {
      StubBrowsable stub = new StubBrowsable(new BrowseListing(
         List.of(file("a.csv"), file("b.csv")), true));

      TabularCatalog catalog = OneDriveCatalog.listDatasets(stub);

      assertTrue(catalog.truncated());
      assertEquals(2, catalog.datasets().size(),
         "a truncated listing is a USABLE PREFIX, not something to discard (D2)");
   }

   @Test
   void notTruncatedListingReportsFalse() throws Exception {
      StubBrowsable stub = new StubBrowsable(new BrowseListing(List.of(file("a.csv")), false));

      TabularCatalog catalog = OneDriveCatalog.listDatasets(stub);

      assertFalse(catalog.truncated());
   }

   @Test
   void maxEntriesPassedToBrowseChildrenMatchesWizsOwnFileEnumerationCap() throws Exception {
      // MAX_ENTRIES (5000) deliberately equals wiz's MAX_ANNOTATABLE_FILES -- the bound the file
      // pipeline's own enumerateTabularFileTargets otherwise loses once this connector flips to
      // METADATA (see the design doc's §2).
      class CapturingBrowsable extends StubBrowsable {
         int capturedMaxEntries = -1;

         CapturingBrowsable() {
            super(new BrowseListing(List.of(), false));
         }

         @Override
         public BrowseListing browseChildren(String path, boolean recursive,
                                              List<String> acceptTypes, int maxEntries)
         {
            capturedMaxEntries = maxEntries;
            return super.browseChildren(path, recursive, acceptTypes, maxEntries);
         }
      }

      CapturingBrowsable stub = new CapturingBrowsable();
      OneDriveCatalog.listDatasets(stub);

      assertEquals(5000, stub.capturedMaxEntries);
   }

   // ----- D5: listRelationships is never overridden -----

   @Test
   void listRelationshipsIsNeverOverridden() throws Exception {
      Method method = OneDriveRuntime.class.getMethod(
         "listRelationships", TabularDataSource.class, java.util.Collection.class);
      assertEquals(TabularCatalogProvider.class, method.getDeclaringClass(),
         "OneDriveRuntime must not declare its own listRelationships -- files have no " +
         "relationships, so the interface default (filter an empty edge list) is exactly right");
   }

   // ----- B11/D0 (paging): the paged listDatasets is never overridden either -----

   @Test
   void listDatasetsPagedIsNeverOverridden() throws Exception {
      Method method = OneDriveRuntime.class.getMethod(
         "listDatasets", TabularDataSource.class, TabularCatalogRequest.class);
      assertEquals(TabularCatalogProvider.class, method.getDeclaringClass(),
         "the default paged listDatasets is kept -- see OneDriveRuntime's own javadoc for why no " +
         "native cursor override exists");
   }
}
