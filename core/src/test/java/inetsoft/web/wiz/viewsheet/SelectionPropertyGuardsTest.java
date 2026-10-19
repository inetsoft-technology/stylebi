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
package inetsoft.web.wiz.viewsheet;

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.TimeInfo;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import inetsoft.web.composer.model.vs.RangeSliderPropertyDialogModel;
import inetsoft.web.composer.model.vs.SelectionTreePropertyDialogModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * bug-78566: single-select clear identity diff (P1), singleSelectionLevels validation (P2) and
 * range slider rangeSize/maxRangeSize/rangeType applicability (P4).
 */
@Tag("core")
class SelectionPropertyGuardsTest {
   private static final String LEVELS = "selectionGeneralPaneModel.singleSelectionLevels";
   private static final String SIZE = "rangeSliderAdvancedPaneModel.rangeSliderSizePaneModel.";

   // P1: clear_selection compares identity, not counts

   @Test
   void replacedSingleSelectValueCountsAsClearedAndReselected() {
      List<List<String>> before = List.of(List.of("4"));
      List<List<String>> after = List.of(List.of("1"));

      assertEquals(1, SelectionRuntimeService.clearedCount(before, after));
      assertEquals(after, SelectionRuntimeService.reselectedAfterClear(before, after));
   }

   @Test
   void keptValueIsNeitherClearedNorReselected() {
      List<List<String>> same = List.of(List.of("A", "B"));

      assertEquals(0, SelectionRuntimeService.clearedCount(same, same));
      assertTrue(SelectionRuntimeService.reselectedAfterClear(same, same).isEmpty());
   }

   @Test
   void fullyClearedSelectionReportsAllCleared() {
      List<List<String>> before = List.of(List.of("A"), List.of("B"));

      assertEquals(2, SelectionRuntimeService.clearedCount(before, List.of()));
      assertTrue(SelectionRuntimeService.reselectedAfterClear(before, List.of()).isEmpty());
   }

   @Test
   void searchRetainedValuesAreNotReselected() {
      List<List<String>> before = List.of(List.of("A"), List.of("B"));
      List<List<String>> after = List.of(List.of("B"));

      assertEquals(1, SelectionRuntimeService.clearedCount(before, after));
      assertTrue(SelectionRuntimeService.reselectedAfterClear(before, after).isEmpty());
   }

   // P2: singleSelectionLevels

   private static SelectionTreePropertyDialogModel tree(boolean single, int mode,
                                                        List<?> levels, String... columns)
   {
      SelectionTreePropertyDialogModel model = new SelectionTreePropertyDialogModel();
      OutputColumnRefModel[] refs = Arrays.stream(columns).map(name -> {
         OutputColumnRefModel ref = new OutputColumnRefModel();
         ref.setAttribute(name);
         return ref;
      }).toArray(OutputColumnRefModel[]::new);
      model.getSelectionTreePaneModel().setSelectedColumns(refs);
      model.getSelectionTreePaneModel().setMode(mode);
      model.getSelectionGeneralPaneModel().setSingleSelection(single);
      @SuppressWarnings("unchecked")
      List<String> typed = (List<String>) levels;
      model.getSelectionGeneralPaneModel().setSingleSelectionLevels(typed);
      return model;
   }

   private static String levelsRefusal(Object model) {
      return assertThrows(IllegalArgumentException.class,
         () -> AssemblyPropertyService.requireKnownSingleSelectionLevels(model)).getMessage();
   }

   @Test
   void unknownLevelIsRefusedNamingItAndTheValidLevels() {
      String message = levelsRefusal(
         tree(true, SelectionTreeVSAssemblyInfo.COLUMN, List.of("NoSuchLevel"), "Region", "State"));

      assertTrue(message.contains("singleSelectionLevels"), message);
      assertTrue(message.contains("NoSuchLevel"), message);
      assertTrue(message.contains("Region") && message.contains("State"), message);
   }

   @Test
   void levelNamesAreCaseSensitive() {
      assertTrue(levelsRefusal(
         tree(true, SelectionTreeVSAssemblyInfo.COLUMN, List.of("state"), "Region", "State"))
         .contains("'state'"));
   }

   @Test
   void partiallyUnknownListIsRefused() {
      assertTrue(levelsRefusal(
         tree(true, SelectionTreeVSAssemblyInfo.COLUMN, List.of("State", "Bogus"), "Region",
              "State")).contains("Bogus"));
   }

   @Test
   void nonStringElementIsRefusedInsteadOfThrowingClassCast() {
      String message = levelsRefusal(
         tree(true, SelectionTreeVSAssemblyInfo.COLUMN, Arrays.asList((Object) 1), "Region",
              "State"));

      assertTrue(message.contains("singleSelectionLevels") && message.contains("1"), message);
      assertTrue(message.contains("Region"), message);
   }

