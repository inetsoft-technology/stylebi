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
package inetsoft.graph.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.*;

class ContourCellTest {

   private static final double DELTA = 1e-9;

   private ContourCell cell(int caseIndex) {
      ContourCell c = new ContourCell();
      c.setCaseIndex(caseIndex);
      return c;
   }

   private ContourCell cell(int caseIndex, boolean flipped) {
      ContourCell c = cell(caseIndex);
      c.setFlipped(flipped);
      return c;
   }

   // ---- getCrossingPoint ----

   @Test
   void getCrossingPointBottomReturnsXOnBottom() {
      ContourCell c = new ContourCell();
      c.setBottomCrossing(0.3);
      Point2D pt = c.getCrossingPoint(ContourCell.Side.BOTTOM);
      assertNotNull(pt);
      assertEquals(0.3, pt.getX(), DELTA);
      assertEquals(0.0, pt.getY(), DELTA);
   }

   @Test
   void getCrossingPointLeftReturnsYOnLeft() {
      ContourCell c = new ContourCell();
      c.setLeftCrossing(0.6);
      Point2D pt = c.getCrossingPoint(ContourCell.Side.LEFT);
      assertNotNull(pt);
      assertEquals(0.0, pt.getX(), DELTA);
      assertEquals(0.6, pt.getY(), DELTA);
   }

   @Test
   void getCrossingPointRightReturnsYOnRight() {
      ContourCell c = new ContourCell();
      c.setRightCrossing(0.75);
      Point2D pt = c.getCrossingPoint(ContourCell.Side.RIGHT);
      assertNotNull(pt);
      assertEquals(1.0, pt.getX(), DELTA);
      assertEquals(0.75, pt.getY(), DELTA);
   }

   @Test
   void getCrossingPointTopReturnsXOnTop() {
      ContourCell c = new ContourCell();
      c.setTopCrossing(0.5);
      Point2D pt = c.getCrossingPoint(ContourCell.Side.TOP);
      assertNotNull(pt);
      assertEquals(0.5, pt.getX(), DELTA);
      assertEquals(1.0, pt.getY(), DELTA);
   }

   @Test
   void getCrossingPointNoneReturnsNull() {
      ContourCell c = new ContourCell();
      assertNull(c.getCrossingPoint(ContourCell.Side.NONE));
   }

   @Test
   void getCrossingPointReflectsUpdatedValues() {
      ContourCell c = new ContourCell();
      c.setLeftCrossing(0.2);
      assertEquals(0.2, c.getCrossingPoint(ContourCell.Side.LEFT).getY(), DELTA);
      c.setLeftCrossing(0.8);
      assertEquals(0.8, c.getCrossingPoint(ContourCell.Side.LEFT).getY(), DELTA);
   }

   // ---- getCaseIndex / setCaseIndex / setFlipped / isFlipped ----

   @Test
   void caseIndexGetterSetter() {
      ContourCell c = new ContourCell();
      c.setCaseIndex(5);
      assertEquals(5, c.getCaseIndex());
   }

   @Test
   void flippedDefaultIsFalse() {
      ContourCell c = new ContourCell();
      assertFalse(c.isFlipped());
   }

   @Test
   void flippedGetterSetter() {
      ContourCell c = new ContourCell();
      c.setFlipped(true);
      assertTrue(c.isFlipped());
      c.setFlipped(false);
      assertFalse(c.isFlipped());
   }

   // ---- firstSide — unambiguous cases ----

   @ParameterizedTest
   @CsvSource({
      "1,  LEFT",
      "3,  LEFT",
      "7,  LEFT",
      "2,  BOTTOM",
      "6,  BOTTOM",
      "14, BOTTOM",
      "4,  RIGHT",
      "12, RIGHT",
      "13, RIGHT",
      "8,  TOP",
      "9,  TOP",
      "11, TOP"
   })
   void firstSideForUnambiguousCases(int caseIndex, String expectedSide) {
      ContourCell c = cell(caseIndex);
      ContourCell.Side result = c.firstSide(ContourCell.Side.LEFT);
      assertEquals(ContourCell.Side.valueOf(expectedSide), result);
   }

   @Test
   void firstSideCase5WithLeftReturnsRight() {
      ContourCell c = cell(5);
      assertEquals(ContourCell.Side.RIGHT, c.firstSide(ContourCell.Side.LEFT));
   }

   @Test
   void firstSideCase5WithRightReturnsLeft() {
      ContourCell c = cell(5);
      assertEquals(ContourCell.Side.LEFT, c.firstSide(ContourCell.Side.RIGHT));
   }

   @Test
   void firstSideCase10WithBottomReturnsTop() {
      ContourCell c = cell(10);
      assertEquals(ContourCell.Side.TOP, c.firstSide(ContourCell.Side.BOTTOM));
   }

   @Test
   void firstSideCase10WithTopReturnsBottom() {
      ContourCell c = cell(10);
      assertEquals(ContourCell.Side.BOTTOM, c.firstSide(ContourCell.Side.TOP));
   }

   @Test
   void firstSideCasesZeroAndFifteenReturnNull() {
      // Cases 0 and 15 fall through to default: returns null
      assertNull(cell(0).firstSide(ContourCell.Side.LEFT));
      assertNull(cell(15).firstSide(ContourCell.Side.LEFT));
   }

   // ---- nextSide — unambiguous cases ----

   @ParameterizedTest
   @CsvSource({
      "8,  LEFT",
      "12, LEFT",
      "14, LEFT",
      "1,  BOTTOM",
      "9,  BOTTOM",
      "13, BOTTOM",
      "2,  RIGHT",
      "3,  RIGHT",
      "11, RIGHT",
      "4,  TOP",
      "6,  TOP",
      "7,  TOP"
   })
   void nextSideForUnambiguousCases(int caseIndex, String expectedSide) {
      ContourCell c = cell(caseIndex);
      ContourCell.Side result = c.nextSide(ContourCell.Side.LEFT);
      assertEquals(ContourCell.Side.valueOf(expectedSide), result);
   }

