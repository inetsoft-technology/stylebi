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

import inetsoft.report.script.formula.CalcRef;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CalcTableLensTest {
   @Test
   public void testSerialize() throws Exception {
      CalcTableLens originalTable = new CalcTableLens(XTableUtil.getDefaultTableLens());
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(CalcTableLens.class, deserializedTable.getClass());
   }

   /**
    * Bug #75595: a bare named-cell reference ($name) as a formula result must be
    * resolved to its referenced value so a live CalcRef never leaks into the
    * cached cell data (which later crashed JSON serialization of
    * LoadTableDataCommand and hung the table on "loading").
    */
   @Test
   public void testUnwrapTopLevelCalcRef() {
      CalcRef ref = mock(CalcRef.class);
      when(ref.unwrap()).thenReturn("value1");

      Assertions.assertEquals("value1", CalcTableLens.unwrapCalcRefs(ref));
   }

   /**
    * Bug #75595: a CalcRef nested inside an array result (e.g. [$a, $b]) must
    * also be resolved, since ScriptValueConverter.toHost unwraps each array
    * element back to its raw host object.
    */
   @Test
   public void testUnwrapCalcRefInArray() {
      CalcRef a = mock(CalcRef.class);
      CalcRef b = mock(CalcRef.class);
      when(a.unwrap()).thenReturn(1);
      when(b.unwrap()).thenReturn(2);

      Object result = CalcTableLens.unwrapCalcRefs(new Object[]{ a, "x", b });

      Assertions.assertArrayEquals(new Object[]{ 1, "x", 2 }, (Object[]) result);
   }

   /**
    * Bug #75595: CalcRefs nested in arrays-of-arrays must be resolved too.
    */
   @Test
   public void testUnwrapCalcRefInNestedArray() {
      CalcRef a = mock(CalcRef.class);
      when(a.unwrap()).thenReturn(1);

      Object result = CalcTableLens.unwrapCalcRefs(new Object[]{ new Object[]{ a }, "y" });

      Object[] outer = (Object[]) result;
      Assertions.assertArrayEquals(new Object[]{ 1 }, (Object[]) outer[0]);
      Assertions.assertEquals("y", outer[1]);
   }

   /**
    * Bug #75595: results with no CalcRef must be returned unchanged (no
    * regression for ordinary numeric/string/array formula results).
    */
   @Test
   public void testNonCalcRefResultUnchanged() {
      Assertions.assertEquals("plain", CalcTableLens.unwrapCalcRefs("plain"));
      Assertions.assertEquals(42.0, CalcTableLens.unwrapCalcRefs(42.0));
      Assertions.assertNull(CalcTableLens.unwrapCalcRefs(null));

      Object[] arr = { 1, "a", null };
      Assertions.assertArrayEquals(new Object[]{ 1, "a", null }, (Object[]) CalcTableLens.unwrapCalcRefs(arr));
   }
}
