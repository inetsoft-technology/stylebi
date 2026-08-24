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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.AbstractSheet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

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
   void defaultMatchesTheDocumentedConstant() {
      // the constant is the only place the default lives -- no viewsheet.heartbeat.timeout
      // entry ships in defaults.properties -- so it doubles as the documented floor
      assertEquals(180000L, RuntimeSheet.DEFAULT_HEARTBEAT_TIMEOUT);
      assertEquals(RuntimeSheet.DEFAULT_HEARTBEAT_TIMEOUT, RuntimeSheet.getHeartbeatTimeout());
   }

   @Test
   void clampWarnsNamingThePropertyTheValueAndTheFloor() {
      // a value below the floor is read back from Settings > All Properties exactly as written
      // while the behaviour stays at three minutes; without this warning nothing tells an
      // operator that the value they shortened is not the one in force
      SreeEnv.setProperty("viewsheet.heartbeat.timeout", "60001");

      List<ILoggingEvent> events = captureWarnings(RuntimeSheet::getHeartbeatTimeout);

      assertEquals(1, events.size(), "the clamp must report itself exactly once");

      ILoggingEvent event = events.get(0);
      assertEquals(Level.WARN, event.getLevel(), "the clamp must be reported at WARN");

      String message = event.getFormattedMessage();
      assertTrue(message.contains("viewsheet.heartbeat.timeout"),
                 "the warning must name the property so the cause is discoverable: " + message);
      assertTrue(message.contains("60001"),
                 "the warning must quote the configured value: " + message);
      assertTrue(message.contains("180000"),
                 "the warning must state the floor actually applied: " + message);
   }

   @Test
   void invalidValueWarnsNamingThePropertyAndTheValue() {
      SreeEnv.setProperty("viewsheet.heartbeat.timeout", "3 minutes");

      List<ILoggingEvent> events = captureWarnings(RuntimeSheet::getHeartbeatTimeout);

      assertEquals(1, events.size(), "the fallback must report itself exactly once");

      String message = events.get(0).getFormattedMessage();
      assertTrue(message.contains("viewsheet.heartbeat.timeout"),
                 "the warning must name the property: " + message);
      assertTrue(message.contains("3 minutes"),
                 "the warning must quote the offending value: " + message);
   }

   @Test
   void repeatedReadsOfTheSameBadValueWarnOnce() {
      // getHeartbeatTimeout() is reached from isTimeout(), which RecycleTask calls once per open
      // sheet on every three-minute sweep, so warning on each read floods the log in proportion
      // to the number of open sheets
      SreeEnv.setProperty("viewsheet.heartbeat.timeout", "not-a-number-either");

      List<ILoggingEvent> events = captureWarnings(() -> {
         RuntimeSheet.getHeartbeatTimeout();
         RuntimeSheet.getHeartbeatTimeout();
         RuntimeSheet.getHeartbeatTimeout();
      });

      assertEquals(1, events.size(),
                   "the same bad value must be reported once, not once per read");
   }

   @Test
   void correctingAndThenReintroducingABadValueWarnsAgain() {
      // the suppression must not outlive the value it was suppressing: an operator who sets a
      // bad value, sees the warning, corrects it, and later reverts to the same value is owed
      // the warning a second time, since that revert is otherwise applied in total silence
      SreeEnv.setProperty("viewsheet.heartbeat.timeout", "reverted-bad-value");

      List<ILoggingEvent> events = captureWarnings(() -> {
         RuntimeSheet.getHeartbeatTimeout();
         SreeEnv.setProperty("viewsheet.heartbeat.timeout", "600000");
         RuntimeSheet.getHeartbeatTimeout();
         SreeEnv.setProperty("viewsheet.heartbeat.timeout", "reverted-bad-value");
         RuntimeSheet.getHeartbeatTimeout();
      });

      assertEquals(2, events.size(),
                   "a value honoured in between must re-arm the warning");
   }

   @Test
   void removingThePropertyAlsoReArmsTheWarning() {
      SreeEnv.setProperty("viewsheet.heartbeat.timeout", "removed-bad-value");

      List<ILoggingEvent> events = captureWarnings(() -> {
         RuntimeSheet.getHeartbeatTimeout();
         SreeEnv.setProperty("viewsheet.heartbeat.timeout", null);
         RuntimeSheet.getHeartbeatTimeout();
         SreeEnv.setProperty("viewsheet.heartbeat.timeout", "removed-bad-value");
         RuntimeSheet.getHeartbeatTimeout();
      });

      assertEquals(2, events.size(),
                   "deleting the property must re-arm the warning too");
   }

   @Test
   void aDifferentBadValueWarnsAgain() {
      SreeEnv.setProperty("viewsheet.heartbeat.timeout", "first-bad-value");

      List<ILoggingEvent> events = captureWarnings(() -> {
         RuntimeSheet.getHeartbeatTimeout();
         SreeEnv.setProperty("viewsheet.heartbeat.timeout", "second-bad-value");
         RuntimeSheet.getHeartbeatTimeout();
      });

      assertEquals(2, events.size(),
                   "suppressing a repeat must not suppress a newly configured bad value");
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

   /**
    * Runs the given code with a capturing appender attached to the RuntimeSheet logger and
    * returns what it logged. Each test uses a property value of its own, so the suppression
    * state left behind by an earlier test cannot hide the first warning expected here.
    */
   private static List<ILoggingEvent> captureWarnings(Runnable body) {
      Logger logger = (Logger) LoggerFactory.getLogger(RuntimeSheet.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      try {
         body.run();
         return List.copyOf(appender.list);
      }
      finally {
         logger.detachAppender(appender);
      }
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
