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

import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.swap.XSwappable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.channels.FileLock;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77983, undo/redo of RuntimeWorksheet and RuntimeViewsheet after the swap write of their
 * checkpoints failed, and a real OS write failure (a locked swap file) during the swap.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class RuntimeSheetSwapFailureUndoTest {
   @AfterEach
   void reset() {
      failure = Failure.NONE;
      lockFile = null;
      releaseLocks();
   }

   /**
    * The real FileOutputStream write fails because another handle holds an exclusive lock on
    * the swap file (mandatory on Windows only), and the unmodified writeXML runs.
    */
   @Test
   @EnabledOnOs(OS.WINDOWS)
   void lockedSwapFileKeepsSheet() throws Exception {
      RuntimeSheet.XSwappableSheetList points = new RuntimeSheet.XSwappableSheetList(null);
      FailingWorksheet sheet = new FailingWorksheet();
      points.add(sheet);
      RuntimeSheet.XSwappableSheet swappable = getSwappable(points, 0);
      failure = Failure.LOCK;
      lockFile = getSwapFile(swappable);

      assertFalse(swappable.swap(), "a failed write must not count as a swap");
      assertSame(sheet, points.get(0), "the sheet must be kept after a failed write");
      // the leftover (locked) file must not be reused by the next swap
      assertFalse(swappable.swap());
      assertSame(sheet, points.get(0));

      failure = Failure.NONE;
      releaseLocks();
      assertTrue(swappable.swap());
      assertNull(swappable.get());
      assertInstanceOf(FailingWorksheet.class, points.get(0));
      points.dispose();
   }

   @Test
   void worksheetUndoRedoAfterFailedSwap() throws Exception {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws77983", null);
      XPrincipal user = new XPrincipal(new IdentityID("admin", "host-org"));
      RuntimeWorksheet rws = new RuntimeWorksheet(entry, new FailingWorksheet(), user);

      for(int i = 0; i < 3; i++) {
         FailingWorksheet ws = new FailingWorksheet();
         ws.getWorksheetInfo().setMessageLevels(new String[] { "cp" + i });
         rws.addCheckpoint(ws);
      }

      swapAll(rws.points, false);

      assertTrue(rws.undo(null));
      assertEquals("cp1", marker(rws.getWorksheet()));
      assertTrue(rws.redo(null));
      assertEquals("cp2", marker(rws.getWorksheet()));

      // the failure clears, the checkpoints are written and read back by undo/redo
      swapAll(rws.points, true);
      assertTrue(rws.undo(null));
      assertEquals("cp1", marker(rws.getWorksheet()));
      assertTrue(rws.undo(null));
      assertEquals("cp0", marker(rws.getWorksheet()));
      assertTrue(rws.redo(null));
      assertEquals("cp1", marker(rws.getWorksheet()));
      rws.dispose();
   }

   @Test
   void viewsheetUndoRedoAfterFailedSwap() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class, CALLS_REAL_METHODS);
      rvs.points = new RuntimeSheet.XSwappableSheetList(null);

      for(int i = 0; i < 3; i++) {
         FailingViewsheet vs = new FailingViewsheet();
         vs.getViewsheetInfo().setDescription("vcp" + i);
         rvs.points.add(vs);
      }

      doReturn(null).when(rvs).getAssetRepository();
      doNothing().when(rvs).updateLayoutInfo(any());
      doNothing().when(rvs).setViewsheet(any());
      doNothing().when(rvs).resetRuntime();
      rvs.point = 2;

      swapAll(rvs.points, false);

      String[] state = rvs.points.getXmlForState(1);
      assertNotNull(state, "checkpoint state lost after a failed swap");
      assertTrue(state[1].contains("vcp1"));

      assertTrue(rvs.undo(null));
      assertEquals("vcp1", marker(lastRestored(rvs, 1)));
      assertTrue(rvs.redo(null));
      assertEquals("vcp2", marker(lastRestored(rvs, 2)));

      swapAll(rvs.points, true);
      assertTrue(rvs.undo(null));
      assertEquals("vcp1", marker(lastRestored(rvs, 3)));
      assertTrue(rvs.undo(null));
      assertEquals("vcp0", marker(lastRestored(rvs, 4)));
      rvs.points.dispose();
   }

   private static void swapAll(RuntimeSheet.XSwappableSheetList points, boolean succeed)
      throws Exception
   {
      failure = succeed ? Failure.NONE : Failure.IO;

      for(int i = 0; i < points.size(); i++) {
         RuntimeSheet.XSwappableSheet swappable = getSwappable(points, i);
         assertEquals(succeed, swappable.swap(), "swap of checkpoint " + i);
         assertEquals(succeed, swappable.get() == null, "checkpoint " + i + " in memory");
      }

      failure = Failure.NONE;
   }

   private static String marker(Worksheet ws) {
      return ws.getWorksheetInfo().getMessageLevels()[0];
   }

   private static String marker(Viewsheet vs) {
      return vs.getViewsheetInfo().getDescription();
   }

   private static Viewsheet lastRestored(RuntimeViewsheet rvs, int calls) {
      ArgumentCaptor<Viewsheet> captor = ArgumentCaptor.forClass(Viewsheet.class);
      verify(rvs, times(calls)).setViewsheet(captor.capture());
      return captor.getValue();
   }

   private static void beforeWrite(PrintWriter writer) {
      if(failure == Failure.LOCK && lockFile != null) {
         try {
            RandomAccessFile file = new RandomAccessFile(lockFile, "rw");
            locks.add(file);
            locks.add(file.getChannel().lock(0, Long.MAX_VALUE, false));
         }
         catch(IOException e) {
            throw new UncheckedIOException(e);
         }
      }
      else if(failure == Failure.IO) {
         // the underlying writer fails like a full disk, PrintWriter swallows the IOException
         try {
            Field field = PrintWriter.class.getDeclaredField("out");
            field.setAccessible(true);
            Writer real = (Writer) field.get(writer);

            field.set(writer, new Writer() {
               @Override
               public void write(char[] cbuf, int off, int len) throws IOException {
                  throw new IOException("No space left on device");
               }

               @Override
               public void flush() throws IOException {
                  real.flush();
               }

               @Override
               public void close() throws IOException {
                  real.close();
               }
            });
         }
         catch(ReflectiveOperationException e) {
            throw new RuntimeException(e);
         }
      }
   }

   private static void releaseLocks() {
      for(int i = locks.size() - 1; i >= 0; i--) {
         try {
            locks.get(i).close();
         }
         catch(Exception ignore) {
         }
      }

      locks.clear();
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

   public static class FailingWorksheet extends Worksheet {
      public FailingWorksheet() {
      }

      @Override
      public void writeXML(PrintWriter writer) {
         beforeWrite(writer);
         super.writeXML(writer);
      }
   }

   public static class FailingViewsheet extends Viewsheet {
      public FailingViewsheet() {
      }

      @Override
      public synchronized void writeXML(PrintWriter writer) {
         beforeWrite(writer);
         super.writeXML(writer);
      }
   }

   enum Failure { NONE, IO, LOCK }

   private static volatile Failure failure = Failure.NONE;
   private static volatile File lockFile;
   private static final List<AutoCloseable> locks = Collections.synchronizedList(new ArrayList<>());
}
