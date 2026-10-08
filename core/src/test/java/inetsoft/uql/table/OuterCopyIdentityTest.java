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
package inetsoft.uql.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.*;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.IndexedStorage;
import inetsoft.web.composer.ws.PasteAssembliesService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSPasteAssembliesEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78030: the outer copies of a mirror are found from the mirror, not from the name prefix of
 * the mirrored worksheet's current path. A rename of that worksheet, or another worksheet whose
 * name prefix starts with it, must not make a frozen (Auto Update off) mirror lose its copies.
 * Runs through the real open path ({@code getSheet(..., AssetContent.ALL)}), save path
 * ({@code WorksheetService.setWorksheet}) and rename transform.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SnapshotWorksheetSaveFailureTest.Beans.class,
                                  OuterCopyIdentityTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context with the real asset repository
class OuterCopyIdentityTest {
   @Autowired
   private WorksheetService worksheetService;

   // W1 renamed: the copies keep the names made from its old path
   @Test
   void renameKeepsFrozenCopies() throws Exception {
      AssetEntry e1 = entry("oci_ren_w1");
      AssetEntry e2 = entry("oci_ren_w2");
      AssetEntry moved = entry("oci_ren_w1z");
      saveOwner(e1, "old", 50);
      save(embed(new AssetEntry[] { e1 }, new boolean[] { false }), e2);
      String copy = AssetUtil.createPrefix(e1) + "0";

      repository().changeSheet(e1, moved, null, true);
      clearCaches(e2);
      assertEquals(moved, ((MirrorAssembly) stored(e2).getAssembly(MIRROR + 0)).getEntry(),
                   "the rename transform didn't rewrite the mirror");

      Worksheet ws2 = open(e2);
      assertTrue(names(ws2).contains(copy), "copy dropped on open: " + names(ws2));
      save(ws2, e2);
      assertTrue(names(stored(e2)).contains(copy), "copy not stored: " + names(stored(e2)));

      resaveOwner(moved, "new", 80);
      clearCaches(e2);
      ws2 = open(e2);
      assertEquals(Arrays.asList(MIRROR + 0, copy), sorted(names(ws2)));
      assertEquals("51/old4", mirroredValue(ws2, MIRROR + 0));
   }

   // a worksheet named like the old name of a renamed one, embedded next to its frozen copies
   @Test
   void embeddingWorksheetWithOldNameOfRenamedOneKeepsFrozenCopies() throws Exception {
      AssetEntry e1 = entry("oci_reuse");
      AssetEntry e2 = entry("oci_reuse_w2");
      AssetEntry moved = entry("oci_reuse_z");
      saveOwner(e1, "old", 50);
      save(embed(new AssetEntry[] { e1 }, new boolean[] { false }), e2);
      repository().changeSheet(e1, moved, null, true);
      saveOwner(e1, "other", 30);

      clearCaches(e2);
      Worksheet ws2 = open(e2);
      String frozenCopy = ((MirrorAssembly) ws2.getAssembly(MIRROR + 0)).getAssemblyName();
      assertTrue(frozenCopy.startsWith(AssetUtil.createPrefix(e1)));
      // as dragging the worksheet into W2 does (WorksheetOpenAssetService)
      WSAssembly[] created = AssetUtil.copyOuterAssemblies(repository(), e1, null, ws2, null);
      addMirror(ws2, MIRROR + 1, e1, created, true);

      assertTrue(names(ws2).contains(frozenCopy), "frozen copy replaced: " + names(ws2));
      assertNotEquals(frozenCopy, created[created.length - 1].getName());
      assertEquals("51/old4", mirroredValue(ws2, MIRROR + 0));
      assertEquals("31/other4", mirroredValue(ws2, MIRROR + 1));

      save(ws2, e2);
      clearCaches(e2);
      ws2 = open(e2);
      assertEquals("51/old4", mirroredValue(ws2, MIRROR + 0));
      assertEquals("31/other4", mirroredValue(ws2, MIRROR + 1));
      assertEquals(4, ws2.getAssemblies().length, names(ws2).toString());
   }

