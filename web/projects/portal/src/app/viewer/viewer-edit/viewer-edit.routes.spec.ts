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

import { TestBed } from "@angular/core/testing";
import { viewerEditRoutes } from "./viewer-edit.routes";
import { GlobalSubmitService } from "../../vsobjects/util/global-submit.service";

// Regression for Bug #76497: when a chart in a saved viewsheet is opened for
// editing from the Portal viewer's pencil icon (this route) and the Wizard's
// Filter recommendation type is selected, VSRangeSlider and VSSelection
// (vsobjects/objects/range-slider, .../selection) inject GlobalSubmitService
// as a required (non-@Optional()) constructor dependency. GlobalSubmitService
// has no `providedIn: "root"` -- it is only ever provided by ComposerMainComponent
// and ViewerAppComponent, and this route is a routing *sibling* of the one that
// hosts ViewerAppComponent, not a descendant, so it was never reachable here.
// Angular threw NG0201 (NullInjectorError) the instant the wizard switched to
// Range Slider / Selection List, leaving the preview canvas blank -- see
// docs/teams/2026-09-14-bug-76497/02-root-cause.md for the full trace.
describe("viewerEditRoutes — DI wiring (Bug #76497)", () => {
   it("provides GlobalSubmitService, required by the Wizard's Range Slider and Selection List/Tree preview", () => {
      TestBed.configureTestingModule({
         providers: [...viewerEditRoutes[0].providers!],
      });

      expect(() => TestBed.inject(GlobalSubmitService)).not.toThrow();
   });
});
