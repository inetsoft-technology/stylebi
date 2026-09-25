/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for null handling in the range slider selection list. Bug #75823 (a member with a
 * null aggregate/measure value showed up as a blank min label) was originally fixed with a
 * NULL-exclusion pre-runtime condition, which broke XMLA/cube-bound sliders; the null is
 * now filtered on the query result instead, and only for a SingleTimeInfo binding.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class TimeSliderVSAQueryTest {
   private Viewsheet viewsheet;
   private TimeSliderVSAssembly assembly;
   private TimeSliderVSAQuery query;

   @BeforeEach
   void setUp() {
      viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      assembly = new TimeSliderVSAssembly();
      ((TimeSliderVSAssemblyInfo) assembly.getVSAssemblyInfo()).setName(SLIDER);
      viewsheet.addAssembly(assembly);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(viewsheet);

      query = new TimeSliderVSAQuery(box, SLIDER);
   }

   /**
    * A single-bound slider can't anchor its min/max on a null, so the row is dropped
    * rather than shown as a blank label.
    */
   @Test
   void testSingleTimeInfoSkipsNullValue() throws Exception {
      SingleTimeInfo tinfo = new SingleTimeInfo();
      tinfo.setRangeTypeValue(TimeInfo.MEMBER);
      tinfo.setDataRef(createRef(MEASURE));
      assembly.setTimeInfo(tinfo);

      query.refreshSelectionValue(createTable(new Object[][] {
         { MEASURE },
         { "a" },
         { null },
         { "b" }
      }));

      assertEquals(List.of("a", "b"), getSelectionValues());
   }

   /**
    * A composite slider that happens to have a single ref is a different binding (e.g. a
    * plain string dimension dragged onto a range slider), and still lists null members.
    */
   @Test
   void testCompositeTimeInfoWithSingleRefKeepsNullValue() throws Exception {
      CompositeTimeInfo tinfo = new CompositeTimeInfo();
      tinfo.setDataRefs(new DataRef[]{ createRef(MEASURE) });
      assembly.setTimeInfo(tinfo);

      query.refreshSelectionValue(createTable(new Object[][] {
         { MEASURE },
         { "a" },
         { null },
         { "b" }
      }));

      assertEquals(List.of("a", CoreTool.FAKE_NULL, "b"), getSelectionValues());
   }

   /**
    * A null in one column of a multi-ref composite slider never dropped the row.
    */
   @Test
   void testCompositeTimeInfoWithMultipleRefsKeepsNullValue() throws Exception {
      CompositeTimeInfo tinfo = new CompositeTimeInfo();
      tinfo.setDataRefs(new DataRef[]{ createRef(MEASURE), createRef(OTHER) });
      assembly.setTimeInfo(tinfo);

      query.refreshSelectionValue(createTable(new Object[][] {
         { MEASURE, OTHER },
         { "a", "x" },
         { null, "y" },
         { "b", "z" }
      }));

      assertEquals(List.of("a::x", CoreTool.FAKE_NULL + "::y", "b::z"), getSelectionValues());
   }

   /**
    * Bug #76942 (Finish-path sub-issue): {@code refreshSingleSelectionValue()}'s NUMBER-range
    * branch recomputes a fresh "nice" tick grid from the current query's own min/max every
    * time, then requires an EXACT match against the previously selected value (sourced from
    * {@code TimeSliderVSAssembly.getRuntimeMin()} or, failing that, {@code getSelectedMin()},
    * which reflects a selection made against a possibly different query context -- e.g. a
    * Wizard Chart -> Crosstab type change repointing an ad hoc {@code VS_ASSEMBLY}-sourced
    * range filter's {@code tableName} onto a structurally different query pipeline). When the
    * two independently-computed tick grids differ even slightly, no exact match is found, the
    * position stays -1, and a pre-existing fallback (comment references bug1295840324493)
    * clears the selection to the entire range. See
    * docs/teams/2026-09-23-bugs-76942-wizard-filter-loss/bug-76942/11-diagnosis-finish-numeric.md
    * and 12-fix-finish-numeric.md.
    */
   @Nested
   class NumberRangeTickGridRecomputeDrift {
      @BeforeEach
      void setUpNumberRange() {
         SingleTimeInfo tinfo = new SingleTimeInfo();
         tinfo.setRangeTypeValue(TimeInfo.NUMBER);
         ColumnRef ref = createRef(MEASURE);
         ref.setDataType(XSchema.DOUBLE);
         tinfo.setDataRef(ref);
         assembly.setTimeInfo(tinfo);
      }

      /**
       * Baseline/regression guard: when the previously selected value still lands exactly on a
       * tick in the freshly recomputed grid (the common case -- e.g. an ordinary refresh
       * against the same, unchanged query pipeline), the fast exact-match path is unaffected by
       * this fix and the selection is preserved exactly as before.
       */
      @Test
      void exactTickMatchPreservesSelection() throws Exception {
         double[] ticks = readTickGrid(0.0, 100.0);
         double alignedCurr = ticks[3];

         selectPreviousMin(alignedCurr);
         invokeRefreshSingleSelectionValue(query, new Object[] { 0.0, 100.0 }, NORMAL_HINT);

         SelectionList state = assembly.getStateSelectionList();
         int fullRangeCount = assembly.getSelectionList().getSelectionValueCount();

         assertNotNull(state);
         assertEquals(Tool.toString(alignedCurr), state.getSelectionValue(0).getValue());
         assertTrue(state.getSelectionValueCount() < fullRangeCount,
            "an exact tick match should not fall back to selecting the entire range");
      }

      /**
       * Reproduces the diagnosis's core mechanism directly, using two real, independent query
       * executions with slightly different min/max (not a hand-constructed mismatched value) --
       * exactly modeling "the same TimeSliderVSAssembly is queried by two structurally
       * different pipelines over conceptually the same data" (e.g. ChartVSAQuery, then
       * AbstractCrosstabVSAQuery after a Wizard Chart -> Crosstab Finish).
       * <p>
       * Empirically confirmed (by reverting the production fix and rerunning only this
       * scenario) that the exact-bucket-index match genuinely fails against the pre-fix code:
       * the first query ([0, 100]) selects the tick at value 6; the second query ([8, 100] --
       * simulating a different pipeline whose own computed minimum came out a bit higher, even
       * though the underlying data didn't change) recomputes a grid starting at 8, and the
       * pre-fix code logs "Invalid position, clearing all state selection"
       * (bug1295840324493's fallback) and leaves the state selection empty. This is a stronger,
       * narrower condition than "any off-grid value" (see exactTickMatchPreservesSelection's
       * sibling investigation notes in 12-fix-finish-numeric.md): the currV bucket-index
       * arithmetic already tolerates an interior, off-tick curr via floor-style bucketing --
       * pos only goes negative when curr's computed bucket falls outside the freshly recomputed
       * ticks array entirely, which is exactly what happens here (6 falls below the new grid's
       * first tick of 8).
       * <p>
       * After this fix, the same scenario preserves the selection at the new grid's own nearest
       * tick instead of clearing it.
       */
      @Test
      void survivesTickGridRecomputeDrift() throws Exception {
         invokeRefreshSingleSelectionValue(query, new Object[] { 0.0, 100.0 }, NORMAL_HINT);
         double prevCurr =
            Double.parseDouble(assembly.getSelectionList().getSelectionValue(3).getValue());
         selectPreviousMin(prevCurr);

         invokeRefreshSingleSelectionValue(query, new Object[] { 8.0, 100.0 }, NORMAL_HINT);

         SelectionList newTicks = assembly.getSelectionList();
         double newGridMin = Double.parseDouble(newTicks.getSelectionValue(0).getValue());

         // setup invariant, self-checking against internal nice-number algorithm changes: the
         // new pipeline's grid must have genuinely shifted past the previous selection for this
         // to be a meaningful reproduction of the bug.
         assertTrue(newGridMin > prevCurr,
            "test setup invariant: the second query's recomputed grid must start past the " +
            "previously selected value (was " + prevCurr + ", new grid starts at " +
            newGridMin + ") to reproduce the pos == -1 condition this bug depends on");

         SelectionList state = assembly.getStateSelectionList();

         assertNotNull(state);
         assertTrue(state.getSelectionValueCount() > 0,
            "recomputation drift between two query pipelines over the same underlying data " +
            "should not silently clear the selection to empty (bug #76942)");
         // lands on the new grid's own nearest tick to the previous selection, not an
         // arbitrary one
         assertEquals(newTicks.getSelectionValue(0).getValue(), state.getSelectionValue(0).getValue());
      }

      /**
       * Side-effect guard: when the previously selected value is genuinely nowhere near the
       * freshly computed data range -- simulating the underlying data itself changing, not just
       * a different query pipeline recomputing the same data -- the pre-existing
       * bug1295840324493 fallback (pos stays -1, so the state-selection-building loop a few
       * lines below is skipped entirely, leaving the state selection empty -- the UI then shows
       * no highlighted sub-range, i.e. "the entire range" is in effect) must still fire
       * unchanged. The fix must not paper over a real data change by inventing a nearby
       * selection that doesn't correspond to anything meaningful in the new data.
       */
      @Test
      void fallsBackToFullRangeWhenSelectionIsGenuinelyOutOfRange() throws Exception {
         double[] ticks = readTickGrid(0.0, 100.0);
         double step = ticks[1] - ticks[0];
         double outOfRangeCurr = ticks[ticks.length - 1] + step * 50;

         selectPreviousMin(outOfRangeCurr);
         invokeRefreshSingleSelectionValue(query, new Object[] { 0.0, 100.0 }, NORMAL_HINT);

         SelectionList state = assembly.getStateSelectionList();

         assertNotNull(state);
         assertEquals(0, state.getSelectionValueCount(),
            "a genuinely out-of-range previous selection should still fall back to " +
            "bug1295840324493's pre-existing behavior (pos stays -1, state selection ends " +
            "up empty), not be papered over by the tolerant nearest-tick match");
      }

      /**
       * Reads off the actual "nice" tick grid {@code refreshSingleSelectionValue()} computes
       * for the given min/max, by performing real query executions and inspecting the
       * resulting {@code SelectionList}, rather than re-implementing the nice-number rounding
       * logic in the test. Called twice: {@code SingleTimeInfo.rangeSizeValue} only settles
       * into a fixed point after the first call (the first call derives and persists it; from
       * the second call on, with the same min/max, the persisted value is only ever re-read,
       * never re-derived), so the grid from the second call is the one a subsequent call with
       * the same data (this test's later "real" call) is guaranteed to reproduce exactly.
       */
      private double[] readTickGrid(double min, double max) throws Exception {
         invokeRefreshSingleSelectionValue(query, new Object[] { min, max }, NORMAL_HINT);
         invokeRefreshSingleSelectionValue(query, new Object[] { min, max }, NORMAL_HINT);
         SelectionList ticks = assembly.getSelectionList();
         double[] result = new double[ticks.getSelectionValueCount()];

         for(int i = 0; i < result.length; i++) {
            result[i] = Double.parseDouble(ticks.getSelectionValue(i).getValue());
         }

         return result;
      }

      /**
       * Directly installs a state selection list whose single selected value is {@code value},
       * simulating a previously-made selection that {@code getSelectedMin()} will read back on
       * the next query execution (since {@code TimeSliderVSAssembly.getRuntimeMin()} is null
       * here, matching the diagnosis's traced ad hoc VS_ASSEMBLY filter scenario, not a
       * script-set {@code setMin()} value).
       */
      private void selectPreviousMin(double value) {
         SelectionValue sval = new SelectionValue(Tool.toString(value), Tool.toString(value));
         sval.setState(SelectionValue.STATE_SELECTED);
         SelectionList stateList = new SelectionList();
         stateList.addSelectionValue(sval);
         assembly.setStateSelectionList(stateList);
      }
   }

   /**
    * Bug #76942 (round 5 -- Finish/settle window, {@code slist} corruption): round 4's fix
    * above ({@link NumberRangeTickGridRecomputeDrift}) is real but, per
    * docs/teams/2026-09-23-bugs-76942-wizard-filter-loss/bug-76942/13-diagnosis-round4-insufficient.md,
    * insufficient on its own -- {@code refreshSelectionValue0()} computes an {@code ALL} hint
    * from the assembly's *pre-query* {@code getCurrentPos()}/{@code getTotalLength()}, and
    * {@code refreshSingleSelectionValue()}'s {@code (hint & ALL) == ALL} branch
    * unconditionally overrides whatever {@code pos} the NUMBER branch above it computed --
    * including round 4's own nearest-tick rescue -- forcing a full-range selection regardless.
    * <p>
    * {@code getTotalLength()} (in {@code TimeSliderVSAssemblyInfo}) returns 0 whenever the
    * assembly's own {@code slist} is null, and {@code refreshSelectionValue0()} substitutes a
    * degenerate default ({@code total = 2}) in that case -- which, combined with any positive
    * leftover {@code tinfo.getLength()}, satisfies {@code length >= total - 1} and computes
    * {@code hint = ALL} even though nothing about the user's actual selection was ever "select
    * everything". This is exactly what was captured live: {@code curr} correctly recomputed
    * from {@code TimeSliderVSAssembly.getRuntimeMin()} (a plain field, independent of
    * {@code slist}) while {@code hint} was already locked to {@code ALL} from a degraded prior
    * {@code slist} read.
    * <p>
    * The fix ({@code TimeSliderVSAQuery.refreshSingleSelectionValue()}): only let a recovered
    * NUMBER-branch {@code pos} win over the {@code ALL} override when the {@code ALL} hint
    * itself was derived from that degraded prior state ({@code priorTotalDegraded}, captured
    * from {@code assembly.getTotalLength() <= 1} before this call rebuilds the slist) -- a
    * genuinely fully-populated prior selection (the assembly's own slist really did already
    * span the whole range) must still be preserved as {@code ALL} unchanged.
    */
   @Nested
   class AllHintOverride {
      private static final int ALL_HINT = 1;

      @BeforeEach
      void setUpNumberRange() {
         SingleTimeInfo tinfo = new SingleTimeInfo();
         tinfo.setRangeTypeValue(TimeInfo.NUMBER);
         ColumnRef ref = createRef(MEASURE);
         ref.setDataType(XSchema.DOUBLE);
         tinfo.setDataRef(ref);
         assembly.setTimeInfo(tinfo);
      }

      /**
       * Direct reproduction of the live-captured mechanism: the assembly's own {@code slist} was
       * never (yet) meaningfully populated going into this call -- {@code getTotalLength() <= 1}
       * -- so an {@code ALL} hint computed from it is a false "preserve full range" signal, not
       * a real one. But {@code runtimeMin} (an independent, non-{@code slist}-derived field --
       * exactly what a right-click-bar ad hoc {@code VS_ASSEMBLY} range filter's previously
       * selected value survives in through the Wizard Finish/settle window) still correctly
       * carries the real previously-selected value, and the NUMBER branch's own tick-matching
       * (including round 4's nearest-tick rescue) is able to recover a real position from it.
       * That recovered position must not be discarded for "select everything".
       */
      @Test
      void recoveredNumberPositionSurvivesAllHintWhenPriorStateWasDegraded() throws Exception {
         assertEquals(0, assembly.getTotalLength(),
            "test setup invariant: the assembly's slist must be genuinely unpopulated " +
            "(getTotalLength() <= 1) going into this call, matching the live-captured " +
            "'first query since Finish' state, not a hand-picked flag");

         assembly.setRuntimeMin(30.0);
         invokeRefreshSingleSelectionValue(query, new Object[] { 0.0, 100.0 }, ALL_HINT);

         SelectionList state = assembly.getStateSelectionList();
         int fullRangeCount = assembly.getSelectionList().getSelectionValueCount();

         assertNotNull(state);
         assertTrue(state.getSelectionValueCount() > 0 &&
            state.getSelectionValueCount() < fullRangeCount,
            "an ALL hint computed from a merely-degraded prior slist (bug #76942) must not " +
            "discard a position the NUMBER branch was able to recover from the real prior " +
            "selection (runtimeMin=30.0); got " + state.getSelectionValueCount() +
            " of " + fullRangeCount + " selected");
      }

      /**
       * Side-effect guard, the mirror image of the test above: when the assembly's slist really
       * was already meaningfully populated (a genuine prior selection existed, not a degraded/
       * unpopulated one) and that prior state genuinely covered the whole range, an ALL hint on
       * the next query must still select the entire freshly recomputed range exactly as before
       * this fix -- this fix only ever skips the override when the ALL signal itself was
       * spurious (degraded prior slist), never for a real full-range selection.
       */
      @Test
      void allHintStillSelectsFullRangeWhenPriorStateWasGenuinelyPopulated() throws Exception {
         invokeRefreshSingleSelectionValue(query, new Object[] { 0.0, 100.0 }, NORMAL_HINT);
         assertTrue(assembly.getTotalLength() > 1,
            "test setup invariant: the first call must leave a genuinely populated slist " +
            "behind, or this test would not be exercising priorTotalDegraded == false");
         int fullRangeCount = assembly.getSelectionList().getSelectionValueCount();

         invokeRefreshSingleSelectionValue(query, new Object[] { 0.0, 100.0 }, ALL_HINT);

         SelectionList state = assembly.getStateSelectionList();

         assertNotNull(state);
         assertEquals(fullRangeCount, state.getSelectionValueCount(),
            "a genuine ALL hint (prior slist was already meaningfully populated) must still " +
            "select the entire range -- this fix only skips the override when the prior state " +
            "was degraded, per bug #76942 round 5");
      }

      /**
       * Guard against the fix accidentally becoming a blanket "NUMBER always wins" rule: when
       * the prior slist was degraded (so the ALL hint is suspect, same as the first test above)
       * but the NUMBER branch is unable to recover any position at all (no runtimeMin/
       * selectedMin -- pos stays -1), the pre-existing bug1295840324493 fallback must still
       * apply and select the full range, exactly as it always has.
       */
      @Test
      void allHintStillFallsBackToFullRangeWhenNumberBranchRecoversNothing() throws Exception {
         assertEquals(0, assembly.getTotalLength());

         invokeRefreshSingleSelectionValue(query, new Object[] { 0.0, 100.0 }, ALL_HINT);

         SelectionList state = assembly.getStateSelectionList();
         int fullRangeCount = assembly.getSelectionList().getSelectionValueCount();

         assertNotNull(state);
         assertEquals(fullRangeCount, state.getSelectionValueCount(),
            "with no recoverable prior value at all, an ALL hint (even from a degraded prior " +
            "slist) must still fall back to selecting the full range");
      }
   }

   /**
    * Invokes the private {@code TimeSliderVSAQuery.refreshSingleSelectionValue(Object, int)}
    * directly -- the real production method the diagnosis traced, not a reimplementation of its
    * logic -- so the hint (NORMAL/ALL/FIRST_N/LAST_N) can be controlled explicitly rather than
    * derived from {@code refreshSelectionValue0()}'s currentPos/length bookkeeping, which is
    * unrelated to what this test is verifying.
    */
   private static void invokeRefreshSingleSelectionValue(TimeSliderVSAQuery query, Object data,
                                                           int hint)
      throws Exception
   {
      Method m = TimeSliderVSAQuery.class.getDeclaredMethod(
         "refreshSingleSelectionValue", Object.class, int.class);
      m.setAccessible(true);

      try {
         m.invoke(query, data, hint);
      }
      catch(InvocationTargetException e) {
         if(e.getCause() instanceof Exception) {
            throw (Exception) e.getCause();
         }

         throw e;
      }
   }

   // TimeSliderVSAQuery.NORMAL is private; 0 is the "normal selection" hint (no ALL/FIRST_N/
   // LAST_N override), confirmed by reading the constant declarations directly.
   private static final int NORMAL_HINT = 0;

   private static ColumnRef createRef(String name) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType(XSchema.STRING);

      return ref;
   }

   private static XTable createTable(Object[][] data) {
      return new DefaultTableLens(data);
   }

   /**
    * Get the values of the refreshed selection list, ignoring the artificial end value
    * that is appended when the upper bound is exclusive.
    */
   private List<String> getSelectionValues() {
      SelectionList slist = assembly.getSelectionList();
      List<String> values = new ArrayList<>();

      for(int i = 0; i < slist.getSelectionValueCount(); i++) {
         SelectionValue value = slist.getSelectionValue(i);

         if(!(value instanceof SelectionValue.UpperExclusiveEndValue)) {
            values.add(value.getValue());
         }
      }

      return values;
   }

   private static final String SLIDER = "RangeSlider1";
   private static final String MEASURE = "Measure";
   private static final String OTHER = "Other";
}
