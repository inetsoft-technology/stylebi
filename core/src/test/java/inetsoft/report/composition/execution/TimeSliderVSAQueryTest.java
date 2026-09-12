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

package inetsoft.report.composition.execution;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;
import inetsoft.util.CoreTool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for null handling in the range slider selection list. Bug #75823 (a member with a
 * null aggregate/measure value showed up as a blank min label) was originally fixed with a
 * NULL-exclusion pre-runtime condition, which broke XMLA/cube-bound sliders; the null is
 * now filtered on the query result instead, and only for a SingleTimeInfo binding.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class TimeSliderVSAQueryTest {
   private Viewsheet viewsheet;
   private TimeSliderVSAssembly assembly;
   private TimeSliderVSAQuery query;

   @BeforeEach
   void setUp() {
      viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      assembly = new TimeSliderVSAssembly();
      ((TimeSliderVSAssemblyInfo) assembly.getVSAssemblyInfo()).setName(SLIDER);
      viewsheet.addAssembly(assembly);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(viewsheet);

      query = new TimeSliderVSAQuery(box, SLIDER);
   }

   /**
    * A single-bound slider can't anchor its min/max on a null, so the row is dropped
    * rather than shown as a blank label.
    */
   @Test
   void testSingleTimeInfoSkipsNullValue() throws Exception {
      SingleTimeInfo tinfo = new SingleTimeInfo();
      tinfo.setRangeTypeValue(TimeInfo.MEMBER);
      tinfo.setDataRef(createRef(MEASURE));
      assembly.setTimeInfo(tinfo);

      query.refreshSelectionValue(createTable(new Object[][] {
         { MEASURE },
         { "a" },
         { null },
         { "b" }
      }));

      assertEquals(List.of("a", "b"), getSelectionValues());
   }

   /**
    * A composite slider that happens to have a single ref is a different binding (e.g. a
    * plain string dimension dragged onto a range slider), and still lists null members.
    */
   @Test
   void testCompositeTimeInfoWithSingleRefKeepsNullValue() throws Exception {
      CompositeTimeInfo tinfo = new CompositeTimeInfo();
      tinfo.setDataRefs(new DataRef[]{ createRef(MEASURE) });
      assembly.setTimeInfo(tinfo);

      query.refreshSelectionValue(createTable(new Object[][] {
         { MEASURE },
         { "a" },
         { null },
         { "b" }
      }));

      assertEquals(List.of("a", CoreTool.FAKE_NULL, "b"), getSelectionValues());
   }

   /**
    * A null in one column of a multi-ref composite slider never dropped the row.
    */
   @Test
   void testCompositeTimeInfoWithMultipleRefsKeepsNullValue() throws Exception {
      CompositeTimeInfo tinfo = new CompositeTimeInfo();
      tinfo.setDataRefs(new DataRef[]{ createRef(MEASURE), createRef(OTHER) });
      assembly.setTimeInfo(tinfo);

      query.refreshSelectionValue(createTable(new Object[][] {
         { MEASURE, OTHER },
         { "a", "x" },
         { null, "y" },
         { "b", "z" }
      }));

      assertEquals(List.of("a::x", CoreTool.FAKE_NULL + "::y", "b::z"), getSelectionValues());
   }

   private static ColumnRef createRef(String name) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType(XSchema.STRING);

      return ref;
   }

   private static XTable createTable(Object[][] data) {
      return new DefaultTableLens(data);
   }

   /**
    * Get the values of the refreshed selection list, ignoring the artificial end value
    * that is appended when the upper bound is exclusive.
    */
   private List<String> getSelectionValues() {
      SelectionList slist = assembly.getSelectionList();
      List<String> values = new ArrayList<>();

      for(int i = 0; i < slist.getSelectionValueCount(); i++) {
         SelectionValue value = slist.getSelectionValue(i);

         if(!(value instanceof SelectionValue.UpperExclusiveEndValue)) {
            values.add(value.getValue());
         }
      }

      return values;
   }

   private static final String SLIDER = "RangeSlider1";
   private static final String MEASURE = "Measure";
   private static final String OTHER = "Other";
}
