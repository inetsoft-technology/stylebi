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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link WorksheetTableService#droppedColumns} — the name-matching half of the
 * execution probe's dropped-column backstop.
 *
 * <p>The bug: the probe only answered "did the query run". A column the source does not have is
 * REMOVED by {@code PreAssetQuery.validateColumnSelection} rather than failing the query, so the SQL
 * stayed valid and the probe passed, while the assembly kept advertising the column to every
 * read-back. The chart bound to it rendered empty with no error.
 *
 * <p>The matcher deliberately runs only after a count comparison has already proven columns went
 * missing, so these tests fix the two behaviours that matter: a genuinely absent column is named,
 * and a name that merely SPELLS differently between the assembly and the lens is not.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceProducedColumnsTest {
   @Test
   void aColumnTheQueryDidNotProduceIsNamed() {
      List<String> advertised = List.of("ORDER_ID", "DISCOUNT", "ORDER_AMOUNT", "discount_status");
      List<String> produced = List.of("ORDER_ID", "DISCOUNT", "discount_status");

      assertEquals(List.of("ORDER_AMOUNT"),
                   WorksheetTableService.droppedColumns(advertised, produced));
   }

   @Test
   void everyMissingColumnIsNamed() {
      List<String> advertised = List.of("A", "B", "C", "D");
      List<String> produced = List.of("B");

      assertEquals(List.of("A", "C", "D"),
                   WorksheetTableService.droppedColumns(advertised, produced));
   }

   @Test
   void nothingMissingWhenEveryColumnWasProduced() {
      List<String> names = List.of("ORDER_ID", "DISCOUNT");

      assertTrue(WorksheetTableService.droppedColumns(names, names).isEmpty());
   }

   @Test
   void aMirrorsQualifiedColumnNameMatchesTheLensBareHeader() {
      // The assembly names a mirror's inherited columns "BaseTable.col"; the lens reports them bare.
      // Treating that spelling difference as a dropped column would fail a perfectly good table.
      List<String> advertised = List.of("ORDERS_physical_1.ORDER_ID", "ORDERS_physical_1.DISCOUNT");
      List<String> produced = List.of("ORDER_ID", "DISCOUNT");

      assertTrue(WorksheetTableService.droppedColumns(advertised, produced).isEmpty());
   }

   @Test
   void theQualifiedMatchWorksInBothDirections() {
      List<String> advertised = List.of("ORDER_ID");
      List<String> produced = List.of("ORDERS_physical_1.ORDER_ID");

      assertTrue(WorksheetTableService.droppedColumns(advertised, produced).isEmpty());
   }

   @Test
   void matchingIsCaseInsensitive() {
      List<String> advertised = List.of("Order_Id", "discount");
      List<String> produced = List.of("ORDER_ID", "DISCOUNT");

      assertTrue(WorksheetTableService.droppedColumns(advertised, produced).isEmpty());
   }

   @Test
   void aQualifiedColumnIsStillMissedWhenOnlyItsPrefixDiffersAndTheAttributeIsAbsent() {
      List<String> advertised = List.of("T.ORDER_AMOUNT");
      List<String> produced = List.of("T.ORDER_ID");

      assertEquals(List.of("T.ORDER_AMOUNT"),
                   WorksheetTableService.droppedColumns(advertised, produced));
   }

   @Test
   void aNullLensColumnNameNeitherMatchesNorCrashes() {
      List<String> advertised = new ArrayList<>(Arrays.asList("A", null));
      List<String> produced = new ArrayList<>(Arrays.asList((String) null));

      // A null on either side carries no match key, so the real column is reported and the null
      // advertised entry is not — an unnamed lens column is no evidence about a named one.
      assertEquals(List.of("A"), WorksheetTableService.droppedColumns(advertised, produced));
   }
}
