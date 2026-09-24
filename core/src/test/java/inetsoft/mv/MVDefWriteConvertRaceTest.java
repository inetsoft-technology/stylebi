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
package inetsoft.mv;

import inetsoft.mv.MVDef.MVContainer;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Regression stress test for Bug #76971.
 * <p>
 * In production, every {@code MVCompositeDispatcher} of a parallel incremental MV build shares
 * the exact same {@code MVDef} (and therefore the exact same {@code DateMVColumn} instances --
 * see {@code MVDispatcher.processDispatch()} and {@code MVDef.getColumns()}). Before the fix,
 * {@code MVDef.write()} sized its output buffer via {@code getLength()} (which reads a
 * {@code DateMVColumn}'s {@code min0}/{@code max0} fields), then independently re-read those same
 * fields a second time via {@code col.write(buf)}. If a sibling dispatcher thread's unsynchronized
 * {@code DateMVColumn.convert()} call flipped {@code min0}/{@code max0} from {@code null} to
 * non-null in between those two reads, the exactly-sized buffer would be undersized by up to 16
 * bytes and {@code DateMVColumn.write()}'s {@code putLong} calls would throw
 * {@code BufferOverflowException}.
 * <p>
 * The fix (see {@code MVDef.snapshotColumn()} and the {@code synchronized(mvcol)} block added to
 * {@code MVCreatorUtil}'s private {@code setDateMVRange} overload) makes the column's length
 * computation and its serialization one atomic, mutually-exclusive critical section keyed on the
 * column instance itself.
 * <p>
 * A single run of this scenario only has a narrow, timing-dependent chance of actually landing
 * inside the old unguarded window, so this test does not attempt to force one specific
 * interleaving deterministically (there is no test hook inside {@code snapshotColumn()} to pause
 * on). Instead, it races {@code MVDef.write()} against the now-synchronized convert path on a
 * *fresh* {@code DateMVColumn} (min0/max0 null, exactly mirroring the real first-conversion
 * exposure window described in the bug's diagnosis) for many iterations, using a
 * {@link CyclicBarrier} so both threads reach their respective critical sections as close to
 * simultaneously as possible each time. This is a stress/regression signal, not a
 * mathematically-guaranteed reproduction: if the {@code synchronized(col)} guarantee the fix
 * relies on were ever broken by a future edit, this test would be expected to catch it with high
 * probability well before it reached a customer build, without being flaky in the failure-free
 * (fixed) case.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVDefWriteConvertRaceTest {
   private static final int ITERATIONS = 3000;

   @Test
   void concurrentWriteAndConvertNeverCorruptsTheBuffer() throws Exception {
      AtomicReference<Throwable> writerError = new AtomicReference<>();
      AtomicReference<Throwable> mutatorError = new AtomicReference<>();
      Path tmp = Files.createTempFile("mvdef-write-convert-race", ".dat");

      try(FileChannel channel = FileChannel.open(tmp, StandardOpenOption.READ,
                                                   StandardOpenOption.WRITE))
      {
         for(int i = 0; i < ITERATIONS && writerError.get() == null && mutatorError.get() == null;
             i++)
         {
            // A fresh column each iteration: min0/max0 start null, exactly mirroring the real
            // exposure window -- the race only matters for the single null-to-non-null
            // transition that happens once per DateMVColumn per build.
            DateMVColumn col = newYearIntervalColumn();
            MVDef def = newSingleColumnDef(col);
            CyclicBarrier barrier = new CyclicBarrier(2);

            channel.truncate(0);
            channel.position(0);

            Thread writer = new Thread(() -> {
               try {
                  barrier.await();
                  def.write(channel);
               }
               catch(Throwable t) {
                  writerError.compareAndSet(null, t);
               }
            }, "mvdef-write-race-writer-" + i);

            Thread mutator = new Thread(() -> {
               try {
                  barrier.await();

                  // Mirrors the fixed MVCreatorUtil private setDateMVRange(DateMVColumn, ...)
                  // overload: convert() calls that mutate min0/max0 are synchronized on the
                  // column itself, matching MVDef.snapshotColumn()'s synchronized(col).
                  synchronized(col) {
                     col.convert(new Date(0L));
                     col.convert(new Date(86_400_000L));
                  }
               }
               catch(Throwable t) {
                  mutatorError.compareAndSet(null, t);
               }
            }, "mvdef-write-race-mutator-" + i);

            writer.start();
            mutator.start();
            writer.join();
            mutator.join();

            if(writerError.get() == null && mutatorError.get() == null) {
               // Extra correctness check beyond "didn't throw": the length prefix MVDef.write()
               // computed up front must match the number of bytes it actually wrote. A silent
               // byte-count drift (over- or under-count that happens not to overflow/underflow
               // the buffer) would be caught here even if it didn't throw.
               long fileLen = channel.size();
               ByteBuffer head = ByteBuffer.allocate(4);
               channel.position(0);

               while(head.hasRemaining()) {
                  channel.read(head);
               }

               head.flip();
               int declaredLen = head.getInt();
               assertEquals(fileLen - 4, declaredLen,
                  "MVDef.write() length prefix disagreed with actual bytes written " +
                  "(iteration " + i + ")");
            }
         }
      }
      finally {
         Files.deleteIfExists(tmp);
      }

      if(writerError.get() != null) {
         fail("MVDef.write() threw while racing a concurrent DateMVColumn.convert() call",
              writerError.get());
      }

      if(mutatorError.get() != null) {
         fail("DateMVColumn.convert() threw while racing a concurrent MVDef.write() call",
              mutatorError.get());
      }
   }

   private static DateMVColumn newYearIntervalColumn() {
      ColumnRef baseRef = new ColumnRef(new AttributeRef("test", "dateCol"));
      baseRef.setDataType(XSchema.TIME_INSTANT);
      MVColumn base = new MVColumn(baseRef, true);

      ColumnRef rangeRef = new ColumnRef(new AttributeRef("test", "dateCol_Year"));
      rangeRef.setDataType(XSchema.TIME_INSTANT);

      DateMVColumn col = new DateMVColumn(base, rangeRef, DateRangeRef.YEAR_INTERVAL);
      col.setDimension(true);
      return col;
   }

   private static MVDef newSingleColumnDef(DateMVColumn col) {
      MVDef def = new MVDef();
      List<MVColumn> columns = new ArrayList<>();
      columns.add(col);
      def.container = new MVContainer(columns, null);
      return def;
   }
}