   @Test
   void emptyListAndIdModeAndNonSingleSelectAreRefused() {
      assertTrue(levelsRefusal(
         tree(true, SelectionTreeVSAssemblyInfo.COLUMN, List.of(), "Region")).contains("empty"));
      assertTrue(levelsRefusal(
         tree(true, SelectionTreeVSAssemblyInfo.ID, List.of("Region"), "Region"))
         .contains("ID-mode"));
      assertTrue(levelsRefusal(
         tree(false, SelectionTreeVSAssemblyInfo.COLUMN, List.of("Region"), "Region"))
         .contains("not single-select"));
   }

   @Test
   void emptyOrNullLevelsAreAcceptedWhenSingleSelectIsOff() {
      for(List<String> cleared : Arrays.asList(List.<String>of(), null)) {
         SelectionTreePropertyDialogModel model =
            tree(false, SelectionTreeVSAssemblyInfo.COLUMN, cleared, "Region");

         assertSame(model, AssemblyPropertyService.requireKnownSingleSelectionLevels(model));
      }
   }

   @Test
   void nullLevelsAreRefusedWhileSingleSelectIsOn() {
      assertTrue(levelsRefusal(
         tree(true, SelectionTreeVSAssemblyInfo.COLUMN, null, "Region")).contains("empty"));
   }

   @Test
   void validLevelsPassAndDuplicatesAreRemoved() {
      SelectionTreePropertyDialogModel model = tree(
         true, SelectionTreeVSAssemblyInfo.COLUMN, List.of("State", "State", "Region"), "Region",
         "State");

      Object result = AssemblyPropertyService.requireKnownSingleSelectionLevels(model);

      assertEquals(List.of("State", "Region"), PropertyPath.get(result, LEVELS));
   }

   // P4: range slider applicability

   private static RangeSliderPropertyDialogModel slider(boolean composite, int rangeType,
                                                        boolean logScale)
   {
      RangeSliderPropertyDialogModel model = new RangeSliderPropertyDialogModel();
      model.getRangeSliderDataPaneModel().setComposite(composite);
      model.getRangeSliderAdvancedPaneModel().getRangeSliderSizePaneModel()
         .setRangeType(rangeType);
      model.getRangeSliderAdvancedPaneModel().getRangeSliderSizePaneModel().setLogScale(logScale);
      return model;
   }

   private static String sliderRefusal(Object model, String... leaves) {
      List<String> paths = Arrays.stream(leaves).map(leaf -> SIZE + leaf).toList();
      return assertThrows(IllegalArgumentException.class,
         () -> AssemblyPropertyService.requireApplicableRangeSize(model, paths)).getMessage();
   }

   private static void sliderAccepts(Object model, String... leaves) {
      List<String> paths = Arrays.stream(leaves).map(leaf -> SIZE + leaf).toList();
      assertDoesNotThrow(() -> AssemblyPropertyService.requireApplicableRangeSize(model, paths));
   }

   @Test
   void rangeSizeOnNonNumberSliderIsRefused() {
      String message = sliderRefusal(slider(false, TimeInfo.DAY, false), "rangeSize");

      assertTrue(message.startsWith("rangeSize") && message.contains("NUMBER"), message);
   }

   @Test
   void rangeSizeOrMaxRangeSizeOnLogScaleNumberSliderIsRefused() {
      assertTrue(sliderRefusal(slider(false, TimeInfo.NUMBER, true), "rangeSize")
         .contains("logScale:false"));
      assertTrue(sliderRefusal(slider(false, TimeInfo.NUMBER, true), "maxRangeSize")
         .startsWith("maxRangeSize"));
   }

   @Test
   void compositeSliderRefusesRangeTypeAndSizes() {
      for(String leaf : List.of("rangeType", "rangeSize", "maxRangeSize")) {
         // composite is the slider's source, which only set_selection_source changes (#78480)
         String message = sliderRefusal(slider(true, TimeInfo.NUMBER, false), leaf);
         assertTrue(message.contains("set_selection_source") &&
                    !message.contains("rangeSliderDataPaneModel.composite"), message);
      }
   }

   @Test
   void applicableWritesStillPass() {
      sliderAccepts(slider(false, TimeInfo.NUMBER, false), "rangeSize");
      sliderAccepts(slider(false, TimeInfo.NUMBER, false), "rangeSize", "maxRangeSize");
      // {rangeType:"number", rangeSize:7} is judged on the post-patch model
      sliderAccepts(slider(false, TimeInfo.NUMBER, false), "rangeType", "rangeSize");
      sliderAccepts(slider(false, TimeInfo.DAY, false), "maxRangeSize");
      sliderAccepts(slider(false, TimeInfo.DAY, false), "rangeType");
      sliderAccepts(slider(false, TimeInfo.NUMBER, true), "rangeType");
   }

