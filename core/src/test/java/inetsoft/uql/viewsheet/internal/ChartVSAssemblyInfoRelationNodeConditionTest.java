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
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalSizeFrameWrapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78481. A relation chart's Node Color/Size dimension is left out of getRTFields()
 * (#75253, so it is not in the GROUP BY), but a node legend item selects on it. The selection
 * and condition lookups must still find it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartVSAssemblyInfoRelationNodeConditionTest {
   private ColumnSelection columns;
   private RelationVSChartInfo info;

   @BeforeEach
   void setUp() {
      columns = new ColumnSelection();
      columns.addAttribute(column("Source", XSchema.STRING));
      columns.addAttribute(column("Target", XSchema.STRING));
      columns.addAttribute(column("State", XSchema.STRING));
      columns.addAttribute(column("Date", XSchema.DATE));
      columns.addAttribute(column("Sales", XSchema.DOUBLE));

      info = new RelationVSChartInfo();
      info.setChartType(GraphTypes.CHART_NETWORK);
      info.setSourceField((ChartRef) dim("Source", XSchema.STRING));
      info.setTargetField((ChartRef) dim("Target", XSchema.STRING));
      info.setNodeColorField(aesthetic(dim("State", XSchema.STRING), false));
      info.setNodeSizeField(aesthetic(dim("Date", XSchema.DATE), true));
   }

   @Test
   void rtFieldsStillExcludeNodeDimensions() {
      update();

      // #75253: the node dimensions must stay out of the query fields
      assertEquals(List.of("Source", "Target"), names(info.getRTFields()));
      assertNull(info.getRTFieldByFullName("State"));
      assertEquals(List.of("Source", "Target", "State", "Year(Date)"),
                   names(info.getRTSelectionFields()));
   }

   @Test
   void selectionFieldsAreNotDuplicated() {
      // Color and Node Color bound to the same field
      info.setColorField(aesthetic(dim("State", XSchema.STRING), false));
      update();

      List<String> names = names(info.getRTSelectionFields());
      assertEquals(1, names.stream().filter("State"::equals).count(), names.toString());
      assertTrue(names.contains("Year(Date)"), names.toString());
   }

   @Test
   void nonRelationChartSelectionFieldsEqualRtFields() {
      VSChartInfo bar = new DefaultVSChartInfo();
      bar.setChartType(GraphTypes.CHART_BAR);
      bar.addXField(dim("State", XSchema.STRING));
      bar.setColorField(aesthetic(dim("Target", XSchema.STRING), false));
      bar.update(new Viewsheet(), columns, false, null, null, null);

      assertArrayEquals(bar.getRTFields(), bar.getRTSelectionFields());
   }

   @Test
   void nodeSelectionIsValid() {
      update();

      assertTrue(selection("State", "TX").isValid(info));
      assertTrue(selection("Year(Date)", "2021-01-01 00:00:00").isValid(info));
      assertFalse(selection("Sales", "10").isValid(info));
   }

   @Test
   void conditionOnNodeStringDimension() {
      update();
      ChartVSAssemblyInfo ainfo = new ChartVSAssemblyInfo();
      ainfo.setVSChartInfo(info);

      ConditionList conds = ainfo.getConditionList(selection("State", "TX"), columns);

      assertNotNull(conds);
      assertEquals("[State] [is] [equal to] [TX]", conds.toString());
   }

   @Test
   void conditionOnNodeDateDimension() {
      update();
      ChartVSAssemblyInfo ainfo = new ChartVSAssemblyInfo();
      ainfo.setVSChartInfo(info);

      ConditionList conds = ainfo.getConditionList(
         selection("Year(Date)", "2021-01-01 00:00:00"), columns);

      assertNotNull(conds);
      assertTrue(conds.toString().startsWith("[Year(Date)] [is] [equal to]"), conds.toString());
   }

   private void update() {
      info.update(new Viewsheet(), columns, false, null, null, null);
   }

   private static List<String> names(VSDataRef[] refs) {
      return Arrays.stream(refs).map(VSDataRef::getFullName).collect(Collectors.toList());
   }

   private static VSSelection selection(String field, String value) {
      VSPoint point = new VSPoint();
      point.addValue(new VSFieldValue(field, value));
      VSSelection selection = new VSSelection();
      selection.addPoint(point);
      return selection;
   }

   private static ColumnRef column(String name, String type) {
      ColumnRef col = new ColumnRef(new AttributeRef(null, name));
      col.setDataType(type);
      return col;
   }

   private static ChartRef dim(String col, String dtype) {
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue(col);
      dim.setDataType(dtype);

      if(XSchema.isDateType(dtype)) {
         dim.setDateLevelValue(String.valueOf(DateRangeRef.YEAR_INTERVAL));
      }

      return dim;
   }

   private static VSAestheticRef aesthetic(ChartRef ref, boolean size) {
      VSAestheticRef aes = new VSAestheticRef();
      aes.setDataRef(ref);
      aes.setVisualFrameWrapper(size ? new CategoricalSizeFrameWrapper() :
                                   new CategoricalColorFrameWrapper());
      return aes;
   }
}
