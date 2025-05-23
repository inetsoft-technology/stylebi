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
import inetsoft.uql.viewsheet.GroupContainerVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.GroupContainerVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class GroupContainerVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private GroupContainerVSAScriptable groupContainerVSAScriptable;
   private GroupContainerVSAssemblyInfo groupContainerVSAssemblyInfo;
   private GroupContainerVSAssembly groupContainerVSAssembly;
   private VSAScriptable vsaScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      groupContainerVSAssembly = new GroupContainerVSAssembly();
      groupContainerVSAssemblyInfo =
         (GroupContainerVSAssemblyInfo) groupContainerVSAssembly.getVSAssemblyInfo();
      groupContainerVSAssemblyInfo.setName("GroupContainer1");
      viewsheet.addAssembly(groupContainerVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      groupContainerVSAScriptable = new GroupContainerVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      groupContainerVSAScriptable.setAssembly("GroupContainer1");
      vsaScriptable.setAssembly("GroupContainer1");
   }

   @Test
   void testGetClassName() {
      assertEquals("GroupContainerVSA", groupContainerVSAScriptable.getClassName());
   }

   @ParameterizedTest
   @ValueSource(strings = { "maintainAspectRatio", "scaleImage", "animate", "tile"})
   void testAddProperties(String propertyName) {
      groupContainerVSAScriptable.addProperties();
      assert groupContainerVSAScriptable.get(propertyName, null) instanceof Boolean;
   }

   @ParameterizedTest
   @CsvSource({
      "maintainAspectRatio, false, false",
      "scaleImage, true, true",
      "animate, true, true",
      "tile, true, true",
      "imageAlpha, 0.5, 0.5"
   })
   void testSetProperty(String propertyName, Object propertyValue, Object expectedValue) {
      groupContainerVSAScriptable.setProperty(propertyName, propertyValue);
      assertEquals(expectedValue, groupContainerVSAScriptable.get(propertyName, null));
   }

   @ParameterizedTest
   @CsvSource({
      "scale9, []",
      "visible, ''"
   })
   void testGetSuffix(String propertyName, String expectedValue) {
      assertEquals(expectedValue, groupContainerVSAScriptable.getSuffix(propertyName));
   }
}