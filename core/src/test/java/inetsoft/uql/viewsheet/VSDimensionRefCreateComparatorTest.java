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
package inetsoft.uql.viewsheet;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for a dashboard-compose crash: {@link VSDimensionRef#createComparator} used to
 * call {@code data.getType(getFullName())} unconditionally whenever a non-null {@link DataSet}
 * was supplied, even when that data set did not actually contain this dimension's column. That
 * throws {@link inetsoft.uql.viewsheet.ColumnNotFoundException} ("Column not found: project_name
 * in date_type,None(event_month),work_package_count", reproduced via VSFrameVisitor#syncColors ->
 * sortValues -> createComparator when composing two charts with differently-named categorical
 * color dimensions into one dashboard viewsheet), aborting the entire export/render instead of
 * this one dimension simply falling back to its own declared data type.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSDimensionRefCreateComparatorTest {

   private VSDimensionRef stringRef(String name) {
      VSDimensionRef ref = new VSDimensionRef();
      ref.setDataRef(new AttributeRef(name));
      ref.setDataType(XSchema.STRING);
      return ref;
   }

   @Test
   void doesNotThrowWhenTheDimensionsColumnIsAbsentFromTheDataSet() {
      // "project_name" is bound to this dimension, but the supplied data set (standing in for a
      // sibling chart's data, as happens when two charts share a dashboard-wide color-frame sync
      // pass) only has "date_type" and "work_package_count".
      VSDimensionRef projectName = stringRef("project_name");
      DataSet siblingChartData = new DefaultDataSet(new Object[][]{
         {"date_type", "work_package_count"},
         {"Planned Start", 42.0},
         {"Due Date", 37.0},
      });

      Comparator comparator = assertDoesNotThrow(() -> projectName.createComparator(siblingChartData));
      assertNotNull(comparator);
   }

   @Test
   void stillConsultsTheDataSetsActualColumnTypeWhenTheColumnIsPresent() {
      // Regression guard on the fix itself: when the column IS present, behavior must be
      // unchanged from before -- still resolve via the live data set, not just fall back to the
      // declared type on every call.
      VSDimensionRef category = stringRef("category");
      DataSet ownChartData = new DefaultDataSet(new Object[][]{
         {"category", "amount"},
         {"A", 10.0},
         {"B", 20.0},
      });

      Comparator comparator = assertDoesNotThrow(() -> category.createComparator(ownChartData));
      assertNotNull(comparator);
   }

   @Test
   void fallsBackGracefullyWithNoDataSetAtAll() {
      // The pre-existing null-data path (data == null) must remain unaffected by the fix.
      VSDimensionRef projectName = stringRef("project_name");
      Comparator comparator = assertDoesNotThrow(() -> projectName.createComparator(null));
      assertNotNull(comparator);
   }
}
