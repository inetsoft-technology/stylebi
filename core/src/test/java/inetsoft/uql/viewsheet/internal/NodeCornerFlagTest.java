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

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.viewsheet.ChartVSAScriptable;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.ChartAdvancedPaneModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A tree corner saved by a build that had no corner attribute is the author's square corner; a
 * stored flag is read as is; a modern chart seeds the modern corner, and only the reset copy
 * replaces an author's corner.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class NodeCornerFlagTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aReleasedFilesTreeIsTheAuthors() throws Exception {
      ChartVSAssemblyInfo info = parseInfo(releasedXml(true, null));
      assertInstanceOf(RelationVSChartInfo.class, info.getVSChartInfo(), "premise: a tree");
      assertEquals(0, corner(info), 1e-9);
      assertTrue(plot(info).isUserNodeCornerRadius());
   }

   @Test
   void aReleasedNonTreeChartIsFlaggedToo() throws Exception {
      ChartVSAssemblyInfo info = parseInfo(releasedXml(false, null));
      assertEquals(GraphTypes.CHART_BAR, info.getVSChartInfo().getChartType(), "premise: a bar");
      assertEquals(0, corner(info), 1e-9);
      assertTrue(plot(info).isUserNodeCornerRadius());

      // changed into a tree on a classic dashboard, it keeps square nodes
      info.setVSChartInfo(tree());
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(0, corner(info), 1e-9);
   }

   @Test
   void aDefaultCornerIsNoOpinion() throws Exception {
      ChartVSAssemblyInfo info = parseInfo(releasedXml(true, "0.3"));
      assertEquals(PlotDescriptor.DEFAULT_NODE_CORNER_RADIUS, corner(info), 1e-9);
      assertFalse(plot(info).isUserNodeCornerRadius());
   }

   @Test
   void anotherCornerIsTheAuthors() throws Exception {
      ChartVSAssemblyInfo info = parseInfo(releasedXml(true, "0.2"));
      assertEquals(0.2, corner(info), 1e-9);
      assertTrue(plot(info).isUserNodeCornerRadius());
   }

   @Test
   void aStoredFlagIsReadAsIs() throws Exception {
      String xml = releasedXml(true, "0.08")
         .replace("<plotDescriptor ", "<plotDescriptor userNodeCornerRadius=\"false\" ");
      assertTrue(xml.contains("userNodeCornerRadius=\"false\""), "premise: the flag is stored");
      ChartVSAssemblyInfo info = parseInfo(xml);
      assertEquals(0.08, corner(info), 1e-9);
      assertFalse(plot(info).isUserNodeCornerRadius());
   }

   @Test
   void aReleasedFileResavesWithoutAFlag() throws Exception {
      String released = releasedXml(true, "0.2");
      assertFalse(released.contains("userNodeCornerRadius"), "premise: no flag in the file");

      ChartVSAssemblyInfo info = parseInfo(released);
      assertTrue(plot(info).isUserNodeCornerRadius(), "premise: derived as the author's");

      String saved = writeXml(info);
      assertFalse(saved.contains("userNodeCornerRadius"));
      assertTrue(saved.contains(" nodeCornerRadius=\"0.2\""));
   }

   @Test
   void aNewModernChartSeeds008() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      ChartVSAssemblyInfo info = newChart();
      assertNotNull(info.getVizMark(), "premise: a marked chart");
      assertEquals(VSChartChromeDefaults.NODE_CORNER_RADIUS, corner(info), 1e-9);
      assertEquals(0.08, VSChartChromeDefaults.NODE_CORNER_RADIUS, 1e-9);
      assertFalse(plot(info).isUserNodeCornerRadius());

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(PlotDescriptor.DEFAULT_NODE_CORNER_RADIUS, corner(info), 1e-9);
   }

   @Test
   void aNewClassicChartSeeds03() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      ChartVSAssemblyInfo info = newChart();
      assertNull(info.getVizMark(), "premise: a classic chart");
      assertEquals(0.3, PlotDescriptor.DEFAULT_NODE_CORNER_RADIUS, 1e-9);
      assertEquals(PlotDescriptor.DEFAULT_NODE_CORNER_RADIUS, corner(info), 1e-9);
      assertFalse(plot(info).isUserNodeCornerRadius());
   }

   @Test
   void keepAndRevertKeepAReleasedTreeSquare() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssemblyInfo info = load(vs, releasedXml(true, null));
      assertNull(info.getVizMark(), "premise: a classic chart");

      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      VizModernizeUtil.modernize(vs);
      assertEquals(VizMark.MODERN_LIGHT, info.getVizMark(), "premise: modernized");
      assertEquals(0, corner(info), 1e-9);
      assertTrue(plot(info).isUserNodeCornerRadius());

      VizModernizeUtil.revert(vs);
      assertNull(info.getVizMark(), "premise: reverted");
      assertEquals(0, corner(info), 1e-9);
      assertTrue(plot(info).isUserNodeCornerRadius());
   }

   @Test
   void theResetCopyRoundsAReleasedTree() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssemblyInfo info = load(vs, releasedXml(true, null));
      assertTrue(plot(info).isUserNodeCornerRadius(), "premise: the author's square corner");

      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      VizModernizeUtil.resetToModern(vs);

      assertFalse(plot(info).isUserNodeCornerRadius());
      assertEquals(VSChartChromeDefaults.NODE_CORNER_RADIUS, corner(info), 1e-9);
   }

   @Test
   void aClassicChartWritesWhatItAlwaysWrote() throws Exception {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(tree());
      assertNull(info.getVizMark(), "premise: a classic chart");

      String first = writeXml(info);
      String second = writeXml(parseInfo(first));
      String third = writeXml(parseInfo(second));

      assertEquals(second, third);
      assertFalse(first.contains("userNodeCornerRadius"));
      assertFalse(second.contains("userNodeCornerRadius"));
      assertTrue(second.contains(" nodeCornerRadius=\"0.3\""));

      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      ChartVSAssemblyInfo modern = newChart();
      modern.setVSChartInfo(tree());
      assertNotNull(modern.getVizMark(), "premise: a marked chart");
      String seeded = writeXml(modern);

      assertTrue(seeded.contains("userNodeCornerRadius=\"false\""));
      assertTrue(seeded.contains(" nodeCornerRadius=\"0.08\""));
      assertFalse(plot(parseInfo(seeded)).isUserNodeCornerRadius());
   }

   @Test
   void anApplyWithoutAChangeDoesNotStamp() {
      for(String gate : new String[] { "false", "true" }) {
         SreeEnv.setProperty("viewsheet.modernVisualization", gate);
         ChartVSAssemblyInfo info = newChart();
         info.setVSChartInfo(tree());
         double before = corner(info);

         ChartAdvancedPaneModel pane = new ChartAdvancedPaneModel(info);
         pane.updateChartAdvancedPaneModel(info);

         assertEquals(before, corner(info), 1e-9, gate);
         assertFalse(plot(info).isUserNodeCornerRadius(), gate);
      }
   }

   @Test
   void aPaneChangeStamps() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(tree());

      ChartAdvancedPaneModel pane = new ChartAdvancedPaneModel(info);
      pane.getChartPlotOptionsPaneModel().setNodeCornerRadius(0.2);
      pane.updateChartAdvancedPaneModel(info);

      assertEquals(0.2, corner(info), 1e-9);
      assertTrue(plot(info).isUserNodeCornerRadius());

      // Revert keeps it
      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(0.2, corner(info), 1e-9);
   }

   @Test
   void aScriptAssignmentStamps() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vsTree");
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVSChartInfo(tree());

      ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
      when(sandbox.getID()).thenReturn("vsTree");
      when(sandbox.getViewsheet()).thenReturn(vs);
      ChartVSAScriptable scriptable = new ChartVSAScriptable(sandbox);
      scriptable.setAssembly("Chart1");

      scriptable.putMember("nodeCornerRadius", 0.2);

      PlotDescriptor runtime = scriptable.getRTChartDescriptor().getPlotDescriptor();
      assertEquals(0.2, runtime.getNodeCornerRadius(), 1e-9);
      assertTrue(runtime.isUserNodeCornerRadius());
   }

   // a classic chart written, then edited into what a build without the corner attribute wrote,
   // or into one holding the given corner
   private static String releasedXml(boolean tree, String radius) {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      ChartVSAssemblyInfo info = newChart();

      if(tree) {
         info.setVSChartInfo(tree());
      }
      else {
         info.getVSChartInfo().setChartType(GraphTypes.CHART_BAR);
      }

      String xml = writeXml(info);
      String attr = " nodeCornerRadius=\"0.3\"";
      assertTrue(xml.contains(attr), "premise: the corner is written");
      assertFalse(xml.contains("userNodeCornerRadius"), "premise: no flag is written");
      return xml.replace(attr, radius == null ? "" : " nodeCornerRadius=\"" + radius + "\"");
   }

   // a saved chart opened on a dashboard
   private static ChartVSAssemblyInfo load(Viewsheet vs, String xml) throws Exception {
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return info;
   }

   private static PlotDescriptor plot(ChartVSAssemblyInfo info) {
      return info.getChartDescriptor().getPlotDescriptor();
   }

   private static double corner(ChartVSAssemblyInfo info) {
      return plot(info).getNodeCornerRadius();
   }

   // parent to child
   private static RelationVSChartInfo tree() {
      RelationVSChartInfo tree = new RelationVSChartInfo();
      tree.setSourceField(dim("parent"));
      tree.setTargetField(dim("child"));
      tree.setChartType(GraphTypes.CHART_TREE);
      return tree;
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, name));
      dim.setGroupColumnValue(name);
      dim.setDataType(XSchema.STRING);
      return dim;
   }

   private static ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   private static ChartVSAssemblyInfo parseInfo(String xml) throws Exception {
      ChartVSAssemblyInfo restored = new ChartVSAssemblyInfo();
      restored.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return restored;
   }

   private static String writeXml(ChartVSAssemblyInfo info) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return buf.toString();
   }
}
