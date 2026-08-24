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
package inetsoft.report.composition.graph;

import inetsoft.graph.internal.GDefaults;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.graph.AxisDescriptor;
import inetsoft.uql.viewsheet.graph.CompositeTextFormat;
import inetsoft.uql.viewsheet.internal.VSChartChromeDefaults;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.util.css.CSSParameter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
// fixChartFormat needs a live ViewsheetSandbox to reach (see VGraphPairModernPaletteTest), but it
// delegates the value-axis per-column loop to the private copyDefaultFormat(AxisDescriptor)
// overload below, so drive that one directly via reflection.
class VGraphPairAxisLabelRevertTest {
   private static final String COLUMN = "measure";

   @Test
   void revertRestoresLegacyDefaultColor() throws Exception {
      AxisDescriptor axisDesc = new AxisDescriptor();
      CompositeTextFormat colFmt = new CompositeTextFormat();
      axisDesc.setColumnLabelTextFormat(COLUMN, colFmt);

      VizContext modern = VizContext.of(VizMark.MODERN_LIGHT);
      invokeCopyDefaultFormat(axisDesc, modern);
      assertEquals(VSChartChromeDefaults.labelColor(modern), colFmt.getDefaultFormat().getColor());

      VizContext unmarked = VizContext.of((VizMark) null);
      invokeCopyDefaultFormat(axisDesc, unmarked);
      assertEquals(GDefaults.DEFAULT_TEXT_COLOR, colFmt.getDefaultFormat().getColor());
   }

   @Test
   void gateOffOverwritesPresetColorWithLegacyDefault() throws Exception {
      AxisDescriptor axisDesc = new AxisDescriptor();
      CompositeTextFormat colFmt = new CompositeTextFormat();
      Color preset = new Color(0x123456);
      colFmt.getDefaultFormat().setColor(preset);
      axisDesc.setColumnLabelTextFormat(COLUMN, colFmt);
      // establish the starting value before driving the path below, so the final assertion
      // proves a write happened rather than the DEFAULT tier's own constructor default matching
      assertEquals(preset, colFmt.getDefaultFormat().getColor());

      invokeCopyDefaultFormat(axisDesc, VizContext.of((VizMark) null));

      assertEquals(GDefaults.DEFAULT_TEXT_COLOR, colFmt.getDefaultFormat().getColor());
   }

   // invokes VGraphPair's private copyDefaultFormat(VSCompositeFormat, ArrayList, AxisDescriptor,
   // VizContext) overload, the one fixChartFormat calls for the chart-wide value axis
   private void invokeCopyDefaultFormat(AxisDescriptor axisDesc, VizContext ctx) throws Exception {
      VGraphPair pair = new VGraphPair();
      Method method = VGraphPair.class.getDeclaredMethod("copyDefaultFormat",
         VSCompositeFormat.class, ArrayList.class, AxisDescriptor.class, VizContext.class);
      method.setAccessible(true);
      method.invoke(pair, new VSCompositeFormat(), new ArrayList<CSSParameter>(), axisDesc, ctx);
   }
}
