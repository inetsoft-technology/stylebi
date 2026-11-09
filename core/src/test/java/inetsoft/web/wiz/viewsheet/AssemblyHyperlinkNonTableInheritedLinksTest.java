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

import inetsoft.graph.data.DataSet;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.uql.DrillPath;
import inetsoft.uql.XDrillInfo;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.web.wiz.viewsheet.AssemblyHyperlinkService.Region;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #80805: get_hyperlink reported the logical-model Auto Drill paths (inheritedLinks) only for
 * table, crosstab and freehand cells, so a chart field, Text or Gauge bound to the same attribute
 * read back as "no link" while the Viewer showed the drill menu. The axis these tests vary over
 * is the assembly type (chart addressed by colName, Text, Gauge) against the table baseline.
 */
@Tag("core")
class AssemblyHyperlinkNonTableInheritedLinksTest {
   /** Mocks: the real infos need the Spring context to construct. */
   private static TextVSAssemblyInfo textInfo(XDrillInfo drill) {
      TextVSAssemblyInfo info = mock(TextVSAssemblyInfo.class);
      when(info.getXDrillInfo()).thenReturn(drill);
      return info;
   }

   private static GaugeVSAssemblyInfo gaugeInfo(XDrillInfo drill) {
      GaugeVSAssemblyInfo info = mock(GaugeVSAssemblyInfo.class);
      when(info.getXDrillInfo()).thenReturn(drill);
      return info;
   }

   private static DrillPath webPath(String name, String link) {
      DrillPath path = new DrillPath(name);
      path.setLinkType(DrillPath.WEB_LINK);
      path.setLink(link);
      path.setParameterField("id", "CUSTOMER_ID");
      path.setParameterType("id", "string");
      return path;
   }

   private static XDrillInfo drill(DrillPath... paths) {
      XDrillInfo info = new XDrillInfo();

      for(DrillPath path : paths) {
         info.addDrillPath(path);
      }

      return info;
   }

   /** A chart data set whose column 1 is "Customer" and carries the given drill info. */
   private static VSDataSet chartData(XDrillInfo customerDrill) {
      TableLens lens = mock(TableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(3);
      when(lens.moreRows(1)).thenReturn(true);
      when(lens.getXDrillInfo(1, 1)).thenReturn(customerDrill);

      VSDataSet data = mock(VSDataSet.class);
      when(data.getTable()).thenReturn(lens);
      when(data.getColCount()).thenReturn(3);
      when(data.getHeader(0)).thenReturn("Region");
      when(data.getHeader(1)).thenReturn("Customer");
      when(data.getHeader(2)).thenReturn("Sum(Total)");
      when(data.indexOfHeader(anyString())).thenReturn(-1);
      when(data.indexOfHeader("Region")).thenReturn(0);
      when(data.indexOfHeader("Customer")).thenReturn(1);
      when(data.indexOfHeader("Sum(Total)")).thenReturn(2);
      return data;
   }

   private static RuntimeViewsheet session(String name, VSAssembly assembly, Object data)
      throws Exception
   {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssembly(name)).thenReturn(assembly);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      if(data instanceof Exception e) {
         when(box.getData(name)).thenThrow(e);
      }
      else {
         when(box.getData(name)).thenReturn(data);
      }

      return rvs;
   }

   private static Region field(String colName) {
      return new Region(0, null, colName, false, false, false, false);
   }

   @Test
   void chartFieldReportsItsDrillPaths() {
      VSDataSet data = chartData(drill(webPath("Search", "https://example.com/q")));

      List<Map<String, Object>> links =
         AssemblyHyperlinkService.chartInheritedLinks(data, "Customer");

      assertEquals(1, links.size());
      assertEquals("dataModel", links.get(0).get("source"));
      assertEquals("Search", links.get(0).get("name"));
      assertEquals("web", links.get(0).get("linkType"));
      assertEquals("https://example.com/q", links.get(0).get("target"));
      Map<?, ?> param = (Map<?, ?>) ((List<?>) links.get(0).get("parameters")).get(0);
      assertEquals("CUSTOMER_ID", param.get("field"));
   }

   @Test
   void chartFieldNameMatchesIgnoringCase() {
      VSDataSet data = chartData(drill(webPath("Search", "https://example.com/q")));

      assertEquals(1, AssemblyHyperlinkService.chartInheritedLinks(data, "customer").size());
   }

   @Test
   void chartFieldWithoutDrillIsEmptyAndUnknownFieldIsNull() {
      VSDataSet data = chartData(null);

      assertTrue(AssemblyHyperlinkService.chartInheritedLinks(data, "Customer").isEmpty());
      assertTrue(AssemblyHyperlinkService.chartInheritedLinks(data, "Region").isEmpty());
      assertNull(AssemblyHyperlinkService.chartInheritedLinks(data, "NoSuchField"));
   }

