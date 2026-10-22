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
package inetsoft.web.wiz.viewsheet;

import inetsoft.mv.MVManager;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.*;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79194: a calendar min/max written through {@code set_assembly_properties} moves the
 * selected dates in the same request, inside {@code ViewsheetSandbox.processChange} (the call
 * {@code VSObjectPropertyService.editObjectProperty} makes after {@code setVSAssemblyInfo}),
 * i.e. before the write returns. The calendar is bound to a real worksheet table and a real
 * sandbox runs the real selection refresh ({@code CalendarVSAQuery.refreshSelectionValue}); no
 * stub replaces {@code setRange}. This is the ordering the plugin-side disclosure
 * ({@code FilterEffectGuard}) relies on; the guard itself is run around the same write.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class,
                                  CalendarFilterNoteRealSandboxTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CalendarFilterNoteRealSandboxTest {
   @Configuration
   static class TestConfig {
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }

      @Bean
      public DataSourceRegistry dataSourceRegistry() {
         return mock(DataSourceRegistry.class);
      }

      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache(DataSourceRegistry registry) {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(registry, provider);
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "D");
      // 2023-03-13 .. 2023-12-30
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.DATE },
         new Object[][] { { "d" },
                          { java.sql.Date.valueOf("2023-03-13") },
                          { java.sql.Date.valueOf("2023-06-15") },
                          { java.sql.Date.valueOf("2023-12-30") } }));
      ws.addAssembly(table);

      vs = new Viewsheet();
      Method setBase = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBase.setAccessible(true);
      setBase.invoke(vs, ws);

      calendar = new CalendarVSAssembly(vs, "DateFilter");
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) calendar.getVSAssemblyInfo();
      info.setTableName("D");
      ColumnRef date = new ColumnRef(new AttributeRef(null, "d"));
      date.setDataType(XSchema.DATE);
      info.setDataRef(date);
      vs.addAssembly(calendar);

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, "test/Bug79194", null);
      vs.setEntry(entry);
      box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
      // loads the data range, as opening the viewsheet does
      box.getData("DateFilter");
   }

   @AfterEach
   void tearDown() {
      if(box != null) {
         box.dispose();
      }
   }

   @Test
   void minOverTheSelectionIsAlreadyMovedWhenTheGuardLooks() throws Exception {
      select("d2023-4-15", "d2023-5-30");     // 2023-05-15 .. 2023-06-30 (0-based months)
      assertArrayEquals(new String[] { "d2023-4-15", "d2023-5-30" }, calendar.getDates());

      List<String> notes = write("2023-06-01", null);

      assertArrayEquals(new String[] { "d2023-5-1", "d2023-5-30" }, calendar.getDates());
      assertEquals(1, notes.size(), notes.toString());
      assertTrue(notes.get(0).startsWith("Filter note: "), notes.get(0));
      assertTrue(notes.get(0).contains(
         "2023-05-15, 2023-06-30 became 2023-06-01, 2023-06-30"), notes.get(0));
   }

   @Test
   void minAndMaxCollapseASelectionOutsideTheNewRange() throws Exception {
      select("d2023-2-13", "d2023-2-31");     // 2023-03-13 .. 2023-03-31
      List<String> notes = write("2023-04-01", "2023-06-30");

      assertArrayEquals(new String[] { "d2023-3-1", "d2023-3-1" }, calendar.getDates());
      assertEquals(1, notes.size(), notes.toString());
      assertTrue(notes.get(0).contains("2023-03-13, 2023-03-31 became 2023-04-01, 2023-04-01"),
                 notes.get(0));
   }

   @Test
   void aMinBelowTheSelectionLeavesTheDatesAloneAndSaysNothing() throws Exception {
      select("d2023-4-15", "d2023-5-30");
      List<String> notes = write("2023-04-01", null);

      assertArrayEquals(new String[] { "d2023-4-15", "d2023-5-30" }, calendar.getDates());
      assertEquals(List.of(), notes);
   }

   private void select(String... dates) throws Exception {
      CalendarVSAssemblyInfo info =
         (CalendarVSAssemblyInfo) Tool.clone(calendar.getVSAssemblyInfo());
      info.setDates(dates);
      calendar.setVSAssemblyInfo(info);
      box.processChange("DateFilter", VSAssembly.INPUT_DATA_CHANGED, new ChangedAssemblyList());
   }

   /**
    * What VSObjectPropertyService.editObjectProperty does for the calendar: write the bounds on
    * a clone of the info, apply it, then processChange; AssemblyPropertyService.set takes the
    * guard's snapshot before and reads it after. The dates are checked before the processChange
    * to show that the write alone does not move them.
    */
   private List<String> write(String min, String max) throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      List<String> paths = new java.util.ArrayList<>();

      if(min != null) {
         paths.add("calendarAdvancedPaneModel.min");
      }

      if(max != null) {
         paths.add("calendarAdvancedPaneModel.max");
      }

      FilterEffectGuard.Snapshot snapshot =
         FilterEffectGuard.snapshot(rvs, "calendar", "DateFilter", paths);

      CalendarVSAssemblyInfo info =
         (CalendarVSAssemblyInfo) Tool.clone(calendar.getVSAssemblyInfo());
      String[] before = info.getDates().clone();
      info.setMinValue(min);
      info.setMaxValue(max);
      int hint = calendar.setVSAssemblyInfo(info);
      assertArrayEquals(before, calendar.getDates(), "setVSAssemblyInfo alone moved the dates");

      box.processChange("DateFilter", hint, new ChangedAssemblyList());

      return FilterEffectGuard.apply(snapshot, rvs, "DateFilter", new Object(), paths);
   }

   private Viewsheet vs;
   private ViewsheetSandbox box;
   private CalendarVSAssembly calendar;
}
