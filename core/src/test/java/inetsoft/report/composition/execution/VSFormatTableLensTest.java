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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.Map;

@SreeHome
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
}
