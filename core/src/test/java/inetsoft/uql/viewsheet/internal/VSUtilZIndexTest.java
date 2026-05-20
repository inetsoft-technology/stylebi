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

package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Bug #75075
public class VSUtilZIndexTest {
   // Must match the z-index of .composer-overlay in vs-viewsheet.component.scss.
   private static final int COMPOSER_OVERLAY_ZINDEX = 99997;

   @Test
   void embeddedViewsheetZIndexIncreasesByViewsheetZIndexGap() {
      Viewsheet[] embedded = createEmbeddedViewsheets(11);
      VSUtil.calcChildZIndex(embedded, 0);

      assertEquals(1, embedded[0].getZIndex());
      assertEquals(1 + 10 * Viewsheet.VIEWSHEET_ZINDEX_GAP, embedded[10].getZIndex());
   }

   // Regression test: inner objects of the 11th embedded viewsheet were assigned z-indexes
   // starting at 10002, exceeding the composer-overlay CSS z-index of 9998 and
   // blocking right-click access to Properties. The overlay was raised to 99997.
   @Test
   void innerObjectZIndexesRemainBelowComposerOverlayFor100EmbeddedViewsheets() {
      final int maxEmbedded = 100;
      Viewsheet[] embedded = createEmbeddedViewsheets(maxEmbedded);
      VSUtil.calcChildZIndex(embedded, 0);

      for(int i = 0; i < maxEmbedded; i++) {
         int embeddedZIndex = embedded[i].getZIndex();
         TextVSAssembly innerObject = new TextVSAssembly();
         innerObject.getVSAssemblyInfo().setName("Text" + i);
         VSUtil.calcChildZIndex(new Assembly[]{ innerObject }, embeddedZIndex);

         assertTrue(innerObject.getZIndex() < COMPOSER_OVERLAY_ZINDEX,
                    "Inner object z-index " + innerObject.getZIndex() + " of embedded viewsheet #" +
                    (i + 1) + " (outer z-index=" + embeddedZIndex + ") must be below " +
                    "composer overlay z-index " + COMPOSER_OVERLAY_ZINDEX);
      }
   }

   private Viewsheet[] createEmbeddedViewsheets(int count) {
      Viewsheet[] viewsheets = new Viewsheet[count];

      for(int i = 0; i < count; i++) {
         viewsheets[i] = new Viewsheet();
         viewsheets[i].getVSAssemblyInfo().setName("Embedded" + i);
      }

      return viewsheets;
   }
}
