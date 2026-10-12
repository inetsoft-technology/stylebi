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

   // Bug #76831: a user-defined object-level foreground wins over the table style's own
   // foreground on every cell region, even when the style defines a color for each cell.
   @Test
   public void testUserObjectForegroundWinsOverStyleForeground() throws Exception {
      VSFormatTableLens table = createTable(userObjectFormat(Color.RED), whiteStyleLens());

      Assertions.assertEquals(Color.RED, table.getForeground(0, 0));
      Assertions.assertEquals(Color.RED, table.getForeground(1, 0));
      Assertions.assertEquals(Color.RED, table.getForeground(4, 2));
   }

   // Bug #76831 regression guard: when the object format carries no user-defined foreground
   // (only the default format's), the table style's own color (e.g. Colorful1's white header
   // text, which Style.getForeground returns for every cell) must still win.
   @Test
   public void testDefaultObjectForegroundYieldsToStyleForeground() throws Exception {
      VSCompositeFormat objFormat = new VSCompositeFormat();
      VSFormat defaultFormat = new VSFormat();
      defaultFormat.setForeground(Color.BLACK);
      objFormat.setDefaultFormat(defaultFormat);

      VSFormatTableLens table = createTable(objFormat, whiteStyleLens());

      Assertions.assertEquals(Color.WHITE, table.getForeground(0, 0));
      Assertions.assertEquals(Color.WHITE, table.getForeground(1, 0));
   }

   // Bug #76831: the style defines a color for the header row only; the user-defined object
   // foreground applies to both the header and the body.
   @Test
   public void testUserObjectForegroundWithHeaderOnlyStyleColor() throws Exception {
      TableLens styledLens = new DefaultTableLens(XTableUtil.getDefaultData()) {
         @Override
         public Color getForeground(int r, int c) {
            return r == 0 ? Color.WHITE : null;
         }
      };
      VSFormatTableLens table = createTable(userObjectFormat(Color.RED), styledLens);

      Assertions.assertEquals(Color.RED, table.getForeground(0, 0));
      Assertions.assertEquals(Color.RED, table.getForeground(1, 0));
   }

   // Bug #76831: a per-cell user foreground still beats the object-level one.
   @Test
   public void testPerCellForegroundBeatsUserObjectForeground() throws Exception {
      VSCompositeFormat objFormat = userObjectFormat(Color.RED);
      Viewsheet vs = new Viewsheet();
      TableVSAssembly assembly = new TableVSAssembly(vs, "table1");
      assembly.setTableStyleValue("Normal");
      assembly.getFormatInfo().setFormat(VSAssemblyInfo.OBJECTPATH, objFormat);
      vs.addAssembly(assembly);

      TableLens styledLens = whiteStyleLens();
      TableDataPath cellPath = styledLens.getDescriptor().getCellDataPath(1, 0);
      VSCompositeFormat cellFormat = userObjectFormat(Color.BLUE);
      cellFormat.getUserDefinedFormat().setForegroundValue("#0000ff");
      assembly.getFormatInfo().setFormat(cellPath, cellFormat);

      ViewsheetSandbox box = new ViewsheetSandbox(vs, RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE,
                                                  null, testEntry());
      VSFormatTableLens table = new VSFormatTableLens(box, "table1", styledLens, false);

      Assertions.assertEquals(Color.BLUE, table.getForeground(1, 0));
   }

   // Bug #76831: a dynamic (script) object foreground, once resolved, also wins over the style.
   @Test
   public void testDynamicUserObjectForegroundWinsOverStyleForeground() throws Exception {
      VSCompositeFormat objFormat = new VSCompositeFormat();
      VSFormat userFormat = new VSFormat();
      userFormat.setForegroundValue("=\"red\"");
      userFormat.setForeground(Color.RED);
      objFormat.setUserDefinedFormat(userFormat);

      VSFormatTableLens table = createTable(objFormat, whiteStyleLens());

      Assertions.assertEquals(Color.RED, table.getForeground(0, 0));
      Assertions.assertEquals(Color.RED, table.getForeground(1, 0));
   }

   private static VSCompositeFormat userObjectFormat(Color color) {
      VSCompositeFormat objFormat = new VSCompositeFormat();
      VSFormat userFormat = new VSFormat();
      userFormat.setForeground(color);
      objFormat.setUserDefinedFormat(userFormat);
      return objFormat;
   }

   // Mimics Colorful1, whose Style.getForeground returns white for every cell.
   private static TableLens whiteStyleLens() {
      return new DefaultTableLens(XTableUtil.getDefaultData()) {
         @Override
         public Color getForeground(int r, int c) {
            return Color.WHITE;
         }
      };
   }

   private static VSFormatTableLens createTable(VSCompositeFormat objFormat, TableLens styledLens)
      throws Exception
   {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly assembly = new TableVSAssembly(vs, "table1");
      assembly.setTableStyleValue("Normal");
      assembly.getFormatInfo().setFormat(VSAssemblyInfo.OBJECTPATH, objFormat);
      vs.addAssembly(assembly);

      ViewsheetSandbox box = new ViewsheetSandbox(vs, RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE,
                                                  null, testEntry());
      return new VSFormatTableLens(box, "table1", styledLens, false);
   }

   private static AssetEntry testEntry() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                            "test/VSFormatTableLensTest", null,
                            OrganizationManager.getInstance().getCurrentOrgID());
   }
}
