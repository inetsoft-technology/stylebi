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
package inetsoft.uql.viewsheet;

import inetsoft.report.TableDataPath;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Renaming a calc field must carry the per-column format keyed by the old name over to the
 * new name, the same way highlights and hyperlinks already are.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RenameBindingColFormatTest {
   private static TableDataPath path(int type, String name) {
      return new TableDataPath(-1, type, "double", new String[] { name });
   }

   private static VSCompositeFormat format() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setFormat("DecimalFormat");
      return fmt;
   }

   @Test
   void tableDetailFormatFollowsRename() {
      TableVSAssembly table = new TableVSAssembly();
      FormatInfo info = table.getFormatInfo();
      int base = info.getPaths().length;
      VSCompositeFormat fmt = format();
      TableDataPath old = path(TableDataPath.DETAIL, "Net Sales");
      info.setFormat(old, fmt);

      table.renameBindingCol("Net Sales", "Net Revenue");

      TableDataPath renamed = path(TableDataPath.DETAIL, "Net Revenue");
      assertSame(fmt, info.getFormatMap().get(renamed));
      assertFalse(info.getFormatMap().containsKey(old));
      assertEquals(base + 1, info.getPaths().length);
   }

   @Test
   void tableHeaderFormatFollowsRename() {
      TableVSAssembly table = new TableVSAssembly();
      FormatInfo info = table.getFormatInfo();
      int base = info.getPaths().length;
      VSCompositeFormat fmt = format();
      info.setFormat(path(TableDataPath.HEADER, "Net Sales"), fmt);

      table.renameBindingCol("Net Sales", "Net Revenue");

      assertSame(fmt, info.getFormatMap().get(path(TableDataPath.HEADER, "Net Revenue")));
      assertEquals(base + 1, info.getPaths().length);
   }

   @Test
   void unrelatedFormatUntouched() {
      TableVSAssembly table = new TableVSAssembly();
      FormatInfo info = table.getFormatInfo();
      int base = info.getPaths().length;
      VSCompositeFormat fmt = format();
      TableDataPath other = path(TableDataPath.DETAIL, "Cost");
      info.setFormat(other, fmt);

      table.renameBindingCol("Net Sales", "Net Revenue");

      assertSame(fmt, info.getFormatMap().get(other));
      assertEquals(base + 1, info.getPaths().length);
   }

   @Test
   void noFormatIsNoOp() {
      TableVSAssembly table = new TableVSAssembly();
      int base = table.getFormatInfo().getPaths().length;

      table.renameBindingCol("Net Sales", "Net Revenue");

      assertEquals(base, table.getFormatInfo().getPaths().length);
   }

   @Test
   void crosstabAggregateFormatFollowsRename() {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly();
      FormatInfo info = crosstab.getFormatInfo();
      int base = info.getPaths().length;
      VSCompositeFormat fmt = format();
      info.setFormat(path(TableDataPath.SUMMARY, "Sum(Net Sales)"), fmt);

      crosstab.renameBindingCol("Net Sales", "Net Revenue");

      assertSame(fmt, info.getFormatMap().get(path(TableDataPath.SUMMARY, "Sum(Net Revenue)")));
      assertEquals(base + 1, info.getPaths().length);
   }
}
