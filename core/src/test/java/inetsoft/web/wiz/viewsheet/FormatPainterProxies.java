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

import inetsoft.report.composition.WorksheetService;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.controller.FormatPainterServiceProxy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wraps a {@link FormatPainterService} (real or mock) in the cluster proxy that
 * {@link ViewsheetFormatService} now injects, as a single local node, so every call lands on the
 * wrapped painter exactly as in a non-clustered deployment.
 */
final class FormatPainterProxies {
   private FormatPainterProxies() {
   }

   static FormatPainterServiceProxy local(FormatPainterService painter) {
      WorksheetService worksheets = mock(WorksheetService.class);
      when(worksheets.isLocal(org.mockito.ArgumentMatchers.any())).thenReturn(true);
      return new FormatPainterServiceProxy(null, worksheets, painter);
   }
}
