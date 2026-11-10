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
package inetsoft.uql.viewsheet.graph;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartDescriptorSavedDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aSwitchedOnDescriptorWritesItsRegisteredDefaultTiers() throws Exception {
      ChartDescriptor desc = new ChartDescriptor();
      List<Object> sentinels = new ArrayList<>();

      for(CompositeValue<?> field : desc.savedDefaults()) {
         sentinels.add(setSentinelDefault(field));
      }

      desc.setSaveDefaults(true);

      // a fresh descriptor reads as an older build does: its own switch is off
      ChartDescriptor read = parse(new ChartDescriptor(), xml(desc));

      assertEquals(sentinels.size(), read.savedDefaults().size());

      for(int i = 0; i < sentinels.size(); i++) {
         assertEquals(sentinels.get(i),
                      read.savedDefaults().get(i).get(CompositeValue.Type.DEFAULT), "field " + i);
         assertFalse(read.savedDefaults().get(i).hasUserValue(), "field " + i);
      }
   }

   @Test
   void aSwitchedOffDescriptorWritesWhatItAlwaysWrote() {
      ChartDescriptor desc = new ChartDescriptor();
      String before = xml(desc);
      desc.setSaveDefaults(true);
      desc.setSaveDefaults(false);

      assertEquals(before, xml(desc));
      assertFalse(before.contains("^^DEF^^"));
   }

   @Test
   void aBareValueFromAnOldFileStaysTheAuthors() throws Exception {
      ChartDescriptor old = new ChartDescriptor();
      old.savedDefaults().forEach(ChartDescriptorSavedDefaultsTest::promoteDefaultToUser);
      String oldXml = xml(old);
      assertFalse(oldXml.contains("^^DEF^^"), "precondition: bare values, as every older file has");

      ChartDescriptor read = parse(new ChartDescriptor(), oldXml);

      read.savedDefaults().forEach(v -> assertTrue(v.hasUserValue()));
   }

   @Test
   void theSwitchDoesNotChangeHowABareValueParses() {
      CompositeValue<Color> value = new CompositeValue<>(Color.class, Color.BLACK);
      value.setSaveDefault(true);

      value.parse(String.valueOf(Color.RED.getRGB()));

      assertTrue(value.hasUserValue());
      assertEquals(Color.BLACK, value.get(CompositeValue.Type.DEFAULT));
   }

   @Test
   void aCopyKeepsTheSwitchAndOwnsIt() {
      ChartDescriptor original = new ChartDescriptor();
      original.setSaveDefaults(true);

      ChartDescriptor copy = (ChartDescriptor) original.clone();
      assertTrue(xml(copy).contains("^^DEF^^"));

      copy.setSaveDefaults(false);
      assertTrue(xml(original).contains("^^DEF^^"));
      assertFalse(xml(copy).contains("^^DEF^^"));
   }

   @Test
   void aLegacyPassRestoresEveryRegisteredDefault() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      info.setVizMark(null);
      // not a transition, as a restore and the residue simulation run it
      VizModernizeUtil.reseedAfterRestore(info);
      gateOff();
      List<CompositeValue<?>> classic = newChart().getChartDescriptor().savedDefaults();
      List<CompositeValue<?>> after = info.getChartDescriptor().savedDefaults();

      assertFalse(classic.isEmpty());
      assertEquals(classic.size(), after.size());

      for(int i = 0; i < classic.size(); i++) {
         assertEquals(classic.get(i).get(CompositeValue.Type.DEFAULT),
                      after.get(i).get(CompositeValue.Type.DEFAULT), "field " + i);
      }
   }

   @Test
   void aTypeReseedLeavesEveryAuthorValueAlone() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      assertNotNull(info.getVizMark(), "precondition: only a marked chart re-seeds");
      List<CompositeValue<?>> fields = info.getChartDescriptor().savedDefaults();
      List<CompositeValue<?>> classic = new ChartDescriptor().savedDefaults();

      for(int i = 0; i < fields.size(); i++) {
         setUser(fields.get(i), classic.get(i));
      }

      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      info.setUserBarCornerRadius(true);
      plot.setBarCornerRadius(0.17);
      info.setUserSmoothLines(true);
      plot.setSmoothLines(false);

      info.getVSChartInfo().setChartType(GraphTypes.CHART_LINE);
      info.reseedIfResolvedTypeChanged();

      assertEquals(GraphTypes.CHART_LINE, info.getSeededType().type(),
                   "precondition: the re-seed recorded the new type");

      for(int i = 0; i < fields.size(); i++) {
         assertTrue(fields.get(i).hasUserValue(), "field " + i);
         assertEquals(classic.get(i).get(CompositeValue.Type.DEFAULT),
                      fields.get(i).get(CompositeValue.Type.USER), "field " + i);
      }

      assertEquals(0.17, plot.getBarCornerRadius(), 1e-9);
      assertFalse(plot.isSmoothLines());
   }

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   private ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   private static <T> void promoteDefaultToUser(CompositeValue<T> value) {
      value.setValue(value.get(CompositeValue.Type.DEFAULT), CompositeValue.Type.USER);
   }

   // gives the field a default that differs from its constructor's; returns it
   @SuppressWarnings({ "unchecked", "rawtypes" })
   private static Object setSentinelDefault(CompositeValue field) {
      Object current = field.get(CompositeValue.Type.DEFAULT);
      Object sentinel;

      if(current == null) {
         // a colour whose classic default is null
         sentinel = new Color(0x123456);
      }
      else if(current instanceof Color c) {
         sentinel = new Color(~c.getRGB() & 0xFFFFFF);
      }
      else if(current instanceof Boolean b) {
         sentinel = !b;
      }
      else if(current instanceof Integer n) {
         sentinel = n + 1;
      }
      else if(current instanceof Double n) {
         sentinel = n + 1;
      }
      else {
         return fail("no sentinel for registered field type " +
                     current.getClass().getName());
      }

      field.setValue(sentinel, CompositeValue.Type.DEFAULT);
      return sentinel;
   }

   // copies from's default into to's user tier; both are the same registered field
   @SuppressWarnings("unchecked")
   private static <T> void setUser(CompositeValue<T> to, CompositeValue<?> from) {
      to.setValue((T) from.get(CompositeValue.Type.DEFAULT), CompositeValue.Type.USER);
   }

   private static String xml(XMLSerializable obj) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      obj.writeXML(writer);
      writer.flush();
      return out.toString();
   }

   private static <T extends XMLSerializable> T parse(T target, String xml) throws Exception {
      target.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return target;
   }
}
