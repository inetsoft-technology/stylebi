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
package inetsoft.web.admin.ai.repository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class AdminAssetExportServiceTest {
   @Test
   void namesUnresolvedInputs() {
      String m = AdminAssetExportService.noResolvedEntriesMessage(List.of("Nope"), 1);
      assertTrue(m.contains("not found: Nope"), m);
      assertFalse(m.contains("()"), m);
      assertFalse(m.contains("exist but contain no"), m);
   }

   @Test
   void existingEmptyFolderSaysSo() {
      String m = AdminAssetExportService.noResolvedEntriesMessage(List.of(), 1);
      assertTrue(m.contains("exist but contain no viewsheets or worksheets"), m);
      assertFalse(m.contains("()"), m);
   }

   @Test
   void mixedNamesBoth() {
      String m = AdminAssetExportService.noResolvedEntriesMessage(List.of("Nope"), 2);
      assertTrue(m.contains("not found: Nope"), m);
      assertTrue(m.contains("other given folders exist"), m);
   }
}
