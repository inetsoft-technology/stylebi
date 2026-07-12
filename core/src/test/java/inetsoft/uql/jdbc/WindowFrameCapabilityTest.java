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
package inetsoft.uql.jdbc;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SQLHelper#supportsWindowFrame(String, boolean, boolean)} and
 * {@link SQLHelper#formatWindowFrameInterval(int, String)}, and the PostgreSQL override.
 *
 * NOTE: as with {@link WindowFunctionCapabilityTest}, a bare helper has no UniformSQL wired to
 * a JDBCDataSource, so uniformSql.getDataSource() is null and any version gate falls back to
 * its permissive default.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WindowFrameCapabilityTest {

   // ── Base SQLHelper: default-deny ────────────────────────────────────────

   @Test
   void base_defaultDeny() {
      SQLHelper h = new SQLHelper();   // generic base; WINDOW_FUNCTION defaults true
      // ROWS + RANGE-peer allowed; RANGE value/date + GROUPS denied
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));   // peer
      assertFalse(h.supportsWindowFrame("RANGE", true, false));   // numeric value offset
      assertFalse(h.supportsWindowFrame("RANGE", true, true));    // date INTERVAL
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   @Test
   void base_interval_ansi() {
      assertEquals("INTERVAL '7 day'", new SQLHelper().formatWindowFrameInterval(7, "day"));
   }

   // ── PostgreSQL: opts into everything (byte-parity anchor) ───────────────

   @Test
   void postgres_full() {
      PostgreSQLHelper h = new PostgreSQLHelper();
      assertTrue(h.supportsWindowFrame("RANGE", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", true, true));
      assertTrue(h.supportsWindowFrame("GROUPS", false, false));   // PG >= 11 (default permissive when version unknown)
      assertEquals("INTERVAL '7 day'", h.formatWindowFrameInterval(7, "day"));   // inherits base literal
   }
}
