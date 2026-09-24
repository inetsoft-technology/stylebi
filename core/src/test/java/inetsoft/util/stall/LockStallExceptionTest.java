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
package inetsoft.util.stall;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
public class LockStallExceptionTest {
   @Test
   public void messageNamesSiteThreadTimeAndDump() {
      LockStallException ex = new LockStallException("SummaryFilter.waitForRow", "worker-1", 301234,
                                                     "/logs/stall-dump-1.txt");

      assertEquals("SummaryFilter.waitForRow", ex.getSite());
      assertEquals("worker-1", ex.getThreadName());
      assertEquals(301234, ex.getStalledMillis());
      assertEquals("/logs/stall-dump-1.txt", ex.getDumpPath());
      assertTrue(ex.getMessage().contains("SummaryFilter.waitForRow"));
      assertTrue(ex.getMessage().contains("worker-1"));
      assertTrue(ex.getMessage().contains("301234 ms"));
      assertTrue(ex.getMessage().contains("/logs/stall-dump-1.txt"));
   }

   @Test
   public void copyKeepsFieldsAndCause() {
      LockStallException original = new LockStallException("site", "t", 5, null);
      LockStallException copy = new LockStallException(original);

      assertSame(original, copy.getCause());
      assertEquals(original.getMessage(), copy.getMessage());
      assertEquals("site", copy.getSite());
      assertEquals("t", copy.getThreadName());
      assertEquals(5, copy.getStalledMillis());
      assertNull(copy.getDumpPath());
   }
}
