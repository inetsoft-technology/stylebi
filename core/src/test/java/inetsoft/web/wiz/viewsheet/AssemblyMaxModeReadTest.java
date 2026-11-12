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
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.MaxModeSupportAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.wiz.viewsheet.model.AssemblyNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81240: an assembly's Maximize ("Show Enlarged") state had no read path. The read model
 * ({@code read_viewsheet_model}) and the property read ({@code get_assembly_properties}) must
 * report it for every type {@code set_assembly_maximized} accepts, from the assembly info.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class AssemblyMaxModeReadTest {
   private static final Dimension ENLARGED = new Dimension(1580, 825);

   /** One assembly of every type the write side accepts, by a readable label. */
   private static Map<String, VSAssembly> supported(Viewsheet vs) {
      Map<String, VSAssembly> all = new LinkedHashMap<>();
      all.put("table", new TableVSAssembly(vs, "table"));
      all.put("crosstab", new CrosstabVSAssembly(vs, "crosstab"));
      all.put("calc table", new CalcTableVSAssembly(vs, "calc"));
      all.put("chart", new ChartVSAssembly(vs, "chart"));
      all.put("selection list", new SelectionListVSAssembly(vs, "list"));
      all.put("selection tree", new SelectionTreeVSAssembly(vs, "tree"));
      all.put("selection container", new CurrentSelectionVSAssembly(vs, "container"));
      all.put("range slider", new TimeSliderVSAssembly(vs, "slider"));
      return all;
   }

   /** Sets the state the way the native max-mode services do: on the assembly info. */
   private static void setMaxSize(VSAssembly assembly, Dimension size) {
      Object info = assembly.getVSAssemblyInfo();

      if(info instanceof ChartVSAssemblyInfo chart) {
         chart.setMaxSize(size);
      }
      else if(info instanceof TableDataVSAssemblyInfo table) {
         table.setMaxSize(size);
      }
      else {
         ((MaxModeSupportAssemblyInfo) info).setMaxSize(size);
      }
   }

   private static AssemblyNode nodeOf(Viewsheet vs, String name) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return new ViewsheetReadService().read(rvs).assemblies().stream()
         .filter(a -> name.equals(a.name())).findFirst().orElseThrow();
   }

   private static Map<String, Object> propertiesOf(VSAssembly assembly) {
      Map<String, Object> out = new LinkedHashMap<>();
      AssemblyPropertyService.putMaxMode(out, assembly);
      return out;
   }

   /** The read set must equal the write set: no supported type may be missed by the reader. */
   @Test
   void everyTypeTheWriterAcceptsReadsAsEnlargedAndAsRestored() {
      Viewsheet vs = new Viewsheet();

      for(Map.Entry<String, VSAssembly> e : supported(vs).entrySet()) {
         VSAssembly assembly = e.getValue();
         vs.addAssembly(assembly);
         String label = e.getKey();

         assertTrue(AssemblyMaxModeService.supportsMaxMode(assembly), label);
         assertNull(AssemblyMaxModeService.maxSizeOf(assembly), label + " restored");
         assertFalse(nodeOf(vs, assembly.getName()).maximized(), label + " restored node");
         assertEquals(Map.of("maximized", false), propertiesOf(assembly),
                      label + " restored props");

         setMaxSize(assembly, ENLARGED);

         AssemblyNode node = nodeOf(vs, assembly.getName());
         assertTrue(node.maximized(), label + " enlarged node");
         assertEquals(new AssemblyNode.MaxSize(1580, 825), node.maxSize(), label);
         assertEquals(
            Map.of("maximized", true, "maxSize", Map.of("width", 1580, "height", 825)),
            propertiesOf(assembly), label + " enlarged props");

         setMaxSize(assembly, null);
         assertFalse(nodeOf(vs, assembly.getName()).maximized(), label + " restored again");
      }
   }

   /** A type with no Maximize state reads false/null on a node, and has no property keys. */
   @Test
   void unsupportedTypeIsNotEnlargedAndHasNoMaximizeProperties() {
      Viewsheet vs = new Viewsheet();
      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "Gauge1");
      vs.addAssembly(gauge);

      assertFalse(AssemblyMaxModeService.supportsMaxMode(gauge));
      assertNull(AssemblyMaxModeService.maxSizeOf(gauge));
      assertNull(AssemblyMaxModeService.maxSizeOf(null));
      assertFalse(nodeOf(vs, "Gauge1").maximized());
      assertNull(nodeOf(vs, "Gauge1").maxSize());
      assertTrue(propertiesOf(gauge).isEmpty());
   }

   /**
    * A chart's maxSize is saved with the sheet, but {@code Viewsheet.isMaxMode()} is not, so a
    * reopened sheet must read the chart as enlarged from its info alone.
    */
   @Test
   void chartStillEnlargedAfterSaveAndReopenReadsAsEnlarged() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "C1");
      vs.addAssembly(chart);
      setMaxSize(chart, ENLARGED);

      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<viewsheet>");
      vs.writeXML(writer);
      writer.println("</viewsheet>");
      writer.flush();
      Viewsheet reloaded = new Viewsheet();
      reloaded.parseXML(Tool.parseXML(new ByteArrayInputStream(
         buffer.toString().getBytes(StandardCharsets.UTF_8))).getDocumentElement(), false);

      assertFalse(reloaded.isMaxMode());
      AssemblyNode node = nodeOf(reloaded, "C1");
      assertTrue(node.maximized());
      assertEquals(new AssemblyNode.MaxSize(1580, 825), node.maxSize());
   }

   /**
    * A selection list inside an enlarged container reports its own state; the container node
    * carries the enlarged one.
    */
   @Test
   void selectionListInsideEnlargedContainerReportsItsOwnState() {
      Viewsheet vs = new Viewsheet();
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "Container1");
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "List1");
      vs.addAssembly(container);
      vs.addAssembly(list);
      setMaxSize(container, ENLARGED);

      assertTrue(nodeOf(vs, "Container1").maximized());
      assertFalse(nodeOf(vs, "List1").maximized());
   }
}
