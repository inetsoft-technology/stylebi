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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.CurrentSelectionVSAssembly;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.viewsheet.model.AssemblyNode;
import inetsoft.util.Tool;
import inetsoft.web.wiz.viewsheet.model.ViewsheetModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Point;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Constructing a real {@link Viewsheet} runs {@code VSAssemblyInfo}'s constructor, which reads
 * {@code SreeEnv} and therefore needs a Spring context — hence the harness annotations, which
 * mirror {@code ClipboardControllerServiceTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class ViewsheetReadServiceTest {
   @Test
   void readsEveryAssemblyWithItsPositionAndType() {
      Viewsheet vs = new Viewsheet();
      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "Gauge1");
      gauge.setPixelOffset(new Point(10, 20));
      vs.addAssembly(gauge);
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      text.setPixelOffset(new Point(30, 40));
      vs.addAssembly(text);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetModel model = new ViewsheetReadService().read(rvs);

      assertEquals(2, model.assemblies().size());
      AssemblyNode first = model.assemblies().stream()
         .filter(a -> "Gauge1".equals(a.name())).findFirst().orElseThrow();
      assertEquals("Gauge", first.type());
      assertEquals(10, first.x());
      assertEquals(20, first.y());
   }

   @Test
   void reportsAnEmptyViewsheetAsNoAssembliesRatherThanFailing() {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(new Viewsheet());

      assertTrue(new ViewsheetReadService().read(rvs).assemblies().isEmpty());
   }

   /**
    * The sheet's name comes from its asset entry, not from {@code Viewsheet.getName()}.
    *
    * <p>{@code Viewsheet.getName()} is the <em>assembly</em> name — it is set only by
    * {@code createVSAssembly(name)}, i.e. when a viewsheet is embedded inside another viewsheet as
    * an assembly. For a top-level sheet nobody ever sets it, so it is legitimately null and was
    * never the right field to answer "what is this viewsheet called".
    *
    * <p>Reading it was also actively misleading, because null does not survive a save/reload:
    * {@code AssemblyInfo.writeAttributes} emits {@code "<name><![CDATA[" + name + "]]></name>"},
    * and Java string concatenation turns a null into the four characters {@code null}. So a
    * reopened sheet reports the <b>string</b> {@code "null"} — worse than a JSON null, since a
    * caller writing {@code if(name)} gets a truthy value and renders "null" to a user. Observed
    * live: an unsaved viewsheet reported {@code null}, and the saved {@code TestVS_L3} reported
    * {@code "null"}. The sibling field already carries a hand-patch for the identical flaw
    * ({@code VSAssemblyInfo} normalizes {@code "null"} back to null when parsing
    * {@code absoluteName2}), which is left alone here: repairing the shared serialization would
    * change the written XML for every assembly of every sheet, far beyond what this read needs.
    */
   @Test
   void namesTheSheetFromItsAssetEntryRatherThanTheAssemblyName() {
      Viewsheet vs = new Viewsheet();
      // What a reopened top-level sheet actually holds, per the serialization above.
      vs.createVSAssembly("null");
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getEntry()).thenReturn(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "Test/TestVS_L3", null));

      assertEquals("TestVS_L3", new ViewsheetReadService().read(rvs).name());
   }

   /** A sheet with no entry at all — never saved — reports no name rather than a fabricated one. */
   @Test
   void reportsNoNameWhenTheSheetHasNoAssetEntry() {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(new Viewsheet());
      when(rvs.getEntry()).thenReturn(null);

      assertNull(new ViewsheetReadService().read(rvs).name());
   }

   /** A Selection Container reports its children in display order; other assemblies report none. */
   @Test
   void selectionContainerReportsItsChildrenInOrder() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new SelectionListVSAssembly(vs, "ListA"));
      vs.addAssembly(new SelectionListVSAssembly(vs, "ListB"));
      CurrentSelectionVSAssembly box = new CurrentSelectionVSAssembly(vs, "Box");
      vs.addAssembly(box);
      box.setAssemblies(new String[] { "ListB", "ListA" });

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetModel model = new ViewsheetReadService().read(rvs);

      AssemblyNode node = model.assemblies().stream()
         .filter(a -> "Box".equals(a.name())).findFirst().orElseThrow();
      assertEquals(List.of("ListB", "ListA"), node.children());
      assertNull(model.assemblies().stream()
                    .filter(a -> "ListA".equals(a.name())).findFirst().orElseThrow().children());
   }

   /**
    * An embedded Viewsheet's referenced dashboard is read from {@code Viewsheet.getEntry()}, the
    * field {@code add_assembly} sets (via {@code Viewsheet.setEntry}), never from
    * {@code ViewsheetVSAssemblyInfo}, which stays empty on the live assembly.
    */
   @Test
   void embeddedViewsheetReportsItsGlobalDashboardPathAndScope() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(embed("Embed1", new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "Reports/Sales", null)));

      AssemblyNode node = nodeOf(vs, "Embed1");

      assertEquals("Viewsheet", node.type());
      assertEquals("Reports/Sales", node.embeddedPath());
      assertEquals("global", node.embeddedScope());
   }

   /** A user dashboard and a global one can share a path, so the scope must tell them apart. */
   @Test
   void embeddedViewsheetReportsUserScope() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(embed("Embed1", new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, "Reports/Sales",
         new IdentityID("alice", "host-org"))));

      AssemblyNode node = nodeOf(vs, "Embed1");

      assertEquals("Reports/Sales", node.embeddedPath());
      assertEquals("user", node.embeddedScope());
   }

   /** A scope the tools never create is named, not silently reported as global. */
   @Test
   void embeddedViewsheetNamesAnUnexpectedScopeInsteadOfDefaultingToGlobal() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(embed("Embed1", new AssetEntry(
         AssetRepository.REPORT_SCOPE, AssetEntry.Type.VIEWSHEET, "Reports/Sales", null)));
      vs.addAssembly(embed("Embed2", new AssetEntry(
         AssetRepository.REPOSITORY_SCOPE, AssetEntry.Type.VIEWSHEET, "Reports/Sales", null)));

      assertEquals("report", nodeOf(vs, "Embed1").embeddedScope());
      assertEquals("scope-" + AssetRepository.REPOSITORY_SCOPE,
                   nodeOf(vs, "Embed2").embeddedScope());
   }

   /** An embed with no entry reports null, not empty strings and not "global". */
   @Test
   void embeddedViewsheetWithoutAnEntryReportsNothing() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(embed("Embed1", null));

      AssemblyNode node = nodeOf(vs, "Embed1");

      assertNull(node.embeddedPath());
      assertNull(node.embeddedScope());
   }

   /** Only an embedded Viewsheet carries the fields. */
   @Test
   void nonEmbeddedAssembliesReportNoEmbeddedFields() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));

      AssemblyNode node = nodeOf(vs, "Text1");

      assertNull(node.embeddedPath());
      assertNull(node.embeddedScope());
   }

   /** The entry survives save and reopen, so the read-back is not session-local. */
   @Test
   void embeddedViewsheetPathAndScopeSurviveASaveAndReload() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(embed("Embed1", new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, "Reports/Sales",
         new IdentityID("alice", "host-org"))));

      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<viewsheet>");
      vs.writeXML(writer);
      writer.println("</viewsheet>");
      writer.flush();
      Viewsheet reloaded = new Viewsheet();
      reloaded.parseXML(Tool.parseXML(new ByteArrayInputStream(
         buffer.toString().getBytes(StandardCharsets.UTF_8))).getDocumentElement(), false);

      AssemblyNode node = nodeOf(reloaded, "Embed1");

      assertEquals("Reports/Sales", node.embeddedPath());
      assertEquals("user", node.embeddedScope());
   }

   /** Built the way {@code ComposerObjectService.addEmbeddedViewsheet} does. */
   private static Viewsheet embed(String name, AssetEntry entry) {
      Viewsheet embedded = new Viewsheet();
      embedded.createVSAssembly(name);
      embedded.setEntry(entry);
      return embedded;
   }

   private static AssemblyNode nodeOf(Viewsheet vs, String name) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      return new ViewsheetReadService().read(rvs).assemblies().stream()
         .filter(a -> name.equals(a.name())).findFirst().orElseThrow();
   }
}
