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
import inetsoft.uql.viewsheet.TextInputVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class TextInputVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private TextInputVSAScriptable textInputVSAScriptable;
   private TextInputVSAssemblyInfo textInputVSAssemblyInfo;
   private TextInputVSAssembly textInputVSAssembly;
   private VSAScriptable vsaScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      textInputVSAssembly = new TextInputVSAssembly();
      textInputVSAssemblyInfo = (TextInputVSAssemblyInfo) textInputVSAssembly.getVSAssemblyInfo();
      textInputVSAssemblyInfo.setName("TextInput1");
      viewsheet.addAssembly(textInputVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      textInputVSAScriptable = new TextInputVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      textInputVSAScriptable.setAssembly("TextInput1");
      vsaScriptable.setAssembly("TextInput1");
   }

   @Test
   void testGetClassName() {
      assertEquals("TextInputVSA", textInputVSAScriptable.getClassName());
   }

   @Test
   void testAddProperties() {
      textInputVSAScriptable.addProperties();

      assertNull(textInputVSAScriptable.get("value", textInputVSAScriptable));
   }
}
