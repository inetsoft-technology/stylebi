/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report.lib.logical;

import inetsoft.report.lib.ScriptEntry;
import inetsoft.report.lib.Transaction;
import inetsoft.report.lib.TransactionType;
import inetsoft.report.lib.physical.PhysicalLibrary;
import inetsoft.report.lib.physical.PhysicalLibraryEntry;
import inetsoft.test.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import inetsoft.report.style.XTableStyle;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81057: a reload (load with init) that lands between a put and the save of that put used to
 * clear the new entry together with its pending transaction, so the save flushed nothing and the
 * entry was lost although the request succeeded.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class LogicalLibraryReloadPendingTest {
   @BeforeEach
   void before() {
      lib = new ScriptLogicalLibrary(new NoopLibrarySecurity());
      folders = new TableStyleFolderLogicalLibrary(new NoopLibrarySecurity());
   }

   private static final LoadOptions INIT = LoadOptions.builder()
      .overwrite(true).audit(false).init(true).build();

   /** a storage snapshot that holds nothing, i.e. one read before the put */
   private static final PhysicalLibrary EMPTY = new PhysicalLibrary() {
      @Override
      public PhysicalLibraryEntry createEntry(String path, Properties properties) {
         throw new UnsupportedOperationException();
      }

      @Override
      public Iterator<PhysicalLibraryEntry> getEntries() {
         return Collections.emptyIterator();
      }

      @Override
      public void close() {
      }
   };

   private static ScriptEntry script(String name) {
      return ScriptEntry.builder().from(name, "var x = 1;").build();
   }

   @Test
   void reloadBeforeSaveKeepsPutAndItsTransaction() throws Exception {
      lib.put("B", script("B"));

      lib.load(EMPTY, INIT);

      assertNotNull(lib.get("B"), "put lost by reload");
      assertTrue(lib.hasTransactions(), "pending transaction lost by reload");
      final List<Transaction<LogicalLibraryEntry<ScriptEntry>>> tx = lib.flushTransactions();
      assertEquals(1, tx.size());
      assertEquals(TransactionType.CREATE, tx.get(0).type());
      assertEquals("B", tx.get(0).identifier());
   }

   @Test
   void twoReloadsBeforeSaveKeepPut() throws Exception {
      lib.put("B", script("B"));
      lib.load(EMPTY, INIT);
      lib.load(EMPTY, INIT);

      assertNotNull(lib.get("B"));
      assertEquals(1, lib.flushTransactions().size());
   }

   @Test
   void renameThenPutThenReloadKeepsBoth() throws Exception {
      lib.put("A", script("A"));
      lib.flushTransactions(); // A saved
      lib.load(EMPTY, INIT);   // snapshot: nothing, A is only in memory here
      lib.put("A", script("A"));
      lib.flushTransactions();

      assertTrue(lib.rename("A", "Ax"));
      lib.put("B", script("B"));
      lib.load(EMPTY, INIT);

      assertNull(lib.get("A"));
      assertNotNull(lib.get("Ax"));
      assertNotNull(lib.get("B"));
      assertEquals(3, lib.flushTransactions().size()); // DELETE A, CREATE Ax, CREATE B
   }

   @Test
   void pendingRemoveSurvivesReload() throws Exception {
      lib.put("A", script("A"));
      lib.flushTransactions();
      lib.remove("A");
      lib.load(EMPTY, INIT);

      assertNull(lib.get("A"));
      final List<Transaction<LogicalLibraryEntry<ScriptEntry>>> tx = lib.flushTransactions();
      assertEquals(1, tx.size());
      assertEquals(TransactionType.DELETE, tx.get(0).type());
   }

   @Test
   void savedEntriesAreStillReplacedByReload() throws Exception {
      // an entry that has been saved is not pending, a reload must still reflect storage
      // (for example a delete done and saved by another node)
      lib.put("A", script("A"));
      lib.flushTransactions();

      lib.load(EMPTY, INIT);

      assertNull(lib.get("A"));
      assertFalse(lib.hasTransactions());
   }

   @Test
   void folderAddBeforeReloadIsKept() throws Exception {
      folders.add("F1");
      folders.load(EMPTY, INIT);

      assertTrue(folders.contains("F1"));
      assertTrue(folders.hasTransactions());
      assertEquals(1, folders.flushTransactions().size());
   }

   @Test
   void tableStylePutBeforeReloadIsKept() throws Exception {
      final TableStyleLogicalLibrary styles = new TableStyleLogicalLibrary(new NoopLibrarySecurity());
      final XTableStyle style = new XTableStyle(null);
      style.setName("S1");
      styles.put("S1", style, false);

      styles.load(EMPTY, INIT);

      assertNotNull(styles.get("S1"));
      assertEquals(1, styles.flushTransactions().size());
   }

   ScriptLogicalLibrary lib;
   TableStyleFolderLogicalLibrary folders;
}
