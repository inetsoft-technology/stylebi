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
package inetsoft.mv.data;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79507: the MV group ordering ({@link MVRow2.RowComparator}) must follow
 * string.compare.casesensitive like the non-MV sort, otherwise position based consumers
 * (range slider bookmark restore) select a different window with MV.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVRow2ComparatorCaseTest {
   private static final String[] VALUES = {
      "NY", "Northwest Outfitters", "Nova Tera", "NV", "Old World Insurance",
      "FL", "FMG Consulting", "Fast Transportation", "Folk Mining", "FISGA Corp"
   };

   @Test
   void caseInsensitiveByDefault() {
      withCaseSensitive(false, () -> {
         List<String> expected = new ArrayList<>(Arrays.asList(VALUES));
         expected.sort(String::compareToIgnoreCase);
         assertEquals(expected, sorted(true, VALUES));
         assertEquals(Arrays.asList("Fast Transportation", "FISGA Corp", "FL", "FMG Consulting",
                                    "Folk Mining"), sorted(true, VALUES).subList(0, 5));
         // descending is the exact reverse
         List<String> desc = new ArrayList<>(expected);
         Collections.reverse(desc);
         assertEquals(desc, sorted(false, VALUES));
      });
   }

   @Test
   void caseSensitiveWhenConfigured() {
      withCaseSensitive(true, () -> {
         List<String> expected = new ArrayList<>(Arrays.asList(VALUES));
         Collections.sort(expected);
         assertEquals(expected, sorted(true, VALUES));
         assertEquals("FL", sorted(true, VALUES).get(1));
      });
   }

   @Test
   void caseOnlyDifferenceIsNotTreatedAsSameGroup() {
      withCaseSensitive(false, () -> {
         MVRow2.RowComparator cmp = new MVRow2.RowComparator(new boolean[] { true });
         MVRow2 a = new MVRow2(new Object[] { "a" }, new double[0]);
         MVRow2 b = new MVRow2(new Object[] { "A" }, new double[0]);
         assertNotEquals(0, cmp.compare(a, b));
         assertEquals(-Integer.signum(cmp.compare(a, b)), Integer.signum(cmp.compare(b, a)));
      });
   }

   private static List<String> sorted(boolean asc, String[] values) {
      List<MVRow> rows = new ArrayList<>();

      for(String v : values) {
         rows.add(new MVRow2(new Object[] { v }, new double[0]));
      }

      rows.sort(new MVRow2.RowComparator(new boolean[] { asc }));
      List<String> result = new ArrayList<>();
      rows.forEach(r -> result.add((String) ((MVRow2) r).groups2[0]));
      return result;
   }

   private static void withCaseSensitive(boolean cs, Runnable run) {
      String old = SreeEnv.getProperty("string.compare.casesensitive");
      SreeEnv.setProperty("string.compare.casesensitive", "" + cs);
      Tool.invalidateCaseSensitive();

      try {
         run.run();
      }
      finally {
         if(old == null) {
            SreeEnv.remove("string.compare.casesensitive");
         }
         else {
            SreeEnv.setProperty("string.compare.casesensitive", old);
         }

         Tool.invalidateCaseSensitive();
      }
   }
}
