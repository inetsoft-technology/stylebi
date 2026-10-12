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
package inetsoft.report.io.viewsheet.pdf;

import inetsoft.report.ReportElement;
import inetsoft.report.ReportSheet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.SectionElementDef;
import inetsoft.report.internal.TextBased;
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.vslayout.PrintLayoutResolver;
import inetsoft.util.Catalog;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78132: a sheet that wraps a thin wrapper of a viewsheet with a print layout must export
 * through that layout, looking up the sandbox the root keeps for the layout owner. The print
 * layout PDF is built from the report sheets the exporter queues, so the test checks their
 * texts; writing the PDF needs the text shaping classes of inetsoft-xml-formats, which are not
 * on this module's test classpath.
 *
 * The sheets embed <tt>child</tt>, whose print layout places Text1 (CHILDCANVAS) and Text2
 * (P= the parameter set by the onLoad of the sheets that wrap it), through <tt>wrapper</tt>.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome(importResources = "/inetsoft/report/io/viewsheet/pdf/PDFVSExporterNestedLayoutTest.zip")
@Tag("slow")
@Tag("integration")
class PDFVSExporterNestedLayoutTest {
   // one level, the #76774 case: wrapper -> child
   @Test
   void directChildLayoutIsInherited() throws Exception {
      List<String> texts = exportInherited(wrapper, "Viewsheet1");
      assertTrue(texts.contains("CHILDCANVAS"), () -> "report texts: " + texts);
   }

   // nestedthin -> Thin (wrapper) -> Viewsheet1 (child)
   @Test
   void layoutIsInheritedThroughThinWrapper() throws Exception {
      List<String> texts = exportInherited(nestedThin, "Thin.Viewsheet1");
      assertTrue(texts.contains("CHILDCANVAS"), () -> "report texts: " + texts);
      assertTrue(texts.contains("P=ONLOADW"), () -> "report texts: " + texts);
      assertEquals(0, countWarnings(texts), () -> "report texts: " + texts);
   }

   // nestedtwo -> Viewsheet1 (wrapper) -> Viewsheet1 (child), the default names collide, so a
   // relative lookup on the root would find the wrapper's sandbox
   @Test
   void layoutIsInheritedWithCollidingDefaultNames() throws Exception {
      List<String> texts = exportInherited(nestedTwo, "Viewsheet1.Viewsheet1");
      assertTrue(texts.contains("CHILDCANVAS"), () -> "report texts: " + texts);
   }

   // nestedthree -> Outer (nestedthin) -> Thin (wrapper) -> Viewsheet1 (child)
   @Test
   void layoutIsInheritedThroughTwoThinWrappers() throws Exception {
      List<String> texts = exportInherited(nestedThree, "Outer.Thin.Viewsheet1");
      assertTrue(texts.contains("CHILDCANVAS"), () -> "report texts: " + texts);
   }

   // nestedgroup -> Viewsheet1 (wrappergroup) -> Viewsheet1 (childgroup), whose print layout
   // places a group container holding Text3
   @Test
   void grandchildInsideGroupIsPlacedByLayout() throws Exception {
      List<String> texts = exportInherited(nestedGroup, "Viewsheet1.Viewsheet1");
      assertTrue(texts.contains("CHILDCANVAS"), () -> "report texts: " + texts);
      assertTrue(texts.contains("GROUPEDTEXT"), () -> "report texts: " + texts);
   }

   // nestedtoperr -> Thin (wrapper) -> Viewsheet1 (child), the top sheet's onLoad throws
   @Test
   void topOnLoadFailureWarnsOnce() throws Exception {
      List<String> texts = exportInherited(nestedTopErr, "Thin.Viewsheet1");
      assertEquals(1, countWarnings(texts), () -> "report texts: " + texts);
   }

   // nestedmiderr -> Thin (wrappererr) -> Viewsheet1 (child), the intermediate's onLoad throws
   @Test
   void intermediateOnLoadFailureWarnsOnce() throws Exception {
      List<String> texts = exportInherited(nestedMidErr, "Thin.Viewsheet1");
      assertEquals(1, countWarnings(texts), () -> "report texts: " + texts);
   }

   // nestedmidcanvas -> Thin (wrappercanvas, shows a text of its own) -> Viewsheet1 (child)
   @Test
   void intermediateWithOwnContentDoesNotInherit() {
      Viewsheet vs = nestedMidCanvas.getRuntimeViewsheet().getViewsheet();
      assertNull(PrintLayoutResolver.getInheritedOwner(vs));
      assertFalse(PrintLayoutResolver.hasPrintLayout(vs));
   }

