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
package inetsoft.report.composition.execution;

import inetsoft.test.*;
import inetsoft.uql.XConstants;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Bug #79522: sorting a list input must move label, value and format together, so rows
 * sharing a label keep their own value.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class InputVSAQuerySortTest {
   private static Object[] sorted(Object[] arr, int[] order) {
      Object[] copy = arr.clone();
      InputVSAQuery.permute(copy, order);
      return copy;
   }

   @Test
   void ascendingKeepsValuesOfDuplicateLabels() {
      Object[] labels = {"Smith", "Smith", "Adams", "Jones"};
      Object[] values = {"1", "2", "3", "4"};
      int[] order = InputVSAQuery.sortOrder(labels, XConstants.SORT_ASC, false);

      assertArrayEquals(new Object[] {"Adams", "Jones", "Smith", "Smith"},
                        sorted(labels, order));
      assertArrayEquals(new Object[] {"3", "4", "1", "2"}, sorted(values, order));
   }

   @Test
   void descendingKeepsValuesOfDuplicateLabels() {
      Object[] labels = {"Smith", "Smith", "Adams", "Jones"};
      Object[] values = {"1", "2", "3", "4"};
      int[] order = InputVSAQuery.sortOrder(labels, XConstants.SORT_DESC, false);

      assertArrayEquals(new Object[] {"Smith", "Smith", "Jones", "Adams"},
                        sorted(labels, order));
      assertArrayEquals(new Object[] {"1", "2", "4", "3"}, sorted(values, order));
   }

   @Test
   void sortByValueKeepsLabelsOfDuplicateValues() {
      Object[] labels = {"b", "a", "c"};
      Object[] values = {"2", "1", "1"};
      int[] order = InputVSAQuery.sortOrder(values, XConstants.SORT_ASC, true);

      assertArrayEquals(new Object[] {"1", "1", "2"}, sorted(values, order));
      assertArrayEquals(new Object[] {"a", "c", "b"}, sorted(labels, order));
   }

   @Test
   void emptyKeysAreLastAscendingAndFirstDescending() {
      Object[] labels = {"", "b", null, "a"};
      Object[] values = {"1", "2", "3", "4"};

      int[] asc = InputVSAQuery.sortOrder(labels, XConstants.SORT_ASC, false);
      assertArrayEquals(new Object[] {"4", "2", "1", "3"}, sorted(values, asc));

      int[] desc = InputVSAQuery.sortOrder(labels, XConstants.SORT_DESC, false);
      assertArrayEquals(new Object[] {"1", "3", "2", "4"}, sorted(values, desc));
   }

   @Test
   void numericLabelsSortNumericallyAndMismatchedArrayIsUntouched() {
      Object[] labels = {"10", "9", "9"};
      Object[] values = {"x", "y", "z"};
      Object[] formats = new Object[0];
      int[] order = InputVSAQuery.sortOrder(labels, XConstants.SORT_ASC, false);

      assertArrayEquals(new Object[] {"9", "9", "10"}, sorted(labels, order));
      assertArrayEquals(new Object[] {"y", "z", "x"}, sorted(values, order));
      InputVSAQuery.permute(formats, order);
      assertArrayEquals(new Object[0], formats);
   }
}
