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
package inetsoft.report.lens;

import inetsoft.report.TableLens;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WindowTableLensTest {
   /** Base: header row + data. cols: stage(0,String), amount(1,Double). */
   private static DefaultTableLens base() {
      Object[][] data = {
         {"stage", "amount"},
         {"A", 30.0},
         {"A", 10.0},
         {"A", 20.0},
         {"B", 5.0},
      };
      DefaultTableLens t = new DefaultTableLens(data);
      t.setHeaderRowCount(1);
      return t;
   }

   private static Object cell(TableLens t, int dataRow, int col) {
      return t.getObject(dataRow + t.getHeaderRowCount(), col);
   }

   @Test
   void rowNumber_perPartition_orderedDesc() {
      // ROW_NUMBER() OVER (PARTITION BY stage ORDER BY amount DESC) as "rn"
      WindowTableLens.Spec spec = new WindowTableLens.Spec(
         "rn", "ROW_NUMBER", -1, 0, new int[]{0}, new int[]{1}, new boolean[]{false});
      WindowTableLens lens = new WindowTableLens(base(), new WindowTableLens.Spec[]{spec});

      assertEquals(3, lens.getColCount());                 // 2 base + 1 window
      assertEquals("rn", lens.getObject(0, 2));            // header
      // base rows preserved in ORIGINAL order; rn computed over sorted partition
      // original data: A/30, A/10, A/20, B/5  →  A desc: 30=1,20=2,10=3 ; B: 5=1
      assertEquals("A", cell(lens, 0, 0)); assertEquals(1, cell(lens, 0, 2)); // A/30 → 1
      assertEquals("A", cell(lens, 1, 0)); assertEquals(3, cell(lens, 1, 2)); // A/10 → 3
      assertEquals("A", cell(lens, 2, 0)); assertEquals(2, cell(lens, 2, 2)); // A/20 → 2
      assertEquals("B", cell(lens, 3, 0)); assertEquals(1, cell(lens, 3, 2)); // B/5  → 1
   }
}
