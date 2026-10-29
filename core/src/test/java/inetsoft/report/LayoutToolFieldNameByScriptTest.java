/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Bug #79633: the column recovered from a formula group cell's script must not keep the
 * closing quote of the option string, or the cell stops being recognized as a group.
 */
@Tag("core")
class LayoutToolFieldNameByScriptTest {
   private static final String COL = "Customer:Region";

   @Test
   void toListWithFieldOptionFirst() {
      assertEquals(COL, LayoutTool.getFieldNameByScript(
         "toList(data['*'],'sort=asc,field=Customer:Region,sorton2=sum(Product:Total)')"
         + ".map(function(v) { return v.toUpperCase(); })"));
   }

   @Test
   void arrayFormWithColumnKey() {
      assertEquals(COL, LayoutTool.getFieldNameByScript(
         "mapList(data['Customer:Region'],[[\"USA West\"],\"Region WEST\"])"));
   }

   @Test
   void fieldOptionIsLastInQuotedOptions() {
      assertEquals(COL, LayoutTool.getFieldNameByScript(
         "mapList(data['*'],[[\"USA West\"],\"Region WEST\"],'field=Customer:Region')"));
      assertEquals(COL, LayoutTool.getFieldNameByScript(
         "toList(data['*'],'sort=asc,field=Customer:Region')"));
   }

   @Test
   void fieldOptionFollowedByOtherOptions() {
      assertEquals(COL, LayoutTool.getFieldNameByScript(
         "mapList(data['*'],[[\"USA West\"],\"Region WEST\"],'field=Customer:Region,sort=asc')"));
      assertEquals(COL, LayoutTool.getFieldNameByScript(
         "mapList(data['*'],[[\"USA West\"],\"Region WEST\"],"
         + "'sort=asc,field=Customer:Region,sorton2=sum(Product:Total),sortotherslast=true')"));
   }

   @Test
   void fieldsOptionHasNoLeftoverEqualsSign() {
      assertEquals(COL, LayoutTool.getFieldNameByScript(
         "toList(data['*'],'fields=Customer:Region')"));
      assertEquals(COL, LayoutTool.getFieldNameByScript(
         "toList(data['*'],'fields=Customer:Region,sort=asc')"));
   }

   @Test
   void nonScriptValuesAreUnchanged() {
      assertNull(LayoutTool.getFieldNameByScript(null));
      assertNull(LayoutTool.getFieldNameByScript("Customer:Region"));
   }
}
