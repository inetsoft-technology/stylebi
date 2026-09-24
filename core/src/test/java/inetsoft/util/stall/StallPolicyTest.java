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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the lock-stall watchdog settings (bug #76967).
 */
@Tag("core")
public class StallPolicyTest {
   @AfterEach
   public void tearDown() {
      StallPolicy.setOverride(null);
   }

   @Test
   public void defaultsWhenUnset() {
      File logDir = new File("logs");
      StallPolicy policy = StallPolicy.fromProperties(new HashMap<String, String>()::get, logDir);

      assertEquals(StallPolicy.Mode.FAIL, policy.getMode());
      assertEquals(300000L, policy.getNoProgressMillis());
      assertEquals(30000L, policy.getScanMillis());
      assertEquals(logDir, policy.getDumpDir());
      assertEquals(20, policy.getMaxDumps());
   }

   @Test
   public void readsEveryProperty() {
      Map<String, String> props = new HashMap<>();
      props.put("stall.watchdog.mode", "alert");
      props.put("stall.watchdog.noProgressMillis", "2000");
      props.put("stall.watchdog.scanMillis", "500");
      props.put("stall.watchdog.dumpDir", " dumps ");
      props.put("stall.watchdog.maxDumps", "7");
      StallPolicy policy = StallPolicy.fromProperties(props::get, new File("logs"));

      assertEquals(StallPolicy.Mode.ALERT, policy.getMode());
      assertEquals(2000L, policy.getNoProgressMillis());
      assertEquals(500L, policy.getScanMillis());
      assertEquals(new File("dumps"), policy.getDumpDir());
      assertEquals(7, policy.getMaxDumps());
   }

   @Test
   public void invalidValuesFallBackToDefaults() {
      Map<String, String> props = new HashMap<>();
      props.put("stall.watchdog.mode", "bogus");
      props.put("stall.watchdog.noProgressMillis", "-5");
      props.put("stall.watchdog.scanMillis", "abc");
      props.put("stall.watchdog.maxDumps", "0");
      StallPolicy policy = StallPolicy.fromProperties(props::get, new File("logs"));

      assertEquals(StallPolicy.Mode.FAIL, policy.getMode());
      assertEquals(300000L, policy.getNoProgressMillis());
      assertEquals(30000L, policy.getScanMillis());
      assertEquals(20, policy.getMaxDumps());
   }

   @Test
   public void modeIsCaseInsensitive() {
      assertEquals(StallPolicy.Mode.OFF, StallPolicy.parseMode(" OFF "));
      assertEquals(StallPolicy.Mode.FAIL, StallPolicy.parseMode("Fail"));
      assertEquals(StallPolicy.Mode.ALERT, StallPolicy.parseMode("ALERT"));
   }

   @Test
   public void overrideWins() {
      StallPolicy policy = new StallPolicy(StallPolicy.Mode.OFF, 1, 1, new File("x"));
      StallPolicy.setOverride(policy);
      assertSame(policy, StallPolicy.get());
      StallPolicy.setOverride(null);
      assertNotSame(policy, StallPolicy.get());
   }
}
