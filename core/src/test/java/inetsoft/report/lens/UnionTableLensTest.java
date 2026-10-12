/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class UnionTableLensTest {
   @Test
   public void testSerialize() throws Exception {
      UnionTableLens originalTable = new UnionTableLens(XTableUtil.getDefaultTableLens(),
                                                        XTableUtil.getDefaultTableLens());
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(UnionTableLens.class, deserializedTable.getClass());
   }

   /**
    * A union all read to the end, whose base then shrinks, reports no row past its new end
    * after invalidate() (bug #77397).
    */
   @Test
   public void invalidateAfterShrinkReportsNoRowPastEnd() {
      DefaultTableLens right = new DefaultTableLens(data(5));
      UnionTableLens lens = new UnionTableLens(new DefaultTableLens(data(5)), right);
      lens.setDistinct(false);

      for(int r = 0; lens.moreRows(r); r++) {
         lens.getObject(r, 1);
      }

      right.setData(data(2));
      lens.invalidate();

      UnionTableLens fresh = new UnionTableLens(new DefaultTableLens(data(5)),
                                                new DefaultTableLens(data(2)));
      fresh.setDistinct(false);
      Assertions.assertFalse(fresh.moreRows(9));
      Assertions.assertEquals(fresh.moreRows(9), lens.moreRows(9));
      Assertions.assertEquals(fresh.moreRows(7), lens.moreRows(7));
      Assertions.assertEquals(fresh.getObject(7, 1), lens.getObject(7, 1));
      Assertions.assertDoesNotThrow(() -> lens.getObject(9, 1));
   }

   /**
    * {@code rows} rows of {@code id, value}, both {@code r} for row {@code r}.
    */
   private static Object[][] data(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { r, r };
      }

      return data;
   }
}
