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
package inetsoft.web.wiz.binding;

import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.wiz.binding.model.BindableTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #79192: worksheet tables Customers and CUSTOMERS are two tables. */
@Tag("core")
class TableNamesTest {
   @Test
   void anExactMatchWinsWhateverTheListingOrder() {
      assertEquals("Customers", TableNames.resolve(List.of("CUSTOMERS", "Customers"), "Customers"));
      assertEquals("Customers", TableNames.resolve(List.of("Customers", "CUSTOMERS"), "Customers"));
      assertEquals("CUSTOMERS", TableNames.resolve(List.of("Customers", "CUSTOMERS"), "CUSTOMERS"));
   }

   @Test
   void aUniqueCaseOnlyMatchIsReturnedInItsRealSpelling() {
      assertEquals("Orders", TableNames.resolve(List.of("Orders", "Products"), "ORDERS"));
   }

   @Test
   void severalCaseOnlyMatchesAreRefusedNamingAllOfThem() {
      for(List<String> names : List.of(List.of("Customers", "CUSTOMERS", "Orders"),
                                       List.of("CUSTOMERS", "Customers", "Orders")))
      {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> TableNames.resolve(names, "customers"));

         assertTrue(e.getMessage().contains("'customers'"), e.getMessage());
         assertTrue(e.getMessage().contains("Customers"), e.getMessage());
         assertTrue(e.getMessage().contains("CUSTOMERS"), e.getMessage());
         assertFalse(e.getMessage().contains("Orders"), e.getMessage());
      }
   }

   @Test
   void anUnknownOrNullNameMatchesNothing() {
      assertNull(TableNames.resolve(List.of("Orders"), "Nope"));
      assertNull(TableNames.resolve(List.of("Orders"), null));
      assertNull(TableNames.resolve(null, "Orders"));
      assertNull(TableNames.resolve(new ArrayList<>(java.util.Arrays.asList(null, "Orders")), "x"));
   }

   @Test
   void resolvesOverSourceTablesAndBindableTables() {
      BindingModel.SourceTable upper = new BindingModel.SourceTable();
      upper.setName("CUSTOMERS");
      BindingModel.SourceTable mixed = new BindingModel.SourceTable();
      mixed.setName("Customers");

      assertEquals("Customers", TableNames.resolveSourceTable(List.of(upper, mixed), "Customers"));
      assertNull(TableNames.resolveSourceTable(null, "Customers"));
      assertThrows(IllegalArgumentException.class,
                   () -> TableNames.resolveSourceTable(List.of(upper, mixed), "customers"));

      BindableTable a = new BindableTable("CUSTOMERS", null, List.of());
      BindableTable b = new BindableTable("Customers", null, List.of());

      assertSame(b, TableNames.resolveTable(List.of(a, b), "Customers"));
      assertSame(a, TableNames.resolveTable(List.of(b, a), "CUSTOMERS"));
      assertThrows(IllegalArgumentException.class,
                   () -> TableNames.resolveTable(List.of(a, b), "customers"));
   }

   @Test
   void aStoredSpellingResolvesToItsOneTableAndNeverGuessesBetweenSeveral() {
      assertEquals("Customers", TableNames.resolveStored(List.of("CUSTOMERS", "Customers"),
                                                         "Customers"));
      assertEquals("Customers", TableNames.resolveStored(List.of("Customers", "Orders"),
                                                         "customers"));
      assertNull(TableNames.resolveStored(List.of("CUSTOMERS", "Customers"), "customers"));
      assertNull(TableNames.resolveStored(List.of("Orders"), null));
   }
}