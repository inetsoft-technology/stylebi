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
package inetsoft.web.wiz.controller;

import inetsoft.web.wiz.controller.WizDataModelErrors.DmFailure;
import inetsoft.web.wiz.controller.WizPhysicalViewMapper.JoinDef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #82182: a FULL_OUTER join is refused on a database that does not support it. */
@Tag("core")
class WizPhysicalViewFullOuterTest {
   private static JoinDef join(String type) {
      return new JoinDef("A", "id", "B", "id", type, "and", false, 0, "MANY_TO_ONE", false);
   }

   @Test
   void fullOuterRefusedWhenUnsupported() {
      DmFailure e = assertThrows(DmFailure.class, () -> WizPhysicalViewController.checkFullOuter(
         "Orders", List.of(join("FULL_OUTER")), Set.of(), false));
      assertTrue(e.getMessage().contains("FULL_OUTER"));
      assertTrue(e.getMessage().contains("Orders"));
   }

   @Test
   void fullOuterAllowedWhenSupported() {
      assertDoesNotThrow(() -> WizPhysicalViewController.checkFullOuter(
         "pg", List.of(join("FULL_OUTER")), Set.of(), true));
   }

   @Test
   void otherTypesAndAlreadyStoredJoinsAllowed() {
      assertDoesNotThrow(() -> WizPhysicalViewController.checkFullOuter(
         "Orders", List.of(join("EQUAL"), join("LEFT_OUTER")), Set.of(), false));
      assertDoesNotThrow(() -> WizPhysicalViewController.checkFullOuter(
         "Orders", List.of(join("FULL_OUTER")), Set.of(join("FULL_OUTER").key()), false));
   }
}
