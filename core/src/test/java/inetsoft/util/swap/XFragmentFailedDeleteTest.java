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
package inetsoft.util.swap;

import inetsoft.test.*;
import inetsoft.util.FileSystemService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77877, when a swap file of a live fragment could not be deleted, XIntFragment and
 * XObjectFragment queued FileSystemService.remove(file, 30000). The queued removal deletes by
 * name, so it removed the file the next swap wrote with the same name, and the next read threw
 * SwapFileReadException. A failed delete in XObjectFragment.change() also left the old swap
 * file to be reused by the next swap, dropping the changed values.
 *
 * <p>The delete failure is forced with the test-only {@code testDelete} hook, so the tests do
 * not depend on platform file locking or permissions.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class XFragmentFailedDeleteTest {
   @Test
   void intFragmentFailedDeleteIsNotQueuedAndIsRewritten() throws Exception {
      XIntFragment fragment = new XIntFragment(new int[] { 100, 101, 102, 103, 104 });
      preventBackgroundSwapping(fragment);
      File file = fragment.getFile(fragment.prefix + ".tdat");

      try {
         // the failed write leaves a stub, and the access recovering from it can't delete it
         fragment.testBeforeWrite = XFragmentFailedDeleteTest::throwSimulatedWriteFailure;
         fragment.testDelete = f -> false;
         assertTrue(fragment.swap());
         clearInvocations(fileSystemService);
         assertEquals(100, fragment.getSafely(0));
         assertTrue(file.exists(), "test setup: the stub was deleted");
         verify(fileSystemService, never()).remove(any(File.class), anyInt());

         // the rewritten file must not have a delayed removal pending against its name
         fragment.testBeforeWrite = null;
         fragment.testDelete = null;
         assertTrue(fragment.swap());
         assertFalse(fragment.isValid());
         verify(fileSystemService, never()).remove(any(File.class), anyInt());
         assertTrue(file.exists(), "the live swap file is gone");
         assertEquals(100, fragment.getSafely(0));
         assertEquals(104, fragment.getSafely(4));
      }
      finally {
         fragment.dispose();
      }
   }

   @Test
   void objectFragmentFailedDeleteIsNotQueuedAndIsRewritten() throws Exception {
      XObjectFragment<String> fragment = createObjectFragment();
      File file = swapFile(fragment);

      try {
         fragment.testBeforeWrite = XFragmentFailedDeleteTest::throwSimulatedWriteFailure;
         fragment.testDelete = f -> false;
         assertTrue(fragment.swap());
         clearInvocations(fileSystemService);
         assertEquals("alpha", fragment.getSafely(0));
         assertTrue(file.exists(), "test setup: the stub was deleted");
         verify(fileSystemService, never()).remove(any(File.class), anyInt());

         fragment.testBeforeWrite = null;
         fragment.testDelete = null;
         assertTrue(fragment.swap());
         assertFalse(fragment.isValid());
         verify(fileSystemService, never()).remove(any(File.class), anyInt());
         assertTrue(file.exists(), "the live swap file is gone");
         assertEquals("alpha", fragment.getSafely(0));
         assertEquals("beta", fragment.getSafely(1));
      }
      finally {
         fragment.dispose();
      }
   }

   @Test
   void objectFragmentChangeWithFailedDeleteIsWrittenOnNextSwap() throws Exception {
      XObjectFragment<String> fragment = createObjectFragment();
      File file = swapFile(fragment);

      try {
         // swap and read back, the swap file is kept for the next swap to reuse
         assertTrue(fragment.swap());
         assertEquals("alpha", fragment.getSafely(0));
         assertTrue(file.exists(), "test setup: the swap file was not kept");

         fragment.testDelete = f -> false;
         clearInvocations(fileSystemService);
         set(fragment, 0, "NEW");
         assertTrue(file.exists(), "test setup: the swap file was deleted");
         verify(fileSystemService, never()).remove(any(File.class), anyInt());
         fragment.testDelete = null;

         // the old swap file must not be reused for the changed values
         assertTrue(fragment.swap());
         assertFalse(fragment.isValid());
         assertEquals("NEW", fragment.getSafely(0));
         assertEquals("beta", fragment.getSafely(1));
      }
      finally {
         fragment.dispose();
      }
   }

   private static XObjectFragment<String> createObjectFragment() {
      XObjectFragment<String> fragment = new XObjectFragment<>((char) 10, (char) 100, null);
      fragment.add("alpha");
      fragment.add("beta");
      preventBackgroundSwapping(fragment);
      return fragment;
   }

   /**
    * Set a value the way XSwappableObjectList.set() does.
    */
   private static void set(XObjectFragment<?> fragment, int r, Object obj) {
      synchronized(fragment) {
         fragment.access();
         fragment.change();
         fragment.set(r, obj);
      }
   }

   private static File swapFile(XObjectFragment<?> fragment) {
      return fragment.getFile(fragment.prefix + "_0.tdat");
   }

   /**
    * Keep the background swapper from swapping the fragment while the test runs.
    */
   private static void preventBackgroundSwapping(XSwappable fragment) {
      fragment.complete();
      XSwapper.getSwapper().deregister(fragment);
   }

   private static void throwSimulatedWriteFailure() {
      throw new UncheckedIOException(new IOException("simulated write failure"));
   }

   @MockitoSpyBean
   private FileSystemService fileSystemService;
}
