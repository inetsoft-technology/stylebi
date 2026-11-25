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
package inetsoft.report.composition.execution;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Include all rows" (Bug #82313) must reach the bound table of the logical model however many
 * mirrors the viewsheet and the worksheet wrapped it in, without touching the tables the other
 * assemblies of the viewsheet share.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxAllRowsTest {
   /** The shape a wiz viewsheet over a logical model has: VS mirror -> M -> M_O -> bound. */
   private static final class Sheet {
      final Worksheet ws = new Worksheet();
      final BoundTableAssembly bound;
      final MirrorTableAssembly outer;
      final MirrorTableAssembly worksheetMirror;
      final TableAssembly viewsheetTable;
      final CrosstabVSAssembly crosstab;

      Sheet(int sourceType, boolean extraMirror) {
         bound = new BoundTableAssembly(ws, "M_O");
         bound.setSourceInfo(new SourceInfo(sourceType, "ds", "M"));
         ws.addAssembly(bound);

         MirrorTableAssembly inner = null;
         TableAssembly under = bound;

         if(extraMirror) {
            inner = new MirrorTableAssembly(ws, "M_base", bound);
            ws.addAssembly(inner);
            under = inner;
         }

         outer = new MirrorTableAssembly(ws, "M_O2", under);
         ws.addAssembly(outer);
         worksheetMirror = new MirrorTableAssembly(ws, "M", outer);
         ws.addAssembly(worksheetMirror);
         viewsheetTable = ws.getVSTableAssembly("M");

         Viewsheet vs = new Viewsheet();
         crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
         vs.addAssembly(crosstab);
      }

      void allRows(String... entities) {
         ((CrosstabVSAssemblyInfo) crosstab.getInfo()).setAllRows(List.of(entities));
      }

      TableAssembly boundCopyOfCrosstab() {
         TableAssembly ntable = ViewsheetSandbox.copyBoundTable(viewsheetTable, "__vs_MCrosstab1");
         ws.addAssembly(ntable);
         ViewsheetSandbox.applyAllRows(ntable, crosstab, ws);
         return ntable;
      }
   }

   @Test
   void theOptionReachesTheBoundTableUnderEveryMirror() {
      Sheet sheet = new Sheet(SourceInfo.MODEL, true);
      sheet.allRows("Product");

      TableAssembly ntable = sheet.boundCopyOfCrosstab();
      BoundTableAssembly reached = BoundTableAssembly.findLogicalModelTable(ntable);

      assertNotNull(reached);
      assertEquals(List.of("Product"), reached.getAllRows());
      assertNotSame(sheet.bound, reached, "the bound table the query runs on is a private copy");
   }

   @Test
   void theTablesTheOtherAssembliesShareAreUntouched() {
      Sheet sheet = new Sheet(SourceInfo.MODEL, true);
      sheet.allRows("Product");

      sheet.boundCopyOfCrosstab();

      assertTrue(sheet.bound.getAllRows().isEmpty());
      assertSame(sheet.bound,
                 ((MirrorTableAssembly) sheet.ws.getAssembly("M_base")).getTableAssembly());
      assertSame(sheet.outer, sheet.worksheetMirror.getTableAssembly());
      assertTrue(BoundTableAssembly.findLogicalModelTable(sheet.viewsheetTable)
                    .getAllRows().isEmpty());
   }

   @Test
   void aSingleMirrorOverTheBoundTableWorksToo() {
      Sheet sheet = new Sheet(SourceInfo.MODEL, false);
      sheet.allRows("Order", "Product");

      TableAssembly ntable = sheet.boundCopyOfCrosstab();

      assertEquals(List.of("Order", "Product"),
                   BoundTableAssembly.findLogicalModelTable(ntable).getAllRows());
   }

   @Test
   void theCopiesAreInTheWorksheetSoTheQueryFindsThemByName() {
      Sheet sheet = new Sheet(SourceInfo.MODEL, true);
      sheet.allRows("Product");

      TableAssembly ntable = sheet.boundCopyOfCrosstab();
      TableAssembly child = ((MirrorTableAssembly) ntable).getTableAssembly();

      assertSame(child, sheet.ws.getAssembly(child.getName()));
   }

   @Test
   void nothingChangesWithoutTheOptionOrWithoutALogicalModel() {
      Sheet plain = new Sheet(SourceInfo.MODEL, true);
      TableAssembly ntable = plain.boundCopyOfCrosstab();
      assertTrue(BoundTableAssembly.findLogicalModelTable(ntable).getAllRows().isEmpty());
      assertSame(plain.worksheetMirror, ((MirrorTableAssembly) ntable).getTableAssembly());

      Sheet physical = new Sheet(SourceInfo.PHYSICAL_TABLE, true);
      physical.allRows("Product");
      TableAssembly pcopy = physical.boundCopyOfCrosstab();
      assertNull(BoundTableAssembly.findLogicalModelTable(pcopy));
      assertSame(physical.worksheetMirror, ((MirrorTableAssembly) pcopy).getTableAssembly());
   }

   private static String key(TableAssembly table) throws Exception {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      assertTrue(table.printKey(writer));
      writer.flush();
      return out.toString();
   }

   private static String dataKey(TableAssembly table) throws Exception {
      return DataKey.create(table, null, null, AssetQuerySandbox.RUNTIME_MODE, true, 0, 0, false,
                            -1L).getValue();
   }

   /**
    * The data cache is keyed by the table's printed key. Two queries that differ only in "Include
    * all rows" return different rows, so they must not share cached data (live: with the option set
    * after the plain data was cached, the plain data came back with no query at all).
    */
   @Test
   void tablesThatDifferOnlyInAllRowsDoNotShareCachedData() throws Exception {
      Sheet plain = new Sheet(SourceInfo.MODEL, true);
      Sheet all = new Sheet(SourceInfo.MODEL, true);
      all.allRows("Product");

      TableAssembly plainTable = plain.boundCopyOfCrosstab();
      TableAssembly allTable = all.boundCopyOfCrosstab();

      assertNotEquals(key(plainTable), key(allTable));
      assertNotEquals(dataKey(plainTable), dataKey(allTable));
   }

   @Test
   void theOptionOnTheBoundTableAloneChangesItsKeyAndItsContentEquality() throws Exception {
      BoundTableAssembly a = new BoundTableAssembly(new Worksheet(), "T");
      a.setSourceInfo(new SourceInfo(SourceInfo.MODEL, "ds", "M"));
      BoundTableAssembly b = (BoundTableAssembly) a.copyAssembly("T");
      assertEquals(key(a), key(b));
      assertTrue(a.equalsContent(b));

      b.setAllRows(List.of("Product"));

      assertNotEquals(key(a), key(b));
      assertFalse(a.equalsContent(b));
      assertFalse(b.equalsContent(a));

      BoundTableAssembly c = (BoundTableAssembly) a.copyAssembly("T");
      c.setAllRows(List.of("Order"));
      assertNotEquals(key(b), key(c), "which table keeps its rows matters");
   }

   /** Off again is the plain query's key, so the plain cached data is the right data. */
   @Test
   void turningTheOptionOffGoesBackToThePlainKey() throws Exception {
      Sheet sheet = new Sheet(SourceInfo.MODEL, true);
      String plainKey = dataKey(sheet.boundCopyOfCrosstab());

      sheet.allRows("Product");
      String onKey = dataKey(sheet.boundCopyOfCrosstab());
      sheet.allRows();
      String offKey = dataKey(sheet.boundCopyOfCrosstab());

      assertNotEquals(plainKey, onKey);
      assertEquals(plainKey, offKey);
   }

   /** Records whether the worksheet lock was held each time the shared table was copied. */
   private static final class LockedBound extends BoundTableAssembly {
      final Worksheet lock;
      final List<Boolean> held;

      LockedBound(Worksheet ws, String name, List<Boolean> held) {
         super(ws, name);
         this.lock = ws;
         this.held = held;
      }

      @Override
      public WSAssembly copyAssembly(String name) {
         held.add(Thread.holdsLock(lock));
         return super.copyAssembly(name);
      }
   }

   private static final class LockedMirror extends MirrorTableAssembly {
      final Worksheet lock;
      final List<Boolean> held;

      LockedMirror(Worksheet ws, String name, WSAssembly base, List<Boolean> held) {
         super(ws, name, base);
         this.lock = ws;
         this.held = held;
      }

      @Override
      public WSAssembly copyAssembly(String name) {
         held.add(Thread.holdsLock(lock));
         return super.copyAssembly(name);
      }
   }

   /**
    * The mirrors and the bound table are shared with the queries of the other assemblies, which
    * change them in place under the worksheet lock (77867): a copy taken without it can be torn.
    * Whether a copy is torn cannot be provoked deterministically, so this checks the cause: every
    * shared table is copied with the lock held.
    */
   @Test
   void everySharedTableIsCopiedUnderTheWorksheetLock() {
      Worksheet ws = new Worksheet();
      List<Boolean> held = new ArrayList<>();
      LockedBound bound = new LockedBound(ws, "M_O", held);
      bound.setSourceInfo(new SourceInfo(SourceInfo.MODEL, "ds", "M"));
      ws.addAssembly(bound);
      LockedMirror outer = new LockedMirror(ws, "M_O2", bound, held);
      ws.addAssembly(outer);
      LockedMirror worksheetMirror = new LockedMirror(ws, "M", outer, held);
      ws.addAssembly(worksheetMirror);
      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      vs.addAssembly(crosstab);
      ((CrosstabVSAssemblyInfo) crosstab.getInfo()).setAllRows(List.of("Product"));
      TableAssembly ntable = ViewsheetSandbox.copyBoundTable(ws.getVSTableAssembly("M"),
                                                             "__vs_MCrosstab1");
      ws.addAssembly(ntable);
      held.clear();

      ViewsheetSandbox.applyAllRows(ntable, crosstab, ws);

      // M, M_O2 and the bound table
      assertEquals(3, held.size());
      assertFalse(held.contains(false), "a shared table was copied without the worksheet lock");
   }

   @Test
   void findLogicalModelTableIsNullForATableThatIsNotOverOne() {
      assertNull(BoundTableAssembly.findLogicalModelTable(null));
      assertNull(BoundTableAssembly.findLogicalModelTable(
         new BoundTableAssembly(new Worksheet(), "plain")));
   }
}
