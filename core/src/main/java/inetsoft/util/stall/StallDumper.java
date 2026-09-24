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

import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Writes the thread dumps of the lock-stall watchdog (bug #76967), at most one per
 * {@code minIntervalMillis} and {@link Kind}: a lock cycle usually trips several waiters at
 * once, and they all get the path of the same dump. JVM deadlocks and stalled waits are
 * rate-limited separately, so a deadlock that stays around (it never resolves) does not keep
 * a concurrent stall from getting its own dump.
 */
public final class StallDumper {
   /**
    * @param nanoClock         the clock of the dump start times, which must be the nano clock
    *                          of the {@link WaitRegistry} that uses this dumper: a waiter (and
    *                          the watchdog) compares the start of the last dump with the
    *                          stall's progress time to tell whether that dump shows the stall.
    *                          In production both are {@code System::nanoTime}.
    * @param dir               the directory to write the dumps to.
    * @param minIntervalMillis the minimum time between two dump attempts.
    */
   public StallDumper(LongSupplier nanoClock, Supplier<File> dir, long minIntervalMillis) {
      this.nanoClock = nanoClock;
      this.dir = dir;
      this.minIntervalNanos = TimeUnit.MILLISECONDS.toNanos(minIntervalMillis);
   }

   /**
    * Get the dumper of the server, writing to {@code stall.watchdog.dumpDir}.
    */
   public static StallDumper global() {
      return GLOBAL;
   }

   /**
    * Same as {@link #dump(Kind, String)} for a stalled wait ({@link Kind#WAIT}).
    */
   public String dump(String reason) {
      return dump(Kind.WAIT, reason);
   }

   /**
    * Write a dump of all threads headed by {@code reason}, unless one of the same kind was
    * attempted less than the minimum interval ago. A failed attempt (e.g. an unwritable dump
    * directory) also starts the back-off window of its kind and is not retried until the
    * window elapses, so a stall that keeps tripping waiters does not turn into a stream of
    * failing dump attempts and error logs.
    *
    * @param kind   what the dump is for, each kind has its own window and last dump.
    * @param reason the head of the dump.
    *
    * @return the path of the new or of the most recently written dump of the kind, or
    *         {@code null} if none has been written yet (either because the last attempt
    *         failed, or none was made).
    */
   public synchronized String dump(Kind kind, String reason) {
      Window window = windows.get(kind);
      long now = nanoClock.getAsLong();

      if(window.attempted && now - window.lastAttemptNanos < minIntervalNanos) {
         return window.lastPath;
      }

      window.attempted = true;
      window.lastAttemptNanos = now;
      File file = null;

      // everything that can fail is guarded, the dump directory lookup included: the dumper
      // never throws into a waiter, which must fail with its LockStallException
      try {
         File folder = dir.get();
         file = new File(folder, "stall-dump-" + LocalDateTime.now().format(FORMAT) + "-" +
            (count + 1) + ".txt");
         Files.createDirectories(folder.toPath());

         try(PrintStream out = new PrintStream(new FileOutputStream(file), false,
                                               StandardCharsets.UTF_8))
         {
            out.println(reason);
            out.println();
            Tool.dumpAllThreads(out);
         }

         count++;
         window.count++;
         window.lastPath = file.getAbsolutePath();
         window.lastDumpStartNanos = now;
         LOG.warn("Lock stall thread dump written to {}: {}", window.lastPath, reason);
         return window.lastPath;
      }
      catch(IOException | RuntimeException ex) {
         LOG.error("Failed to write the lock stall thread dump " +
                      (file == null ? "(no dump directory)" : file), ex);
         return window.lastPath;
      }
   }

   /**
    * Forget the last dumps and the back-off windows of all kinds, so a test does not see the
    * dump of an earlier test. The dump counts are kept.
    */
   synchronized void resetForTest() {
      for(Window window : windows.values()) {
         window.attempted = false;
         window.lastAttemptNanos = 0;
         window.lastPath = null;
         window.lastDumpStartNanos = 0;
      }
   }

   /**
    * Get the number of dumps written, of all kinds.
    */
   public synchronized int getDumpCount() {
      return count;
   }

   /**
    * Get the number of dumps of one kind written.
    */
   public synchronized int getDumpCount(Kind kind) {
      return windows.get(kind).count;
   }

   /**
    * Same as {@link #getLastDump(Kind)} for a stalled wait ({@link Kind#WAIT}).
    */
   public LastDump getLastDump() {
      return getLastDump(Kind.WAIT);
   }

   /**
    * Get the most recently written dump of a kind and when it was started, read together.
    *
    * @return the dump, or {@code null} if none of the kind has been written.
    */
   public synchronized LastDump getLastDump(Kind kind) {
      Window window = windows.get(kind);
      return window.lastPath == null ? null :
         new LastDump(window.lastPath, window.lastDumpStartNanos);
   }

   /**
    * What a dump is for. Each kind has its own rate-limit window and last dump.
    */
   public enum Kind {
      /**
       * A JVM-level deadlock found by the watchdog.
       */
      DEADLOCK,
      /**
       * A stalled registered wait (waiter or watchdog), or a probe finding.
       */
      WAIT
   }

   /**
    * The rate-limit window and the last dump of one kind, guarded by the dumper's monitor.
    */
   private static final class Window {
      private boolean attempted;
      private long lastAttemptNanos;
      private String lastPath;
      private long lastDumpStartNanos;
      private int count;
   }

   /**
    * A written dump.
    *
    * @param path       the dump file.
    * @param startNanos when writing it began, before the thread snapshot, on the dumper's
    *                   clock. A failed attempt never moves it.
    */
   public record LastDump(String path, long startNanos) {
   }

   private static final Logger LOG = LoggerFactory.getLogger(StallDumper.class);
   private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
   private static final StallDumper GLOBAL =
      new StallDumper(System::nanoTime, () -> StallPolicy.get().getDumpDir(), 60000L);

   private final LongSupplier nanoClock;
   private final Supplier<File> dir;
   private final long minIntervalNanos;
   private final Map<Kind, Window> windows = new EnumMap<>(Kind.class);
   private int count;

   {
      for(Kind kind : Kind.values()) {
         windows.put(kind, new Window());
      }
   }
}
