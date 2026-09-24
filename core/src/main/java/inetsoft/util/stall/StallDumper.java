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
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Writes the thread dumps of the lock-stall watchdog (bug #76967), at most one per
 * {@code minIntervalMillis}: a lock cycle usually trips several waiters at once, and they all
 * get the path of the same dump.
 */
public final class StallDumper {
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
    * Write a dump of all threads headed by {@code reason}, unless one was attempted less than
    * the minimum interval ago. A failed attempt (e.g. an unwritable dump directory) also starts
    * the back-off window and is not retried until the window elapses, so a stall that keeps
    * tripping waiters does not turn into a stream of failing dump attempts and error logs.
    *
    * @return the path of the new or of the most recently written dump, or {@code null} if none
    *         has been written yet (either because the last attempt failed, or none was made).
    */
   public synchronized String dump(String reason) {
      long now = nanoClock.getAsLong();

      if(attempted && now - lastAttemptNanos < minIntervalNanos) {
         return lastPath;
      }

      attempted = true;
      lastAttemptNanos = now;
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
         lastPath = file.getAbsolutePath();
         lastDumpStartNanos = now;
         LOG.warn("Lock stall thread dump written to {}: {}", lastPath, reason);
         return lastPath;
      }
      catch(IOException | RuntimeException ex) {
         LOG.error("Failed to write the lock stall thread dump " +
                      (file == null ? "(no dump directory)" : file), ex);
         return lastPath;
      }
   }

   /**
    * Get the number of dumps written.
    */
   public synchronized int getDumpCount() {
      return count;
   }

   /**
    * Get the most recently written dump and when it was started, read together.
    *
    * @return the dump, or {@code null} if none has been written.
    */
   public synchronized LastDump getLastDump() {
      return lastPath == null ? null : new LastDump(lastPath, lastDumpStartNanos);
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
   private boolean attempted;
   private long lastAttemptNanos;
   private String lastPath;
   private long lastDumpStartNanos;
   private int count;
}
