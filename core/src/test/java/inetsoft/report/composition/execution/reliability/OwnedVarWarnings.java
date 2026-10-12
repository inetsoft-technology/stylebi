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
package inetsoft.report.composition.execution.reliability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import inetsoft.report.script.TableRowScope;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The loss warnings of lens-owned formula vars (Testing #77123, B1 residual) a run logs: the
 * evidence that excuses a B1 difference. {@link TableRowScope} logs one WARN per var of a
 * table when the var reads as undefined on another pooled context (a value that is not kept,
 * a home in use by another thread, a hand-off over its budget, a value that could not be
 * read) or a Date is rebuilt without its properties; it logs it on the thread that reads the
 * var, i.e. the thread running the run. So a run records the warnings of its own thread.
 */
final class OwnedVarWarnings {
   private OwnedVarWarnings() {
   }

   /**
    * Capture the warnings of TableRowScope from now on (idempotent, and again after a logging
    * reconfiguration detached it). Its level is set to WARN so a test that quiets "inetsoft"
    * still sees them; they are not passed on to the console.
    */
   static synchronized void install() {
      Logger logger = (Logger) LoggerFactory.getLogger(TableRowScope.class);

      if(logger.isAttached(APPENDER) && logger.getLevel() == Level.WARN) {
         return;
      }

      if(saved == null) {
         saved = new Object[] { logger.getLevel(), logger.isAdditive() };
      }

      logger.setLevel(Level.WARN);
      logger.setAdditive(false);

      if(!APPENDER.isStarted()) {
         APPENDER.setContext(logger.getLoggerContext());
         APPENDER.start();
      }

      if(!logger.isAttached(APPENDER)) {
         logger.addAppender(APPENDER);
      }
   }

   /**
    * Stop capturing: detach the appender and give the TableRowScope logger back its level and
    * additivity, so later test classes of the fork log its warnings as before.
    */
   static synchronized void uninstall() {
      Logger logger = (Logger) LoggerFactory.getLogger(TableRowScope.class);
      logger.detachAppender(APPENDER);

      if(saved != null) {
         logger.setLevel((Level) saved[0]);
         logger.setAdditive((Boolean) saved[1]);
         saved = null;
      }
   }

   /**
    * Start recording the warnings of the calling thread; {@link Recording#close()} stops it.
    */
   static Recording record() {
      install();
      Recording recording = new Recording();
      RECORDINGS.put(Thread.currentThread(), recording);
      return recording;
   }

   /**
    * Start recording the warnings of every thread, e.g. of a table several threads read: a
    * shared table's rows are computed by whichever reader gets there first, so its warnings
    * belong to the whole read, not to one thread. One such recording at a time;
    * {@link Recording#close()} stops it. Threads that record for themselves still do.
    */
   static Recording recordAllThreads() {
      install();
      Recording recording = new Recording();

      synchronized(OwnedVarWarnings.class) {
         if(all != null) {
            throw new IllegalStateException("already recording every thread");
         }

         all = recording;
      }

      return recording;
   }

   /**
    * @return the warnings logged on a thread that was not recording, e.g. by a thread a run
    * did not expect to read its vars.
    */
   static long unattributed() {
      return UNATTRIBUTED.get();
   }

   /**
    * The warnings of one thread over one run: the kind of loss per var name.
    */
   static final class Recording implements AutoCloseable {
      /**
       * @return the vars that warned, each with what it held (its first warning).
       */
      Map<String, String> lost() {
         synchronized(lost) {
            return new LinkedHashMap<>(lost);
         }
      }

      /**
       * @return the warnings per kind of loss (a var's kind as {@link #lost()} gives it).
       */
      Map<String, Integer> kinds() {
         synchronized(lost) {
            return new LinkedHashMap<>(kinds);
         }
      }

      /**
       * @return the warnings per var (TableRowScope logs one per var of a table).
       */
      Map<String, Integer> counts() {
         synchronized(lost) {
            return new LinkedHashMap<>(counts);
         }
      }

      @Override
      public void close() {
         synchronized(OwnedVarWarnings.class) {
            if(all == this) {
               all = null;
               return;
            }
         }

         RECORDINGS.remove(Thread.currentThread(), this);
      }

      private void add(String var, String kind) {
         synchronized(lost) {
            lost.putIfAbsent(var, kind);
            counts.merge(var, 1, Integer::sum);
            kinds.merge(kind, 1, Integer::sum);
         }
      }

      private final Map<String, String> lost = new LinkedHashMap<>();
      private final Map<String, Integer> counts = new LinkedHashMap<>();
      private final Map<String, Integer> kinds = new LinkedHashMap<>();
   }

   private static final class Capture extends AppenderBase<ILoggingEvent> {
      @Override
      protected void append(ILoggingEvent event) {
         if(!event.getLevel().isGreaterOrEqual(Level.WARN)) {
            return;
         }

         // the line naming new kept copies of a var that already warned: no loss of its own
         if(event.getMessage().contains("was lost again")) {
            return;
         }

         Object[] args = event.getArgumentArray();
         Recording recording = RECORDINGS.get(Thread.currentThread());
         Recording every = all;

         if(recording == null && every == null || args == null || args.length == 0) {
            UNATTRIBUTED.incrementAndGet();
            return;
         }

         String message = event.getMessage();
         // the three warnings of TableRowScope: a home in use, a value not kept, a Date
         // rebuilt from its time value only
         String kind = message.contains("another thread") ? HOME_IN_USE
            : message.contains("holds a Date") ? "a Date " + args[1]
            : args.length > 1 ? String.valueOf(args[1]) : message;

         if(recording != null) {
            recording.add(String.valueOf(args[0]), kind);
         }

         if(every != null) {
            every.add(String.valueOf(args[0]), kind);
         }
      }
   }

   /** what a var whose home another thread held is recorded as */
   static final String HOME_IN_USE = "a home in use by another thread";

   private static final Capture APPENDER = new Capture();
   // the logger's level and additivity before install()
   private static Object[] saved;
   // the recording of every thread's warnings, if any
   private static volatile Recording all;
   private static final Map<Thread, Recording> RECORDINGS = new ConcurrentHashMap<>();
   private static final AtomicLong UNATTRIBUTED = new AtomicLong();
}
