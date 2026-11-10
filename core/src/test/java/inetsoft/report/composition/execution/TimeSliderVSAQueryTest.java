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

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.util.XSourceInfo;
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
import java.util.Date;
import java.util.GregorianCalendar;
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
    * Bug #79506: a database may return nulls last for ORDER BY (e.g. DB2) while the MV puts
    * them first. The composite slider data must be null-first either way so min/max and the
    * bookmark range comparison agree with and without an MV.
    */
   @Test
   void testCompositeDataSortedNullFirstWhenDatabaseReturnsNullLast() {
      TableLens nullLast = new DefaultTableLens(new Object[][] {
         { MEASURE, OTHER },
         { 0.0, "AZ" },
         { 0.0, "WA" },
         { 0.25, "WA" },
         { null, "AZ" },
         { null, "FL" }
      });

      TableLens result = TimeSliderVSAQuery.sortNullFirst(nullLast);

      assertEquals(6, result.getRowCount());
      Object[][] expected = {
         { null, "AZ" }, { null, "FL" }, { 0.0, "AZ" }, { 0.0, "WA" }, { 0.25, "WA" }
      };

      for(int r = 0; r < expected.length; r++) {
         assertEquals(expected[r][0], result.getObject(r + 1, 0), "row " + (r + 1));
         assertEquals(expected[r][1], result.getObject(r + 1, 1), "row " + (r + 1));
      }

      assertEquals(MEASURE, result.getObject(0, 0));
   }

   /**
    * An already null-first table (MV result, or a database that sorts nulls first) is kept.
    */
   @Test
   void testCompositeDataAlreadyNullFirstIsUnchanged() {
      TableLens nullFirst = new DefaultTableLens(new Object[][] {
         { MEASURE, OTHER },
         { null, "AZ" },
         { 0.0, "AZ" },
         { 0.25, "WA" }
      });

      assertSame(nullFirst, TimeSliderVSAQuery.sortNullFirst(nullFirst));
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
         // the nearest-tick rescue only applies to ad hoc VS_ASSEMBLY sliders
         assembly.setSourceType(XSourceInfo.VS_ASSEMBLY);
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
    * When the data max drops below a selection on the top bucket, a slider bound to a
    * worksheet table resets to the full range (bug1295840324493). #76942's nearest-tick
    * rescue must not snap it to the grid's last tick, which starts no bucket and so shows
    * no data.
    */
   @Nested
   class DataShrinkBelowSelection {
      @BeforeEach
      void setUpNumberRange() {
         SingleTimeInfo tinfo = new SingleTimeInfo();
         tinfo.setRangeTypeValue(TimeInfo.NUMBER);
         ColumnRef ref = createRef(MEASURE);
         ref.setDataType(XSchema.DOUBLE);
         tinfo.setDataRef(ref);
         assembly.setTimeInfo(tinfo);
      }

      @Test
      void keepsSelectionWhenGridIsUnchanged() throws Exception {
         double[] ticks = selectLastTick();
         double step = ticks[1] - ticks[0];
         double top = ticks[ticks.length - 1];

         invokeRefreshSingleSelectionValue(query, new Object[] { 0.0, 1.0 - step * 0.5 },
                                           NORMAL_HINT);

         SelectionList state = assembly.getStateSelectionList();

         assertNotNull(state);
         assertTrue(state.getSelectionValueCount() > 0);
         assertEquals(Tool.toString(top), state.getSelectionValue(0).getValue());
      }

      @Test
      void resetsToFullRangeWhenMaxDropsOneStep() throws Exception {
         double[] ticks = selectLastTick();
         double step = ticks[1] - ticks[0];

         invokeRefreshSingleSelectionValue(query, new Object[] { 0.0, 1.0 - step },
                                           NORMAL_HINT);

         assertFullRange();
      }

      @Test
      void resetsToFullRangeWhenMaxDropsOneAndAHalfSteps() throws Exception {
         double[] ticks = selectLastTick();
         double step = ticks[1] - ticks[0];

         invokeRefreshSingleSelectionValue(query, new Object[] { 0.0, 1.0 - step * 1.5 },
                                           NORMAL_HINT);

         assertFullRange();
      }

      /**
       * An ad hoc VS_ASSEMBLY slider keeps the nearest-tick rescue, but never lands on the
       * grid-closing last tick.
       */
      @Test
      void vsAssemblyRescueDoesNotLandOnLastTick() throws Exception {
         assembly.setSourceType(XSourceInfo.VS_ASSEMBLY);
         double[] ticks = selectLastTick();
         double step = ticks[1] - ticks[0];

         invokeRefreshSingleSelectionValue(query, new Object[] { 0.0, 1.0 - step * 1.5 },
                                           NORMAL_HINT);

         SelectionList newTicks = assembly.getSelectionList();
         String lastTick = newTicks.getSelectionValue(
            newTicks.getSelectionValueCount() - 1).getValue();
         SelectionList state = assembly.getStateSelectionList();

         assertNotNull(state);
         assertTrue(state.getSelectionValueCount() > 0);
         assertNotEquals(lastTick, state.getSelectionValue(0).getValue());
      }

      /**
       * Settles the [0, 1] grid and selects its last tick as the previous min, as the
       * issue's probe does.
       */
      private double[] selectLastTick() throws Exception {
         invokeRefreshSingleSelectionValue(query, new Object[] { 0.0, 1.0 }, NORMAL_HINT);
         invokeRefreshSingleSelectionValue(query, new Object[] { 0.0, 1.0 }, NORMAL_HINT);
         SelectionList list = assembly.getSelectionList();
         double[] ticks = new double[list.getSelectionValueCount()];

         for(int i = 0; i < ticks.length; i++) {
            ticks[i] = Double.parseDouble(list.getSelectionValue(i).getValue());
         }

         String top = Tool.toString(ticks[ticks.length - 1]);
         SelectionValue sval = new SelectionValue(top, top);
         sval.setState(SelectionValue.STATE_SELECTED);
         SelectionList stateList = new SelectionList();
         stateList.addSelectionValue(sval);
         assembly.setStateSelectionList(stateList);

         return ticks;
      }

      // pos stays -1, so no sub-range is selected and the entire range is in effect
      private void assertFullRange() {
         SelectionList state = assembly.getStateSelectionList();

         assertNotNull(state);
         assertEquals(0, state.getSelectionValueCount(),
            "a selection above the new data max should reset to the full range, not snap " +
            "to the grid's last tick");
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
    * Bug #80813: turning Use Metadata on and then off again must give back the slider's own
    * selection. While metadata is on the query runs on placeholder data that yields a one-tick
    * list; the full-range length must not be overwritten with that placeholder's tick count,
    * or the slider comes back selecting only its first tick.
    */
   @Nested
   class MetadataToggle {
      @BeforeEach
      void setUpDesignMode() {
         ViewsheetSandbox box = mock(ViewsheetSandbox.class);
         when(box.getID()).thenReturn("vs1");
         when(box.getViewsheet()).thenReturn(viewsheet);
         when(box.getMode()).thenReturn(RuntimeViewsheet.VIEWSHEET_DESIGN_MODE);
         query = new TimeSliderVSAQuery(box, SLIDER);
      }

      @Test
      void fullRangeMonthSliderSurvivesMetadataToggle() throws Exception {
         setUpDateSlider(TimeInfo.MONTH);
         refresh(dateData);
         int ticks = assembly.getSelectionList().getSelectionValueCount();
         assertSelected(0, ticks - 1);

         toggleMetadata(datePlaceholder, dateData);

         assertEquals(ticks - 1, assembly.getTimeInfo().getLength(),
            "the full-range length must not be replaced by the metadata placeholder's");
         assertSelected(0, ticks - 1);
      }

      @Test
      void fullRangeNumberSliderSurvivesMetadataToggle() throws Exception {
         setUpNumberSlider();
         refresh(numberData);
         int ticks = assembly.getSelectionList().getSelectionValueCount();
         assertSelected(0, ticks - 1);

         toggleMetadata(numberPlaceholder, numberData);

         assertEquals(ticks - 1, assembly.getTimeInfo().getLength());
         assertSelected(0, ticks - 1);
      }

      @Test
      void partialMonthSelectionSurvivesMetadataToggle() throws Exception {
         setUpDateSlider(TimeInfo.MONTH);
         refresh(dateData);
         selectRange(10, 5);
         refresh(dateData);
         assertSelected(10, 5);

         toggleMetadata(datePlaceholder, dateData);

         assertEquals(5, assembly.getTimeInfo().getLength());
         assertSelected(10, 5);
      }

      /**
       * Bug #80814: Preview while Use Metadata is still on runs a runtime box with real data
       * over a metadata sheet whose slider was last refreshed on the one-tick placeholder.
       * The runtime box must behave as if metadata were off and keep the full range.
       */
      @Test
      void previewWhileMetadataOnKeepsFullRangeMonthSlider() throws Exception {
         setUpDateSlider(TimeInfo.MONTH);
         refresh(dateData);
         int ticks = assembly.getSelectionList().getSelectionValueCount();

         viewsheet.getViewsheetInfo().setMetadata(true);
         refresh(datePlaceholder);
         openPreview();
         refresh(dateData);

         assertEquals(ticks - 1, assembly.getTimeInfo().getLength());
         assertSelected(0, ticks - 1);
      }

      @Test
      void previewWhileMetadataOnKeepsFullRangeNumberSlider() throws Exception {
         setUpNumberSlider();
         refresh(numberData);
         int ticks = assembly.getSelectionList().getSelectionValueCount();

         viewsheet.getViewsheetInfo().setMetadata(true);
         refresh(numberPlaceholder);
         openPreview();
         refresh(numberData);

         assertEquals(ticks - 1, assembly.getTimeInfo().getLength());
         assertSelected(0, ticks - 1);
      }

      @Test
      void previewWhileMetadataOnKeepsPartialMonthSelection() throws Exception {
         setUpDateSlider(TimeInfo.MONTH);
         refresh(dateData);
         selectRange(10, 5);
         refresh(dateData);

         viewsheet.getViewsheetInfo().setMetadata(true);
         refresh(datePlaceholder);
         openPreview();
         refresh(dateData);

         assertEquals(5, assembly.getTimeInfo().getLength());
         assertSelected(10, 5);
      }

      /**
       * The year slider of Examples/Sales Summary: a NUMBER slider on an integer Year column
       * with range size 1 over 2013..2016, whose metadata placeholder is 999..999.
       */
      @Test
      void previewWhileMetadataOnKeepsFullRangeYearSlider() throws Exception {
         setUpYearSlider();
         refresh(yearData);
         int ticks = assembly.getSelectionList().getSelectionValueCount();
         assertSelected(0, ticks - 1);

         viewsheet.getViewsheetInfo().setMetadata(true);
         refresh(yearPlaceholder);
         openPreview();
         refresh(yearData);

         assertEquals(ticks - 1, assembly.getTimeInfo().getLength());
         assertSelected(0, ticks - 1);
      }

      @Test
      void metadataToggleKeepsFullRangeYearSlider() throws Exception {
         setUpYearSlider();
         refresh(yearData);
         int ticks = assembly.getSelectionList().getSelectionValueCount();

         toggleMetadata(yearPlaceholder, yearData);

         assertEquals(ticks - 1, assembly.getTimeInfo().getLength());
         assertSelected(0, ticks - 1);
      }

      private void setUpYearSlider() {
         SingleTimeInfo tinfo = new SingleTimeInfo();
         tinfo.setRangeTypeValue(TimeInfo.NUMBER);
         tinfo.setRangeSizeValue(1.0);
         ColumnRef ref = createRef(MEASURE);
         ref.setDataType(XSchema.INTEGER);
         tinfo.setDataRef(ref);
         assembly.setTimeInfo(tinfo);
      }

      private final Object[] yearData = { 2013, 2016 };
      private final Object[] yearPlaceholder = { 999, 999 };
      private final Object[] grownYearData = { 2013, 2017 };

      /**
       * A viewer of a sheet saved with Use Metadata on goes to a full-range bookmark after the
       * data domain of an auto-tick number slider grew so much that the nice tick step changed:
       * the bookmark was written against a real list, so it must restore the full range of the
       * current list, as for a sheet without Use Metadata.
       */
      @Test
      void fullBookmarkRestoresFullRangeAfterTickStepChange() throws Exception {
         useRuntimeBox();
         viewsheet.getViewsheetInfo().setMetadata(true);
         setUpNumberSlider();
         refresh(new Object[] { 0.0, 100.0 });
         int ticks = assembly.getSelectionList().getSelectionValueCount();
         assertSelected(0, ticks - 1);
         String bookmark = writeBookmark();
         List<String> oldTicks = getValues(assembly.getSelectionList());

         Object[] grown = { 0.0, 2500.0 };
         refresh(grown);
         assertFalse(getValues(assembly.getSelectionList()).containsAll(oldTicks),
            "test setup invariant: the tick step must change with the grown domain");

         gotoBookmark(bookmark);
         refresh(grown);

         int ticks2 = assembly.getSelectionList().getSelectionValueCount();
         assertEquals(ticks2 - 1, assembly.getTimeInfo().getLength());
         assertSelected(0, ticks2 - 1);
      }

      /**
       * A partial slider saved while Use Metadata is on (view list = the placeholder) must come
       * back partial when the sheet is opened again, in the viewer or in Composer. The year
       * slider of Examples/Sales Summary narrowed to 2013..2015.
       */
      @Test
      void partialYearSelectionSurvivesSaveWithMetadataOn() throws Exception {
         setUpYearSlider();
         String saved = saveWithMetadataOn(yearData, 0, 2, yearPlaceholder);
         assertTrue(saved.contains("full=\"false\" placeholder=\"true\""),
            "a partial selection over the placeholder list must be written as partial");

         reloadAndRefreshInViewer(saved, yearData);

         assertEquals(2, assembly.getTimeInfo().getLength());
         assertSelected(0, 2);
      }

      @Test
      void partialMonthSelectionSurvivesSaveWithMetadataOn() throws Exception {
         setUpDateSlider(TimeInfo.MONTH);
         String saved = saveWithMetadataOn(dateData, 10, 5, datePlaceholder);

         reloadAndRefreshInViewer(saved, dateData);

         assertEquals(5, assembly.getTimeInfo().getLength());
         assertSelected(10, 5);
      }

      /**
       * Review r3 blocker 2: a full slider saved with Use Metadata on is the full range, also
       * once the data has grown (2013..2016 saved, 2013..2017 now), as with Use Metadata off.
       */
      @Test
      void fullYearSliderSavedWithMetadataOnCoversGrownData() throws Exception {
         setUpYearSlider();
         refresh(yearData);
         String saved = saveWithMetadataOn(yearData, 0,
            assembly.getSelectionList().getSelectionValueCount() - 1, yearPlaceholder);
         assertTrue(saved.contains("full=\"true\" placeholder=\"true\""),
            "the full range must be written as full over the placeholder list");

         reloadAndRefreshInViewer(saved, grownYearData);

         assertFullRange();
         assertEquals("2017", getStateValues().get(getStateValues().size() - 1));
      }

      @Test
      void fullYearSliderSavedWithMetadataOnCoversGrownDataInComposer() throws Exception {
         setUpYearSlider();
         refresh(yearData);
         String saved = saveWithMetadataOn(yearData, 0,
            assembly.getSelectionList().getSelectionValueCount() - 1, yearPlaceholder);

         // reopened in Composer with Use Metadata on, then turned off
         reload(saved);
         setUpDesignMode();
         refresh(yearPlaceholder);
         viewsheet.getViewsheetInfo().setMetadata(false);
         refresh(grownYearData);

         assertFullRange();
         assertEquals("2017", getStateValues().get(getStateValues().size() - 1));
      }

      @Test
      void fullMonthSliderSavedWithMetadataOnCoversGrownData() throws Exception {
         setUpDateSlider(TimeInfo.MONTH);
         refresh(dateData);
         String saved = saveWithMetadataOn(dateData, 0,
            assembly.getSelectionList().getSelectionValueCount() - 1, datePlaceholder);

         reloadAndRefreshInViewer(saved, grownDateData);

         assertFullRange();
      }

      @Test
      void partialYearSelectionSavedWithMetadataOnSurvivesDataGrowth() throws Exception {
         setUpYearSlider();
         String saved = saveWithMetadataOn(yearData, 0, 2, yearPlaceholder);

         reloadAndRefreshInViewer(saved, grownYearData);

         assertEquals(List.of("2013", "2014", "2015"), getStateValues());
      }

      /**
       * Review r3 blocker 1: a full-range bookmark of a sheet without Use Metadata, applied
       * where the slider's list starts before the bookmark's min and is shorter than its length
       * (another user's data, or the data changed), selects the full range as before.
       */
      @Test
      void fullBookmarkOnEarlierShorterListStaysFull() throws Exception {
         useRuntimeBox();
         setUpYearSlider();
         refresh(yearData);
         assertSelected(0, assembly.getSelectionList().getSelectionValueCount() - 1);
         String bookmark = writeBookmark();
         assertTrue(bookmark.contains("full=\"true\"/>"),
            "a sheet without Use Metadata writes the state as before");

         Object[] earlier = { 2012, 2014 };
         refresh(earlier);
         gotoBookmark(bookmark);
         refresh(earlier);

         assertFullRange();
         assertEquals("2012", getStateValues().get(0));
      }

      /**
       * Review r3 minor-1: a date slider of a sheet without Use Metadata whose data had a single
       * value is saved with a list that reads back without ticks. Opened after the data grew,
       * it gives the same result as main: the first two months (length 1), not the full range.
       */
      @Test
      void singleValueDateSliderWithoutMetadataKeepsFirstTwoMonthsAsMain() throws Exception {
         useRuntimeBox();
         setUpDateSlider(TimeInfo.MONTH);
         refresh(datePlaceholder);
         String saved = writeSheet();

         reload(saved);
         useRuntimeBox();
         gotoBookmark(writeBookmark());
         refresh(dateData);

         assertEquals(1, assembly.getTimeInfo().getLength());
         assertEquals(List.of("{m '2022-03'}", "{m '2022-04'}"), getStateValues());
      }

      /**
       * A sheet saved with Use Metadata on before the fix has no placeholder marker: it is read
       * as before, i.e. a full="true" state restores the full range of the saved view list, and
       * no fullness is carried to the refresh.
       */
      @Test
      void sheetSavedWithMetadataOnBeforeTheFixParsesAsBefore() throws Exception {
         setUpYearSlider();
         String saved = saveWithMetadataOn(yearData, 0, 2, yearPlaceholder)
            .replace("full=\"false\" placeholder=\"true\"", "full=\"true\"");
         assertFalse(saved.contains("placeholder=\"true\""));

         reload(saved);

         assertNull(assembly.getPlaceholderFull());
         assertEquals(getValues(assembly.getSelectionList()), getStateValues());
         assertEquals(assembly.getSelectionList().getSelectionValueCount() - 1,
                      assembly.getTimeInfo().getLengthValue());

         // reopened in Composer with Use Metadata on: still nothing to carry
         setUpDesignMode();
         refresh(yearPlaceholder);
         assertNull(assembly.getPlaceholderFull());
      }

      /**
       * A state written over the placeholder (e.g. by an embedded viewsheet copy during the
       * Use Metadata reset) and parsed where the view list is real again restores the full
       * range of that real list, as before.
       */
      @Test
      void markedFullStateOnRealListRestoresFullRange() throws Exception {
         setUpYearSlider();
         refresh(yearData);
         viewsheet.getViewsheetInfo().setMetadata(true);
         refresh(yearPlaceholder);
         String bookmark = writeBookmark();
         assertTrue(bookmark.contains("full=\"true\" placeholder=\"true\""));

         viewsheet.getViewsheetInfo().setMetadata(false);
         refresh(grownYearData);
         gotoBookmark(bookmark);

         assertNull(assembly.getPlaceholderFull());
         assertEquals(getValues(assembly.getSelectionList()), getStateValues());
         refresh(grownYearData);
         assertFullRange();
      }

      /**
       * A bookmark written in the viewer (no placeholder marker), applied to a sheet saved with
       * Use Metadata on before its first refresh (e.g. a scheduled bookmark export), keeps the
       * bookmark's range instead of taking the placeholder list as its full range.
       */
      @Test
      void viewerBookmarkOnSheetSavedWithMetadataOnKeepsItsRange() throws Exception {
         setUpYearSlider();
         refresh(yearData);
         selectRange(1, 1);
         refresh(yearData);
         String partial = writeBookmark();
         assertFalse(partial.contains("placeholder"));
         String saved = saveWithMetadataOn(yearData, 0, 3, yearPlaceholder);

         reload(saved);
         useRuntimeBox();
         gotoBookmark(partial);
         refresh(grownYearData);

         assertEquals(List.of("2014", "2015"), getStateValues());
      }

      /**
       * Review r4 minor-1: a composite (Year + Month) slider goes through refreshDimensionsValue,
       * which has its own ALL branch and placeholder switch. A full range survives Use Metadata
       * on and off.
       */
      @Test
      void fullRangeCompositeSliderSurvivesMetadataToggle() throws Exception {
         setUpCompositeSlider();
         selectFullComposite(compositeData(2013, 2016));

         toggleMetadata(compositePlaceholder(), compositeData(2013, 2016));

         assertFullRange();
      }

      @Test
      void partialCompositeSelectionSurvivesSaveWithMetadataOn() throws Exception {
         setUpCompositeSlider();
         String saved = saveWithMetadataOn(compositeData(2013, 2016), 2, 5,
                                           compositePlaceholder());
         assertTrue(saved.contains("full=\"false\" placeholder=\"true\""));

         reloadAndRefreshInViewer(saved, compositeData(2013, 2016));

         assertEquals(5, assembly.getTimeInfo().getLength());
         assertSelected(2, 5);
      }

      @Test
      void fullCompositeSliderSavedWithMetadataOnCoversGrownData() throws Exception {
         setUpCompositeSlider();
         selectFullComposite(compositeData(2013, 2016));
         viewsheet.getViewsheetInfo().setMetadata(true);
         refresh(compositePlaceholder());
         String saved = writeSheet();
         assertTrue(saved.contains("full=\"true\" placeholder=\"true\""));

         reloadAndRefreshInViewer(saved, compositeData(2013, 2017));

         assertFullRange();
         assertEquals("2017::10", getStateValues().get(getStateValues().size() - 1));
      }

      /**
       * Without Use Metadata a full composite bookmark applied where the list starts earlier
       * and is shorter selects the full range, as on main.
       */
      @Test
      void fullCompositeBookmarkOnEarlierShorterListStaysFull() throws Exception {
         useRuntimeBox();
         setUpCompositeSlider();
         selectFullComposite(compositeData(2013, 2016));
         String bookmark = writeBookmark();
         assertTrue(bookmark.contains("full=\"true\"/>"));

         XTable earlier = compositeData(2012, 2014);
         refresh(earlier);
         gotoBookmark(bookmark);
         refresh(earlier);

         assertFullRange();
         assertEquals("2012::1", getStateValues().get(0));
      }

      private void setUpCompositeSlider() {
         CompositeTimeInfo tinfo = new CompositeTimeInfo();
         ColumnRef year = createRef(MEASURE);
         year.setDataType(XSchema.INTEGER);
         ColumnRef month = createRef(OTHER);
         month.setDataType(XSchema.INTEGER);
         tinfo.setDataRefs(new DataRef[] { year, month });
         assembly.setTimeInfo(tinfo);
         ((TimeSliderVSAssemblyInfo) assembly.getVSAssemblyInfo()).setComposite(true);
      }

      private void selectFullComposite(XTable data) throws Exception {
         refresh(data);
         selectRange(0, assembly.getSelectionList().getSelectionValueCount() - 1);
         refresh(data);
         assertFullRange();
      }

      // one row per quarter: year, month 1/4/7/10
      private XTable compositeData(int from, int to) {
         List<Object[]> rows = new ArrayList<>();
         rows.add(new Object[] { MEASURE, OTHER });

         for(int y = from; y <= to; y++) {
            for(int m = 1; m <= 10; m += 3) {
               rows.add(new Object[] { y, m });
            }
         }

         return createTable(rows.toArray(new Object[0][]));
      }

      private XTable compositePlaceholder() {
         return createTable(new Object[][] { { MEASURE, OTHER }, { 999, 999 } });
      }

      @Test
      void cloneKeepsCarriedFullness() throws Exception {
         setUpYearSlider();
         refresh(yearData);
         viewsheet.getViewsheetInfo().setMetadata(true);
         refresh(yearPlaceholder);

         assertEquals(Boolean.TRUE, assembly.getPlaceholderFull());
         assertEquals(Boolean.TRUE, assembly.clone().getPlaceholderFull());
      }

      private String saveWithMetadataOn(Object data, int pos, int length, Object placeholder)
         throws Exception
      {
         refresh(data);
         selectRange(pos, length);
         refresh(data);
         assertSelected(pos, length);
         viewsheet.getViewsheetInfo().setMetadata(true);
         refresh(placeholder);
         return writeSheet();
      }

      private String writeSheet() {
         java.io.StringWriter buf = new java.io.StringWriter();
         java.io.PrintWriter writer = new java.io.PrintWriter(buf);
         viewsheet.writeXML(writer);
         writer.flush();
         return buf.toString();
      }

      private void reload(String saved) throws Exception {
         Viewsheet reloaded = new Viewsheet();
         reloaded.parseXML(Tool.parseXML(new java.io.StringReader(saved)).getDocumentElement());
         viewsheet = reloaded;
         assembly = (TimeSliderVSAssembly) reloaded.getAssembly(SLIDER);
      }

      /**
       * Open a saved sheet in the viewer: a runtime box, the initial bookmark taken from the
       * opened sheet and applied again (RuntimeViewsheet), then the first refresh.
       */
      private void reloadAndRefreshInViewer(String saved, Object data) throws Exception {
         reload(saved);
         assertTrue(viewsheet.getViewsheetInfo().isMetadata());
         useRuntimeBox();
         gotoBookmark(writeBookmark());
         refresh(data);
      }

      private void assertFullRange() {
         int ticks = assembly.getSelectionList().getSelectionValueCount();
         assertEquals(ticks - 1, assembly.getTimeInfo().getLength());
         assertSelected(0, ticks - 1);
      }

      private List<String> getValues(SelectionList list) {
         List<String> values = new ArrayList<>();

         for(int i = 0; list != null && i < list.getSelectionValueCount(); i++) {
            values.add(list.getSelectionValue(i).getValue());
         }

         return values;
      }

      /**
       * A viewer opening a sheet saved with Use Metadata on: the runtime box runs on real data
       * and must give the same slider as for the same sheet saved with Use Metadata off, also
       * when the data no longer holds the selected position (the pos < 0 fallback).
       */
      @Test
      void runtimeBoxIgnoresMetadataFlag() throws Exception {
         List<Object> expected = runRuntimeScenario(false);
         setUp();
         setUpDesignMode();

         assertEquals(expected, runRuntimeScenario(true));
      }

      private List<Object> runRuntimeScenario(boolean metadata) throws Exception {
         useRuntimeBox();
         viewsheet.getViewsheetInfo().setMetadata(metadata);
         setUpDateSlider(TimeInfo.MONTH);
         refresh(dateData);
         int full = assembly.getTimeInfo().getLength();
         selectRange(10, 5);
         refresh(dateData);
         List<String> partial = getStateValues();
         // the selected months are gone from the data
         refresh(new Object[] { date(2024, 6, 1), date(2025, 12, 28), date(2025, 12, 28) });

         return List.of(full, partial, assembly.getTimeInfo().getLength(), getStateValues());
      }

      /**
       * What Composer Preview does to the slider before its first runtime refresh: a runtime
       * box, and the initial/home bookmark captured from the composer sheet and applied
       * again (RuntimeViewsheet constructor -> refresh() -> default bookmark).
       */
      private void openPreview() throws Exception {
         viewsheet = (Viewsheet) viewsheet.clone();
         assembly = (TimeSliderVSAssembly) viewsheet.getAssembly(SLIDER);
         useRuntimeBox();
         gotoBookmark(writeBookmark());
      }

      private String writeBookmark() {
         java.io.StringWriter buf = new java.io.StringWriter();
         java.io.PrintWriter writer = new java.io.PrintWriter(buf);
         assembly.writeState(writer, true);
         writer.flush();
         return buf.toString();
      }

      private void gotoBookmark(String state) throws Exception {
         org.w3c.dom.Document doc = Tool.parseXML(new java.io.StringReader(state));
         assembly.parseState(doc.getDocumentElement());
      }

      private void useRuntimeBox() {
         ViewsheetSandbox box = mock(ViewsheetSandbox.class);
         when(box.getID()).thenReturn("vs1");
         when(box.getViewsheet()).thenReturn(viewsheet);
         when(box.getMode()).thenReturn(RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE);
         when(box.isRuntime()).thenReturn(true);
         query = new TimeSliderVSAQuery(box, SLIDER);
      }

      private List<String> getStateValues() {
         SelectionList state = assembly.getStateSelectionList();
         List<String> values = new ArrayList<>();

         for(int i = 0; state != null && i < state.getSelectionValueCount(); i++) {
            values.add(state.getSelectionValue(i).getValue());
         }

         return values;
      }

      private void toggleMetadata(Object placeholder, Object data) throws Exception {
         viewsheet.getViewsheetInfo().setMetadata(true);
         refresh(placeholder);
         assertEquals(1, assembly.getSelectionList().getSelectionValueCount(),
            "test setup invariant: the metadata placeholder must produce a one-tick list");
         viewsheet.getViewsheetInfo().setMetadata(false);
         refresh(data);
      }

      private void setUpDateSlider(int unit) {
         SingleTimeInfo tinfo = new SingleTimeInfo();
         tinfo.setRangeTypeValue(unit);
         ColumnRef ref = createRef(MEASURE);
         ref.setDataType(XSchema.DATE);
         tinfo.setDataRef(ref);
         assembly.setTimeInfo(tinfo);
      }

      private void setUpNumberSlider() {
         SingleTimeInfo tinfo = new SingleTimeInfo();
         tinfo.setRangeTypeValue(TimeInfo.NUMBER);
         ColumnRef ref = createRef(MEASURE);
         ref.setDataType(XSchema.DOUBLE);
         tinfo.setDataRef(ref);
         assembly.setTimeInfo(tinfo);
      }

      // selects ticks pos..pos+length, as a user drag would leave it
      private void selectRange(int pos, int length) {
         SelectionList list = assembly.getSelectionList();
         SelectionList state = new SelectionList();

         for(int i = 0; i < list.getSelectionValueCount(); i++) {
            SelectionValue value = list.getSelectionValue(i);
            boolean selected = i >= pos && i <= pos + length;
            value.setState(selected ? SelectionValue.STATE_SELECTED : 0);

            if(selected) {
               state.addSelectionValue(value);
            }
         }

         assembly.setStateSelectionList(state);
         assembly.getTimeInfo().setLengthValue(length);
      }

      private void assertSelected(int pos, int length) {
         SelectionList list = assembly.getSelectionList();
         SelectionList state = assembly.getStateSelectionList();

         assertNotNull(state);
         assertEquals(length + 1, state.getSelectionValueCount());
         assertEquals(list.getSelectionValue(pos).getValue(),
                      state.getSelectionValue(0).getValue());
         assertEquals(list.getSelectionValue(pos + length).getValue(),
                      state.getSelectionValue(length).getValue());
      }

      private void refresh(Object data) throws Exception {
         Method m = TimeSliderVSAQuery.class.getDeclaredMethod(
            "refreshSelectionValue0", Object.class);
         m.setAccessible(true);

         try {
            m.invoke(query, (Object) data);
         }
         catch(InvocationTargetException e) {
            if(e.getCause() instanceof Exception) {
               throw (Exception) e.getCause();
            }

            throw e;
         }
      }

      private Date date(int year, int month, int day) {
         return new GregorianCalendar(year, month - 1, day).getTime();
      }

      // real data: min, max, omax
      private final Object[] dateData =
         { date(2022, 3, 13), date(2025, 12, 28), date(2025, 12, 28) };
      // metadata placeholder: a single date with omax present, so the list has one tick
      private final Object[] datePlaceholder =
         { date(2022, 3, 13), date(2022, 3, 13), date(2022, 3, 13) };
      private final Object[] grownDateData =
         { date(2022, 3, 13), date(2026, 6, 28), date(2026, 6, 28) };
      private final Object[] numberData = { 0.0, 1000.0 };
      private final Object[] numberPlaceholder = { 0.0, 0.0 };
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
