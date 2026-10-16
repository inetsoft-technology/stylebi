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

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

// AggregateFormula static init reads SreeEnv: needs the Spring context and a SREE home.
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionFormulaResolverTest {
   @Test
   void canonicalisesTheOfferedAggregatesInAnyCase() {
      assertEquals("Count", SelectionFormulaResolver.resolve("L1", "count"));
      assertEquals("Sum", SelectionFormulaResolver.resolve("L1", "SUM"));
      assertEquals("Max", SelectionFormulaResolver.resolve("L1", " Max "));
      assertEquals("Min", SelectionFormulaResolver.resolve("L1", "min"));
      assertEquals("none", SelectionFormulaResolver.resolve("L1", "None"));
   }

   @Test
   void neverKeepsRawTextTheLookupTolerates() {
      assertEquals("Sum", SelectionFormulaResolver.resolve("L1", "Sum(Bar)"));
   }

   @Test
   void passesStrictDynamicValuesThrough() {
      assertEquals("$(Combo1)", SelectionFormulaResolver.resolve("L1", " $(Combo1) "));
      assertEquals("=field['f']", SelectionFormulaResolver.resolve("L1", "=field['f']"));
   }

   @Test
   void refusesEmptyDynamicReferences() {
      assertThrows(IllegalArgumentException.class, () -> SelectionFormulaResolver.resolve("L1", "="));
      assertThrows(IllegalArgumentException.class, () -> SelectionFormulaResolver.resolve("L1", "$()"));
   }

   @Test
   void refusesUnknownAndNotOfferedFormulasNamingTheValueAndTheValidSet() {
      for(String bad : new String[] {"Foo", "$Foo", "Average", "DistinctCount", "Correlation"}) {
         Exception e = assertThrows(IllegalArgumentException.class,
            () -> SelectionFormulaResolver.resolve("L1", bad));
         assertTrue(e.getMessage().contains("'" + bad + "'"), e.getMessage());
         assertTrue(e.getMessage().contains("Count, Sum, Max, Min"), e.getMessage());
      }
   }
}
