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
package inetsoft.report.script.viewsheet;

import inetsoft.graph.aesthetic.TealColorFrame;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.LinearColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.TealColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A ramp a script assigns to the colour field is the author's pick.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RampScriptAuthorTest {
   @BeforeEach
   void setUp() {
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vsRamp");

      ChartVSAssembly chart = new ChartVSAssembly();
      info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setName("Chart1");
      info.setVizMark(VizMark.MODERN_LIGHT);

      VSChartDimensionRef state = new VSChartDimensionRef(new AttributeRef(null, "state"));
      VSChartAggregateRef sales = new VSChartAggregateRef();
      sales.setDataRef(new AttributeRef(null, "sales"));
      VSAestheticRef color = new VSAestheticRef();
      color.setALLDataRef(sales);
      color.setVisualFrameWrapper(new TealColorFrameWrapper());
      DefaultVSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setSeparatedGraph(false);
      cinfo.setRTXFields(new ChartRef[] { state });
      cinfo.setRTYFields(new ChartRef[] { sales });
      cinfo.setColorField(color);
      info.setVSChartInfo(cinfo);
      viewsheet.addAssembly(chart);

      ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
      when(sandbox.getID()).thenReturn("vsRamp");
      when(sandbox.getViewsheet()).thenReturn(viewsheet);

      scriptable = new ChartVSAScriptable(sandbox);
      scriptable.setAssembly("Chart1");
      scriptable.addProperties();
   }

   @Test
   void aScriptRampIsTheAuthors() {
      new VSChartBindingScriptable(scriptable).setColorFrame(new TealColorFrame());

      AestheticRef ref = scriptable.getChartInfo().getColorField();
      assertInstanceOf(TealColorFrameWrapper.class, ref.getVisualFrameWrapper());
      assertTrue(((LinearColorFrameWrapper) ref.getVisualFrameWrapper()).isAuthorRamp());
   }

   private ChartVSAssemblyInfo info;
   private ChartVSAScriptable scriptable;
}
