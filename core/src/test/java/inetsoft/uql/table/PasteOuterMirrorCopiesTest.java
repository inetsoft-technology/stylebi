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
 * Bug #78037: an outer mirror pasted into a worksheet that already embeds the same worksheet
 * gets its own copies. Sharing the target's copies made a frozen (Auto Update off) pasted mirror
 * show the target's rows, and made an auto pasted mirror unfreeze the target's frozen mirror.
 * W1 is saved with "old" rows, embedded by W3, re-saved with "mid" rows, embedded by W2 and
 * re-saved with "new" rows, so the rows a mirror reads tell whose copy it is. Runs through the
 * real paste service, save path ({@code WorksheetService.setWorksheet}) and open path
 * ({@code getSheet(..., AssetContent.ALL)}).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SnapshotWorksheetSaveFailureTest.Beans.class,
                                  PasteOuterMirrorCopiesTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context with the real asset repository
class PasteOuterMirrorCopiesTest {
   @Autowired
   private WorksheetService worksheetService;

   // S1: frozen mirror pasted next to an auto mirror of the same worksheet
   @Test
   void frozenMirrorPastedNextToAutoMirrorKeepsItsRows() throws Exception {
      Sheets s = timeline("pomc_s1", false, true);
      String pasted = paste(s);
      assertEquals("51/old4", mirroredValue(s.ws2, pasted));
      assertOwnCopy(s.ws2, pasted);

      reopenTwice(s, ws2 -> {
         assertEquals(4, ws2.getAssemblies().length, names(ws2).toString());
         assertEquals("81/new4", mirroredValue(ws2, MIRROR + 0));
         assertEquals("51/old4", mirroredValue(ws2, pasted));
      });
   }

   // S2: frozen mirror pasted next to a frozen mirror of the same worksheet, and the pasted copy
   // doesn't need W3 once W2 is saved
   @Test
   void frozenMirrorPastedNextToFrozenMirrorKeepsItsRows() throws Exception {
      Sheets s = timeline("pomc_s2", false, false);
      String pasted = paste(s);
      assertEquals("51/old4", mirroredValue(s.ws2, pasted));
      assertEquals("61/mid4", mirroredValue(s.ws2, MIRROR + 0));
      assertOwnCopy(s.ws2, pasted);
      save(s.ws2, s.e2);
      repository().removeSheet(s.e3, null, true);

      reopenTwice(s, ws2 -> {
         assertEquals(4, ws2.getAssemblies().length, names(ws2).toString());
         assertEquals("61/mid4", mirroredValue(ws2, MIRROR + 0));
         assertEquals("51/old4", mirroredValue(ws2, pasted));
      });
   }

   // S3: W1 renamed after W3 embedded it, W2 embeds it under the new name
   @Test
   void frozenMirrorOfRenamedWorksheetPastedKeepsItsRows() throws Exception {
      AssetEntry e1 = entry("pomc_s3_w1");
      AssetEntry moved = entry("pomc_s3_w1z");
      AssetEntry e2 = entry("pomc_s3_w2");
      AssetEntry e3 = entry("pomc_s3_w3");
      saveOwner(e1, "old", 50);
      save(embed(new AssetEntry[] { e1 }, new boolean[] { false }), e3);
      repository().changeSheet(e1, moved, null, true);
      resaveOwner(moved, "mid", 60);
      save(embed(new AssetEntry[] { moved }, new boolean[] { false }), e2);
      resaveOwner(moved, "new", 80);
      Sheets s = load(e2, e3);
      assertEquals(moved, ((MirrorAssembly) s.ws3.getAssembly(MIRROR + 0)).getEntry(),
                   "the rename transform didn't rewrite the mirror");

      String pasted = paste(s);
      assertEquals("51/old4", mirroredValue(s.ws2, pasted));
      assertOwnCopy(s.ws2, pasted);

      reopenTwice(s, ws2 -> {
         assertEquals(4, ws2.getAssemblies().length, names(ws2).toString());
         assertEquals("61/mid4", mirroredValue(ws2, MIRROR + 0));
         assertEquals("51/old4", mirroredValue(ws2, pasted));
      });
   }

