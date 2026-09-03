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

/**
 * Regression test for bug #76418: ComposerMainComponent used to declare its own
 * component-scoped `ResizeHandlerService` provider, which shadowed the composer
 * route's provider for ComposerMainComponent and its whole descendant subtree
 * (WSPaneComponent -> WSDetailsPaneComponent -> WSDetailsTableDataComponent, etc).
 * Because only the route-scoped instance ever had initListeners() called on it
 * (in ComposerAppComponent.ngOnInit()), the shadowed instance used by the
 * descendants never received real `window: resize` events, so tables never
 * re-rendered their virtualized column range after a browser maximize/restore.
 *
 * This test asserts that ComposerMainComponent resolves ResizeHandlerService from
 * an ancestor injector (module/"route" level) rather than creating its own
 * instance. If the duplicate component-level provider is reintroduced, this test
 * fails because ComposerMainComponent's injected instance will differ from the
 * ancestor-provided one.
 */

import "@angular/compiler";
import { TestBed } from "@angular/core/testing";
import { ResizeHandlerService } from "./resize-handler.service";
import { renderComponent } from "./composer-main.spec-helpers";

beforeAll(() => {
   (window as any).BroadcastChannel = (window as any).BroadcastChannel ?? class {
      onmessage: any = null;
      postMessage() {}
      close() {}
      addEventListener() {}
      removeEventListener() {}
   };
});

afterEach(() => {
   vi.restoreAllMocks();
   localStorage.clear();
});

describe("ComposerMainComponent — ResizeHandlerService DI resolution (bug #76418)", () => {
   it("resolves the same ResizeHandlerService instance provided by an ancestor injector", async () => {
      const { comp } = await renderComponent();

      const ancestorInstance = TestBed.inject(ResizeHandlerService);

      expect((comp as any).resizeHandlerService).toBe(ancestorInstance);
   });

   it("routes onSplitDragEnd's resize notification through the ancestor-provided instance", async () => {
      const { comp, mocks } = await renderComponent();
      (comp as any).splitPane = { getSizes: () => [25, 75] };

      comp.onSplitDragEnd(null);

      // mocks.resizeHandlerService is provided at module ("route") level in
      // composer-main.spec-helpers.ts — this only passes because ComposerMainComponent
      // resolves the same ancestor-provided instance rather than a shadowed one of its own.
      expect(mocks.resizeHandlerService.onVerticalResizeEnd).toHaveBeenCalled();
   });
});
