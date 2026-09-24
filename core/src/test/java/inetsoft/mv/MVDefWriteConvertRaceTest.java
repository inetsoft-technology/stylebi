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
import inetsoft.mv.data.MVColumnInfo;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
 * on). Instead, it races {@code MVDef.write()} against the mutator path for many iterations,
 * using a {@link CyclicBarrier} so both threads reach their respective critical sections as
 * close to simultaneously as possible each time. This is a stress/regression signal, not a
 * mathematically-guaranteed reproduction: if the {@code synchronized(col)}/{@code
 * synchronized(mvcol)} guarantees the fix relies on were ever broken by a future edit, this test
 * would be expected to catch it with high probability well before it reached a customer build,
 * without being flaky in the failure-free (fixed) case.
 * <p>
 * The mutator thread does not hand-reimplement {@code MVCreatorUtil}'s locking pattern; it
 * invokes the real, private {@code MVCreatorUtil.setDateMVRange(DateMVColumn, String[],
 * MVColumnInfo, MVColumnInfo[])} overload via reflection (see {@link #SET_DATE_MV_RANGE}). That
 * way, if a future edit ever silently drops the {@code synchronized(mvcol)} wrapper from
 * {@code MVCreatorUtil} itself, this test starts failing instead of continuing to pass against a
 * hand-rolled stand-in that has nothing to do with whether the production code still
 * synchronizes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVDefWriteConvertRaceTest {
   private static final int ITERATIONS = 3000;

   /**
    * The base column name used by {@link #newYearIntervalColumn()}. {@code
    * MVCreatorUtil.setDateMVRange(DateMVColumn, ...)}'s private overload resolves the original
    * (unranged) column via {@code DefaultTableBlock.getOriginalColumn(String[], MVColumn)},
    * which parses it out of the range column's name -- {@code "Year(dateCol)"} -- so the range
    * column's name must follow that {@code <Range>(<original>)} convention for the reflective
    * call below to actually reach the {@code convert()} calls instead of silently no-op'ing on
    * {@code idx < 0}.
    */
   private static final String BASE_COLUMN_NAME = "dateCol";

   /**
    * {@code MVCreatorUtil}'s private {@code setDateMVRange(DateMVColumn, String[],
    * MVColumnInfo, MVColumnInfo[])} overload -- the exact method this PR wraps in {@code
    * synchronized(mvcol)}. Resolved once via reflection so the mutator thread below invokes the
    * real production code path instead of reimplementing its locking by hand.
    */
   private static final Method SET_DATE_MV_RANGE = resolveSetDateMVRange();

   private static Method resolveSetDateMVRange() {
      try {
         Method method = MVCreatorUtil.class.getDeclaredMethod(
            "setDateMVRange", DateMVColumn.class, String[].class,
            MVColumnInfo.class, MVColumnInfo[].class);
         method.setAccessible(true);
         return method;
      }
      catch(NoSuchMethodException e) {
         throw new ExceptionInInitializerError(e);
      }
   }

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

                  // Invoke the real, fixed MVCreatorUtil private setDateMVRange(DateMVColumn,
                  // String[], MVColumnInfo, MVColumnInfo[]) overload via reflection, rather than
                  // reimplementing its synchronized(mvcol) { convert(); convert(); } pattern by
                  // hand -- so this test exercises whether that method itself still
                  // synchronizes, not just whether the test's own stand-in does.
                  String[] columnNames = { BASE_COLUMN_NAME };
                  MVColumnInfo sourceInfo =
                     new MVColumnInfo(null, new Date(0L), new Date(86_400_000L));
                  MVColumnInfo[] cinfos = { sourceInfo };
                  MVColumnInfo targetInfo = new MVColumnInfo();

                  SET_DATE_MV_RANGE.invoke(null, col, columnNames, targetInfo, cinfos);
               }
               catch(InvocationTargetException e) {
                  mutatorError.compareAndSet(null, e.getCause());
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
      ColumnRef baseRef = new ColumnRef(new AttributeRef("test", BASE_COLUMN_NAME));
      baseRef.setDataType(XSchema.TIME_INSTANT);
      MVColumn base = new MVColumn(baseRef, true);

      // Name follows DateRangeRef.getName()'s "<Range>(<original>)" convention -- e.g.
      // "Year(dateCol)" -- so that DefaultTableBlock.getOriginalColumn(), called from the real
      // MVCreatorUtil.setDateMVRange() overload invoked reflectively below, can resolve this
      // range column back to BASE_COLUMN_NAME.
      ColumnRef rangeRef = new ColumnRef(
         new AttributeRef("test", DateRangeRef.getName(BASE_COLUMN_NAME,
                                                         DateRangeRef.YEAR_INTERVAL)));
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
