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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers charter assertions A1, A2, A3, A4, A8, B2, B3, B8 for
 * {@link TabularCatalogProvider#listDatasets(TabularDataSource, TabularCatalogRequest)}'s default
 * implementation. Uses {@link FixedCatalogProvider}, which overrides neither new default method —
 * exercising the default implementation itself is the point of these assertions (see charter A9).
 */
@Tag("core")
class TabularCatalogProviderPagingTest {

   private static final int LIMIT = 3;
   private static final TabularDataSource<?> DS = new FakeTabularDataSource();

   // A1/A2 fixture: a Chinese id (订单), a pair differing only by case (orders/Orders — both are
   // independent, legal, distinct ids; A1 does not fold on case), and a dotted id
   // (customers.archive — legal per TabularDatasetRef's javadoc, and the subject of wiz#2252).
   private static final List<TabularDatasetRef> NINE_DATASETS =
      refs("orders", "Orders", "订单", "customers.archive", "A", "B", "C", "D", "E");

   // ----- A1 / A2 -----

   @ParameterizedTest(name = "catalog size {0}")
   @ValueSource(ints = {0, 1, 2, 3, 4, 7, 9})
   void pagingThroughEveryPage_withNoFilter_yieldsExactlyTheFullDatasetSet(int size) throws Exception {
      List<TabularDatasetRef> datasets = NINE_DATASETS.subList(0, size);
      FixedCatalogProvider provider = new FixedCatalogProvider(datasets);

      List<TabularCatalogPage> pages = collectAllPages(provider, DS, null, LIMIT, size);

      List<TabularDatasetRef> paged = pages.stream()
         .flatMap(p -> p.datasets().stream())
         .collect(Collectors.toList());

      // Set equality AND size equality -- set equality alone would let a duplicate id hide behind
      // Set's de-duplication.
      assertEquals(idSet(datasets), idSet(paged), "size=" + size + ": id set mismatch");
      assertEquals(datasets.size(), paged.size(), "size=" + size + ": total count mismatch");

      // A2: every non-final page holds exactly LIMIT entries, and has a non-null nextCursor; the
      // page carrying the last batch of data -- and only it -- has a null nextCursor, with no extra
      // empty page even when size is an exact multiple of LIMIT (size=9 here).
      for(int i = 0; i < pages.size() - 1; i++) {
         assertEquals(LIMIT, pages.get(i).datasets().size(),
            "size=" + size + ": page " + i + " is not the last page but lacks exactly " + LIMIT +
            " entries");
         assertNotNull(pages.get(i).nextCursor(),
            "size=" + size + ": page " + i + " is not the last page but has a null nextCursor");
      }
      assertNull(pages.get(pages.size() - 1).nextCursor(),
         "size=" + size + ": last page must have a null nextCursor");

      int expectedPageCount = size == 0 ? 1 : (int) Math.ceil(size / (double) LIMIT);
      assertEquals(expectedPageCount, pages.size(), "size=" + size + ": unexpected page count");
   }

   // ----- A3 -----

   /**
    * This loop is itself the A3 evidence: it only ever stores the exact {@code nextCursor} a
    * previous call returned and compares it to {@code null} — never {@code equals}, {@code parse},
    * {@code substring}, or {@code length} on the cursor string. A review pass over this file
    * confirms that discipline was not violated anywhere else in it either.
    */
   private static List<TabularCatalogPage> collectAllPages(TabularCatalogProvider provider,
      TabularDataSource<?> ds, String nameContains, int limit, int datasetCountBound)
      throws Exception
   {
      List<TabularCatalogPage> pages = new java.util.ArrayList<>();
      String cursor = null;
      int iterations = 0;

      while(true) {
         if(iterations++ > datasetCountBound + 2) {
            fail("paging loop exceeded " + (datasetCountBound + 2) +
               " iterations without terminating -- the implementation under test is likely stuck " +
               "in a loop rather than genuinely failing");
         }

         TabularCatalogPage page =
            provider.listDatasets(ds, new TabularCatalogRequest(nameContains, limit, cursor));
         pages.add(page);

         if(page.nextCursor() == null) {
            break;
         }

         cursor = page.nextCursor();
      }

      return pages;
   }

   /**
    * D1's resolution, recorded rather than forbidden: the default implementation's cursor is,
    * structurally, an offset into a freshly recomputed list ({@link TabularCatalogProvider}'s
    * "Stated residual" javadoc says so explicitly). When the underlying source legitimately changes
    * between two page calls — nothing in the SPI ever promised a snapshot across calls — the
    * default implementation can skip or repeat an entry. This test pins that EXACT, ACCEPTED
    * behavior; it must NOT be read as a bug report. A future keyset-based rewrite of the default
    * implementation would legitimately turn this test red — whoever does that should update this
    * test to match the new (stronger) contract, not treat a red run here as a regression to revert.
    */
   @Test
   void cursorAcrossUnderlyingListShift_recordsTheStatedResidualWeakness() throws Exception {
      ShiftingCatalogProvider provider = new ShiftingCatalogProvider(refs("A", "B", "C", "D"));

      TabularCatalogPage page1 =
         provider.listDatasets(DS, new TabularCatalogRequest(null, 2, null));
      assertEquals(List.of("A", "B"), idList(page1.datasets()));
      String cursor1 = page1.nextCursor();
      assertNotNull(cursor1);

      // Between the two calls, an element is inserted ahead of everything already delivered -- a
      // legitimate change the SPI never forbade.
      provider.setCurrent(refs("E", "A", "B", "C", "D"));

      TabularCatalogPage page2 =
         provider.listDatasets(DS, new TabularCatalogRequest(null, 2, cursor1));

      // Offset 2 into the shifted list is now [B, C]: B is repeated (already delivered in page1)
      // and D is skipped entirely. This is the residual the javadoc names, not a bug this test is
      // trying to catch.
      assertEquals(List.of("B", "C"), idList(page2.datasets()));
   }

   private static class ShiftingCatalogProvider implements TabularCatalogProvider {
      ShiftingCatalogProvider(List<TabularDatasetRef> initial) {
         this.current = initial;
      }

      void setCurrent(List<TabularDatasetRef> current) {
         this.current = current;
      }

      @Override
      public TabularCatalog listDatasets(TabularDataSource<?> dataSource) {
         return new TabularCatalog(current, List.of());
      }

      @Override
      public TabularDatasetSchema describeDataset(TabularDataSource<?> dataSource, String datasetId) {
         throw new UnsupportedOperationException("not used by this test");
      }

      private List<TabularDatasetRef> current;
   }

   // ----- A4 / B8 -----

   private static final List<TabularDatasetRef> NAME_FILTER_DATASETS = refs(
      "Orders", "orders_2025", "CustomerOrders", "订单汇总", "Invoices", "customers.archive");

   @Test
   void nameContains_substringNotPrefix_matchesMidStringCaseInsensitively() throws Exception {
      assertFilterYields("rder", Set.of("Orders", "orders_2025", "CustomerOrders"));
   }

   @Test
   void nameContains_upperCaseFilter_stillMatchesLowerCaseId() throws Exception {
      assertFilterYields("ORDERS", Set.of("Orders", "orders_2025", "CustomerOrders"));
   }

   @Test
   void nameContains_chineseSubstring_matchesById() throws Exception {
      assertFilterYields("订单", Set.of("订单汇总"));
   }

   @Test
   void nameContains_dotDoesNotActAsWildcardOrSeparator() throws Exception {
      assertFilterYields("archive", Set.of("customers.archive"));
   }

   @Test
   void nameContains_noSubstringMatches_yieldsEmptySet_notEveryDataset() throws Exception {
      // Paired with the null/blank cases below: this is what "really matches nothing" looks like,
      // so the two outcomes are told apart rather than assumed different (charter B8).
      assertFilterYields("ZZZ_NO_MATCH", Set.of());
   }

   @Test
   void blankOrNullNameContains_meansNoFiltering_notMatchNothing() throws Exception {
      Set<String> all = idSet(NAME_FILTER_DATASETS);
      assertFilterYields(null, all);
      assertFilterYields("", all);
      assertFilterYields("   ", all);
   }

   @Test
   void pagingAFilteredListing_satisfiesA1AndA2OverTheFilteredSet() throws Exception {
      FixedCatalogProvider provider = new FixedCatalogProvider(NAME_FILTER_DATASETS);

      List<TabularCatalogPage> pages = collectAllPages(provider, DS, "rder", 2, 3);

      List<TabularDatasetRef> paged = pages.stream()
         .flatMap(p -> p.datasets().stream()).collect(Collectors.toList());
      assertEquals(Set.of("Orders", "orders_2025", "CustomerOrders"), idSet(paged));
      assertEquals(3, paged.size());

      assertEquals(2, pages.size());
      assertNotNull(pages.get(0).nextCursor());
      assertEquals(2, pages.get(0).datasets().size());
      assertNull(pages.get(1).nextCursor());
      assertEquals(1, pages.get(1).datasets().size());
   }

   private static void assertFilterYields(String nameContains, Set<String> expectedIds)
      throws Exception
   {
      FixedCatalogProvider provider = new FixedCatalogProvider(NAME_FILTER_DATASETS);
      // limit set above the fixture size: this method only checks the filtered SET, paging itself
      // is covered separately above.
      List<TabularCatalogPage> pages =
         collectAllPages(provider, DS, nameContains, NAME_FILTER_DATASETS.size() + 1,
            NAME_FILTER_DATASETS.size());

      List<TabularDatasetRef> matched = pages.stream()
         .flatMap(p -> p.datasets().stream()).collect(Collectors.toList());
      assertEquals(expectedIds, idSet(matched), "nameContains='" + nameContains + "'");
   }

   // ----- A8 -----

   @Test
   void limitZero_throwsNamingTheField() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> new TabularCatalogRequest(null, 0, null));
      assertTrue(ex.getMessage().toLowerCase().contains("limit"),
         "exception must name the offending field: " + ex.getMessage());
   }

   @Test
   void limitNegative_throwsNamingTheField() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> new TabularCatalogRequest(null, -1, null));
      assertTrue(ex.getMessage().toLowerCase().contains("limit"),
         "exception must name the offending field: " + ex.getMessage());
   }

   // ----- B2 / B3 -----

   @Test
   void tabularCatalogPage_carriesExactlyDatasetsAndNextCursor_noRelationshipsField() {
      RecordComponent[] comps = TabularCatalogPage.class.getRecordComponents();
      assertEquals(Set.of("datasets", "nextCursor"),
         Arrays.stream(comps).map(RecordComponent::getName).collect(Collectors.toSet()));
      assertEquals(2, comps.length);
   }

   @Test
   void tabularCatalogRequest_carriesExactlyThreeFields_noReservedFilterDimension() {
      RecordComponent[] comps = TabularCatalogRequest.class.getRecordComponents();
      assertEquals(Set.of("nameContains", "limit", "cursor"),
         Arrays.stream(comps).map(RecordComponent::getName).collect(Collectors.toSet()));
      assertEquals(3, comps.length);
   }

   // ----- shared fixture helpers -----

   private static List<TabularDatasetRef> refs(String... ids) {
      return Arrays.stream(ids).map(TabularDatasetRef::new).collect(Collectors.toList());
   }

   private static List<String> idList(List<TabularDatasetRef> refs) {
      return refs.stream().map(TabularDatasetRef::id).collect(Collectors.toList());
   }

   private static Set<String> idSet(List<TabularDatasetRef> refs) {
      return refs.stream().map(TabularDatasetRef::id).collect(Collectors.toSet());
   }
}