   /**
    * Export a sheet that is expected to inherit the print layout of the given owner and get the
    * texts of the one report sheet queued for it.
    */
   private List<String> exportInherited(RuntimeViewsheetExtension resource, String owner)
      throws Exception
   {
      RuntimeViewsheet rvs = resource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();

      assertEquals(owner, PrintLayoutResolver.getInheritedOwner(vs));
      assertTrue(PrintLayoutResolver.hasPrintLayout(vs), "the dialogs offer the print layout");

      AbstractVSExporter exporter = (AbstractVSExporter) AbstractVSExporter.getVSExporter(
         FileFormatInfo.EXPORT_TYPE_PDF, PortalThemesManager.getColorTheme(),
         new ByteArrayOutputStream(), false, null);

      // the layout is rendered from the sandbox the root keeps for the owner, which is the one
      // reset and refreshed for the export, not a duplicate or the intermediate wrapper's
      ViewsheetSandbox layoutBox = getInheritedLayoutSandbox((PDFVSExporter) exporter, box, vs);
      assertSame(getRootSandboxes(box).get(owner), layoutBox);
      assertNotNull(layoutBox.getViewsheet().getLayoutInfo().getPrintLayout(),
                    "the layout owner's own sandbox is used");

      exporter.setSandbox(box);
      exporter.setAssetEntry(rvs.getEntry());
      exporter.setRuntimeViewsheet(rvs);
      exporter.export(box, "Current View", 0, null);

      Field field = PDFVSExporter.class.getDeclaredField("reportList");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      List<ReportSheet> reports = (List<ReportSheet>) field.get(exporter);
      assertEquals(1, reports.size(), "the sheet should be exported through the print layout");
      return getTexts(reports.get(0));
   }

   private static ViewsheetSandbox getInheritedLayoutSandbox(PDFVSExporter exporter,
                                                             ViewsheetSandbox box, Viewsheet vs)
      throws Exception
   {
      Method method = PDFVSExporter.class.getDeclaredMethod(
         "getInheritedLayoutSandbox", ViewsheetSandbox.class, Viewsheet.class);
      method.setAccessible(true);
      return (ViewsheetSandbox) method.invoke(exporter, box, vs);
   }

   @SuppressWarnings("unchecked")
   private static Map<String, ViewsheetSandbox> getRootSandboxes(ViewsheetSandbox box)
      throws Exception
   {
      Field field = ViewsheetSandbox.class.getDeclaredField("bmap");
      field.setAccessible(true);
      return (Map<String, ViewsheetSandbox>) field.get(box);
   }

   private static List<String> getTexts(ReportSheet report) {
      List<String> texts = new ArrayList<>();

      for(Object elem : report.getAllElements()) {
         addTexts((ReportElement) elem, texts);
      }

      return texts;
   }

   private static void addTexts(ReportElement elem, List<String> texts) {
      if(elem instanceof SectionElementDef section) {
         for(ReportElement child : section.getElements()) {
            addTexts(child, texts);
         }
      }
      else if(elem instanceof TextBased text && text.getText() != null) {
         texts.add(text.getText());
      }
   }

   private static long countWarnings(List<String> texts) {
      String message = Catalog.getCatalog().getString("vs.export.onLoadScriptFailed");
      return texts.stream().filter(text -> text.contains(message)).count();
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent(String name) {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^RegXui/print/" + name);
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension wrapper =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent("wrapper"));

   @RegisterExtension
   RuntimeViewsheetExtension nestedThin =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent("nestedthin"));

   @RegisterExtension
   RuntimeViewsheetExtension nestedTwo =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent("nestedtwo"));

   @RegisterExtension
   RuntimeViewsheetExtension nestedThree =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent("nestedthree"));

   @RegisterExtension
   RuntimeViewsheetExtension nestedGroup =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent("nestedgroup"));

   @RegisterExtension
   RuntimeViewsheetExtension nestedTopErr =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent("nestedtoperr"));

   @RegisterExtension
   RuntimeViewsheetExtension nestedMidErr =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent("nestedmiderr"));

   @RegisterExtension
   RuntimeViewsheetExtension nestedMidCanvas =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent("nestedmidcanvas"));
}
