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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.filter.*;
import inetsoft.test.*;
import inetsoft.test.dc.DcInMemoryViewsheetFixture;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.ThreadContext;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.controller.table.CrosstabDrillService;
import inetsoft.web.viewsheet.event.table.DrillEvent;
import inetsoft.web.viewsheet.event.table.ImmutableDrillEvent;
import inetsoft.web.viewsheet.handler.crosstab.CrosstabDrillHandler;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.report.composition.execution.CrosstabVSAQuerySupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drill down and up of a viewsheet crosstab through the drill service, over an in-memory
 * worksheet table.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  DcInMemoryViewsheetFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
public class CrosstabDrillServiceTest {
   private final DcInMemoryViewsheetFixture fixture = new DcInMemoryViewsheetFixture();

   @BeforeEach
   void setUp() {
      fixture.setUp();
   }

   @AfterEach
   void tearDown() {
      fixture.tearDown();
   }

   private DcInMemoryViewsheetFixture.RuntimeSheet open(List<Object[]> rows) throws Exception {
      return fixture.openRuntime(NAMES, TYPES, rows);
   }

   // ---- drill down then drill up, through the drill service with a renamed header ----

   /** The crosstab drill service over the runtime viewsheet of the sheet, queries run for real. */
   private static void drill(DcInMemoryViewsheetFixture.RuntimeSheet sheet, String name,
                             String field, String op, int row) throws Exception
   {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(eq(sheet.rvs().getID()), any())).thenReturn(sheet.rvs());
      CoreLifecycleService lifecycle = mock(CoreLifecycleService.class);
      when(lifecycle.createList(anyBoolean(), any(), any(), any()))
         .thenAnswer(inv -> new ChangedAssemblyList());
      CrosstabDrillHandler handler = new CrosstabDrillHandler(
         viewsheetService, lifecycle, mock(RuntimeViewsheetRef.class));
      // processDrill is protected: a subclass exposes it so a failure is not wrapped
      var service = new CrosstabDrillService(
         lifecycle, viewsheetService, handler, mock(VSBindingService.class))
      {
         void drillForTest(String id, DrillEvent event) throws Exception {
            processDrill(id, event, ThreadContext.getPrincipal(), mock(CommandDispatcher.class),
                         null, false);
         }
      };
      service.drillForTest(sheet.rvs().getID(),
                    ImmutableDrillEvent.builder().assemblyName(name).row(row).col(0).drillOp(op)
                       .direction("y").field(field).build());
   }

   @Test
   void drillingARenamedDateHeaderDownAndUpKeepsTheNameAndRestoresTheCrosstab() throws Exception {
      List<Object[]> rows = new ArrayList<>();

      for(int q = 0; q < 8; q++) {
         rows.add(new Object[] { "E", "p1", "g1", 1 + q, 1.0,
                                 Date.valueOf(LocalDate.of(2023 + q / 4, 1 + (q % 4) * 3, 5)) });
      }

      var sheet = open(rows);
      CrosstabVSAssembly x = add(sheet, "Drilled",
                                 new VSDimensionRef[] { dateDim("Day", XConstants.YEAR_DATE_GROUP) },
                                 new VSDimensionRef[0], agg("Qty", AggregateFormula.SUM));
      String field = ((VSDimensionRef) x.getVSCrosstabInfo().getDesignRowHeaders()[0]).getFullName();
      VSTableLens lens = sheet.box().getVSTableLens("Drilled", false);
      lens.moreRows(Integer.MAX_VALUE);
      // the rename is a message format on the header cell's data path, as the binding editor
      // saves it
      TableDataPath path = lens.getTableDataPath(0, 0);
      VSCompositeFormat rename = new VSCompositeFormat();
      rename.getUserDefinedFormat().setFormatValue(XConstants.MESSAGE_FORMAT);
      rename.getUserDefinedFormat().setFormatExtentValue("Yr");
      x.getFormatInfo().setFormat(path, rename);
      sheet.box().resetDataMap("Drilled");
      lens = sheet.box().getVSTableLens("Drilled", false);
      lens.moreRows(Integer.MAX_VALUE);
      List<List<Object>> before = body(lens);
      assertEquals("Yr", lens.getText(0, 0), "the header shows the new name");

      drill(sheet, "Drilled", field, "+", lens.getHeaderRowCount());
      assertEquals(2, x.getVSCrosstabInfo().getDesignRowHeaders().length, "a child level is added");
      VSTableLens drilled = sheet.box().getVSTableLens("Drilled", false);
      drilled.moreRows(Integer.MAX_VALUE);
      assertTrue(drilled.getRowCount() > lens.getRowCount());

      drill(sheet, "Drilled", field, "-", drilled.getHeaderRowCount());
      assertEquals(1, x.getVSCrosstabInfo().getDesignRowHeaders().length, "the child level is gone");
      VSTableLens back = sheet.box().getVSTableLens("Drilled", false);
      back.moreRows(Integer.MAX_VALUE);

      assertEquals(before, body(back));
      assertEquals("Yr", back.getText(0, 0), "the header keeps its new name after drill down and up");
   }

   @Test
   void drillingYearsOneByOneExpandsEachWithItsSubtotalAndNeverChangesTheGrandTotal()
      throws Exception
   {
      List<Object[]> rows = new ArrayList<>();
      Map<Integer, List<Integer>> quarterSums = new TreeMap<>();

      for(int q = 0; q < 8; q++) {
         int year = 2023 + q / 4;
         rows.add(new Object[] { "E", "p1", "g1", 1 + q, 1.0,
                                 Date.valueOf(LocalDate.of(year, 1 + (q % 4) * 3, 5)) });
         quarterSums.computeIfAbsent(year, y -> new ArrayList<>()).add(1 + q);
      }

      var sheet = open(rows);
      VSDimensionRef yearDim = dateDim("Day", XConstants.YEAR_DATE_GROUP);
      yearDim.setSubTotalVisibleValue("true");
      CrosstabVSAssembly x = add(sheet, "DrilledYears", new VSDimensionRef[] { yearDim },
                                 new VSDimensionRef[0], agg("Qty", AggregateFormula.SUM));
      String field = ((VSDimensionRef) x.getVSCrosstabInfo().getDesignRowHeaders()[0]).getFullName();
      VSTableLens lens = sheet.box().getVSTableLens("DrilledYears", false);
      lens.moreRows(Integer.MAX_VALUE);
      int grand = quarterSums.values().stream().flatMap(List::stream).mapToInt(i -> i).sum();

      // nothing expanded: one row per year and the grand total
      assertEquals(List.of(quarterSums.get(2023).stream().mapToDouble(i -> i).sum(),
                           quarterSums.get(2024).stream().mapToDouble(i -> i).sum(), (double) grand),
                   numbers(lens));

      drill(sheet, "DrilledYears", field, "+", lens.getHeaderRowCount());
      lens = sheet.box().getVSTableLens("DrilledYears", false);
      lens.moreRows(Integer.MAX_VALUE);
      List<Double> expected = new ArrayList<>();
      quarterSums.get(2023).forEach(v -> expected.add((double) v));
      expected.add(quarterSums.get(2023).stream().mapToDouble(i -> i).sum());
      expected.add(quarterSums.get(2024).stream().mapToDouble(i -> i).sum());
      expected.add((double) grand);
      assertEquals(sorted(expected), sorted(numbers(lens)),
                   "2023 expanded with its subtotal, 2024 collapsed without one");

      int row2024 = -1;

      for(int r = lens.getHeaderRowCount(); r < lens.getRowCount(); r++) {
         if("2024".equals(String.valueOf(lens.getObject(r, 0)))) {
            row2024 = r;
         }
      }

      assertTrue(row2024 > 0, "the collapsed year is still listed");
      drill(sheet, "DrilledYears", field, "+", row2024);
      lens = sheet.box().getVSTableLens("DrilledYears", false);
      lens.moreRows(Integer.MAX_VALUE);
      expected.clear();

      for(int year : quarterSums.keySet()) {
         quarterSums.get(year).forEach(v -> expected.add((double) v));
         expected.add(quarterSums.get(year).stream().mapToDouble(i -> i).sum());
      }

      expected.add((double) grand);
      assertEquals(sorted(expected), sorted(numbers(lens)), "both years expanded");
   }

   /** The numbers of the last column of the body rows, top to bottom. */
   private static List<Double> numbers(VSTableLens lens) {
      List<Double> values = new ArrayList<>();

      for(int r = lens.getHeaderRowCount(); r < lens.getRowCount(); r++) {
         Object value = lens.getObject(r, lens.getColCount() - 1);

         if(value instanceof Number n) {
            values.add(n.doubleValue());
         }
      }

      return values;
   }

   private static List<Double> sorted(List<Double> values) {
      List<Double> copy = new ArrayList<>(values);
      Collections.sort(copy);
      return copy;
   }
}
