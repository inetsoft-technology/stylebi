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

package inetsoft.report.script;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class TableRowTest {
   private TableRow tableRow;

   /**
    * test tablerow with get methord.   such as setBackground and getBackground
    */
   @Test
   void  testWithBackground() {
      tableRow = new TableRow(defaultTableLens, 1, "Background", Color.class);

      assertEquals(1, tableRow.getRow());
      assertTrue(tableRow.hasMember("length"));
      assertEquals(3, tableRow.getMember("length"));

      assertArrayEquals(new Object[]{ "name", "id", "date", "length" }, tableRow.getMemberKeys());
      assertEquals("[index]", tableRow.getDisplaySuffix());
      assertEquals("[]", tableRow.getSuffix());
   }

   /**
    * test tableRow with is property method, such as setLineWrap and isLineWrap
    */
   @Test
   void testWithOthers() {
      tableRow = new TableRow(defaultTableLens, 1, "LineWrap", boolean.class);

      assertTrue(tableRow.hasMember("name"));
      assertFalse(tableRow.hasMember("test"));

      // assigning an unknown column name now stores it as a local member
      tableRow.putMember("name2", "test");
      assertEquals("test", tableRow.getMember("name2"));

      TableRow row1 = (TableRow) tableRow.getArrayElement(-1);
      assertArrayEquals(new Object[]{ "name", "id", "date", "length" }, row1.getMemberKeys());

      TableRow.TableCol tableCol = new TableRow.TableCol();
      assertEquals("null[0]", tableCol.toString());
   }

   Object[][] objData = new Object[][]{
      {"name", "id", "date"},
      {"a", 1, new Date(2021 - 1900, 0, 1)},
      {"c", 2, new Date(2023 - 1900, 5, 15)},
      {"a", 3, new Date(2025 - 1900, 11, 31)},
      {"b", 2, new Date(2026 - 1900, 9, 20)}
   };

   private DefaultTableLens defaultTableLens = new DefaultTableLens(objData);
}
