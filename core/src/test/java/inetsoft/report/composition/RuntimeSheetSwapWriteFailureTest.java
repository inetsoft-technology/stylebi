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

import inetsoft.test.*;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.swap.XSwappable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77983, a failed write of an undo/redo checkpoint in XSwappableSheet._swap() dropped the
 * sheet (PrintWriter swallowed the IOException) or left a partial swap file that a later swap
 * reused because the file existed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class RuntimeSheetSwapWriteFailureTest {
   @AfterEach
   void resetMode() {
      mode = Mode.OK;
      writes = 0;
   }

   @Test
   void ioExceptionDuringWriteKeepsSheetInMemory() throws Exception {
      mode = Mode.IO_WRITE;
      RuntimeSheet.XSwappableSheetList points = new RuntimeSheet.XSwappableSheetList(null);
      FailingWorksheet sheet = new FailingWorksheet();
      points.add(sheet);
      RuntimeSheet.XSwappableSheet swappable = getSwappable(points, 0);
      File file = getSwapFile(swappable);

      assertFalse(swappable.swap(), "a failed write must not count as a swap");
      assertTrue(swappable.isValid(), "the checkpoint must stay resident after a failed write");
      assertSame(sheet, swappable.get(), "the sheet must be kept after a failed write");
      assertFalse(file.exists(), "the partial swap file must be deleted");
      assertSame(sheet, points.get(0));

      // the failure clears, the next swap writes the file again and the sheet can be read back
      mode = Mode.OK;
      assertTrue(swappable.swap());
      assertNull(swappable.get());
      assertInstanceOf(FailingWorksheet.class, points.get(0));
      points.dispose();
   }

   @Test
   void ioExceptionDuringCloseClosesFileAndDeletesIt() throws Exception {
      mode = Mode.IO_CLOSE;
      RuntimeSheet.XSwappableSheetList points = new RuntimeSheet.XSwappableSheetList(null);
      FailingWorksheet sheet = new FailingWorksheet();
      points.add(sheet);
      RuntimeSheet.XSwappableSheet swappable = getSwappable(points, 0);
      File file = getSwapFile(swappable);

      assertFalse(swappable.swap());
      assertTrue(swappable.isValid());
      assertSame(sheet, swappable.get());
      // the writer chain never closed the file stream, the swap must close it so it can be deleted
      assertFalse(file.exists(), "the file stream must be closed and the partial file deleted");
      assertSame(sheet, points.get(0));
      points.dispose();
   }

   @Test
   void runtimeExceptionDuringWriteIsNotReusedByLaterSwap() throws Exception {
      checkThrowDuringWrite(Mode.RUNTIME);
   }

   @Test
   void errorDuringWriteIsRethrownAndNotReusedByLaterSwap() throws Exception {
      checkThrowDuringWrite(Mode.ERROR);
   }

   @Test
   void completeSwapFileIsReusedBySecondSwap() throws Exception {
      RuntimeSheet.XSwappableSheetList points = new RuntimeSheet.XSwappableSheetList(null);
      points.add(new FailingWorksheet());
      RuntimeSheet.XSwappableSheet swappable = getSwappable(points, 0);

      assertTrue(swappable.swap());
      assertEquals(1, writes);
      assertInstanceOf(FailingWorksheet.class, points.get(0));
      assertTrue(swappable.swap());
      assertEquals(1, writes, "a completely written swap file should be reused");
      assertNull(swappable.get());
      assertInstanceOf(FailingWorksheet.class, points.get(0));
      points.dispose();
   }

   @Test
   void viewsheetStateXmlAfterFailedSwapIsComplete() throws Exception {
      RuntimeSheet.XSwappableSheetList points = new RuntimeSheet.XSwappableSheetList(null);
      FailingViewsheet sheet = new FailingViewsheet();
      points.add(sheet);
      RuntimeSheet.XSwappableSheet swappable = getSwappable(points, 0);
      String expected = getXmlForState(points, 0)[1];
      assertNotNull(expected);

      mode = Mode.IO_WRITE;
      assertFalse(swappable.swap());
      mode = Mode.OK;

      // checkpoint state sent to other nodes must not be the truncated swap file
      String[] state = getXmlForState(points, 0);
      assertNotNull(state, "checkpoint state lost after a failed swap");
      assertEquals(FailingViewsheet.class.getName(), state[0]);
      assertEquals(expected, state[1]);
      points.dispose();
   }

   private void checkThrowDuringWrite(Mode failure) throws Exception {
      mode = failure;
      RuntimeSheet.XSwappableSheetList points = new RuntimeSheet.XSwappableSheetList(null);
      FailingWorksheet sheet = new FailingWorksheet();
      points.add(sheet);
      RuntimeSheet.XSwappableSheet swappable = getSwappable(points, 0);
      File file = getSwapFile(swappable);

      if(failure == Mode.ERROR) {
         assertThrows(StackOverflowError.class, swappable::swap);
      }
      else {
         assertFalse(swappable.swap());
      }

      assertTrue(swappable.isValid());
      assertSame(sheet, swappable.get());
      assertFalse(file.exists(), "the partial swap file must be deleted");

      // the failure clears, a later swap must write the file again instead of reusing it
      mode = Mode.OK;
      assertSame(sheet, points.get(0));
      assertTrue(swappable.swap());
      assertNull(swappable.get());
      AbstractSheet back = points.get(0);
      assertInstanceOf(FailingWorksheet.class, back, "the checkpoint was lost after the second swap");
      points.dispose();
   }

   @SuppressWarnings("unchecked")
   private static RuntimeSheet.XSwappableSheet getSwappable(RuntimeSheet.XSwappableSheetList points,
                                                            int index)
      throws Exception
   {
      Field field = RuntimeSheet.XSwappableSheetList.class.getDeclaredField("values");
      field.setAccessible(true);
      return ((List<RuntimeSheet.XSwappableSheet>) field.get(points)).get(index);
   }

   private static File getSwapFile(RuntimeSheet.XSwappableSheet swappable) throws Exception {
      Field prefix = XSwappable.class.getDeclaredField("prefix");
      prefix.setAccessible(true);
      Method getFile = XSwappable.class.getDeclaredMethod("getFile", String.class);
      getFile.setAccessible(true);
      return (File) getFile.invoke(swappable, prefix.get(swappable) + ".tdat");
   }

   private static String[] getXmlForState(RuntimeSheet.XSwappableSheetList points, int index) {
      return points.getXmlForState(index);
   }

   /**
    * Replaces the PrintWriter's underlying writer with one that fails like a full disk:
    * PrintWriter swallows the IOException exactly as it would a real one.
    */
   private static void injectIOFailure(PrintWriter writer, boolean onClose) {
      try {
         Field field = PrintWriter.class.getDeclaredField("out");
         field.setAccessible(true);
         Writer real = (Writer) field.get(writer);

         field.set(writer, new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
               int ok = onClose ? len : Math.max(0, Math.min(len, 40 - count));
               real.write(cbuf, off, ok);
               count += ok;

               if(ok < len) {
                  throw new IOException("No space left on device");
               }
            }

            @Override
            public void flush() throws IOException {
               if(onClose) {
                  throw new IOException("No space left on device");
               }

               real.flush();
            }

            @Override
            public void close() throws IOException {
               if(onClose) {
                  // like a failed flush in StreamEncoder, the file stream is never closed
                  throw new IOException("No space left on device");
               }

               real.close();
            }

            private int count;
         });
      }
      catch(ReflectiveOperationException e) {
         throw new RuntimeException(e);
      }
   }

   private static void failingWrite(PrintWriter writer, Runnable write) {
      writes++;

      switch(mode) {
      case IO_WRITE -> {
         injectIOFailure(writer, false);
         write.run();
      }
      case IO_CLOSE -> {
         injectIOFailure(writer, true);
         write.run();
      }
      case RUNTIME -> {
         writer.print("<worksheet class=\"x\">" + "x".repeat(20000));
         throw new IllegalStateException("writeXML failed mid-write");
      }
      case ERROR -> {
         writer.print("<worksheet class=\"x\">" + "x".repeat(20000));
         throw new StackOverflowError("writeXML error mid-write");
      }
      default -> write.run();
      }
   }

   public static class FailingWorksheet extends Worksheet {
      public FailingWorksheet() {
      }

      @Override
      public void writeXML(PrintWriter writer) {
         failingWrite(writer, () -> super.writeXML(writer));
      }
   }

   public static class FailingViewsheet extends Viewsheet {
      public FailingViewsheet() {
      }

      @Override
      public synchronized void writeXML(PrintWriter writer) {
         failingWrite(writer, () -> super.writeXML(writer));
      }
   }

   enum Mode { OK, IO_WRITE, IO_CLOSE, RUNTIME, ERROR }

   private static volatile Mode mode = Mode.OK;
   private static volatile int writes = 0;
}