   // R2: the same frozen mirror pasted twice gets two sets of copies, kept over reopens
   @Test
   void frozenMirrorPastedTwiceKeepsTwoSetsOfCopies() throws Exception {
      Sheets s = timeline("pomc_r2", false, false);
      String pasted1 = paste(s);
      String pasted2 = paste(s);
      assertOwnCopy(s.ws2, pasted1);
      assertOwnCopy(s.ws2, pasted2);
      assertNotEquals(copyName(s.ws2, pasted1), copyName(s.ws2, pasted2));
      save(s.ws2, s.e2);
      List<String> stored = sorted(names(stored(s.e2)));
      assertEquals(6, stored.size(), stored.toString());

      reopenTwice(s, ws2 -> {
         assertEquals(stored, sorted(names(ws2)));
         assertEquals("61/mid4", mirroredValue(ws2, MIRROR + 0));
         assertEquals("51/old4", mirroredValue(ws2, pasted1));
         assertEquals("51/old4", mirroredValue(ws2, pasted2));
      });
   }

   // A1: an auto mirror pasted next to a frozen mirror of the same worksheet doesn't make the
   // frozen mirror's copies updated on load
   @Test
   void autoMirrorPastedNextToFrozenMirrorKeepsItFrozen() throws Exception {
      Sheets s = timeline("pomc_a1", true, false);
      String pasted = paste(s);
      assertOwnCopy(s.ws2, pasted);

      reopenTwice(s, ws2 -> {
         assertEquals("61/mid4", mirroredValue(ws2, MIRROR + 0));
         assertEquals("81/new4", mirroredValue(ws2, pasted));
         assertEquals(4, ws2.getAssemblies().length, names(ws2).toString());
      });
   }

   // A2: an auto mirror pasted next to an auto mirror of the same worksheet, both updated on
   // load, no copies left over
   @Test
   void autoMirrorPastedNextToAutoMirrorKeepsCopyCount() throws Exception {
      Sheets s = timeline("pomc_a2", true, true);
      String pasted = paste(s);
      assertOwnCopy(s.ws2, pasted);

      reopenTwice(s, ws2 -> {
         assertEquals(4, ws2.getAssemblies().length, names(ws2).toString());
         assertEquals("81/new4", mirroredValue(ws2, MIRROR + 0));
         assertEquals("81/new4", mirroredValue(ws2, pasted));
      });
   }

   // N1: W2 embeds W3, so it holds a nested copy of W3's mirror of W1; W3's mirror pasted into
   // W2 still gets its own copies
   @Test
   void frozenMirrorPastedIntoWorksheetEmbeddingItsWorksheetKeepsItsRows() throws Exception {
      AssetEntry e1 = entry("pomc_n1_w1");
      AssetEntry e2 = entry("pomc_n1_w2");
      AssetEntry e3 = entry("pomc_n1_w3");
      saveOwner(e1, "old", 50);
      save(embed(new AssetEntry[] { e1 }, new boolean[] { false }), e3);
      save(embed(new AssetEntry[] { e3 }, new boolean[] { false }), e2);
      resaveOwner(e1, "new", 80);
      Sheets s = load(e2, e3);
      assertTrue(Arrays.asList(s.ws2.getOuterDependents()).contains(e1));

      String pasted = paste(s);
      assertEquals("51/old4", mirroredValue(s.ws2, pasted));
      assertOwnCopy(s.ws2, pasted);

      reopenTwice(s, ws2 -> {
         assertEquals(5, ws2.getAssemblies().length, names(ws2).toString());
         assertEquals("51/old4", mirroredValue(ws2, pasted));
         // the copy of W3's mirror, reading the copy of W3's copy of W1
         String nested = copyName(ws2, MIRROR + 0);
         assertEquals("51/old4", mirroredValue(ws2, nested));
      });
   }

