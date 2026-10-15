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
}
