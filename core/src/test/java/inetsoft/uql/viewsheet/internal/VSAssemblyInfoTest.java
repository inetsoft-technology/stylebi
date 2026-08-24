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
package inetsoft.uql.viewsheet.internal;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers VSAssemblyInfo.getDefaultFont(), which reads viewsheet.font.size.
 *
 * <p>getDefaultFont() is reached from the default-format construction of every assembly type
 * and every chart descriptor (AxisDescriptor, LegendDescriptor, PlotDescriptor, TitleDescriptor,
 * ChartRefImpl, GraphTarget), so an exception raised here failed the layout of any viewsheet at
 * all, in a subsystem far from the property that caused it and long after it was written.
 *
 * <p>SreeEnv is mocked statically rather than configured: getDefaultFont() is a static with no
 * collaborators, and leaving SreeEnv.isInitialized() at its default false makes StyleFont fall
 * back to its own font family without needing an application context.
 */
@Tag("core")
class VSAssemblyInfoTest {
   @Test
   void unsetPropertyLeavesTheCallerSizeAlone() {
      assertEquals(11, sizeWith(null), "an unset viewsheet.font.size must not adjust the size");
   }

   @Test
   void absoluteValueReplacesTheCallerSize() {
      assertEquals(14, sizeWith("14"));
   }

   @Test
   void relativeValuesAdjustTheCallerSize() {
      assertEquals(13, sizeWith("+2"), "a leading + must add to the size passed in");
      assertEquals(9, sizeWith("-2"), "a leading - must subtract from the size passed in");
   }

   @ParameterizedTest(name = "viewsheet.font.size [{0}] does not throw")
   @ValueSource(strings = { "12pt", "large", "", " ", "+", "-", "+x", "-x", "1.5", "12,0" })
   void aValueThatIsNotANumberKeepsTheCallerSize(String propertyValue) {
      // this is the whole of the reported defect: an unguarded Integer.parseInt raised
      // NumberFormatException out of default-format construction while a dashboard was being
      // laid out, so the property was accepted when written and failed only when something
      // later asked for a font
      assertEquals(11, assertDoesNotThrow(() -> sizeWith(propertyValue)),
                   "a viewsheet.font.size that is not a number must leave the size passed in " +
                   "intact rather than propagate out of default-format construction");
   }

   @ParameterizedTest(name = "viewsheet.font.size [{0}] clamps to 1pt")
   @ValueSource(strings = { "0", "-11", "-20", "-999" })
   void aSizeReducedToZeroOrBelowIsClampedToOnePoint(String propertyValue) {
      // -20 against an 11pt base yields -9; a non-positive size is the same operator error as
      // an unparseable one and should not reach StyleFont to fail further downstream
      assertEquals(1, sizeWith(propertyValue),
                   "a computed size of zero or less must be clamped to a usable size");
   }

   @Test
   void styleIsPassedThroughUnchanged() {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("viewsheet.font.size")).thenReturn("14");

         assertEquals(Font.BOLD, VSAssemblyInfo.getDefaultFont(Font.BOLD, 11).getStyle(),
                      "only the size is derived from the property");
      }
   }

   @Test
   void invalidValueWarnsNamingThePropertyAndTheValue() {
      List<ILoggingEvent> events = captureWarnings(() -> sizeWith("12pt"));

      assertEquals(1, events.size(), "the fallback must report itself exactly once");

      ILoggingEvent event = events.get(0);
      assertEquals(Level.WARN, event.getLevel(), "the fallback must be reported at WARN");

      String message = event.getFormattedMessage();
      assertTrue(message.contains("viewsheet.font.size"),
                 "the warning must name the property so the cause is discoverable: " + message);
      assertTrue(message.contains("12pt"),
                 "the warning must quote the offending value: " + message);
   }

   @Test
   void clampWarnsNamingThePropertyAndTheValue() {
      List<ILoggingEvent> events = captureWarnings(() -> sizeWith("-31"));

      assertEquals(1, events.size(), "the clamp must report itself exactly once");

      String message = events.get(0).getFormattedMessage();
      assertTrue(message.contains("viewsheet.font.size"),
                 "the warning must name the property: " + message);
      assertTrue(message.contains("-31"),
                 "the warning must quote the offending value: " + message);
   }

   @Test
   void repeatedCallsWithTheSameBadValueWarnOnce() {
      // getDefaultFont() is called upwards of thirty times per sheet -- once per assembly
      // default format and once per chart descriptor -- so warning on each call would repeat
      // for every object on every viewsheet opened
      List<ILoggingEvent> events = captureWarnings(() -> {
         sizeWith("not-a-size");
         sizeWith("not-a-size");
         return sizeWith("not-a-size");
      });

      assertEquals(1, events.size(),
                   "the same bad value must be reported once, not once per call");
   }

   @Test
   void correctingAndThenReintroducingABadValueWarnsAgain() {
      // the suppression must not outlive the value it was suppressing: an operator who sets a
      // bad value, sees the warning, corrects it, and later reverts to the same value is owed
      // the warning a second time, since that revert is otherwise applied in total silence
      List<ILoggingEvent> events = captureWarnings(() -> {
         sizeWith("reverted-bad-size");
         sizeWith("14");
         return sizeWith("reverted-bad-size");
      });

      assertEquals(2, events.size(), "a value honoured in between must re-arm the warning");
   }

   @Test
   void removingThePropertyAlsoReArmsTheWarning() {
      List<ILoggingEvent> events = captureWarnings(() -> {
         sizeWith("removed-bad-size");
         sizeWith(null);
         return sizeWith("removed-bad-size");
      });

      assertEquals(2, events.size(), "deleting the property must re-arm the warning too");
   }

   @Test
   void aDifferentBadValueWarnsAgain() {
      List<ILoggingEvent> events = captureWarnings(() -> {
         sizeWith("first-bad-size");
         return sizeWith("second-bad-size");
      });

      assertEquals(2, events.size(),
                   "suppressing a repeat must not suppress a newly configured bad value");
   }

   /**
    * Resolves the default font size for an 11pt caller under the given viewsheet.font.size.
    */
   private static int sizeWith(String propertyValue) {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("viewsheet.font.size")).thenReturn(propertyValue);

         return VSAssemblyInfo.getDefaultFont(Font.PLAIN, 11).getSize();
      }
   }

   /**
    * Runs the given code with a capturing appender attached to the VSAssemblyInfo logger and
    * returns what it logged. Each test uses a property value of its own, so the suppression
    * state left behind by an earlier test cannot hide the first warning expected here.
    */
   private static List<ILoggingEvent> captureWarnings(Supplier<Integer> body) {
      Logger logger = (Logger) LoggerFactory.getLogger(VSAssemblyInfo.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      try {
         body.get();
         return List.copyOf(appender.list);
      }
      finally {
         logger.detachAppender(appender);
      }
   }
}
