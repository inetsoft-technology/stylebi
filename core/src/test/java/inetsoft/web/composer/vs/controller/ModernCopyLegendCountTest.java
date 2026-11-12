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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.mv.MVManager;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.ThreadContext;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The reset copy is saved before the composer draws it, so its legend counts are taken first and
 * the stored copy draws where the composer shows it. TableView1 has 30 years and 2 storm types.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class ModernCopyLegendCountTest {
   private static final String LINK = "http://localhost:8080/sree/";

   @Autowired
   ViewsheetService viewsheetService;

   @BeforeEach
   void setUp() {
      principal = ThreadContext.getContextPrincipal();
      repository = AssetUtil.getAssetRepository(false);
      CoreLifecycleService lifecycle = mock(CoreLifecycleService.class);
      SaveViewsheetDialogService saveService = new SaveViewsheetDialogService(
         lifecycle, repository, viewsheetService, new ViewsheetSettingsService(viewsheetService),
         new QueryManagerService(null, null, null, null, null));
      // a mock MVManager reports no MV, so the save raises no MV confirmation
      composerService = spy(new ComposerViewsheetService(
         null, lifecycle, viewsheetService, null, null, null, null, null, mock(MVManager.class),
         mock(XSessionService.class)));
      service = new ModernCopyService(viewsheetService, lifecycle, repository,
                                      new ModernizeDialogService(viewsheetService, saveService),
                                      saveService, composerService);
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         if(copy != null && repository.containsEntry(copy)) {
            repository.removeSheet(copy, principal, true);
         }
      }
      finally {
         copy = null;
         SreeEnv.setProperty("viewsheet.modernVisualization", null);
         SreeEnv.setProperty("viewsheet.darkMode", null);
         SreeEnv.setProperty("viewsheet.density", null);
      }
   }

   @Test
   void theStoredCopyKeepsTheCountedPosition() throws Exception {
      RuntimeViewsheet rvs = composer.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      ChartVSAssemblyInfo crowded = addClassicBar(box, CROWDED, "type", "year");
      ChartVSAssemblyInfo narrow = addClassicBar(box, NARROW, "year", "type");
      // the original runtime has drawn both charts
      box.getVGraphPair(CROWDED, true, null);
      box.getVGraphPair(NARROW, true, null);
      assertNull(crowded.getVizMark(), "premise: a classic chart");
      assertEquals(LegendsDescriptor.RIGHT, layout(crowded), "premise: the classic position");
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      service.createModernCopy(composer.getRuntimeId(), "EmbeddedVS1 (modern)", principal,
                               mock(CommandDispatcher.class), LINK);

      copy = rvs.getEntry();
      assertEquals("EmbeddedVS1 (modern)", copy.getName(), "premise: the runtime is on the copy");
      Viewsheet stored = (Viewsheet) repository.getSheet(copy, principal, false, AssetContent.ALL);
      ChartVSAssemblyInfo storedCrowded = chart(stored, CROWDED);
      ChartVSAssemblyInfo storedNarrow = chart(stored, NARROW);
      assertNotNull(storedCrowded.getVizMark(), "premise: the copy is marked");
      assertEquals(LegendsDescriptor.RIGHT, layout(storedCrowded),
                   "the counted position is saved");
      assertTrue(storedCrowded.isLegendCrowded(), "the verdict is saved");
      assertEquals(LegendsDescriptor.TOP, layout(storedNarrow));
      assertFalse(storedNarrow.isLegendCrowded());

      // nothing is left for the composer's next draw to change after the save
      ChartVSAssemblyInfo live = chart(rvs.getViewsheet(), CROWDED);
      assertFalse(live.isLegendCountPending());
      assertFalse(chart(rvs.getViewsheet(), NARROW).isLegendCountPending());
      VGraphPair pair = box.getVGraphPair(CROWDED, true, null);
      assertEquals(LegendsDescriptor.RIGHT, pair.getEGraph().getLegendLayout());
   }

   @Test
   void aFailedCopyDrawsTheOriginalAsItWas() throws Exception {
      RuntimeViewsheet rvs = composer.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      AssetEntry original = rvs.getEntry();
      addClassicBar(box, NARROW, "year", "type");
      box.getVGraphPair(NARROW, true, null);
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      doAnswer(i -> {
         copy = rvs.getEntry();
         throw new IllegalStateException("forced");
      }).when(composerService).saveViewsheet(anyString(), any(), any(), any(), any());

      assertThrows(IllegalStateException.class, () -> service.createModernCopy(
         composer.getRuntimeId(), "EmbeddedVS1 (modern)", principal,
         mock(CommandDispatcher.class), LINK));

      assertEquals(original, rvs.getEntry(), "premise: the runtime is back on the original");
      ChartVSAssemblyInfo info = chart(rvs.getViewsheet(), NARROW);
      assertNull(info.getVizMark(), "premise: the original's chart is classic");
      VGraphPair pair = box.getVGraphPair(NARROW, true, null);
      assertEquals(LegendsDescriptor.RIGHT, pair.getEGraph().getLegendLayout(),
                   "the original draws as it was");
   }

   // a classic bar of Sum(wind) by the x column, with the color column on Color
   private static ChartVSAssemblyInfo addClassicBar(ViewsheetSandbox box, String name, String x,
                                                    String color)
   {
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.setSourceInfo(source);
      VSChartInfo cinfo = chart.getVSChartInfo();
      cinfo.addXField(dim(x));

      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      cinfo.addYField(agg);

      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setDataRef(dim(color));
      colorRef.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      cinfo.setColorField(colorRef);
      cinfo.setChartType(GraphTypes.CHART_BAR);

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVizMark(null);
      info.initDefaultFormat();
      info.clearLegendCountPending();
      vs.addAssembly(chart);
      return info;
   }

   private static ChartVSAssemblyInfo chart(Viewsheet vs, String name) {
      return (ChartVSAssemblyInfo) ((ChartVSAssembly) vs.getAssembly(name)).getVSAssemblyInfo();
   }

   private static int layout(ChartVSAssemblyInfo info) {
      return info.getChartDescriptor().getLegendsDescriptor().getLayout();
   }

   private static VSChartDimensionRef dim(String col) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, col)));
      dim.setGroupColumnValue(col);
      dim.setOrder(XConstants.SORT_ASC);
      return dim;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(false);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension composer = new RuntimeViewsheetExtension(openViewsheetEvent());

   private static final String TABLE = "TableView1";
   private static final String CROWDED = "ChartCrowded";
   private static final String NARROW = "ChartNarrow";
   private Principal principal;
   private AssetRepository repository;
   private ModernCopyService service;
   private ComposerViewsheetService composerService;
   private AssetEntry copy;
}
