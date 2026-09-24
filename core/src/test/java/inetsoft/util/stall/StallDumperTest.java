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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the lock-stall dump writer and its 1-per-minute rate limit (bug #76967).
 */
@Tag("core")
public class StallDumperTest {
   @Test
   public void writesDumpWithReasonAndThreads() throws Exception {
      StallDumper dumper = new StallDumper(now::get, () -> dumpDir, 60000);
      String path = dumper.dump("reason one");

      assertNotNull(path);
      String text = Files.readString(new File(path).toPath(), StandardCharsets.UTF_8);
      assertTrue(text.startsWith("reason one"));
      assertTrue(text.contains("\"" + Thread.currentThread().getName() + "\""));
      assertEquals(1, dumper.getDumpCount());
   }

   @Test
   public void rateLimitedToOnePerInterval() {
      StallDumper dumper = new StallDumper(now::get, () -> dumpDir, 60000);
      String first = dumper.dump("first");
      advance(30000);
      String second = dumper.dump("second");
      advance(31000);
      String third = dumper.dump("third");

      assertEquals(first, second, "a dump within the interval reuses the recent one");
      assertNotEquals(first, third);
      assertEquals(2, dumper.getDumpCount());
      assertEquals(2, dumpDir.listFiles().length);
   }

   @Test
   public void createsMissingDirectory() {
      File nested = new File(dumpDir, "a/b");
      StallDumper dumper = new StallDumper(now::get, () -> nested, 60000);

      assertNotNull(dumper.dump("nested"));
      assertTrue(nested.isDirectory());
   }

   @Test
   public void backsOffAfterFailedWriteToo() throws Exception {
      // a regular file where a directory is expected: createDirectories() always fails on it.
      File notADir = new File(dumpDir, "not-a-dir");
      Files.createFile(notADir.toPath());
      AtomicInteger attempts = new AtomicInteger();
      StallDumper dumper = new StallDumper(now::get, () -> {
         attempts.incrementAndGet();
         return notADir;
      }, 60000);

      assertNull(dumper.dump("first"));
      advance(1000);
      assertNull(dumper.dump("second"));
      advance(1000);
      assertNull(dumper.dump("third"));

      assertEquals(1, attempts.get(), "a failed attempt should back off, not retry every call");
      assertEquals(0, dumper.getDumpCount());
   }

   @Test
   public void failedDumpKeepsTheLastDumpAndItsStartTime() throws Exception {
      // a regular file where a directory is expected makes the second write fail
      File notADir = new File(dumpDir, "not-a-dir");
      Files.createFile(notADir.toPath());
      AtomicInteger calls = new AtomicInteger();
      StallDumper dumper = new StallDumper(now::get,
         () -> calls.incrementAndGet() > 1 ? notADir : dumpDir, 60000);
      assertNull(dumper.getLastDump());

      long start = now.get();
      String path = dumper.dump("first");
      advance(61000);
      assertEquals(path, dumper.dump("second"), "a failed attempt returns the last dump");

      StallDumper.LastDump last = dumper.getLastDump();
      assertEquals(path, last.path());
      assertEquals(start, last.startNanos(), "a failed attempt does not move the start time");
   }

   @Test
   public void throwingDumpDirectoryNeverEscapesAndBacksOff() {
      AtomicInteger attempts = new AtomicInteger();
      StallDumper dumper = new StallDumper(now::get, () -> {
         attempts.incrementAndGet();
         throw new IllegalStateException("no dump dir");
      }, 60000);

      assertNull(dumper.dump("first"));
      advance(1000);
      assertNull(dumper.dump("second"));
      assertEquals(1, attempts.get(), "one attempt per window");
      assertNull(dumper.getLastDump());

      advance(60000);
      assertNull(dumper.dump("third"));
      assertEquals(2, attempts.get());
      assertEquals(0, dumper.getDumpCount());
   }

   private void advance(long millis) {
      now.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
   }

   @TempDir
   File dumpDir;
   private final AtomicLong now = new AtomicLong(1_000_000_000L);
}