   // ---- nextSide — case 5 (saddle point, flipped flag) ----

   @Test
   void nextSideCase5LeftNotFlippedReturnsTop() {
      ContourCell c = cell(5, false);
      assertEquals(ContourCell.Side.TOP, c.nextSide(ContourCell.Side.LEFT));
   }

   @Test
   void nextSideCase5LeftFlippedReturnsBottom() {
      ContourCell c = cell(5, true);
      assertEquals(ContourCell.Side.BOTTOM, c.nextSide(ContourCell.Side.LEFT));
   }

   @Test
   void nextSideCase5RightNotFlippedReturnsBottom() {
      ContourCell c = cell(5, false);
      assertEquals(ContourCell.Side.BOTTOM, c.nextSide(ContourCell.Side.RIGHT));
   }

   @Test
   void nextSideCase5RightFlippedReturnsTop() {
      ContourCell c = cell(5, true);
      assertEquals(ContourCell.Side.TOP, c.nextSide(ContourCell.Side.RIGHT));
   }

   // ---- nextSide — case 10 (saddle point, flipped flag) ----

   @Test
   void nextSideCase10BottomNotFlippedReturnsLeft() {
      ContourCell c = cell(10, false);
      assertEquals(ContourCell.Side.LEFT, c.nextSide(ContourCell.Side.BOTTOM));
   }

   @Test
   void nextSideCase10BottomFlippedReturnsRight() {
      ContourCell c = cell(10, true);
      assertEquals(ContourCell.Side.RIGHT, c.nextSide(ContourCell.Side.BOTTOM));
   }

   @Test
   void nextSideCase10TopNotFlippedReturnsRight() {
      ContourCell c = cell(10, false);
      assertEquals(ContourCell.Side.RIGHT, c.nextSide(ContourCell.Side.TOP));
   }

   @Test
   void nextSideCase10TopFlippedReturnsLeft() {
      ContourCell c = cell(10, true);
      assertEquals(ContourCell.Side.LEFT, c.nextSide(ContourCell.Side.TOP));
   }

   @Test
   void nextSideDefaultCaseReturnsNone() {
      // Cases not explicitly handled return NONE from default
      ContourCell c = cell(0);
      assertEquals(ContourCell.Side.NONE, c.nextSide(ContourCell.Side.LEFT));
   }

   // ---- clearIndex ----

   @Test
   void clearIndexCase0LeavesIndexUnchanged() {
      ContourCell c = cell(0);
      c.clearIndex();
      assertEquals(0, c.getCaseIndex());
   }

   @Test
   void clearIndexCase5LeavesIndexUnchanged() {
      ContourCell c = cell(5);
      c.clearIndex();
      assertEquals(5, c.getCaseIndex());
   }

   @Test
   void clearIndexCase10LeavesIndexUnchanged() {
      ContourCell c = cell(10);
      c.clearIndex();
      assertEquals(10, c.getCaseIndex());
   }

   @Test
   void clearIndexCase15LeavesIndexUnchanged() {
      ContourCell c = cell(15);
      c.clearIndex();
      assertEquals(15, c.getCaseIndex());
   }

   @ParameterizedTest
   @ValueSource(ints = {1, 2, 3, 4, 6, 7, 8, 9, 11, 12, 13, 14})
   void clearIndexOtherCasesSetsTo15(int caseIndex) {
      ContourCell c = cell(caseIndex);
      c.clearIndex();
      assertEquals(15, c.getCaseIndex());
   }

   // ---- Verify all 16 marching squares cases have expected firstSide ----

   @Test
   void allSixteenCasesReturnExpectedFirstSides() {
      // Cases 0, 15 → null (default branch)
      assertNull(cell(0).firstSide(ContourCell.Side.LEFT));
      assertNull(cell(15).firstSide(ContourCell.Side.LEFT));

      // Deterministic firstSide for each case (prev argument irrelevant for non-ambiguous)
      assertEquals(ContourCell.Side.LEFT,   cell(1).firstSide(ContourCell.Side.TOP));
      assertEquals(ContourCell.Side.BOTTOM, cell(2).firstSide(ContourCell.Side.TOP));
      assertEquals(ContourCell.Side.LEFT,   cell(3).firstSide(ContourCell.Side.TOP));
      assertEquals(ContourCell.Side.RIGHT,  cell(4).firstSide(ContourCell.Side.TOP));
      // case 5 is prev-dependent; tested separately
      assertEquals(ContourCell.Side.BOTTOM, cell(6).firstSide(ContourCell.Side.TOP));
      assertEquals(ContourCell.Side.LEFT,   cell(7).firstSide(ContourCell.Side.TOP));
      assertEquals(ContourCell.Side.TOP,    cell(8).firstSide(ContourCell.Side.TOP));
      assertEquals(ContourCell.Side.TOP,    cell(9).firstSide(ContourCell.Side.TOP));
      // case 10 is prev-dependent; tested separately
      assertEquals(ContourCell.Side.TOP,    cell(11).firstSide(ContourCell.Side.TOP));
      assertEquals(ContourCell.Side.RIGHT,  cell(12).firstSide(ContourCell.Side.TOP));
      assertEquals(ContourCell.Side.RIGHT,  cell(13).firstSide(ContourCell.Side.TOP));
      assertEquals(ContourCell.Side.BOTTOM, cell(14).firstSide(ContourCell.Side.TOP));
   }
}
