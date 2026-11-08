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
package inetsoft.web.admin.ai;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class StorageBackupPolicyTest {
   @Test void onlyPureCreatesSkipTheBackup() {
      assertFalse(StorageBackupPolicy.requiresBackup(List.of("create", "create"),
                                                     "create"::equals));
   }

   @Test void anyNonCreateKeepsTheBackup() {
      assertTrue(StorageBackupPolicy.requiresBackup(List.of("create", "delete"),
                                                    "create"::equals));
      assertTrue(StorageBackupPolicy.requiresBackup(List.of("update"), "create"::equals));
   }

   @Test void emptyOrNullPlanKeepsTheBackup() {
      assertTrue(StorageBackupPolicy.requiresBackup(List.<String>of(), "create"::equals));
      assertTrue(StorageBackupPolicy.requiresBackup(null, s -> true));
   }
}
