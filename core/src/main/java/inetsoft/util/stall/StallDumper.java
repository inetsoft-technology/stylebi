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
    * Write a dump of all threads headed by {@code reason}, unless one was written less than
    * the minimum interval ago.
    *
    * @return the path of the new or of the recent dump, or {@code null} if it could not be
    *         written.
    */
   public synchronized String dump(String reason) {
      long now = nanoClock.getAsLong();

      if(lastPath != null && now - lastNanos < minIntervalNanos) {
         return lastPath;
      }

      File folder = dir.get();
      File file = new File(folder, "stall-dump-" + LocalDateTime.now().format(FORMAT) + "-" +
         (count + 1) + ".txt");

      try {
         Files.createDirectories(folder.toPath());

         try(PrintStream out = new PrintStream(new FileOutputStream(file), false,
                                               StandardCharsets.UTF_8))
         {
            out.println(reason);
            out.println();
            Tool.dumpAllThreads(out);
         }

         count++;
         lastNanos = now;
         lastPath = file.getAbsolutePath();
         LOG.warn("Lock stall thread dump written to {}: {}", lastPath, reason);
         return lastPath;
      }
      catch(IOException | RuntimeException ex) {
         LOG.error("Failed to write the lock stall thread dump " + file, ex);
         return null;
      }
   }

   /**
    * Get the number of dumps written.
    */
   public synchronized int getDumpCount() {
      return count;
   }

   private static final Logger LOG = LoggerFactory.getLogger(StallDumper.class);
   private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
   private static final StallDumper GLOBAL =
      new StallDumper(System::nanoTime, () -> StallPolicy.get().getDumpDir(), 60000L);

   private final LongSupplier nanoClock;
   private final Supplier<File> dir;
   private final long minIntervalNanos;
   private long lastNanos;
   private String lastPath;
   private int count;
}
