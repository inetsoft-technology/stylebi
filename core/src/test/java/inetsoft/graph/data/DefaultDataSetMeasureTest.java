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
package inetsoft.graph.data;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How {@link DefaultDataSet} classifies a column as a measure. The constructor loads
 * {@code GTool}, whose static initializer touches {@code inetsoft.report.internal.Common},
 * so the class runs with a Spring context (see CLAUDE.md, "Java Tests That Reach Spring
 * Beans").
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DefaultDataSetMeasureTest {
   // Bug #82042: the measure flag looked only at the first data row, so a numeric
   // column whose first value is null was a dimension although getType() is numeric.
   @Test
   void numericColumnWithLeadingNullIsMeasure() {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "k", "v" },
         { "a", null },
         { "b", 1.0 },
         { "c", 2.0 }
      });

      assertEquals(Double.class, data.getType("v"));
      assertTrue(data.isMeasure("v"));
      assertFalse(data.isMeasure("k"));
   }

   @Test
   void numericColumnWithSeveralLeadingNullsIsMeasure() {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "v" }, { null }, { null }, { 3 }
      });

      assertTrue(data.isMeasure("v"));
   }

   @Test
   void numericFirstRowIsMeasure() {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "v" }, { 1 }, { null }, { 2 }
      });

      assertTrue(data.isMeasure("v"));
   }

   @Test
   void stringColumnWithLeadingNullIsDimension() {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "v" }, { null }, { "x" }, { "y" }
      });

      assertFalse(data.isMeasure("v"));
   }

   @Test
   void allNullColumnIsDimension() {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "v" }, { null }, { null }
      });

      assertFalse(data.isMeasure("v"));
   }

   @Test
   void columnWithoutRowsIsDimension() {
      DefaultDataSet data = new DefaultDataSet(new Object[][] { { "v" } });

      assertFalse(data.isMeasure("v"));
   }

   // The measure flag follows the same value as getType(): the first non-null value.
   @Test
   void mixedColumnFollowsFirstNonNullValue() {
      DefaultDataSet numFirst = new DefaultDataSet(new Object[][] {
         { "v" }, { null }, { 1 }, { "x" }
      });
      DefaultDataSet strFirst = new DefaultDataSet(new Object[][] {
         { "v" }, { null }, { "x" }, { 1 }
      });

      assertTrue(numFirst.isMeasure("v"));
      assertFalse(strFirst.isMeasure("v"));
   }
}
