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
import inetsoft.uql.viewsheet.CurrentSelectionVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CurrentSelectionVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class SelectionContainerVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private SelectionContainerVSAScriptable selectionContainerVSAScriptable;
   private CurrentSelectionVSAssemblyInfo selectionContainerVSAssemblyInfo;
   private CurrentSelectionVSAssembly selectionContainerVSAssembly;
   private VSAScriptable vsaScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      selectionContainerVSAssembly = new CurrentSelectionVSAssembly();
      selectionContainerVSAssemblyInfo = (CurrentSelectionVSAssemblyInfo) selectionContainerVSAssembly.getVSAssemblyInfo();
      selectionContainerVSAssemblyInfo.setName("SelectionContainer1");
      viewsheet.addAssembly(selectionContainerVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      selectionContainerVSAScriptable = new SelectionContainerVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      selectionContainerVSAScriptable.setAssembly("SelectionContainer1");
      vsaScriptable.setAssembly("SelectionContainer1");
   }

   @Test
   void testGetClassName() {
      assertEquals("SelectionContainerVSA", selectionContainerVSAScriptable.getClassName());
   }

   @ParameterizedTest
   @ValueSource(strings = { "titleVisible", "showCurrentSelection", "adhocEnabled", "empty"})
   void testAddProperties(String propertyName) {
      selectionContainerVSAScriptable.addProperties();
      assert selectionContainerVSAScriptable.get(propertyName, null) instanceof Boolean;
   }

   @ParameterizedTest
   @CsvSource({
      "titleVisible, false, false",
      "showCurrentSelection, true, true",
      "adhocEnabled, false, false",
      "title, title1, title1"
   })
   void testSetProperty(String propertyName, Object propertyValue, Object expectedValue) {
      selectionContainerVSAScriptable.setProperty(propertyName, propertyValue);
      assertEquals(expectedValue, selectionContainerVSAScriptable.get(propertyName, null));
   }

   @Test
   void testSetSize() {
      when(viewsheetSandbox.isRuntime()).thenReturn(true);
      selectionContainerVSAssemblyInfo.setAssemblies(new String[]{"SelectionList1", "SelectionList2"});
      Dimension size1 = new Dimension(120, 260);
      selectionContainerVSAScriptable.setSize(size1);
      assertEquals(size1, selectionContainerVSAScriptable.getSize());
   }
}