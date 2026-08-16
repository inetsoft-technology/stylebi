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
package inetsoft.web.service;

import inetsoft.report.TableDataPath;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.web.composer.model.vs.HighlightDialogModel;
import inetsoft.web.composer.model.vs.HighlightModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class HighlightServiceTest {
   /**
    * A table with no bound data has no cell at (0,0), so {@code VSTableLens.getTableDataPath}
    * returns null and {@code HighlightDialogService} passes that null straight through.
    *
    * <p>Dereferencing it produced {@code NullPointerException: Cannot invoke
    * "TableDataPath.getLevel()" because "dataPath" is null}, which surfaced to the caller as a raw
    * HTTP 500 HTML page — an unhandled exception type that the wiz error handler does not cover.
    * Found by calling list_highlights on an unbound table in a live viewsheet.
    *
    * <p>No data path means no row highlights to report, so the correct behaviour is an empty list.
    */
   @Test
   void getRowHighlightToleratesANullDataPath() {
      HighlightService service = new HighlightService(
         mock(inetsoft.analytic.composition.ViewsheetService.class),
         mock(inetsoft.web.binding.service.DataRefModelFactoryService.class));
      TableHighlightAttr attr = mock(TableHighlightAttr.class);
      List<HighlightModel> out = new ArrayList<>();

      assertDoesNotThrow(() -> service.getRowHighlight(
         attr, out, new HighlightDialogModel(), null, "Query1", null));

      assertTrue(out.isEmpty(), "a null data path contributes no row highlights");
      verify(attr, never()).getHighlight(any(TableDataPath.class));
   }
}
