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
package inetsoft.report.composition.execution;

import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SreeHome()
class ColumnMetaDataTest {
   @Test
   void testBooleanInsertionFollowedByIntInsertionStaysBoolean() {
      final ColumnMetaData metaData = new ColumnMetaData();
      metaData.addValue(true);
      metaData.addValue(0);

      assertEquals(metaData.getValue(0), true);
      assertEquals(metaData.getValue(1), false);
   }

   @Test
   void testIntInsertionFollowedByBooleanInsertionStaysInt() {
      final ColumnMetaData metaData = new ColumnMetaData();
      metaData.addValue(0);
      metaData.addValue(true);

      assertEquals(metaData.getValue(0), 0);
      assertEquals(metaData.getValue(1), 1);
   }
}