   @Test
   void writeThatTouchesNoRangeFieldIsNeverRefused() {
      sliderAccepts(slider(true, TimeInfo.DAY, true), "logScale");
      sliderAccepts(slider(false, TimeInfo.DAY, true), "length");
   }

   // bug-78717 P2: rangeType must fit the bound column's type

   private static RangeSliderPropertyDialogModel sliderOn(String dataType, int refType,
                                                          int rangeType)
   {
      RangeSliderPropertyDialogModel model = slider(false, rangeType, false);
      OutputColumnRefModel column = new OutputColumnRefModel();
      column.setAttribute("OrderDate");
      column.setDataType(dataType);
      column.setRefType(refType);
      model.getRangeSliderDataPaneModel().setSelectedColumns(new OutputColumnRefModel[] { column });
      return model;
   }

   @Test
   void numberRangeTypeOnADateColumnIsRefusedWithTheDateRangeTypes() {
      String message = sliderRefusal(sliderOn("date", 0, TimeInfo.NUMBER), "rangeType");

      assertTrue(message.startsWith("rangeType") && message.contains("'number'"), message);
      assertTrue(message.contains("year, month, day") && !message.contains("hour"), message);
   }

   @Test
   void dateRangeTypeOnANumericColumnIsRefusedWithNumber() {
      String message = sliderRefusal(sliderOn("integer", 0, TimeInfo.MONTH), "rangeType");

      assertTrue(message.contains("'month'") && message.endsWith("number."), message);
   }

   @Test
   void legalRangeTypesFollowTheComposerSizePaneForEveryColumnType() {
      sliderAccepts(sliderOn("date", 0, TimeInfo.MONTH), "rangeType");
      sliderAccepts(sliderOn("double", 0, TimeInfo.NUMBER), "rangeType");
      sliderAccepts(sliderOn("timeInstant", 0, TimeInfo.MINUTE), "rangeType");
      sliderAccepts(sliderOn("time", 0, TimeInfo.HOUR_OF_DAY), "rangeType");
      sliderAccepts(sliderOn("string", DataRef.CUBE_DIMENSION, TimeInfo.MEMBER), "rangeType");

      // HOUR/MINUTE need a time-of-day component the plain date column does not have
      assertTrue(sliderRefusal(sliderOn("date", 0, TimeInfo.HOUR), "rangeType")
         .contains("'hour'"));
      assertTrue(sliderRefusal(sliderOn("time", 0, TimeInfo.YEAR), "rangeType")
         .endsWith("hour_of_day, minute_of_day."));
      assertTrue(sliderRefusal(sliderOn("timeInstant", 0, TimeInfo.HOUR_OF_DAY), "rangeType")
         .endsWith("year, month, day, hour, minute."));
      assertTrue(sliderRefusal(sliderOn("string", DataRef.CUBE_DIMENSION, TimeInfo.NUMBER),
                               "rangeType").endsWith("member."));
   }

   @Test
   void rangeTypeIsOnlyCheckedWhenThePatchWritesIt() {
      // an already-stored mismatch never blocks an unrelated write
      sliderAccepts(sliderOn("date", 0, TimeInfo.NUMBER), "maxRangeSize");
      sliderAccepts(sliderOn("date", 0, TimeInfo.NUMBER), "logScale");
   }

   @Test
   void rangeSizeOnADateSliderNeverRecommendsRangeTypeNumber() {
      String message = sliderRefusal(sliderOn("date", 0, TimeInfo.MONTH), "rangeSize");

      assertTrue(message.contains("maxRangeSize") && !message.contains("rangeType:\"number\""),
                 message);
   }

   @Test
   void rangeSizeOnANumericSliderStillSuggestsRangeTypeNumber() {
      assertTrue(sliderRefusal(sliderOn("integer", 0, TimeInfo.DAY), "rangeSize")
         .contains("rangeType:\"number\""));
   }

   // bug-78717 P3: selectChildren only works on a multi-select ID-mode tree

   private static SelectionTreePropertyDialogModel childrenTree(boolean selectChildren, int mode,
                                                                boolean single)
   {
      SelectionTreePropertyDialogModel model = tree(single, mode, List.of(), "Region");
      model.getSelectionTreePaneModel().setSelectChildren(selectChildren);
      return model;
   }

   @Test
   void selectChildrenOnAColumnModeTreeIsRefused() {
      String message = assertThrows(IllegalArgumentException.class,
         () -> AssemblyPropertyService.requireApplicableSelectChildren(
            childrenTree(true, SelectionTreeVSAssemblyInfo.COLUMN, false))).getMessage();

      assertTrue(message.startsWith("selectChildren") && message.contains("mode:\"id\""),
                 message);
   }