   // the name prefix of "a" is the start of the name prefix of "a_b"
   @Test
   void autoMirrorKeepsFrozenCopiesOfWorksheetWithLongerName() throws Exception {
      checkPrefixCollision(entry("oci_pre"), entry("oci_pre_b"), entry("oci_pre_w2"));
   }

   // the name prefix of "a" is the start of the name prefix of "a/b"
   @Test
   void autoMirrorKeepsFrozenCopiesOfWorksheetInFolderWithSameName() throws Exception {
      repository().addFolder(new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER,
                                            "oci_dir", null), null);
      checkPrefixCollision(entry("oci_dir"), entry("oci_dir/b"), entry("oci_dir_w2"));
   }

   private void checkPrefixCollision(AssetEntry ea, AssetEntry eab, AssetEntry e2)
      throws Exception
   {
      saveOwner(ea, "a", 20);
      saveOwner(eab, "old", 50);
      assertTrue(AssetUtil.createPrefix(eab).startsWith(AssetUtil.createPrefix(ea)));
      save(embed(new AssetEntry[] { ea, eab }, new boolean[] { true, false }), e2);
      String copyAB = AssetUtil.createPrefix(eab) + "0";

      clearCaches(e2);
      Worksheet ws2 = open(e2);
      assertTrue(names(ws2).contains(copyAB), "copy dropped on open: " + names(ws2));
      save(ws2, e2);

      resaveOwner(eab, "new", 80);
      clearCaches(e2);
      ws2 = open(e2);
      assertEquals(4, ws2.getAssemblies().length, names(ws2).toString());
      assertEquals(copyAB, ((MirrorAssembly) ws2.getAssembly(MIRROR + 1)).getAssemblyName());
      assertEquals("51/old4", mirroredValue(ws2, MIRROR + 1));
      assertEquals("21/a4", mirroredValue(ws2, MIRROR + 0));
   }

   // dragging "a" into a worksheet holding the frozen copies of "a_b"
   @Test
   void embeddingKeepsCopiesOfWorksheetWithLongerName() throws Exception {
      AssetEntry ea = entry("oci_drag");
      AssetEntry eab = entry("oci_drag_b");
      saveOwner(ea, "a", 20);
      saveOwner(eab, "old", 50);
      Worksheet ws2 = embed(new AssetEntry[] { eab }, new boolean[] { false });
      String copyAB = AssetUtil.createPrefix(eab) + "0";

      AssetUtil.copyOuterAssemblies(repository(), ea, null, ws2, null);
      assertTrue(names(ws2).contains(copyAB), names(ws2).toString());
   }

   // removing the mirror of "a" removes its copies, not those of "a_b"
   @Test
   void removingMirrorRemovesOnlyItsCopies() throws Exception {
      AssetEntry ea = entry("oci_rm");
      AssetEntry eab = entry("oci_rm_b");
      saveOwner(ea, "a", 20);
      saveOwner(eab, "old", 50);
      Worksheet ws2 = embed(new AssetEntry[] { ea, eab }, new boolean[] { true, false });
      String copyA = ((MirrorAssembly) ws2.getAssembly(MIRROR + 0)).getAssemblyName();

      ws2.removeAssembly(MIRROR + 0);

      assertEquals(Arrays.asList(MIRROR + 1, AssetUtil.createPrefix(eab) + "0"),
                   sorted(names(ws2)));
      assertNull(ws2.getAssembly(copyA));
   }

   // removing the mirror of a renamed worksheet removes its copies named after the old path
   @Test
   void removingMirrorOfRenamedWorksheetRemovesItsCopies() throws Exception {
      AssetEntry e1 = entry("oci_rmren_w1");
      AssetEntry e2 = entry("oci_rmren_w2");
      saveOwner(e1, "old", 50);
      save(embed(new AssetEntry[] { e1 }, new boolean[] { false }), e2);
      repository().changeSheet(e1, entry("oci_rmren_w1z"), null, true);

      clearCaches(e2);
      Worksheet ws2 = open(e2);
      ws2.removeAssembly(MIRROR + 0);
      assertEquals(Collections.emptyList(), names(ws2));
   }

   // two mirrors of one worksheet share one set of copies (a second embed of a worksheet reuses
   // the copies, WorksheetOpenAssetService), both updated on load
   @Test
   void autoMirrorsSharingCopiesKeepOneSet() throws Exception {
      AssetEntry e1 = entry("oci_share_auto_w1");
      AssetEntry e2 = entry("oci_share_auto_w2");
      saveOwner(e1, "old", 50);
      save(embedShared(e1, true, true), e2);

      for(int i = 0; i < 2; i++) {
         clearCaches(e2);
         Worksheet ws2 = open(e2);
         assertSharedCopy(ws2);
         assertEquals("51/old4", mirroredValue(ws2, MIRROR + 1));
         save(ws2, e2);
      }

      resaveOwner(e1, "new", 80);
      clearCaches(e2);
      Worksheet ws2 = open(e2);
      assertSharedCopy(ws2);
      assertEquals("81/new4", mirroredValue(ws2, MIRROR + 0));
      assertEquals("81/new4", mirroredValue(ws2, MIRROR + 1));
   }

   // a frozen mirror sharing the copies of an auto-updated one follows it, as the copies are
   // updated on load (Worksheet.isFrozenOuterAssembly)
   @Test
   void frozenMirrorSharingCopiesFollowsAutoMirror() throws Exception {
      AssetEntry e1 = entry("oci_share_mix_w1");
      AssetEntry e2 = entry("oci_share_mix_w2");
      String[] paths1 = saveOwner(e1, "old", 50);
      save(embedShared(e1, true, false), e2);
      assertArrayEquals(paths1, outerCopy(stored(e2)).getDataPaths(),
                        "copy updated on load wrote its own files");

      for(int i = 0; i < 2; i++) {
         clearCaches(e2);
         Worksheet ws2 = open(e2);
         assertSharedCopy(ws2);
         save(ws2, e2);
      }

      String[] paths2 = resaveOwner(e1, "new", 80);
      clearCaches(e2);
      Worksheet ws2 = open(e2);
      assertSharedCopy(ws2);
      assertEquals("81/new4", mirroredValue(ws2, MIRROR + 0));
      assertEquals("81/new4", mirroredValue(ws2, MIRROR + 1));
      save(ws2, e2);
      assertArrayEquals(paths2, outerCopy(stored(e2)).getDataPaths());
   }

   // Update Mirror on one of two frozen mirrors sharing the copies updates both, as before
   @Test
   void updatingFrozenMirrorSharingCopiesUpdatesBoth() throws Exception {
      AssetEntry e1 = entry("oci_share_frozen_w1");
      AssetEntry e2 = entry("oci_share_frozen_w2");
      saveOwner(e1, "old", 50);
      save(embedShared(e1, false, false), e2);
      resaveOwner(e1, "new", 80);

      clearCaches(e2);
      Worksheet ws2 = open(e2);
      assertSharedCopy(ws2);
      assertEquals("51/old4", mirroredValue(ws2, MIRROR + 1));

      ((MirrorAssembly) ws2.getAssembly(MIRROR + 0)).updateMirror(repository(), null);
      assertSharedCopy(ws2);
      assertEquals("81/new4", mirroredValue(ws2, MIRROR + 0));
      assertEquals("81/new4", mirroredValue(ws2, MIRROR + 1));
   }

   // a worksheet stored with copies whose names no prefix of the mirrored worksheet makes, e.g.
   // by an older version or another server: they still belong to the mirror
   @Test
   void copiesWithOldNamesStillBelongToTheirMirror() throws Exception {
      AssetEntry e1 = entry("oci_legacy_w1");
      AssetEntry frozen = entry("oci_legacy_frozen");
      AssetEntry auto = entry("oci_legacy_auto");
      saveOwner(e1, "old", 50);
      storeWithCopyName(embed(new AssetEntry[] { e1 }, new boolean[] { false }), frozen);
      storeWithCopyName(embed(new AssetEntry[] { e1 }, new boolean[] { true }), auto);
      resaveOwner(e1, "new", 80);

      clearCaches(frozen);
      Worksheet ws = open(frozen);
      assertEquals(Arrays.asList(MIRROR + 0, LEGACY_COPY), sorted(names(ws)));
      assertEquals("51/old4", mirroredValue(ws, MIRROR + 0));
      ws.removeAssembly(MIRROR + 0);
      assertEquals(Collections.emptyList(), names(ws));

      // replaced on load, not left next to the new copy
      clearCaches(auto);
      ws = open(auto);
      assertEquals(2, ws.getAssemblies().length, names(ws).toString());
      assertFalse(names(ws).contains(LEGACY_COPY));
      assertEquals("81/new4", mirroredValue(ws, MIRROR + 0));
   }

   // pasting the frozen mirror of a renamed worksheet into another worksheet pastes its copies
   @Test
   void pastingMirrorOfRenamedWorksheetPastesItsCopies() throws Exception {
      AssetEntry e1 = entry("oci_paste_w1");
      AssetEntry e2 = entry("oci_paste_w2");
      AssetEntry e3 = entry("oci_paste_w3");
      saveOwner(e1, "old", 50);
      save(embed(new AssetEntry[] { e1 }, new boolean[] { false }), e2);
      repository().changeSheet(e1, entry("oci_paste_w1z"), null, true);

      // as stored, so that only the paste finds the copies
      clearCaches(e2);
      Worksheet ws2 = stored(e2);
      String copy = ((MirrorAssembly) ws2.getAssembly(MIRROR + 0)).getAssemblyName();
      // an assembly of the target with the copy's name is not the copy
      Worksheet ws3 = new Worksheet();
      SnapshotEmbeddedTableAssembly other = new SnapshotEmbeddedTableAssembly(ws3, copy);
      ws3.addAssembly(other);
      other.setEmbeddedData(new XEmbeddedTable(createTable("other", 5)));
      // the copy's files are its own, load them as the paste needs the data
      outerCopy(ws2).getTable();

      paste(ws2, e2, ws3, e3, MIRROR + 0);

      MirrorTableAssembly pasted = (MirrorTableAssembly) ws3.getAssembly(MIRROR + 0);
      assertNotNull(pasted);
      assertSame(other, ws3.getAssembly(copy));
      assertNotEquals(copy, pasted.getAssemblyName());
      assertTrue(((WSAssembly) ws3.getAssembly(pasted.getAssemblyName())).isOuter());
      assertEquals(1, ws3.getOuterCopies(pasted).length);
      assertEquals("51/old4", mirroredValue(ws3, MIRROR + 0));
   }

   // the copies are kept when the mirrored worksheet can't be loaded, the cleanup on open
   // doesn't need it
   @Test
   void copiesKeptWhenMirroredWorksheetIsDeleted() throws Exception {
      for(boolean auto : new boolean[] { false, true }) {
         AssetEntry e1 = entry("oci_del_w1_" + auto);
         AssetEntry e2 = entry("oci_del_w2_" + auto);
         saveOwner(e1, "old", 50);
         save(embed(new AssetEntry[] { e1 }, new boolean[] { auto }), e2);
         String copy = AssetUtil.createPrefix(e1) + "0";
         repository().removeSheet(e1, null, true);

         for(int i = 0; i < 2; i++) {
            clearCaches(e2);
            Worksheet ws2 = open(e2);
            assertEquals(Arrays.asList(MIRROR + 0, copy), sorted(names(ws2)));
            assertEquals("51/old4", mirroredValue(ws2, MIRROR + 0));
            save(ws2, e2);
         }
      }
   }

   // a primary depending on other tables has one copy for each, all kept after a rename
   @Test
   void copiesOfPrimaryWithDependenciesKeptAfterRename() throws Exception {
      for(boolean auto : new boolean[] { false, true }) {
         AssetEntry e1 = entry("oci_union_w1_" + auto);
         AssetEntry e2 = entry("oci_union_w2_" + auto);
         AssetEntry moved = entry("oci_union_w1z_" + auto);
         saveUnionOwner(e1);
         save(embed(new AssetEntry[] { e1 }, new boolean[] { auto }), e2);
         repository().changeSheet(e1, moved, null, true);
         // frozen copies keep the old names, updated ones are made from the new path
         String prefix = AssetUtil.createPrefix(auto ? moved : e1);

         for(int i = 0; i < 2; i++) {
            clearCaches(e2);
            Worksheet ws2 = open(e2);
            assertEquals(Arrays.asList(MIRROR + 0, prefix + 0, prefix + 1, prefix + 2),
                         sorted(names(ws2)));
            assertEquals(3, ws2.getOuterCopies((MirrorAssembly) ws2.getAssembly(MIRROR + 0))
               .length);
            save(ws2, e2);
         }
      }
   }

   // W2 embeds W1 (Auto Update off), which embeds W0: the copy of W1's mirror and the copy of
   // its copy of W0 are W2's mirror's copies too, kept on open, and frozen
   @Test
   void copiesOfNestedMirrorKept() throws Exception {
      AssetEntry e0 = entry("oci_nest_w0");
      AssetEntry e1 = entry("oci_nest_w1");
      AssetEntry e2 = entry("oci_nest_w2");
      saveOwner(e0, "old", 50);
      save(embed(new AssetEntry[] { e0 }, new boolean[] { true }), e1);
      save(embed(new AssetEntry[] { e1 }, new boolean[] { false }), e2);
      List<String> stored = sorted(names(stored(e2)));
      assertEquals(3, stored.size(), stored.toString());

      resaveOwner(e0, "new", 80);

      for(int i = 0; i < 2; i++) {
         clearCaches(e1);
         clearCaches(e2);
         Worksheet ws2 = open(e2);
         assertEquals(stored, sorted(names(ws2)));
         // the copy of W1's mirror, reading the copy of W1's copy of W0
         String nested = ((MirrorAssembly) ws2.getAssembly(MIRROR + 0)).getAssemblyName();
         assertEquals("51/old4", mirroredValue(ws2, nested));
         save(ws2, e2);
      }
   }

   /**
    * Save a worksheet whose primary is the union of two snapshot tables.
    */
   private void saveUnionOwner(AssetEntry entry) throws Exception {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly t1 = new SnapshotEmbeddedTableAssembly(ws, "T1");
      SnapshotEmbeddedTableAssembly t2 = new SnapshotEmbeddedTableAssembly(ws, "T2");
      ws.addAssembly(t1);
      ws.addAssembly(t2);
      t1.setEmbeddedData(new XEmbeddedTable(createTable("x", 10)));
      t2.setEmbeddedData(new XEmbeddedTable(createTable("y", 10)));
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setOperation(TableAssemblyOperator.UNION);
      op.setLeftTable("T1");
      op.setRightTable("T2");
      TableAssemblyOperator operator = new TableAssemblyOperator();
      operator.addOperator(op);
      ws.addAssembly(new ConcatenatedTableAssembly(ws, "U", new TableAssembly[] { t1, t2 },
                                                   new TableAssemblyOperator[] { operator }));
      ws.setPrimaryAssembly("U");
      save(ws, entry);
   }

   /**
    * Paste an assembly of one worksheet into another, as the composer does.
    */
   private static void paste(Worksheet from, AssetEntry fromEntry, Worksheet to,
                             AssetEntry toEntry, String name) throws Exception
   {
      RuntimeWorksheet source = runtimeWorksheet(from, fromEntry);
      RuntimeWorksheet target = runtimeWorksheet(to, toEntry);
      ViewsheetService engine = mock(ViewsheetService.class);
      when(engine.getWorksheet(eq("source"), any())).thenReturn(source);
      when(engine.getWorksheet(eq("target"), any())).thenReturn(target);
      WSPasteAssembliesEvent event = new WSPasteAssembliesEvent();
      event.setAssemblies(new String[] { name });
      event.setSourceRuntimeId("source");

      // the commands that show the pasted assemblies
      try(MockedStatic<WorksheetEventUtil> ignored = mockStatic(WorksheetEventUtil.class)) {
         new PasteAssembliesService(engine, null)
            .pasteAssemblies("target", event, null, mock(CommandDispatcher.class));
      }
   }

   private static RuntimeWorksheet runtimeWorksheet(Worksheet ws, AssetEntry entry) {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getEntry()).thenReturn(entry);
      return rws;
   }

   /**
    * Store a worksheet with its outer copy renamed, without the composer save.
    */
   private void storeWithCopyName(Worksheet ws, AssetEntry entry) throws Exception {
      save(ws, entry);
      IndexedStorage storage = repository().getStorage(entry);
      Worksheet stored = (Worksheet) storage.getXMLSerializable(entry.toIdentifier(), null);
      stored.renameAssembly(((MirrorAssembly) stored.getAssembly(MIRROR + 0)).getAssemblyName(),
                            LEGACY_COPY, true);
      storage.putXMLSerializable(entry.toIdentifier(), stored);
      assertEquals(LEGACY_COPY,
                   ((MirrorAssembly) stored(entry).getAssembly(MIRROR + 0)).getAssemblyName());
   }

   /**
    * Embed worksheets the way dragging them into a worksheet in the composer does.
    */
   private static Worksheet embed(AssetEntry[] entries, boolean[] autoUpdate) throws Exception {
      Worksheet ws = new Worksheet();

      for(int i = 0; i < entries.length; i++) {
         WSAssembly[] created =
            AssetUtil.copyOuterAssemblies(repository(), entries[i], null, ws, null);
         addMirror(ws, MIRROR + i, entries[i], created, autoUpdate[i]);
      }

      ws.setPrimaryAssembly(MIRROR + 0);
      return ws;
   }

   /**
    * Embed a worksheet twice, the second mirror sharing the copies of the first, as
    * WorksheetOpenAssetService does when the worksheet already has a visible mirror of it.
    */
   private static Worksheet embedShared(AssetEntry entry, boolean auto0, boolean auto1)
      throws Exception
   {
      Worksheet ws = embed(new AssetEntry[] { entry }, new boolean[] { auto0 });
      WSAssembly root = (WSAssembly)
         ws.getAssembly(((MirrorAssembly) ws.getAssembly(MIRROR + 0)).getAssemblyName());
      addMirror(ws, MIRROR + 1, entry, new WSAssembly[] { root }, auto1);
      return ws;
   }

   private static void addMirror(Worksheet ws, String name, AssetEntry entry,
                                 WSAssembly[] created, boolean autoUpdate)
   {
      MirrorTableAssembly mirror =
         new MirrorTableAssembly(ws, name, entry, true, created[created.length - 1]);
      mirror.setAutoUpdate(autoUpdate);
      ws.addAssembly(mirror);
   }

   /**
    * Check that both mirrors use one copy, which is in the worksheet.
    */
   private static void assertSharedCopy(Worksheet ws) {
      MirrorAssembly mirror0 = (MirrorAssembly) ws.getAssembly(MIRROR + 0);
      MirrorAssembly mirror1 = (MirrorAssembly) ws.getAssembly(MIRROR + 1);
      assertEquals(3, ws.getAssemblies().length, names(ws).toString());
      assertEquals(mirror0.getAssemblyName(), mirror1.getAssemblyName());
      assertSame(ws.getAssembly(mirror0.getAssemblyName()), mirror0.getAssembly());
      assertSame(ws.getAssembly(mirror0.getAssemblyName()), mirror1.getAssembly());
   }

   /**
    * Get the row count and a value of the snapshot table a mirror reads.
    */
   private static String mirroredValue(Worksheet ws, String mirror) {
      Assembly assembly = ((MirrorAssembly) ws.getAssembly(mirror)).getAssembly();
      assertInstanceOf(SnapshotEmbeddedTableAssembly.class, assembly);
      assertSame(ws.getAssembly(assembly.getName()), assembly,
                 "the mirror reads a copy that is not in the worksheet");
      XSwappableTable table = ((SnapshotEmbeddedTableAssembly) assembly).getTable();
      table.moreRows(XTable.EOT);
      return table.getRowCount() + "/" + table.getObject(5, 1);
   }

   private static SnapshotEmbeddedTableAssembly outerCopy(Worksheet ws) {
      List<Assembly> copies = Arrays.stream(ws.getAssemblies())
         .filter(a -> a instanceof SnapshotEmbeddedTableAssembly && ((WSAssembly) a).isOuter())
         .collect(Collectors.toList());
      assertEquals(1, copies.size(), names(ws).toString());
      return (SnapshotEmbeddedTableAssembly) copies.get(0);
   }

   private String[] saveOwner(AssetEntry entry, String tag, int rows) throws Exception {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly table = new SnapshotEmbeddedTableAssembly(ws, NAME);
      ws.addAssembly(table);
      ws.setPrimaryAssembly(NAME);
      table.setEmbeddedData(new XEmbeddedTable(createTable(tag, rows)));
      save(ws, entry);
      return table.getDataPaths();
   }

   private String[] resaveOwner(AssetEntry entry, String tag, int rows) throws Exception {
      Worksheet ws = open(entry);
      SnapshotEmbeddedTableAssembly table = (SnapshotEmbeddedTableAssembly) ws.getAssembly(NAME);
      table.setEmbeddedData(new XEmbeddedTable(createTable(tag, rows)));
      save(ws, entry);
      return table.getDataPaths();
   }

   // a composer save, which updates the dependencies the rename transform finds W2 by
   private void save(Worksheet ws, AssetEntry entry) throws Exception {
      worksheetService.setWorksheet(ws, entry, null, true, true);
   }

   private static Worksheet open(AssetEntry entry) throws Exception {
      return (Worksheet) repository().getSheet(entry, null, false, AssetContent.ALL);
   }

   private static Worksheet stored(AssetEntry entry) throws Exception {
      return (Worksheet) repository().getStorage(entry)
         .getXMLSerializable(entry.toIdentifier(), null);
   }

   private static void clearCaches(AssetEntry entry) {
      repository().clearCache(entry);
      SnapshotEmbeddedTableDataCache.getInstance().clear();
   }

   // assert membership by the assembly list, not getAssembly(name)
   private static List<String> names(Worksheet ws) {
      return Arrays.stream(ws.getAssemblies()).map(Assembly::getName)
         .collect(Collectors.toList());
   }

   private static List<String> sorted(List<String> names) {
      List<String> list = new ArrayList<>(names);
      Collections.sort(list);
      return list;
   }

   private static AbstractAssetEngine repository() {
      return (AbstractAssetEngine) AssetUtil.getAssetRepository(false);
   }

   private static AssetEntry entry(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, name, null);
   }

   private static XSwappableTable createTable(String tag, int rows) {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });

      for(int i = 0; i < rows; i++) {
         table.addRow(new Object[] { i, tag + i });
      }

      table.complete();
      return table;
   }

   @Configuration
   static class Beans {
      // the real dependency handler (the base configuration mocks it), the rename transform
      // finds the worksheets depending on the renamed one by it
      @Bean
      @Primary
      public DependencyHandler realDependencyHandler(XRepository xRepository) {
         return new LocalDependencyHandler(xRepository);
      }

      // the real rename transform, run right away instead of through the cluster queue
      @Bean
      public RenameTransformHandler renameTransformHandler() {
         return new RenameTransformHandler(null, null) {
            @Override
            public void addTransformTask(RenameDependencyInfo dinfo, boolean waitDone) {
               DependencyTransformer.renameDep(dinfo);
            }
         };
      }
   }

   private static final String NAME = "T";
   private static final String MIRROR = "M";
   private static final String LEGACY_COPY = "OUTER_oci_legacy_old_name_0";
}
