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
package inetsoft.uql.rest;

import inetsoft.test.*;
import inetsoft.uql.rest.json.JsonTransformer;
import inetsoft.uql.rest.json.RestJsonQuery;
import inetsoft.uql.rest.json.lookup.LookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The runner tells the query that it stopped at the row limit with rows left unread only when the
 * caller asked, and only when rows really were left: a source with exactly as many rows as the
 * limit is not reported, and an ordinary render never pays for the check.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class RestJsonQueryRunnerRowLimitTest {
   @Test
   void pageBoundaryWithAnotherPageIsReported() {
      PagedStrategy strategy = new PagedStrategy(3, 2);
      RestJsonQuery query = query(4, true);

      run(query, strategy);

      assertTrue(query.isRowLimitReached());
      // two pages read by the loop, one more asked about
      assertEquals(3, strategy.hasNextCalls);
   }

   @Test
   void pageBoundaryWithNoFurtherPageIsNotReported() {
      PagedStrategy strategy = new PagedStrategy(2, 2);
      RestJsonQuery query = query(4, true);

      run(query, strategy);

      assertFalse(query.isRowLimitReached());
   }

   @Test
   void rowsDroppedFromTheOnlyPageAreReportedWithoutAskingForAnotherPage() {
      PagedStrategy strategy = new PagedStrategy(1, 6);
      RestJsonQuery query = query(4, true);

      run(query, strategy);

      assertTrue(query.isRowLimitReached());
      assertEquals(1, strategy.hasNextCalls);
   }

   @Test
   void sourceSmallerThanTheLimitIsNotReported() {
      RestJsonQuery query = query(100, true);

      run(query, new PagedStrategy(3, 2));

      assertFalse(query.isRowLimitReached());
   }

   @Test
   void queryWithoutALimitIsNotReported() {
      RestJsonQuery query = query(0, true);

      run(query, new PagedStrategy(3, 2));

      assertFalse(query.isRowLimitReached());
   }

   @Test
   void nothingIsReportedOrFetchedUnlessTheCallerAsked() {
      PagedStrategy strategy = new PagedStrategy(3, 2);
      RestJsonQuery query = query(4, false);

      run(query, strategy);

      assertFalse(query.isRowLimitReached());
      // only the two the read loop itself asks
      assertEquals(2, strategy.hasNextCalls);
   }

   @Test
   void failureToAskForAnotherPageIsNotReportedAsMoreRows() {
      PagedStrategy strategy = new PagedStrategy(3, 2);
      strategy.failOnHasNextCall = 3;
      RestJsonQuery query = query(4, true);

      run(query, strategy);

      assertFalse(query.isRowLimitReached());
   }

   private static RestJsonQuery query(int maxRows, boolean probe) {
      RestJsonQuery query = new RestJsonQuery();
      query.setExpanded(true);
      query.setMaxRows(maxRows);
      query.setRowLimitProbe(probe);
      return query;
   }

   private static void run(RestJsonQuery query, PagedStrategy strategy) {
      new RestJsonQueryRunner(query, q -> strategy, new LookupService(), new JsonTransformer())
         .runStream();
   }

   /** Serves {@code pages} pages of {@code rowsPerPage} rows each. */
   private static final class PagedStrategy implements RestDataIteratorStrategy<Object> {
      PagedStrategy(int pages, int rowsPerPage) {
         this.pages = pages;
         this.rowsPerPage = rowsPerPage;
      }

      @Override
      public boolean hasNext() throws Exception {
         hasNextCalls++;

         if(hasNextCalls == failOnHasNextCall) {
            throw new Exception("no next page");
         }

         return served < pages;
      }

      @Override
      public Object next() {
         if(served >= pages) {
            return null;
         }

         List<Object> rows = new ArrayList<>();

         for(int i = 0; i < rowsPerPage; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", served * rowsPerPage + i);
            rows.add(row);
         }

         served++;
         return rows;
      }

      @Override
      public void close() {
      }

      private final int pages;
      private final int rowsPerPage;
      private int served;
      int hasNextCalls;
      int failOnHasNextCall = -1;
   }
}