   // removing either mirror after the paste removes only its own copies
   @Test
   void removingMirrorAfterPasteRemovesOnlyItsCopies() throws Exception {
      for(boolean auto : new boolean[] { false, true }) {
         Sheets s = timeline("pomc_rm_" + auto, auto, auto);
         String pasted = paste(s);
         save(s.ws2, s.e2);
         String value0 = auto ? "81/new4" : "61/mid4";
         String value1 = auto ? "81/new4" : "51/old4";

         clearCaches(s.e2);
         Worksheet ws2 = open(s.e2);
         String copy0 = copyName(ws2, MIRROR + 0);
         ws2.removeAssembly(pasted);
         assertEquals(Arrays.asList(MIRROR + 0, copy0), sorted(names(ws2)));
         assertEquals(value0, mirroredValue(ws2, MIRROR + 0));

         clearCaches(s.e2);
         ws2 = open(s.e2);
         String copy1 = copyName(ws2, pasted);
         ws2.removeAssembly(MIRROR + 0);
         assertEquals(sorted(Arrays.asList(pasted, copy1)), sorted(names(ws2)));
         assertEquals(value1, mirroredValue(ws2, pasted));
      }
   }

   /**
    * W1 saved with old rows, embedded by W3, re-saved with mid rows, embedded by W2, re-saved
    * with new rows. W2 is open, W3 as stored with its frozen copy's data loaded, as the paste
    * needs it.
    */
   private Sheets timeline(String name, boolean auto3, boolean auto2) throws Exception {
      AssetEntry e1 = entry(name + "_w1");
      AssetEntry e2 = entry(name + "_w2");
      AssetEntry e3 = entry(name + "_w3");
      saveOwner(e1, "old", 50);
      save(embed(new AssetEntry[] { e1 }, new boolean[] { auto3 }), e3);
      resaveOwner(e1, "mid", 60);
      save(embed(new AssetEntry[] { e1 }, new boolean[] { auto2 }), e2);
      resaveOwner(e1, "new", 80);
      return load(e2, e3);
   }

   private static Sheets load(AssetEntry e2, AssetEntry e3) throws Exception {
      clearCaches(e2);
      clearCaches(e3);
      Sheets s = new Sheets();
      s.e2 = e2;
      s.e3 = e3;
      s.ws2 = open(e2);
      s.ws3 = stored(e3);

      // a frozen copy's files are its own, an auto one's are W1's, replaced since
      if(!((MirrorAssembly) s.ws3.getAssembly(MIRROR + 0)).isAutoUpdate()) {
         outerCopy(s.ws3).getTable();
      }

      return s;
   }

   /**
    * Save W2, then reopen and check it twice.
    */
   private void reopenTwice(Sheets s, WorksheetCheck check) throws Exception {
      save(s.ws2, s.e2);

      for(int i = 0; i < 2; i++) {
         clearCaches(s.e2);
         Worksheet ws2 = open(s.e2);
         check.check(ws2);
         save(ws2, s.e2);
      }
   }

