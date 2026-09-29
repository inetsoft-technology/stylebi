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
package inetsoft.report.composition.execution.lockcycle;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Gate;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Sandbox;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Slow;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.SlowTable;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Started;
import inetsoft.report.filter.DefaultTableFilter;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.lens.*;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77223 review round 1, F1: a distinct lens D over a formula lens whose rows are all
 * computed, first read by a thread holding no lock but holding the monitor of a lens above D.
 * The formula lens takes no engine lock for computed rows (#77215), so on main D's worker reads
 * it without the lock and finishes. A thread inside {@code exec} on the same engine, reading the
 * same lens above D, waits for that monitor while it holds the engine lock, so the first reader
 * must not wait for the engine lock under the monitor.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class ComputedChainFirstReaderCycleTest {
   @BeforeEach
   public void setUp() {
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
   }

   /**
    * R reads the lens above D holding no lock, and parks inside that lens's monitor before it
    * reaches D; X, inside {@code exec}, then reads the same lens and waits for the monitor; R
    * goes on once X has parked.
    */
   @ParameterizedTest
   @EnumSource(Kind.class)
   public void monitorHolderFirstReadsComputedDistinct(Kind kind) throws Exception {
      Sandbox s = harness.sandbox();
      FormulaTableLens formula = s.formula(new SlowTable(ROWS, Slow.NONE), "fx", "field['id'] * 1");
      // every formula row is computed, as the build's type probe does for a small table
      harness.await(harness.submit(() -> drain(formula)), ACTIVE_CAP, "computing the formula");
      Gate gate = harness.gate();
      TableLens top = harness.track(kind.over(new GateFilter(new DistinctTableLens(formula), gate)));

      Started<Integer> reader = harness.startGated(gate, () -> drain(top).size());
      assertTrue(gate.awaitEntered(KNOWN_CAP), "R never read D inside the lens's monitor");
      Started<Integer> script = harness.start(() -> s.asGuest(() -> drain(top).size()));
      releaseAfter(gate, script, KNOWN_CAP);

      assertEquals(ROWS + 1, (int) harness.await(reader.future, KNOWN_CAP,
                                                 "R, the lock-free reader under the monitor"));
      assertEquals(ROWS + 1, (int) harness.await(script.future, KNOWN_CAP,
                                                 "X, the reader inside exec"));
   }

   /**
    * The lens above D, whose reads of D happen inside its own monitor.
    */
   public enum Kind {
      /** {@code MaxRowsTableLens.moreRows} under {@code synchronized(rlock)}. */
      MAX_ROWS {
         @Override
         TableLens over(TableLens base) {
            return new MaxRowsTableLens(base, 100000);
         }
      },
      /** {@code SortFilter.checkInit} under {@code synchronized(lock)}. */
      SORT {
         @Override
         TableLens over(TableLens base) {
            return new SortFilter(base, new int[] { 1 });
         }
      };

      abstract TableLens over(TableLens base);
   }

   /**
    * Parks its gate's owner at its first read of a data row, inside the monitors it holds.
    */
   private static final class GateFilter extends DefaultTableFilter {
      GateFilter(TableLens table, Gate gate) {
         super(table);
         this.gate = gate;
      }

      @Override
      public boolean moreRows(int row) {
         if(row >= 1) {
            gate.onRead();
         }

         return super.moreRows(row);
      }

      private final Gate gate;
   }

   private static final int ROWS = 50;
   private LockCycleHarness harness;
}
