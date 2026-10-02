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
package inetsoft.report.io.viewsheet;

import inetsoft.report.io.viewsheet.ppt.*;
import inetsoft.uql.viewsheet.AbstractVSAssembly;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A PowerPoint selection list lays its rows out inside the card inset, as a tree does. The
 * helper keeps the exporter for its own drawing, and the shared base resolves the inset through
 * the same exporter. PowerPoint bounds are in points, so the pixel inset is scaled to match. The
 * exporter runs its real methods without its constructor, whose context type is private to the
 * ppt package.
 */
class PPTSelectionListInsetTest {
   // a 132 x 202 card is 99 x 151.5pt, and its 16px inset is 12pt: content 12,12 75 x 127.5.
   // x scales by 75/99; a row under the 30px (22.5pt) title moves down 12
   @Test
   void aListsRowsMoveInsideTheInsetInPoints() {
      PPTCoordinateHelper cHelper = mock(PPTCoordinateHelper.class);
      when(cHelper.getScale()).thenReturn(0.75);
      when(cHelper.getBounds((AbstractVSAssembly) null, CoordinateHelper.ALL, true, null))
         .thenReturn(new Rectangle2D.Double(0, 0, 99, 151.5));
      PPTSelectionListHelper helper = new PPTSelectionListHelper(
         null, cHelper, mock(PPTVSExporter.class, Mockito.CALLS_REAL_METHODS));
      helper.boundsList = new ArrayList<>(List.of(
         new Rectangle2D.Double(0, 0, 99, 22.5), new Rectangle2D.Double(0, 22.5, 99, 21)));
      SelectionListVSAssemblyInfo info = mock(SelectionListVSAssemblyInfo.class);
      when(info.getPadding()).thenReturn(new Insets(16, 16, 16, 16));

      helper.insetRowBounds(null, info);

      assertEquals(new Rectangle2D.Double(12, 34.5, 75, 21), helper.boundsList.get(1));
   }
}
