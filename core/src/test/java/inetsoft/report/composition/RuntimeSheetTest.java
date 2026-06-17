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
package inetsoft.report.composition;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.AbstractSheet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("core")
@SreeHome()
class RuntimeSheetTest {
   private String saved;

   @BeforeEach
   void saveProperty() {
      saved = SreeEnv.getProperty("viewsheet.heartbeat.timeout");
      SreeEnv.setProperty("viewsheet.heartbeat.timeout", null);
   }

   @AfterEach
   void restoreProperty() {
      SreeEnv.setProperty("viewsheet.heartbeat.timeout", saved);
   }

   @Test
   void defaultsToThreeMinutesWhenUnset() {
      assertEquals(180000L, RuntimeSheet.getHeartbeatTimeout());
   }

   @Test
   void readsConfiguredValue() {
      SreeEnv.setProperty("viewsheet.heartbeat.timeout", "600000");
      assertEquals(600000L, RuntimeSheet.getHeartbeatTimeout());
   }

   @Test
   void fallsBackToDefaultOnInvalidValue() {
      SreeEnv.setProperty("viewsheet.heartbeat.timeout", "not-a-number");
      assertEquals(180000L, RuntimeSheet.getHeartbeatTimeout());
   }

   @Test
   void clampsValuesBelowMinimumToThreeMinutes() {
      SreeEnv.setProperty("viewsheet.heartbeat.timeout", "0");
      assertEquals(180000L, RuntimeSheet.getHeartbeatTimeout());
      SreeEnv.setProperty("viewsheet.heartbeat.timeout", "-1");
      assertEquals(180000L, RuntimeSheet.getHeartbeatTimeout());
      SreeEnv.setProperty("viewsheet.heartbeat.timeout", "60000");
      assertEquals(180000L, RuntimeSheet.getHeartbeatTimeout());
   }

   @Test
   void heartbeatExpiresWhenOlderThanTimeout() {
      long now = System.currentTimeMillis();
      assertFalse(RuntimeSheet.isHeartbeatExpired(now, now));
      assertTrue(RuntimeSheet.isHeartbeatExpired(now - 200000, now));
   }

   @Test
   void raisedTimeoutKeepsOlderHeartbeatAlive() {
      SreeEnv.setProperty("viewsheet.heartbeat.timeout", "600000");
      long now = System.currentTimeMillis();
      assertFalse(RuntimeSheet.isHeartbeatExpired(now - 200000, now));
   }

   @Test
   void isTimeoutFreshSheetIsNotTimedOut() {
      assertFalse(newSheet().isTimeout());
   }

   @Test
   void isTimeoutReturnsTrueWhenIdleExceeded() {
      RuntimeSheet sheet = newSheet();
      sheet.setAccessed(1L);
      assertTrue(sheet.isTimeout());
   }

   @Test
   void isTimeoutReturnsTrueWhenHeartbeatExpired() {
      RuntimeSheet sheet = newSheet();
      sheet.heartbeat = 1L;
      sheet.setAccessed(System.currentTimeMillis());
      assertTrue(sheet.isTimeout());
   }

   @Test
   void isTimeoutReturnsFalseWhenBothFresh() {
      RuntimeSheet sheet = newSheet();
      sheet.setAccessed(System.currentTimeMillis());
      assertFalse(sheet.isTimeout());
   }

   private static RuntimeSheet newSheet() {
      return new RuntimeSheet() {
         @Override public boolean undo(ChangedAssemblyList clist) { return false; }
         @Override public boolean redo(ChangedAssemblyList clist) { return false; }
         @Override public void rollback() {}
         @Override public AbstractSheet getSheet() { return null; }
         @Override public int getMode() { return 0; }
         @Override public boolean isRuntime() { return false; }
         @Override public boolean isPreview() { return false; }
         @Override RuntimeSheetState saveState(ObjectMapper mapper) { return null; }
      };
   }
}