   /**
    * Paste W3's mirror into W2, as the composer does, and get the name of the pasted mirror.
    */
   private static String paste(Sheets s) throws Exception {
      Set<String> before = new HashSet<>(names(s.ws2));
      RuntimeWorksheet source = runtimeWorksheet(s.ws3, s.e3);
      RuntimeWorksheet target = runtimeWorksheet(s.ws2, s.e2);
      ViewsheetService engine = mock(ViewsheetService.class);
      when(engine.getWorksheet(eq("source"), any())).thenReturn(source);
      when(engine.getWorksheet(eq("target"), any())).thenReturn(target);
      WSPasteAssembliesEvent event = new WSPasteAssembliesEvent();
      event.setAssemblies(new String[] { MIRROR + 0 });
      event.setSourceRuntimeId("source");

      // the commands that show the pasted assemblies
      try(MockedStatic<WorksheetEventUtil> ignored = mockStatic(WorksheetEventUtil.class)) {
         new PasteAssembliesService(engine, null)
            .pasteAssemblies("target", event, null, mock(CommandDispatcher.class));
      }

      List<String> mirrors = Arrays.stream(s.ws2.getAssemblies())
         .filter(a -> a instanceof MirrorAssembly && !((WSAssembly) a).isOuter())
         .map(Assembly::getName)
         .filter(n -> !before.contains(n))
         .collect(Collectors.toList());
      assertEquals(1, mirrors.size(), names(s.ws2).toString());
      return mirrors.get(0);
   }

   private static RuntimeWorksheet runtimeWorksheet(Worksheet ws, AssetEntry entry) {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getEntry()).thenReturn(entry);
      return rws;
   }

   /**
    * Check that a pasted mirror reads copies of its own, not ones another mirror uses.
    */
   private static void assertOwnCopy(Worksheet ws, String pasted) {
      MirrorAssembly mirror = (MirrorAssembly) ws.getAssembly(pasted);
      WSAssembly[] copies = ws.getOuterCopies(mirror);
      assertTrue(copies.length > 0, "no copies pasted: " + names(ws));

      for(Assembly assembly : ws.getAssemblies()) {
         if(assembly != mirror && assembly instanceof MirrorAssembly &&
            ((MirrorAssembly) assembly).isOuterMirror() && !((WSAssembly) assembly).isOuter())
         {
            Set<String> other = Arrays.stream(ws.getOuterCopies((MirrorAssembly) assembly))
               .map(Assembly::getName).collect(Collectors.toSet());

            for(WSAssembly copy : copies) {
               assertFalse(other.contains(copy.getName()),
                           copy.getName() + " shared with " + assembly.getName());
            }
         }
      }
   }

   private static String copyName(Worksheet ws, String mirror) {
      return ((MirrorAssembly) ws.getAssembly(mirror)).getAssemblyName();
   }

   /**
    * Embed worksheets the way dragging them into a worksheet in the composer does.
    */
   private static Worksheet embed(AssetEntry[] entries, boolean[] autoUpdate) throws Exception {
      Worksheet ws = new Worksheet();

      for(int i = 0; i < entries.length; i++) {
         WSAssembly[] created =
            AssetUtil.copyOuterAssemblies(repository(), entries[i], null, ws, null);
         MirrorTableAssembly mirror = new MirrorTableAssembly(
            ws, MIRROR + i, entries[i], true, created[created.length - 1]);
         mirror.setAutoUpdate(autoUpdate[i]);
         ws.addAssembly(mirror);
      }

      ws.setPrimaryAssembly(MIRROR + 0);
      return ws;
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

   private void saveOwner(AssetEntry entry, String tag, int rows) throws Exception {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly table = new SnapshotEmbeddedTableAssembly(ws, NAME);
      ws.addAssembly(table);
      ws.setPrimaryAssembly(NAME);
      table.setEmbeddedData(new XEmbeddedTable(createTable(tag, rows)));
      save(ws, entry);
   }

   private void resaveOwner(AssetEntry entry, String tag, int rows) throws Exception {
      Worksheet ws = open(entry);
      SnapshotEmbeddedTableAssembly table = (SnapshotEmbeddedTableAssembly) ws.getAssembly(NAME);
      table.setEmbeddedData(new XEmbeddedTable(createTable(tag, rows)));
      save(ws, entry);
   }

   // a composer save, which updates the dependencies the rename transform finds W3 by
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

   private static class Sheets {
      AssetEntry e2;
      AssetEntry e3;
      Worksheet ws2;
      Worksheet ws3;
   }

   private interface WorksheetCheck {
      void check(Worksheet ws) throws Exception;
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
}
