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
package inetsoft.web.composer.vs.controller;

import inetsoft.report.TableDataPath;
import inetsoft.uql.viewsheet.internal.SelectionBaseVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The dark-foreground substitution in the format picker must reach the selection cell and nothing
 * else that shares its data-path type.
 *
 * Measure Text, Measure Bar and Measure Bar(-) all carry TableDataPath.DETAIL as their type,
 * distinguished only by a single path element — and for the two bars the DEFAULT-tier foreground is
 * the bar's own colour, not text. A type-only test would have shown the picker a light grey where
 * the canvas draws the categorical palette or the negative-bar red: the exact mismatch the
 * substitution exists to remove, reintroduced one control over.
 */
@Tag("core")
class FormatPainterServiceSelectionCellPathTest {
   @Test
   void thePlainDetailCellIsIncluded() {
      assertTrue(FormatPainterService.isPlainSelectionCell(
         new TableDataPath(-1, TableDataPath.DETAIL)),
                 "the selection cell is what the substitution is for");
   }

   @Test
   void everyMeasureSubPathIsExcluded() {
      for(int level = 0; level < 5; level++) {
         assertFalse(FormatPainterService.isPlainSelectionCell(
            SelectionBaseVSAssemblyInfo.getMeasureTextPath(level)), "measure text, level " + level);
         assertFalse(FormatPainterService.isPlainSelectionCell(
            SelectionBaseVSAssemblyInfo.getMeasureBarPath(level)), "measure bar, level " + level);
         // the one isMeasureTextBar does not cover, which is why this predicate tests the path
         // shape rather than naming the three
         assertFalse(FormatPainterService.isPlainSelectionCell(
            SelectionBaseVSAssemblyInfo.getMeasureNBarPath(level)),
                     "negative measure bar, level " + level);
      }
   }

   @Test
   void otherPathTypesAndNullAreExcluded() {
      assertFalse(FormatPainterService.isPlainSelectionCell(null), "null");
      assertFalse(FormatPainterService.isPlainSelectionCell(
         new TableDataPath(-1, TableDataPath.TITLE)), "title");
      assertFalse(FormatPainterService.isPlainSelectionCell(
         new TableDataPath(-1, TableDataPath.OBJECT)), "object");
   }
}
