/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class GaugeVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private GaugeVSAScriptable gaugeVSAScriptable;
   private GaugeVSAssemblyInfo gaugeVSAssemblyInfo;
   private GaugeVSAssembly gaugeVSAssembly;
   private VSAScriptable vsaScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      gaugeVSAssembly = new GaugeVSAssembly();
      gaugeVSAssemblyInfo = (GaugeVSAssemblyInfo) gaugeVSAssembly.getVSAssemblyInfo();
      gaugeVSAssemblyInfo.setName("Gauge1");
      viewsheet.addAssembly(gaugeVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      gaugeVSAScriptable = new GaugeVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      gaugeVSAScriptable.setAssembly("Gauge1");
      vsaScriptable.setAssembly("Gauge1");
   }

   @Test
   void testGetClassName() {
      assertEquals("GaugeVSA", gaugeVSAScriptable.getClassName());
   }

   @Test
   void testAddProperties() {
      gaugeVSAScriptable.addProperties();
      assertEquals(true, gaugeVSAScriptable.get("labelVisible", gaugeVSAScriptable));

      assertTrue(gaugeVSAScriptable.isPublicProperty("visible"));
   }

   @Test
   void testGetSetQuery() {
      assertNull(gaugeVSAScriptable.getQuery());
      gaugeVSAScriptable.setQuery("query");
      assertEquals("query", gaugeVSAScriptable.getQuery());
   }

   @Test
   void testGetSetFormula() {
      assertNull(gaugeVSAScriptable.getFormula());
      gaugeVSAScriptable.setFormula("Sum");
      assertEquals("Sum", gaugeVSAScriptable.getFormula());
   }

   @Test
   void testGetSetFields() {
      assertNull(gaugeVSAScriptable.getFields());
      gaugeVSAScriptable.setFields(new Object[]{"field1"});
      assertEquals("field1", gaugeVSAScriptable.getFields());
   }

   @Test
   void testGet() throws Exception {
      assertNull(gaugeVSAScriptable.get("value", gaugeVSAScriptable));
      assertNull(gaugeVSAScriptable.get("dataConditions", gaugeVSAScriptable));
      assertNull(gaugeVSAScriptable.getDefaultValue(GaugeVSAScriptable.class));
   }

   @ParameterizedTest
   @CsvSource({
      "ranges, []",
      "rangeColors, []",
      "toolTip, ''"
   })
   void testGetSuffix(String propertyName, String expectedValue) {
      assertEquals(expectedValue, gaugeVSAScriptable.getSuffix(propertyName));
   }
}