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
package inetsoft.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.web.composer.vs.controller.ExportController;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79795: a client that closed the connection while a viewsheet export was being written
 * was logged at ERROR. The failure only reaches the logger as
 * AsyncRequestNotUsableException &lt;- ClientAbortException &lt;- IOException("Broken pipe").
 */
@Tag("core")
class ClientAbortLoggingTest {
   private ListAppender<ILoggingEvent> appender;
   private Logger exportLogger;
   private Level oldLevel;

   @BeforeEach
   void setUp() {
      exportLogger = (Logger) LoggerFactory.getLogger(ExportController.class);
      oldLevel = exportLogger.getLevel();
      exportLogger.setLevel(Level.DEBUG);
      appender = new ListAppender<>();
      appender.start();
      exportLogger.addAppender(appender);
   }

   @AfterEach
   void tearDown() {
      exportLogger.detachAppender(appender);
      exportLogger.setLevel(oldLevel);
   }

   /**
    * Tomcat is not on core's classpath; define a stand-in with Tomcat's exact class name and
    * shape (an IOException) in its own class loader, so it can never clash with the real one.
    */
   private static final Class<? extends IOException> ABORT_CLASS = new ByteBuddy()
      .subclass(IOException.class)
      .name("org.apache.catalina.connector.ClientAbortException")
      .make()
      .load(ClientAbortLoggingTest.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
      .getLoaded();

   private static IOException clientAbort(Throwable cause) {
      try {
         return ABORT_CLASS.getConstructor(Throwable.class).newInstance(cause);
      }
      catch(ReflectiveOperationException e) {
         throw new AssertionError(e);
      }
   }

   private static Exception ticketChain() {
      return new AsyncRequestNotUsableException(
         "ServletOutputStream failed to write: java.io.IOException: Broken pipe",
         clientAbort(new IOException("Broken pipe")));
   }

   private void logCommitted(Exception e) throws Exception {
      Method m = ExportController.class.getDeclaredMethod(
         "logCommittedFailure", String.class, Exception.class);
      m.setAccessible(true);
      m.invoke(null, "gallery/report", e);
   }

   private List<ILoggingEvent> events(Level level) {
      return appender.list.stream().filter(ev -> ev.getLevel() == level).toList();
   }

   @Test
   void ticketChainIsLoggedAtDebugNotError() throws Exception {
      logCommitted(ticketChain());

      assertTrue(events(Level.ERROR).isEmpty());
      assertEquals(1, events(Level.DEBUG).size());
   }

   @Test
   void bareSocketBrokenPipeOnCommittedWriteIsDebug() throws Exception {
      logCommitted(new IOException("Broken pipe"));
      logCommitted(new IOException("Connection reset"));
      logCommitted(new IOException("An established connection was aborted by the software in "
                                   + "your host machine"));

      assertTrue(events(Level.ERROR).isEmpty());
      assertEquals(3, events(Level.DEBUG).size());
   }

   @Test
   void realIoFailureStaysAtError() throws Exception {
      logCommitted(new IOException("No space left on device"));
      logCommitted(new AsyncRequestNotUsableException(
         "ServletOutputStream failed to write: java.io.IOException: disk",
         new IOException("No space left on device")));
      logCommitted(new RuntimeException("boom"));

      assertEquals(3, events(Level.ERROR).size());
      assertTrue(events(Level.DEBUG).isEmpty());
   }

   @Test
   void classRuleWalksWholeChainAndOnlyByClass() {
      assertTrue(GlobalExceptionHandler.isClientAbort(ticketChain()));
      assertTrue(GlobalExceptionHandler.isClientAbort(
         new RuntimeException(new IllegalStateException(ticketChain()))));

      Exception suppressed = new IOException("x");
      suppressed.addSuppressed(clientAbort(null));
      assertTrue(GlobalExceptionHandler.isClientAbort(suppressed));

      // wrapper alone, and message text alone, are not class evidence
      assertFalse(GlobalExceptionHandler.isClientAbort(
         new AsyncRequestNotUsableException("x", new IOException("disk"))));
      assertFalse(GlobalExceptionHandler.isClientAbort(new IOException("Broken pipe")));
      assertFalse(GlobalExceptionHandler.isClientAbort(null));
   }

   @Test
   void cyclicChainTerminates() {
      Exception a = new RuntimeException("a");
      Exception b = new RuntimeException("b", a);
      a.initCause(b);

      assertFalse(GlobalExceptionHandler.isClientAbort(a));
      assertFalse(GlobalExceptionHandler.isClientAbortOnWrite(a));
   }
}
