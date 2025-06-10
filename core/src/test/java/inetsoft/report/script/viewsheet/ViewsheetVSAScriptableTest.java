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
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ViewsheetVSAssemblyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ViewsheetVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox;

   private ViewsheetVSAScriptable viewsheetVSAScriptable;

   private Viewsheet viewsheet;

   private AssetEntry assetEntry;

   @BeforeEach
   void setUp() {
      assetEntry = createAssetEntry();
      viewsheet = mock(Viewsheet.class);
      when(viewsheet.getEntry()).thenReturn(assetEntry);
      when(viewsheet.getName()).thenReturn("vs1");
      when(viewsheet.getAssembly("vs1")).thenReturn(viewsheet);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vid");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);
      when(viewsheetSandbox.getAssetEntry()).thenReturn(assetEntry);

      viewsheetVSAScriptable = new ViewsheetVSAScriptable(viewsheetSandbox);
   }

   @Test
   void testGetViewsheetSandbox() {
      assertArrayEquals(new Object[] { "taskName" },  viewsheetVSAScriptable.getIds());
      assertEquals("mockVSName", viewsheetVSAScriptable.get("viewsheetName", null));
      assertEquals("path1", viewsheetVSAScriptable.get("viewsheetPath", null));
      assertEquals("alias1", viewsheetVSAScriptable.get("viewsheetAlias", null));
      assertEquals("task1", viewsheetVSAScriptable.get("taskName", null));

      //check get bookmark
      when(viewsheetSandbox.getOpenedBookmark()).thenReturn(null);
      VSBookmarkInfo mockVSBookmarkInfo = mock(VSBookmarkInfo.class);
      when(viewsheetSandbox.getOpenedBookmark()).thenReturn(mockVSBookmarkInfo);
      assertEquals("(Home)", viewsheetVSAScriptable.get("currentBookmark", null));
      when(mockVSBookmarkInfo.getName()).thenReturn("bk1");
      assertEquals("bk1", viewsheetVSAScriptable.get("currentBookmark", null));
   }

   @Test
   void testHasProperties() {
      ViewsheetVSAssemblyInfo mockVSAssemblyInfo = mock(ViewsheetVSAssemblyInfo.class);
      when(mockVSAssemblyInfo.isEmbedded()).thenReturn(true);
      when(viewsheet.getVSAssemblyInfo()).thenReturn(mockVSAssemblyInfo);

      //check get updateTime
      viewsheetSandbox.setMVDisabled(false);
      assertNotEquals(null, viewsheetVSAScriptable.get("updateTime", null));

      //check thisParameter and visible
      ViewsheetSandbox mockMySandbox = mock(ViewsheetSandbox.class);
      VariableTable mockVariableTable = mock(VariableTable.class);
      when(mockMySandbox.getVariableTable()).thenReturn(mockVariableTable);
      when(viewsheetSandbox.getSandbox(null)).thenReturn(mockMySandbox);

      ViewsheetVSAssemblyInfo mockViewsheetVSAssemblyInfo = mock(ViewsheetVSAssemblyInfo.class);
      when(mockViewsheetVSAssemblyInfo.isEmbedded()).thenReturn(true);
      when(viewsheet.getInfo()).thenReturn(mockViewsheetVSAssemblyInfo);
      viewsheetVSAScriptable.setAssembly("thisViewsheet");
      viewsheetVSAScriptable.addProperties();

      assertFalse(viewsheetVSAScriptable.has("thisParameter", null));
      assertTrue(viewsheetVSAScriptable.has("visible", null));
   }

   private AssetEntry createAssetEntry() {
      AssetEntry assetEntry = mock(AssetEntry.class);
      when(assetEntry.getName()).thenReturn("mockVSName");
      when(assetEntry.getPath()).thenReturn("path1");
      when(assetEntry.getAlias()).thenReturn("alias1");
      when(assetEntry.getProperty("taskName")).thenReturn("task1");

      return  assetEntry;
   }
}