   @Test
   void selectChildrenOnASingleSelectIdTreeIsRefused() {
      String message = assertThrows(IllegalArgumentException.class,
         () -> AssemblyPropertyService.requireApplicableSelectChildren(
            childrenTree(true, SelectionTreeVSAssemblyInfo.ID, true))).getMessage();

      assertTrue(message.startsWith("selectChildren") && message.contains("singleSelection"),
                 message);
   }

   @Test
   void selectChildrenIsAcceptedWhereItTakesEffect() {
      assertDoesNotThrow(() -> AssemblyPropertyService.requireApplicableSelectChildren(
         childrenTree(true, SelectionTreeVSAssemblyInfo.ID, false)));
      assertDoesNotThrow(() -> AssemblyPropertyService.requireApplicableSelectChildren(
         childrenTree(false, SelectionTreeVSAssemblyInfo.COLUMN, true)));
      assertDoesNotThrow(() -> AssemblyPropertyService.requireApplicableSelectChildren(
         childrenTree(false, SelectionTreeVSAssemblyInfo.ID, true)));
   }

   // bug-78717 P1: set_selection names the value a single-select assembly re-selected

   private static List<List<String>> paths(String... values) {
      return Arrays.stream(values).map(List::of).toList();
   }

   @Test
   void emptyValuesOnSingleSelectReportsTheReselectedFirstValue() {
      assertEquals(paths("CA"), SelectionRuntimeService.reselectedBySetSelection(
         paths("TX"), paths("CA"), List.of(), List.of(), true, false));
   }

   @Test
   void emptyValuesWhereTheFirstValueWasAlreadySelectedStillReportsIt() {
      assertEquals(paths("CA"), SelectionRuntimeService.reselectedBySetSelection(
         paths("CA"), paths("CA"), List.of(), List.of(), true, false));
   }

   @Test
   void deselectingTheOnlyValueReportsTheNewFirstValue() {
      assertEquals(paths("CA"), SelectionRuntimeService.reselectedBySetSelection(
         paths("TX"), paths("CA"), List.of(), paths("TX"), false, false));
   }

   @Test
   void deselectingTheFirstValueThatIsForciblyReselectedIsReported() {
      assertEquals(paths("CA"), SelectionRuntimeService.reselectedBySetSelection(
         paths("CA"), paths("CA"), List.of(), paths("CA"), false, false));
   }

   @Test
   void requestedAndUntouchedValuesAreNotReportedAsReselected() {
      assertTrue(SelectionRuntimeService.reselectedBySetSelection(
         paths("TX"), paths("CA"), paths("CA"), List.of(), true, false).isEmpty());
      assertTrue(SelectionRuntimeService.reselectedBySetSelection(
         paths("TX"), paths("TX"), List.of(), paths("NY"), false, false).isEmpty());
   }

   @Test
   void idModeComparesByNodeId() {
      assertEquals(paths("A1"), SelectionRuntimeService.reselectedBySetSelection(
         paths("B"), paths("A1"), List.of(List.of("B")), List.of(), true, true));
      assertTrue(SelectionRuntimeService.reselectedBySetSelection(
         paths("B"), paths("B"), List.of(List.of("A", "B")), List.of(), true, true).isEmpty());
   }

   @Test
   void subtreeClearReselectInsideTheSameSubtreeIsReportedByValue() {
      assertEquals(List.of(List.of("West", "CA")),
         SelectionRuntimeService.reselectedAfterSubtreeClear(
            List.of(List.of("West", "WA")), List.of(List.of("West", "CA")),
            List.of(List.of("West", "WA")), List.of(List.of("West", "CA")), false));
      // by identity: WA is gone even though the count under the subtree did not change
      assertEquals(1, SelectionRuntimeService.clearedCount(
         List.of(List.of("West", "WA")), List.of(List.of("West", "CA"))));
   }

   @Test
   void subtreeClearReselectOutsideTheSubtreeIsReportedToo() {
      assertEquals(List.of(List.of("East", "NY")),
         SelectionRuntimeService.reselectedAfterSubtreeClear(
            List.of(List.of("West", "WA")), List.of(List.of("East", "NY")),
            List.of(List.of("West", "WA")), List.of(), false));
   }

   @Test
   void subtreeClearOfAnUnselectedBranchReportsNoReselect() {
      assertTrue(SelectionRuntimeService.reselectedAfterSubtreeClear(
         List.of(List.of("East", "NY")), List.of(List.of("East", "NY")),
         List.of(), List.of(), false).isEmpty());
   }
}
