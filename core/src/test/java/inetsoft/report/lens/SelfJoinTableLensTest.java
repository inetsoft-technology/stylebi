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
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class SelfJoinTableLensTest {
   @Test
   public void testSerialize() throws Exception {
      SelfJoinTableLens originalTable = new SelfJoinTableLens(XTableUtil.getDefaultTableLens());
      originalTable.addJoin(1, SelfJoinTableLens.NOT_EQUAL_JOIN, 2);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(SelfJoinTableLens.class, deserializedTable.getClass());
   }

   /**
    * A swap file read failure of the base escapes the join instead of silently leaving the
    * join looking complete with only the matches found before the failure (bug #77651).
    */
   @Test
   public void baseSwapFileReadFailureEscapesTheJoin() {
      SwapFileReadException failure =
         new SwapFileReadException(new File("does-not-exist.dat"), new IOException("gone"));
      FailingBase base = new FailingBase(failure, 5);
      SelfJoinTableLens lens = new SelfJoinTableLens(base);
      lens.addJoin(0, SelfJoinTableLens.INNER_JOIN, 1);

      Assertions.assertSame(failure, Assertions.assertThrows(
         SwapFileReadException.class, () -> lens.moreRows(XTable.EOT)));
   }

   /**
    * A base whose data rows from {@code failAtRow} on fail with {@code failure}. Rows before
    * that match the join (col0 == col1).
    */
   private static final class FailingBase extends DefaultTableLens {
      FailingBase(RuntimeException failure, int failAtRow) {
         super(new Object[][] {
            { "id", "value" }, { 1, 1 }, { 2, 2 }, { 3, 3 }, { 4, 4 }, { 5, 5 }, { 6, 6 } });
         this.failure = failure;
         this.failAtRow = failAtRow;
      }

      @Override
      public boolean moreRows(int row) {
         if(row >= failAtRow) {
            throw failure;
         }

         return super.moreRows(row);
      }

      private final RuntimeException failure;
      private final int failAtRow;
   }
}
