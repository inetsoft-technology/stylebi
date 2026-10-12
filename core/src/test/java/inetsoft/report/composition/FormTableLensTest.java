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

package inetsoft.report.composition;

import inetsoft.report.filter.ConditionFilter;
import inetsoft.report.filter.ConditionGroup;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.XTable;
import inetsoft.util.CancelledException;
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
public class FormTableLensTest {
   @Test
   public void testSerialize() throws Exception {
      FormTableLens originalTable = new FormTableLens(XTableUtil.getDefaultTableLens());
      originalTable.sort(1, XConstants.SORT_DESC);
      originalTable.appendRow(1);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(FormTableLens.class, deserializedTable.getClass());
   }

   /**
    * Bug #78244: a lazy base that a cancel cut short before its first row keeps a negative row
    * count (-2 with only the header). It must fail as a cancel, not with a
    * NegativeArraySizeException.
    */
   @Test
   public void cancelledBaseThrowsCancelledException() {
      ConditionFilter base =
         new ConditionFilter(XTableUtil.getDefaultTableLens(), new ConditionGroup());
      base.cancel();

      Assertions.assertThrows(CancelledException.class, () -> new FormTableLens(base));
   }
}
