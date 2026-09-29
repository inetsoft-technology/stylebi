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
package inetsoft.report.io.viewsheet;

import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the "Expand Components" layout pass
 * ({@link AbstractVSExporter#insertRowCol}, which drives {@code getMore()},
 * {@code addMore()} and {@link AbstractVSExporter#moveAssemblies}). The pass is
 * format-agnostic and runs once in {@code core}, so these cover every exporter.
 *
 * <p><b>The invariant</b>: expanding an assembly inserts rows (or columns) at
 * exactly one position &mdash; the assembly's own bottom (or right) edge &mdash;
 * and every assembly at or beyond that position is translated by the <em>same</em>
 * amount. An expansion that an earlier insert already paid for is netted out of
 * that amount, and an insert only counts as having paid for it when it was made
 * at a position inside {@code (top, bottom]} of the assembly being expanded:
 * only such an insert actually opened room directly beneath it.</p>
 *
 * <p><b>Bug #77208</b> broke the "same amount" half. {@code moveAssemblies()}
 * reassigned its own loop-carried shift variable to the un-netted {@code omore}
 * as soon as the iteration reached a {@code SelectionList}/{@code SelectionTree}
 * assembly, so every later element of {@code Viewsheet.getAssemblies(false)}
 * &mdash; document/creation order, which carries no geometric meaning &mdash;
 * was shifted by a different amount than the earlier ones. Charts ended up
 * separated from the background rectangles they sit on.</p>
 *
 * <p><b>Bug #71211</b> is the other half, and is why that block could not simply
 * be deleted: {@code getMore()} netted out <em>every</em> previously recorded
 * insert below the expander's top, including inserts made below the expander's
 * own bottom. Those never moved the assemblies sitting between the expander's
 * bottom and that position, so netting them under-shifted those assemblies and
 * expanded selection lists overlapped whatever was beneath them.</p>
 *
 * <p>Both now follow from the one invariant: {@code getMore()} nets only inserts
 * in {@code (top, bottom]}, and {@code moveAssemblies()} applies a single shift
 * to everything past the insert point.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AbstractVSExporterMoveAssembliesTest {
   /**
    * Bug #77208, the shared-band topology: several selection lists side by side
    * in one row, so they all insert at the same position. Only the largest
    * expansion is needed there &mdash; the smaller ones are netted out by
    * {@code getMore()} &mdash; and every assembly below the row must come down by
    * that one amount.
    *
    * <p>The assemblies below the row are deliberately split around the first
    * selection list in {@code getAssemblies(false)} order, because that array
    * index is the axis the bug varied along: {@code TextEarly} is added before
    * the lists and {@code TextLate}/{@code ListBelow} after them. Before the fix
    * {@code TextEarly} moved by the netted 100 while {@code TextLate} moved by
    * the un-netted 300.</p>
    */
   @Test
   void everythingBelowASharedExpansionBandIsShiftedByTheSameAmount() {
      Viewsheet vs = new Viewsheet();
      TextVSAssembly textEarly = text(vs, "TextEarly", 0, BELOW_Y);
      SelectionListVSAssembly listA = selectionList(vs, "ListA", 0, BAND_Y, BAND_H);
      SelectionListVSAssembly listB = selectionList(vs, "ListB", 200, BAND_Y, BAND_H);
      TextVSAssembly textLate = text(vs, "TextLate", 200, BELOW_Y);
      SelectionListVSAssembly listBelow = selectionList(vs, "ListBelow", 400, BELOW_Y, 50);

      TestExporter exporter = new TestExporter();
      exporter.expandRows(listA, 200);
      exporter.expandRows(listB, 300);

      // both lists insert at the same position (BAND_Y + BAND_H), so the row only
      // has to grow by the larger of the two expansions
      int expected = BELOW_Y + 300;

      assertEquals(expected, textEarly.getPixelOffset().y,
                   "an assembly below the band that precedes every selection list "
                   + "in getAssemblies(false) order");
      assertEquals(expected, textLate.getPixelOffset().y,
                   "an assembly below the band that follows the selection lists in "
                   + "getAssemblies(false) order must get the identical shift - "
                   + "array index has no geometric meaning (77208)");
      assertEquals(expected, listBelow.getPixelOffset().y,
                   "a selection list below the band is shifted like any other "
                   + "assembly below it, not by the un-netted expansion");
      assertEquals(BAND_Y, listA.getPixelOffset().y,
                   "an assembly in the band itself is not moved");
      assertEquals(BAND_Y, listB.getPixelOffset().y,
                   "an assembly in the band itself is not moved");
   }

   /**
    * The column direction of the same rule ({@code exprow == false},
    * {@code cmap}). Untouched by the reported repro, but the loop and the netting
    * are shared, so the same index-dependent split was latent here.
    */
   @Test
   void everythingRightOfASharedExpansionBandIsShiftedByTheSameAmount() {
      Viewsheet vs = new Viewsheet();
      // the two lists are stacked vertically at the same x, so they share one
      // column band; the texts sit to the right of it
      TextVSAssembly textEarly = text(vs, "TextEarly", BELOW_Y, 0);
      SelectionListVSAssembly listA = selectionList(vs, "ListA", BAND_Y, 0, BAND_H, 50);
      SelectionListVSAssembly listB = selectionList(vs, "ListB", BAND_Y, 100, BAND_H, 50);
      TextVSAssembly textLate = text(vs, "TextLate", BELOW_Y, 100);

      TestExporter exporter = new TestExporter();
      exporter.expandColumns(listA, 200);
      exporter.expandColumns(listB, 300);

      int expected = BELOW_Y + 300;

      assertEquals(expected, textEarly.getPixelOffset().x,
                   "an assembly right of the band, before the lists in array order");
      assertEquals(expected, textLate.getPixelOffset().x,
                   "an assembly right of the band, after the lists in array order, "
                   + "must get the identical shift (77208)");
      assertEquals(BAND_Y, listA.getPixelOffset().x,
                   "an assembly in the band itself is not moved");
      assertEquals(BAND_Y, listB.getPixelOffset().x,
                   "an assembly in the band itself is not moved");
   }

   /**
    * Bug #71211 regression guard, the distinct-band topology: a taller expander
    * higher up whose insert position lies <em>below</em> a shorter expander's
    * insert position, so the two do not share a band and neither expansion pays
    * for the other.
    *
    * <p>{@code TallList} inserts 200 at position 500; that moved {@code TextBelow}
    * (top 600) but not {@code SelBetween}/{@code TextBetween} (top 350).
    * {@code ShortList} then inserts at position 300 and must insert its full 300:
    * netting {@code TallList}'s 200 out of it would leave the two assemblies at
    * 350 only 100 lower, i.e. overlapping the expanded {@code ShortList}, which is
    * exactly what #71211 reported.</p>
    *
    * <p>This test passes both before and after the #77208 fix &mdash; before it,
    * {@code moveAssemblies()}'s {@code more = omore} happened to restore the same
    * numbers. It is here to prove the fix does not re-open #71211.</p>
    */
   @Test
   void anExpanderDoesNotNetOutAnInsertMadeBelowItsOwnBottom() {
      Viewsheet vs = new Viewsheet();
      SelectionTreeVSAssembly tallList = selectionTree(vs, "TallList", 0, 100, 400);
      SelectionListVSAssembly shortList = selectionList(vs, "ShortList", 0, 200, 100);
      SelectionListVSAssembly selBetween = selectionList(vs, "SelBetween", 500, 350, 50);
      TextVSAssembly textBetween = text(vs, "TextBetween", 300, 350);
      TextVSAssembly textBelow = text(vs, "TextBelow", 0, 600);

      TestExporter exporter = new TestExporter();
      // processing order is by top, as expandAll()'s bottomComparator produces
      exporter.expandRows(tallList, 200);
      exporter.expandRows(shortList, 300);

      assertEquals(350 + 300, selBetween.getPixelOffset().y,
                   "below ShortList's band (300) but above TallList's band (500), "
                   + "so TallList's insert never moved it and must not be netted "
                   + "out of ShortList's - otherwise it overlaps ShortList (71211)");
      assertEquals(350 + 300, textBetween.getPixelOffset().y,
                   "same band as SelBetween, so the same shift");
      assertEquals(600 + 200 + 300, textBelow.getPixelOffset().y,
                   "below both bands, so it takes both expansions");
      assertEquals(100, tallList.getPixelOffset().y, "the first expander is not moved");
      assertEquals(200, shortList.getPixelOffset().y,
                   "ShortList's top is above TallList's band, so it was not moved");
   }

   /**
    * The netting itself, isolated: two expanders that <em>do</em> share a band
    * must not both charge for it. Same-position inserts add up to the largest
    * request, not to their sum.
    */
   @Test
   void aSharedBandIsChargedOnlyOnceForTheLargestExpansion() {
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly listA = selectionList(vs, "ListA", 0, BAND_Y, BAND_H);
      SelectionListVSAssembly listB = selectionList(vs, "ListB", 200, BAND_Y, BAND_H);
      SelectionListVSAssembly listC = selectionList(vs, "ListC", 400, BAND_Y, BAND_H);
      TextVSAssembly below = text(vs, "Below", 0, BELOW_Y);

      TestExporter exporter = new TestExporter();
      exporter.expandRows(listA, 120);
      exporter.expandRows(listB, 300);
      // smaller than what the band already has: contributes nothing
      exporter.expandRows(listC, 90);

      assertEquals(BELOW_Y + 300, below.getPixelOffset().y,
                   "three expanders in one band need max(120, 300, 90) rows, "
                   + "not their sum");
   }

   private static TextVSAssembly text(Viewsheet vs, String name, int x, int y) {
      TextVSAssembly text = new TextVSAssembly(vs, name);
      text.setPixelOffset(new Point(x, y));
      text.setPixelSize(new Dimension(100, 20));
      vs.addAssembly(text);
      return text;
   }

   private static SelectionListVSAssembly selectionList(Viewsheet vs, String name, int x, int y,
                                                        int height)
   {
      return selectionList(vs, name, x, y, 100, height);
   }

   private static SelectionListVSAssembly selectionList(Viewsheet vs, String name, int x, int y,
                                                        int width, int height)
   {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);
      list.setPixelOffset(new Point(x, y));
      list.setPixelSize(new Dimension(width, height));
      vs.addAssembly(list);
      return list;
   }

   private static SelectionTreeVSAssembly selectionTree(Viewsheet vs, String name, int x, int y,
                                                        int height)
   {
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, name);
      tree.setPixelOffset(new Point(x, y));
      tree.setPixelSize(new Dimension(100, height));
      vs.addAssembly(tree);
      return tree;
   }

   /**
    * Exposes the protected expand pass. {@code expandSelectionAssembly()} grows
    * the assembly and then hands the <em>old</em> size to
    * {@code insertRowCol()}; this reproduces that contract without needing a
    * bound selection list or a sandbox.
    */
   private static final class TestExporter extends CSVVSExporter {
      void expandRows(VSAssembly obj, int more) {
         Dimension oldSize = (Dimension) obj.getPixelSize().clone();
         obj.setPixelSize(new Dimension(oldSize.width, oldSize.height + more));
         insertRowCol(obj, oldSize, more, 0);
      }

      void expandColumns(VSAssembly obj, int more) {
         Dimension oldSize = (Dimension) obj.getPixelSize().clone();
         obj.setPixelSize(new Dimension(oldSize.width + more, oldSize.height));
         insertRowCol(obj, oldSize, 0, more);
      }
   }

   private static final int BAND_Y = 100;
   private static final int BAND_H = 100;
   private static final int BELOW_Y = 300;
}