   @Test
   void chartDataThatIsNotAViewsheetDataSetReportsNothing() {
      assertNull(AssemblyHyperlinkService.chartInheritedLinks(mock(DataSet.class), "Customer"));
   }

   @Test
   void chartReadThroughTheSandboxReportsTheNamedFieldsDrill() throws Exception {
      VSDataSet data = chartData(drill(webPath("Search", "https://example.com/q")));
      RuntimeViewsheet rvs = session("Chart1", mock(ChartVSAssembly.class), data);

      List<Map<String, Object>> links =
         AssemblyHyperlinkService.nonTableInheritedLinks(rvs, "Chart1", field("Customer"));

      assertNotNull(links);
      assertEquals("Search", links.get(0).get("name"));
   }

   @Test
   void chartReadWithoutAFieldIsNotGuessed() throws Exception {
      VSDataSet data = chartData(drill(webPath("Search", "https://example.com/q")));
      RuntimeViewsheet rvs = session("Chart1", mock(ChartVSAssembly.class), data);

      assertNull(AssemblyHyperlinkService.nonTableInheritedLinks(rvs, "Chart1", Region.whole()));
      assertNull(AssemblyHyperlinkService.nonTableInheritedLinks(
         rvs, "Chart1", new Region(0, null, "Customer", false, false, true, false)));
      assertNull(AssemblyHyperlinkService.nonTableInheritedLinks(
         rvs, "Chart1", new Region(0, null, "Customer", false, false, false, true)));
   }

   @Test
   void unrenderedChartOmitsInheritedLinksInsteadOfFailing() throws Exception {
      RuntimeViewsheet rvs = session("Chart1", mock(ChartVSAssembly.class),
                                     new IllegalStateException("bound table not found"));

      assertNull(AssemblyHyperlinkService.nonTableInheritedLinks(rvs, "Chart1", field("Customer")));
   }

   @Test
   void textReportsItsDrillPaths() throws Exception {
      TextVSAssemblyInfo info = textInfo(drill(webPath("Search", "https://example.com/q")));
      TextVSAssembly text = mock(TextVSAssembly.class);
      when(text.getVSAssemblyInfo()).thenReturn(info);
      RuntimeViewsheet rvs = session("Text1", text, "Acme");

      List<Map<String, Object>> links =
         AssemblyHyperlinkService.nonTableInheritedLinks(rvs, "Text1", Region.whole());

      assertEquals(1, links.size());
      assertEquals("Search", links.get(0).get("name"));
   }

   @Test
   void textDropsABareHyperlinkColumnWebLinkLikeTheViewer() {
      TextVSAssemblyInfo info = textInfo(drill(webPath("Bound", "hyperlink:CUSTOMER"),
                                         webPath("Kept", "https://example.com/q")));

      List<Map<String, Object>> links = AssemblyHyperlinkService.outputInheritedLinks(info);

      assertEquals(1, links.size());
      assertEquals("Kept", links.get(0).get("name"));
   }

   @Test
   void textWithoutDrillInfoIsEmpty() throws Exception {
      TextVSAssembly text = mock(TextVSAssembly.class);
      TextVSAssemblyInfo info = textInfo(null);
      when(text.getVSAssemblyInfo()).thenReturn(info);
      RuntimeViewsheet rvs = session("Text1", text, null);

      assertTrue(AssemblyHyperlinkService.nonTableInheritedLinks(
         rvs, "Text1", Region.whole()).isEmpty());
   }

   @Test
   void gaugeReportsItsDrillPaths() throws Exception {
      GaugeVSAssemblyInfo info = gaugeInfo(drill(webPath("Search", "https://example.com/q")));
      GaugeVSAssembly gauge = mock(GaugeVSAssembly.class);
      when(gauge.getVSAssemblyInfo()).thenReturn(info);
      RuntimeViewsheet rvs = session("Gauge1", gauge, 5.0);

      List<Map<String, Object>> links =
         AssemblyHyperlinkService.nonTableInheritedLinks(rvs, "Gauge1", Region.whole());

      assertEquals(1, links.size());
      assertEquals("web", links.get(0).get("linkType"));
   }

   @Test
   void unrenderedGaugeOmitsInheritedLinks() throws Exception {
      GaugeVSAssembly gauge = mock(GaugeVSAssembly.class);
      GaugeVSAssemblyInfo info = gaugeInfo(null);
      when(gauge.getVSAssemblyInfo()).thenReturn(info);
      RuntimeViewsheet rvs = session("Gauge1", gauge, new IllegalStateException("no table"));

      assertNull(AssemblyHyperlinkService.nonTableInheritedLinks(rvs, "Gauge1", Region.whole()));
   }

   @Test
   void aTableIsLeftToTheTableReader() throws Exception {
      RuntimeViewsheet rvs = session("Table1", mock(TableVSAssembly.class), null);

      assertNull(AssemblyHyperlinkService.nonTableInheritedLinks(rvs, "Table1", Region.whole()));
   }
}
