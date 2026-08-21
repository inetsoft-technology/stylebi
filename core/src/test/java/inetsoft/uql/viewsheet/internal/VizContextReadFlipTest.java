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

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The assertion this phase exists to make true: a marked assembly resolves modern, an unmarked one
 * resolves legacy, under the same open org gate.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VizContextReadFlipTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   /** A table on a sheet stamped under an open gate. */
   private TableVSAssemblyInfo markedTable() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Marked");
      table.getVSAssemblyInfo().initDefaultFormat();
      return (TableVSAssemblyInfo) table.getVSAssemblyInfo();
   }

   /** A table on a legacy sheet: the gate is open, but the assembly carries no mark. */
   private TableVSAssemblyInfo unmarkedTable() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Viewsheet vs = new Viewsheet();
      gateOn();
      TableVSAssembly table = new TableVSAssembly(vs, "Unmarked");
      table.getVSAssemblyInfo().initDefaultFormat();
      return (TableVSAssemblyInfo) table.getVSAssemblyInfo();
   }

   @Test
   void aMarkedInfoResolvesModern() {
      VizContext ctx = VizContext.of(markedTable());
      assertTrue(ctx.modern);
   }

   @Test
   void anUnmarkedInfoResolvesLegacyUnderTheSameOpenGate() {
      TableVSAssemblyInfo info = unmarkedTable();
      assertNull(info.getVizMark());
      assertFalse(VizContext.of(info).modern,
                  "the gate is open, but this assembly is not modern");
   }

   @Test
   void closingTheGateRevertsNothing() {
      TableVSAssemblyInfo marked = markedTable();
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertTrue(VizContext.of(marked).modern,
                 "closing the gate reverts nothing; Revert is the only route back");
   }

   @Test
   void theModelLayerNoLongerReadsTheOrgGate() throws Exception {
      // structural: the nine model classes must derive their context from the assembly they are
      // built for, not from the gate. Cheaper and more durable than constructing nine web models.
      assertEquals(0, countOfGateIn("web/viewsheet/model"),
                   "no model-layer class may call VizContext.ofGate()");
   }

   @Test
   void theExportAndPainterLayersNoLongerReadTheOrgGate() throws Exception {
      assertEquals(0, countOfGateIn("report/io/viewsheet"),
                   "export must resolve chrome per assembly, or export disagrees with view");
      assertEquals(0, countOfGateIn("report/gui/viewsheet"),
                   "the painters likewise");
   }

   @Test
   void theChartPipelineNoLongerReadsTheOrgGate() throws Exception {
      assertEquals(0, countOfGateIn("report/composition/graph"),
                   "the graph generators take their context from construction");
      assertEquals(0, countOfGateIn("uql/viewsheet/graph"),
                   "and CSSChartStyles takes it as a parameter");
   }

   @Test
   void theReportPathIsLegacyByIdentity() {
      // seven chart descriptors compare identity against LEGACY to mean "not a viewsheet chart",
      // so the report constructor must hand out that exact instance, not an equal one
      assertNotSame(VizContext.LEGACY, VizContext.of((VizMark) null),
                    "no factory may return the LEGACY instance");
   }

   @Test
   void theDialogModelsNoLongerReadTheOrgGate() throws Exception {
      assertEquals(0, countOfGateIn("web/graph/model/dialog"),
                   "a dialog opened on a legacy chart must preview legacy chrome");
      assertEquals(0, countOfGateIn("web/composer/model/vs"),
                   "likewise the chart line pane");
   }

   @Test
   void onlyTheDocumentedSitesStillReadTheOrgGate() throws Exception {
      // per-package spot checks, kept for their clearer failure messages; the tree-wide assertion
      // below is the actual guard. ofGate() survives in exactly one call site: the parameterless
      // ChartColorPaletteController bootstrap GET, which has no assembly to resolve a mark from.
      assertEquals(1, countOfGateIn("web/portal/controller"),
                   "ChartColorPaletteController is the one deliberate survivor");
      assertEquals(0, countOfGateIn("report/composition"),
                   "query and lens resolve per assembly");
      assertEquals(0, countOfGateIn("web/viewsheet/controller"),
                   "table services resolve per assembly");
   }

   @Test
   void exactlyOneDocumentedSiteStillReadsTheOrgGate() throws Exception {
      // tree-wide on purpose: per-package assertions left four of this phase's own sites uncovered,
      // and a new package could add a fifth. The survivor is named, so a regression elsewhere fails
      // even if the total happens to stay at one.
      java.util.List<String> callers = filesCallingOfGate();

      assertEquals(java.util.List.of("ChartColorPaletteController.java"), callers,
                   "ChartColorPaletteController is the one deliberate survivor: a parameterless "
                   + "bootstrap GET with no assembly in scope, returning a global swatch list");
   }

   private static java.util.List<String> filesCallingOfGate() throws Exception {
      java.util.List<String> callers = new java.util.ArrayList<>();

      for(java.nio.file.Path root : resolveMainRoots()) {
         try(java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(root)) {
            files.filter(p -> p.toString().endsWith(".java"))
               // VizContext.java declares "public static VizContext ofGate() {". Matching the bare
               // call form (rather than the qualified VizContext.ofGate(), so a static import can't
               // evade the guard) also catches that declaration line, so the declaring file is
               // excluded by name here instead of narrowing the pattern back.
               .filter(p -> !p.getFileName().toString().equals("VizContext.java"))
               .filter(p -> {
                  try {
                     return java.nio.file.Files.readAllLines(p).stream()
                        .anyMatch(l -> l.contains("ofGate()"));
                  }
                  catch(Exception ex) {
                     throw new RuntimeException(ex);
                  }
               })
               .map(p -> p.getFileName().toString())
               .forEach(callers::add);
         }
      }

      callers.sort(java.util.Comparator.naturalOrder());
      return callers;
   }

   /**
    * The src/main/java/inetsoft roots of every module that can call into core: core itself, plus
    * every submodule under utils/ (utils/ is a multi-module aggregator with no source of its own,
    * so its submodules -- inetsoft-xml-formats, inetsoft-ssl-helpers, inetsoft-storage-mapdb -- are
    * walked individually). Never returns an empty list: a guard that can silently see nothing would
    * pass vacuously, so failing to resolve the repository root throws instead.
    */
   private static java.util.List<java.nio.file.Path> resolveMainRoots() {
      java.nio.file.Path repoRoot = resolveRepoRoot();
      java.util.List<java.nio.file.Path> roots = new java.util.ArrayList<>();
      roots.add(repoRoot.resolve("core/src/main/java/inetsoft"));
      java.nio.file.Path utils = repoRoot.resolve("utils");

      if(java.nio.file.Files.isDirectory(utils)) {
         try(java.util.stream.Stream<java.nio.file.Path> children = java.nio.file.Files.list(utils)) {
            children.map(m -> m.resolve("src/main/java/inetsoft"))
               .filter(java.nio.file.Files::isDirectory)
               .forEach(roots::add);
         }
         catch(java.io.IOException ex) {
            throw new RuntimeException(ex);
         }
      }

      return roots;
   }

   private static java.nio.file.Path resolveRepoRoot() {
      // this module's surefire config pins the test working directory to
      // core/target/test-workdir (see core/pom.xml), so a candidate relative to that directory
      // is needed alongside the module-relative and repo-relative ones.
      java.nio.file.Path[] candidates = {
         java.nio.file.Paths.get(".").normalize(),        // cwd == repo root
         java.nio.file.Paths.get("..").normalize(),        // cwd == core/
         java.nio.file.Paths.get("../../..").normalize(),  // cwd == core/target/test-workdir
      };

      for(java.nio.file.Path candidate : candidates) {
         if(java.nio.file.Files.isDirectory(candidate.resolve("core/src/main/java/inetsoft"))) {
            return candidate;
         }
      }

      // throw rather than return empty/null: a caller that silently saw nothing would pass this
      // guard vacuously, which is worse than failing the build.
      throw new IllegalStateException(
         "could not resolve the repository root (no candidate contains core/src/main/java/inetsoft) "
         + "from working directory " + java.nio.file.Paths.get(".").toAbsolutePath());
   }

   private static int countOfGateIn(String relativePackagePath) throws Exception {
      // this module's surefire config pins the test working directory to
      // core/target/test-workdir (see core/pom.xml), so a candidate relative to that directory
      // is needed alongside the module-relative and repo-relative ones.
      java.nio.file.Path root = java.nio.file.Paths.get("src/main/java/inetsoft", relativePackagePath);

      if(!java.nio.file.Files.isDirectory(root)) {
         root = java.nio.file.Paths.get("core/src/main/java/inetsoft", relativePackagePath);
      }

      if(!java.nio.file.Files.isDirectory(root)) {
         root = java.nio.file.Paths.get("../../src/main/java/inetsoft", relativePackagePath);
      }

      try(java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(root)) {
         return (int) files.filter(p -> p.toString().endsWith(".java"))
            .mapToLong(p -> {
               try {
                  return java.nio.file.Files.readAllLines(p).stream()
                     .filter(l -> l.contains("VizContext.ofGate()")).count();
               }
               catch(Exception ex) {
                  throw new RuntimeException(ex);
               }
            })
            .sum();
      }
   }
}
