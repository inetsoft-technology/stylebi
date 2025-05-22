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
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SpinnerVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class SpinnerVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private SpinnerVSAScriptable spinnerVSAScriptable;
   private SpinnerVSAssemblyInfo spinnerVSAssemblyInfo;
   private SpinnerVSAssembly spinnerVSAssembly;
   private VSAScriptable vsaScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      spinnerVSAssembly = new SpinnerVSAssembly();
      spinnerVSAssemblyInfo = (SpinnerVSAssemblyInfo) spinnerVSAssembly.getVSAssemblyInfo();
      spinnerVSAssemblyInfo.setName("Spinner1");
      viewsheet.addAssembly(spinnerVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      spinnerVSAScriptable = new SpinnerVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      spinnerVSAScriptable.setAssembly("Spinner1");
      vsaScriptable.setAssembly("Spinner1");
   }

   @Test
   void testGetClassName() {
      assertEquals("SpinnerVSA", spinnerVSAScriptable.getClassName());
   }

   @Test
   void testAddProperties() {
      spinnerVSAScriptable.addProperties();

      assertEquals(Double.parseDouble("0.0"),
                   spinnerVSAScriptable.get("min", spinnerVSAScriptable));
      assertEquals(Double.parseDouble("100.0"),
                   spinnerVSAScriptable.get("max", spinnerVSAScriptable));
      assertEquals(Double.parseDouble("1.0"),
                   spinnerVSAScriptable.get("increment", spinnerVSAScriptable));
   }

   @Test
   void testNotSupport() {
      spinnerVSAScriptable.setDataType("number");
      assertNull(spinnerVSAScriptable.getDataType());

      spinnerVSAScriptable.setValues(new Object[] { "value1", "value2" });
      assertNull(spinnerVSAScriptable.getValues());

      spinnerVSAScriptable.setLabels(new String[] { "label1", "label2" });
      assertNull(spinnerVSAScriptable.getLabels());

      spinnerVSAScriptable.setSelectedObject("obj1");
      assertEquals(0, spinnerVSAScriptable.getSelectedObject());
   }
}