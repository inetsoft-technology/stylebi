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

package inetsoft.report.composition.execution;

import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.Map;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class VSFormatTableLensTest {
   @Test
   public void testSerialize() throws Exception {
      TableDataPath col1Path = new TableDataPath("col1");
      VSCompositeFormat compositeFormat = new VSCompositeFormat();
      VSFormat format = new VSFormat();
      format.setBackground(Color.BLUE);
      format.setForeground(Color.RED);
      compositeFormat.setUserDefinedFormat(format);

      Viewsheet vs = new Viewsheet();
      TableVSAssembly assembly = new TableVSAssembly(vs, "table1");
      assembly.getFormatInfo().setFormat(col1Path, compositeFormat);
      vs.addAssembly(assembly);

      ViewsheetSandbox box = new ViewsheetSandbox(vs, RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE,
                                                  null, null);
      VSFormatTableLens originalTable = new VSFormatTableLens(box, "table1",
                                                              XTableUtil.getDefaultTableLens(),
                                                              false);
      Map formatMap = originalTable.getFormatMap();
      Assertions.assertEquals(compositeFormat, formatMap.get(col1Path));
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(VSFormatTableLens.class, deserializedTable.getClass());
      formatMap = ((VSFormatTableLens) deserializedTable).getFormatMap();
      Assertions.assertEquals(compositeFormat, formatMap.get(col1Path));
   }

   // Bug #76831: a table style that leaves foreground unset for a region (e.g. "Normal",
   // or any style with no dedicated color for this cell) must not discard the object-level
   // foreground -- it should fall through to the cell instead of being silently dropped.
   @Test
   public void testObjectForegroundFallsThroughWhenStyleLeavesItUnset() throws Exception {
      VSCompositeFormat objFormat = new VSCompositeFormat();
      VSFormat userFormat = new VSFormat();
      userFormat.setForeground(Color.RED);
      objFormat.setUserDefinedFormat(userFormat);

      Viewsheet vs = new Viewsheet();
      TableVSAssembly assembly = new TableVSAssembly(vs, "table1");
      assembly.setTableStyleValue("Normal");
      assembly.getFormatInfo().setFormat(VSAssemblyInfo.OBJECTPATH, objFormat);
      vs.addAssembly(assembly);

      ViewsheetSandbox box = new ViewsheetSandbox(vs, RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE,
                                                  null, testEntry());
      // XTableUtil's plain DefaultTableLens returns null from getForeground(r,c) for every
      // cell -- it stands in for a table style that never defines its own color here.
      VSFormatTableLens table = new VSFormatTableLens(box, "table1",
                                                       XTableUtil.getDefaultTableLens(), false);

      Assertions.assertEquals(Color.RED, table.getForeground(1, 0));
   }

   // Bug #76831 regression guard: when the table style DOES define an explicit color for a
   // region (e.g. Colorful1's white header text), the object-level foreground must still be
   // discarded so the style's own color wins, per mergeColor's cellf-first priority.
   @Test
   public void testObjectForegroundStaysStrippedWhenStyleDefinesItsOwnColor() throws Exception {
      VSCompositeFormat objFormat = new VSCompositeFormat();
      VSFormat userFormat = new VSFormat();
      userFormat.setForeground(Color.RED);
      objFormat.setUserDefinedFormat(userFormat);

      Viewsheet vs = new Viewsheet();
      TableVSAssembly assembly = new TableVSAssembly(vs, "table1");
      assembly.setTableStyleValue("Normal");
      assembly.getFormatInfo().setFormat(VSAssemblyInfo.OBJECTPATH, objFormat);
      vs.addAssembly(assembly);

      ViewsheetSandbox box = new ViewsheetSandbox(vs, RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE,
                                                  null, testEntry());
      TableLens styledLens = new DefaultTableLens(XTableUtil.getDefaultData()) {
         @Override
         public Color getForeground(int r, int c) {
            return Color.WHITE;
         }
      };
      VSFormatTableLens table = new VSFormatTableLens(box, "table1", styledLens, false);

      Assertions.assertEquals(Color.WHITE, table.getForeground(1, 0));
   }

   // Bug #76831 tester follow-up: the fixer's two tests each use a table style that behaves
   // uniformly across every cell (always null, or always white). Real styles like Colorful1
   // define a color for the HEADER region only and leave DETAIL unset -- verify the fix's
   // per-(row,col) check actually discriminates within a single table/style, not just across
   // two separately-configured tables, and that there is no cross-call state leakage between
   // a HEADER cell and a DETAIL cell resolved from the same VSFormatTableLens instance.
   @Test
   public void testObjectForegroundMixedAcrossHeaderAndDetailInSameTable() throws Exception {
      VSCompositeFormat objFormat = new VSCompositeFormat();
      VSFormat userFormat = new VSFormat();
      userFormat.setForeground(Color.RED);
      objFormat.setUserDefinedFormat(userFormat);

      Viewsheet vs = new Viewsheet();
      TableVSAssembly assembly = new TableVSAssembly(vs, "table1");
      assembly.setTableStyleValue("Normal");
      assembly.getFormatInfo().setFormat(VSAssemblyInfo.OBJECTPATH, objFormat);
      vs.addAssembly(assembly);

      ViewsheetSandbox box = new ViewsheetSandbox(vs, RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE,
                                                  null, testEntry());
      // Mimics Colorful1: the style defines a color for the header row only (row 0) and
      // leaves every other row (the DETAIL region) unset.
      TableLens styledLens = new DefaultTableLens(XTableUtil.getDefaultData()) {
         @Override
         public Color getForeground(int r, int c) {
            return r == 0 ? Color.WHITE : null;
         }
      };
      VSFormatTableLens table = new VSFormatTableLens(box, "table1", styledLens, false);

      Assertions.assertEquals(Color.WHITE, table.getForeground(0, 0));
      Assertions.assertEquals(Color.RED, table.getForeground(1, 0));
   }

   private static AssetEntry testEntry() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                            "test/VSFormatTableLensTest", null,
                            OrganizationManager.getInstance().getCurrentOrgID());
   }
}
