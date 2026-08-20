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
package inetsoft.web.viewsheet.model;

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.table.XTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.viewsheet.model.table.VSTableModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The model carries the resolved context, never the mark. P5's central contract: the browser is told
 * "is this assembly modern", not "what mark does it hold", so the gate term that lives on VizContext
 * until P6 is applied exactly once, on the server.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                   LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class VSObjectModelVizContextTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   @Test
   void resolvedContextMatchesTheModelFields() {
      // an unmarked assembly is never modern, whatever the gate says
      VizContext unmarked = VizContext.of((VizMark) null);
      assertFalse(unmarked.modern, "unmarked resolves legacy");
      assertFalse(unmarked.dark, "dark is never true without modern");
   }

   @Test
   void darkNeverTrueWithoutModern() {
      // structural invariant the model inherits by copying ctx.dark rather than recomputing it
      for(VizMark mark : VizMark.values()) {
         VizContext ctx = VizContext.of(mark);

         if(!ctx.modern) {
            assertFalse(ctx.dark, "dark must not survive a legacy resolution for " + mark);
         }
      }
   }

   @Test
   void modelFieldsCopyTheContextRatherThanRecomputing() {
      // VSObjectModel's constructor assigns vizModern/vizDark from VizContext.of(assemblyInfo)
      // verbatim. Building a full VSObjectModel needs a RuntimeViewsheet that isn't cheaply
      // available in a unit test, so this asserts the same resolution the constructor delegates
      // to, directly against a real assembly info.
      Viewsheet vs = new Viewsheet();
      TextVSAssembly assembly = new TextVSAssembly(vs, "Text1");
      vs.addAssembly(assembly);
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      // gate off: even a dark-marked assembly resolves legacy
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      info.setVizMark(VizMark.MODERN_DARK);
      VizContext gateOff = VizContext.of(info);
      assertFalse(gateOff.modern, "gate off overrides the mark");
      assertFalse(gateOff.dark, "dark must not survive a legacy resolution");

      // gate on: the mark now resolves, and dark tracks MODERN_DARK specifically
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      VizContext gateOnDark = VizContext.of(info);
      assertTrue(gateOnDark.modern);
      assertTrue(gateOnDark.dark, "MODERN_DARK resolves dark once modern is true");

      info.setVizMark(VizMark.MODERN_LIGHT);
      VizContext gateOnLight = VizContext.of(info);
      assertTrue(gateOnLight.modern);
      assertFalse(gateOnLight.dark, "MODERN_LIGHT is modern but never dark");

      info.setVizMark(null);
      VizContext unmarked = VizContext.of(info);
      assertFalse(unmarked.modern, "unmarked stays legacy even with the gate on");
      assertFalse(unmarked.dark);
   }

   @Test
   void constructedModelExposesModernNotDarkForAModernLightMark() throws Exception {
      // proves the constructor assigns vizModern/vizDark, not just that VizContext resolves them:
      // modern and dark differ here, so a swap (vizModern = vizContext.dark) fails this
      VSTableModel model = buildModel(VizMark.MODERN_LIGHT);
      assertTrue(model.isVizModern());
      assertFalse(model.isVizDark());
   }

   @Test
   void constructedModelExposesModernAndDarkForAModernDarkMark() throws Exception {
      // both fields are true here, so this is what catches vizDark being left unwired
      // (defaulting to false) while vizModern is correctly assigned
      VSTableModel model = buildModel(VizMark.MODERN_DARK);
      assertTrue(model.isVizModern());
      assertTrue(model.isVizDark());
   }

   @Test
   void constructedModelExposesLegacyForAnUnmarkedAssembly() throws Exception {
      VSTableModel model = buildModel(null);
      assertFalse(model.isVizModern());
      assertFalse(model.isVizDark());
   }

   /** Builds a real {@link VSTableModel} through its factory, following {@link VSTableModelTest}'s harness. */
   private VSTableModel buildModel(VizMark mark) throws Exception {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Viewsheet viewsheet = new Viewsheet();
      TableVSAssembly assembly = new TableVSAssembly(viewsheet, "Table");
      assembly.initDefaultFormat();
      viewsheet.addAssembly(assembly);
      assembly.getVSAssemblyInfo().setVizMark(mark);

      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      VSTableLens lens = createEmptyTable();
      Mockito.when(rvs.getViewsheet()).thenReturn(viewsheet);
      Mockito.when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(sandbox));
      Mockito.when(sandbox.getVSTableLens(Mockito.anyString(), Mockito.anyBoolean()))
         .thenReturn(lens);

      VSTableModel.VSTableModelFactory factory = new VSTableModel.VSTableModelFactory();
      return factory.createModel(assembly, rvs);
   }

   private VSTableLens createEmptyTable() {
      XEmbeddedTable embedded = new XEmbeddedTable(new String[0], new Object[0][0]);
      TableLens table = new XTableLens(embedded);
      return new VSTableLens(table);
   }

   @Mock RuntimeViewsheet rvs;
   @Mock ViewsheetSandbox sandbox;
}
