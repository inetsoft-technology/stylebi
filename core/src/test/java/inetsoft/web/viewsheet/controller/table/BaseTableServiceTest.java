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
package inetsoft.web.viewsheet.controller.table;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
import inetsoft.report.internal.table.SpanMap;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.report.TableDataPath;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.VSFormat;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.web.composer.vs.objects.controller.VSTableService;
import inetsoft.web.viewsheet.model.table.BaseTableCellModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The two per-cell "is this cell's value a named group name" checks (span-cell creation and
 * plain-cell creation, respectively) widen from {@code SNamedGroupInfo}'s own
 * {@code getGroupValue(value) != null} to the type-agnostic {@code getGroups()} membership test.
 * By the time this runs the cell's value is already the group name the query engine computed
 * (see {@code VSDimensionRefTest}) -- this is a String membership test, not a per-cell condition
 * evaluation (the operator's stated performance concern).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BaseTableServiceTest {
   private static VSDimensionRef regionRef(String namedGroup) {
      VSDimensionRef ref = new VSDimensionRef();
      ref.setDataRef(new ColumnRef(new AttributeRef("REGION")));
      ref.setDataType(XSchema.STRING);

      if(namedGroup != null) {
         ExpertNamedGroupInfo info = new ExpertNamedGroupInfo();
         info.setGroupCondition(namedGroup, new inetsoft.uql.ConditionList());
         ref.setNamedGroupInfo(info);
      }

      return ref;
   }

   /**
    * @param spanned when true, the cell hits the span-cell-creation branch (~line 867); when
    *                false, it hits the plain-cell-creation branch (~line 936).
    */
   private static BaseTableCellModel[][] getTableCellsFor(VSDimensionRef dim, Object cellValue,
                                                           boolean spanned) throws Exception
   {
      CrosstabVSAssemblyInfo info = new CrosstabVSAssemblyInfo();

      VSTableLens lens = mock(VSTableLens.class);
      when(lens.getColCount()).thenReturn(1);
      when(lens.getObject(anyInt(), anyInt())).thenReturn(cellValue);
      when(lens.getFormat(anyInt(), anyInt(), anyInt())).thenReturn(new VSFormat());
      when(lens.getTableDataPath(anyInt(), anyInt()))
         .thenReturn(new TableDataPath(-1, TableDataPath.SUMMARY));

      SpanMap spanMap = mock(SpanMap.class);
      when(spanMap.get(anyInt(), anyInt()))
         .thenReturn(spanned ? new Rectangle(0, 0, 1, 1) : null);
      when(lens.getSpanMap(anyInt(), anyInt())).thenReturn(spanMap);

      try(MockedStatic<VSTableService> vsTableService = mockStatic(VSTableService.class)) {
         vsTableService.when(() -> VSTableService.getCrosstabCellDataRef(
               any(), any(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(dim);

         Method method = BaseTableService.class.getDeclaredMethod("getTableCells",
            inetsoft.uql.viewsheet.internal.VSAssemblyInfo.class, VSTableLens.class, int.class,
            int.class, inetsoft.report.composition.FormTableLens.class, boolean.class);
         method.setAccessible(true);
         return (BaseTableCellModel[][]) method.invoke(null, info, lens, 0, 1, null, false);
      }
   }

   @Test
   void spanCellIsMarkedGroupedWhenTheCellValueIsAnExpertGroupName() throws Exception {
      BaseTableCellModel[][] cells = getTableCellsFor(regionRef("West"), "West", true);
      assertEquals(Boolean.TRUE, cells[0][0].isGrouped());
   }

   @Test
   void spanCellIsNotMarkedGroupedForAnUnrelatedValue() throws Exception {
      BaseTableCellModel[][] cells = getTableCellsFor(regionRef("West"), "California", true);
      assertNull(cells[0][0].isGrouped());
   }

   @Test
   void plainCellIsMarkedGroupedWhenTheCellValueIsAnExpertGroupName() throws Exception {
      BaseTableCellModel[][] cells = getTableCellsFor(regionRef("West"), "West", false);
      assertEquals(Boolean.TRUE, cells[0][0].isGrouped());
   }

   @Test
   void plainCellIsNotMarkedGroupedForAnUnrelatedValue() throws Exception {
      BaseTableCellModel[][] cells = getTableCellsFor(regionRef("West"), "California", false);
      assertNull(cells[0][0].isGrouped());
   }

   @Test
   void ungroupedDimensionIsNeverMarkedGrouped() throws Exception {
      BaseTableCellModel[][] cells = getTableCellsFor(regionRef(null), "California", false);
      assertNull(cells[0][0].isGrouped());
   }
}
