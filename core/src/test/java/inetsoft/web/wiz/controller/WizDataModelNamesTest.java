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

import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class WizDataModelNamesTest {
   @Test
   void acceptsOrdinaryAndChineseNames() {
      assertDoesNotThrow(() -> WizDataModelNames.requireValidModelName("Sales 2024"));
      assertDoesNotThrow(() -> WizDataModelNames.requireValidModelName("\u9500\u552e\u6a21\u578b"));
      assertDoesNotThrow(() -> WizDataModelNames.requireValidFolderName("Reports"));
   }

   @Test
   void refusesEveryRuleNamingIt() {
      for(String bad : new String[] { "a/b", "a\\b", "a^b", "a:b", "a*b", "a\"b", "a|b", "a<b",
                                      "a>b", "a?b" })
      {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> WizDataModelNames.requireValidModelName(bad), bad);
         assertTrue(e.getMessage().contains("must not contain any of"), e.getMessage());
      }

      assertThrows(IllegalArgumentException.class, () -> WizDataModelNames.requireValidModelName(null));
      assertThrows(IllegalArgumentException.class, () -> WizDataModelNames.requireValidModelName("  "));
      assertThrows(IllegalArgumentException.class, () -> WizDataModelNames.requireValidModelName(" a"));
      assertThrows(IllegalArgumentException.class, () -> WizDataModelNames.requireValidModelName("a "));
      assertThrows(IllegalArgumentException.class,
                   () -> WizDataModelNames.requireValidModelName("a\u0007b"));
      assertThrows(IllegalArgumentException.class, () -> WizDataModelNames.requireValidModelName("Domain"));
      assertThrows(IllegalArgumentException.class,
                   () -> WizDataModelNames.requireValidModelName("Data Model"));
      assertThrows(IllegalArgumentException.class, () -> WizDataModelNames.requireValidFolderName("a/b"));
      assertThrows(IllegalArgumentException.class, () -> WizDataModelNames.requireValidFolderName(".."));
   }

   @Test
   void uniqueInSourceNamesTheExistingKindOfAnyOfTheThreeRegistries() {
      XDataModel dm = mock(XDataModel.class);
      when(dm.getDataSource()).thenReturn("D");
      when(dm.getLogicalModel("lm")).thenReturn(mock(XLogicalModel.class));
      when(dm.getPartition("pv")).thenReturn(mock(XPartition.class));
      when(dm.getVirtualPrivateModel("vp")).thenReturn(mock(VirtualPrivateModel.class));

      assertEquals("logicalModel", kindOfTaken(dm, "lm"));
      assertEquals("physicalView", kindOfTaken(dm, "pv"));
      assertEquals("vpm", kindOfTaken(dm, "vp"));
      assertDoesNotThrow(() -> WizDataModelNames.requireUniqueInSource(dm, "free"));
   }

   private static String kindOfTaken(XDataModel dm, String name) {
      WizDataModelErrors.DmFailure failure = assertThrows(WizDataModelErrors.DmFailure.class,
         () -> WizDataModelNames.requireUniqueInSource(dm, name));
      assertEquals(WizDataModelWire.DUPLICATE_NAME, failure.reason());
      return failure.toBody().existingKind();
   }
}
