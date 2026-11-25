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

import inetsoft.test.*;
import inetsoft.uql.viewsheet.DynamicValue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Script-set runtime values of these properties must be reverted by resetRuntimeValues()
 * so that removing the script restores the design value (Bug #82199).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptValueResetTest {
   @Test
   void outputShadowIsReverted() {
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      info.setShadowValue(false);
      info.setShadow(true);
      assertTrue(info.isShadow());
      info.resetRuntimeValues();
      assertFalse(info.isShadow());
   }

   @Test
   void outputShadowIsRevertedForCylinder() {
      CylinderVSAssemblyInfo info = new CylinderVSAssemblyInfo();
      info.setShadowValue(false);
      info.setShadow(true);
      assertTrue(info.isShadow());
      info.resetRuntimeValues();
      assertFalse(info.isShadow());
   }

   @Test
   void shapeShadowIsReverted() {
      RectangleVSAssemblyInfo info = new RectangleVSAssemblyInfo();
      info.setShadowValue(false);
      info.setShadow(true);
      assertTrue(info.isShadow());
      info.resetRuntimeValues();
      assertFalse(info.isShadow());
   }

   @Test
   void listInputSortTypeIsReverted() {
      CheckBoxVSAssemblyInfo info = new CheckBoxVSAssemblyInfo();
      info.setSortTypeValue(1);
      info.setSortType(2);
      assertEquals(2, info.getSortType());
      info.resetRuntimeValues();
      assertEquals(1, info.getSortType());
   }

   @Test
   void listInputEmbeddedDataDownIsReverted() {
      CheckBoxVSAssemblyInfo info = new CheckBoxVSAssemblyInfo();
      info.setEmbeddedDataDownValue(false);
      info.setEmbeddedDataDown(true);
      assertTrue(info.isEmbeddedDataDown());
      info.resetRuntimeValues();
      assertFalse(info.isEmbeddedDataDown());
   }

   @Test
   void listInputSelectFirstItemIsReverted() {
      CheckBoxVSAssemblyInfo info = new CheckBoxVSAssemblyInfo();
      info.setSelectFirstItemValue(false);
      info.setSelectFirstItem(true);
      assertTrue(info.isSelectFirstItem());
      info.resetRuntimeValues();
      assertFalse(info.isSelectFirstItem());
   }

   @Test
   void listInputCloneDoesNotShareSelectFirstItem() {
      CheckBoxVSAssemblyInfo info = new CheckBoxVSAssemblyInfo();
      info.setSelectFirstItemValue(false);
      info.setSelectFirstItem(true);
      CheckBoxVSAssemblyInfo copy = (CheckBoxVSAssemblyInfo) info.clone();
      copy.resetRuntimeValues();
      assertTrue(info.isSelectFirstItem());
   }

   @Test
   void comboBoxMinMaxDateAreReverted() {
      ComboBoxVSAssemblyInfo info = new ComboBoxVSAssemblyInfo();
      info.setMinDate(new Timestamp(1000000L));
      info.setMaxDate(new Timestamp(2000000L));
      assertNotNull(info.getMinDate());
      assertNotNull(info.getMaxDate());
      info.resetRuntimeValues();
      assertNull(info.getMinDate());
      assertNull(info.getMaxDate());
   }

   @Test
   void inputSubmitOnChangeIsReverted() {
      SliderVSAssemblyInfo info = new SliderVSAssemblyInfo();
      info.setSubmitOnChangeValue("true");
      info.setSubmitOnChange(false);
      assertFalse(info.isSubmitOnChange());
      info.resetRuntimeValues();
      assertTrue(info.isSubmitOnChange());
   }

   @Test
   void inputRefreshIsReverted() {
      SliderVSAssemblyInfo info = new SliderVSAssemblyInfo();
      info.setRefreshValue("true");
      info.setRefresh(false);
      assertFalse(info.isRefresh());
      info.resetRuntimeValues();
      assertTrue(info.isRefresh());
   }

   @Test
   void submitRefreshIsReverted() {
      SubmitVSAssemblyInfo info = new SubmitVSAssemblyInfo();
      info.setRefreshValue("true");
      info.setRefresh(false);
      assertFalse(info.isRefresh());
      info.resetRuntimeValues();
      assertTrue(info.isRefresh());
   }

   @Test
   void selectionShowTextAndBarAreReverted() {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setShowTextValue(true);
      info.setShowBarValue(false);
      info.setShowText(false);
      info.setShowBar(true);
      assertFalse(info.isShowText());
      assertTrue(info.isShowBar());
      info.resetRuntimeValues();
      assertTrue(info.isShowText());
      assertFalse(info.isShowBar());
   }

   @Test
   void selectionSelectFirstItemIsReverted() {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setSelectFirstItemValue(false);
      info.setSelectFirstItem(true);
      assertTrue(info.isSelectFirstItem());
      info.resetRuntimeValues();
      assertFalse(info.isSelectFirstItem());
   }

   @Test
   void tableTipOptionIsReverted() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setTipOptionValue(TipVSAssemblyInfo.TOOLTIP_OPTION);
      info.setTipOption(TipVSAssemblyInfo.VIEWTIP_OPTION);
      assertEquals(TipVSAssemblyInfo.VIEWTIP_OPTION, info.getTipOption());
      info.resetRuntimeValues();
      assertEquals(TipVSAssemblyInfo.TOOLTIP_OPTION, info.getTipOption());
   }

   /**
    * Every DynamicValue field of every assembly info must be cleared by resetRuntimeValues(),
    * unless it is listed here with the reason it is deliberately kept.
    */
   @Test
   void everyDynamicValueFieldIsClearedByReset() throws Exception {
      Set<String> allowed = new HashSet<>();
      // Setting bottomTabs physically moves the tab bar and its children (and is applied by a
      // run-once onInit), so clearing only the runtime flag would desync flag and positions.
      allowed.add("TabVSAssemblyInfo.bottomTabs");
      // Known gaps outside Bug #82199, no script property found that writes them (unconfirmed
      // for Submit alpha); kept explicit so any NEW uncleared field fails this test.
      allowed.add("VSAssemblyInfo.layoutVisibleValue");
      allowed.add("InputVSAssemblyInfo.columnValue");
      allowed.add("InputVSAssemblyInfo.rowValue");
      allowed.add("InputVSAssemblyInfo.writeBackValue");
      allowed.add("ComboBoxVSAssemblyInfo.rowCountValue");
      allowed.add("ClickableOutputVSAssemblyInfo.alphaValue");
      allowed.add("ClickableOutputVSAssemblyInfo.popOptionValue");
      allowed.add("ClickableOutputVSAssemblyInfo.popComponentValue");
      allowed.add("ClickableOutputVSAssemblyInfo.popLocationValue");

      ClassPathScanningCandidateComponentProvider scanner =
         new ClassPathScanningCandidateComponentProvider(false);
      scanner.addIncludeFilter(new AssignableTypeFilter(VSAssemblyInfo.class));
      List<String> failures = new ArrayList<>();
      int checked = 0;

      for(var def : scanner.findCandidateComponents("inetsoft.uql.viewsheet.internal")) {
         Class<?> cls = Class.forName(def.getBeanClassName());

         if(Modifier.isAbstract(cls.getModifiers())) {
            continue;
         }

         VSAssemblyInfo info;

         try {
            info = (VSAssemblyInfo) cls.getDeclaredConstructor().newInstance();
         }
         catch(Throwable ex) {
            continue;
         }

         Map<Field, DynamicValue> values = new LinkedHashMap<>();

         for(Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for(Field f : c.getDeclaredFields()) {
               if(Modifier.isStatic(f.getModifiers()) ||
                  !DynamicValue.class.isAssignableFrom(f.getType()))
               {
                  continue;
               }

               f.setAccessible(true);
               DynamicValue dv = (DynamicValue) f.get(info);

               if(dv != null) {
                  values.put(f, dv);
               }
            }
         }

         for(DynamicValue dv : values.values()) {
            dv.setRValue("script-sentinel");
         }

         info.resetRuntimeValues();

         for(Map.Entry<Field, DynamicValue> e : values.entrySet()) {
            String name = e.getKey().getDeclaringClass().getSimpleName() + "." +
               e.getKey().getName();
            checked++;

            if("script-sentinel".equals(e.getValue().getRValue()) && !allowed.contains(name)) {
               failures.add(cls.getSimpleName() + " -> " + name);
            }
         }
      }

      assertTrue(checked > 0);
      assertTrue(failures.isEmpty(), "Not cleared by resetRuntimeValues(): " + failures);
   }

   // Bug #82267: script-set tooltip values on Output types and Chart

   @Test
   void outputTooltipIsRevertedForAllTypes() {
      OutputVSAssemblyInfo[] infos = {
         new TextVSAssemblyInfo(), new GaugeVSAssemblyInfo(), new ImageVSAssemblyInfo(),
         new CylinderVSAssemblyInfo(), new ThermometerVSAssemblyInfo(),
         new SlidingScaleVSAssemblyInfo()
      };

      for(OutputVSAssemblyInfo info : infos) {
         String name = info.getClass().getSimpleName();
         info.setTooltipVisibleValue(true);
         info.setCustomTooltipStringValue("design");
         info.setTooltipVisible(false);
         info.setCustomTooltipString("script");
         assertFalse(info.isTooltipVisible(), name);
         assertEquals("script", info.getCustomTooltipString(), name);
         info.resetRuntimeValues();
         assertTrue(info.isTooltipVisible(), name);
         assertEquals("design", info.getCustomTooltipString(), name);
      }
   }

   @Test
   void outputScriptNullTooltipOverridesDesignUntilReset() {
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      info.setCustomTooltipStringValue("design");
      info.setCustomTooltipString(null);
      assertNull(info.getCustomTooltipString());
      info.resetRuntimeValues();
      assertEquals("design", info.getCustomTooltipString());
   }

   @Test
   void outputTooltipCloneDoesNotShareRuntimeValues() {
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      info.setTooltipVisible(false);
      info.setCustomTooltipString("script");
      TextVSAssemblyInfo copy = (TextVSAssemblyInfo) info.clone();
      copy.resetRuntimeValues();
      assertFalse(info.isTooltipVisible());
      assertEquals("script", info.getCustomTooltipString());
      assertTrue(copy.isTooltipVisible());
      assertNull(copy.getCustomTooltipString());
   }

   @Test
   void outputXmlKeepsDesignTooltipAfterScriptWrite() throws Exception {
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      info.setTooltipVisibleValue(false);
      info.setCustomTooltipStringValue("design");
      info.setTooltipVisible(true);
      info.setCustomTooltipString("script");

      java.io.StringWriter sw = new java.io.StringWriter();
      java.io.PrintWriter pw = new java.io.PrintWriter(sw);
      info.writeXML(pw);
      pw.flush();
      String xml = sw.toString();
      assertTrue(xml.contains("tooltipVisible=\"false\""), xml);
      assertTrue(xml.contains("<![CDATA[design"), xml);
      assertFalse(xml.contains("<![CDATA[script"), xml);
   }

   @Test
   void chartTooltipVisibleAndCustomTooltipAreReverted() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      inetsoft.uql.viewsheet.graph.VSChartInfo cinfo = new inetsoft.uql.viewsheet.graph.VSChartInfo();
      info.setVSChartInfo(cinfo);
      cinfo.setTooltipVisibleValue(true);
      cinfo.setTooltipVisible(false);
      cinfo.setToolTip("script");
      assertFalse(cinfo.isTooltipVisible());
      assertEquals("script", cinfo.getToolTip());
      info.resetRuntimeValues();
      assertTrue(cinfo.isTooltipVisible());
      assertNull(cinfo.getToolTip());
      assertEquals("", cinfo.getCustomTooltip());
   }

   @Test
   void chartDesignTooltipSurvivesScriptWriteAndReset() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      inetsoft.uql.viewsheet.graph.VSChartInfo cinfo = new inetsoft.uql.viewsheet.graph.VSChartInfo();
      info.setVSChartInfo(cinfo);
      cinfo.setToolTipValue("design");
      cinfo.setToolTip("script");
      info.resetRuntimeValues();
      assertEquals("design", cinfo.getToolTipValue());
      assertEquals("design", cinfo.getCustomTooltip());
   }
}
