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
import { Component, DebugElement } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { Subject } from "rxjs";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ContextProvider } from "../../context-provider.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSDataTipDirective } from "../data-tip/vs-data-tip.directive";
import { MiniToolbar } from "./mini-toolbar.component";
import { MiniToolbarService } from "./mini-toolbar.service";

// Mirrors the real composition in vs-object-container.component.html: a <mini-toolbar> that
// also carries [VSDataTip][miniToolbar]=true, plus the assembly's own main element (looked up
// by id inside VSDataTipDirective.ngDoCheck) that the data tip popup is positioned relative to.
@Component({
   standalone: true,
   imports: [MiniToolbar, VSDataTipDirective],
   template: `
      <div id="Tip1_main"></div>
      <mini-toolbar VSDataTip [dataTipName]="'Tip1'" [miniToolbar]="true"
         [assembly]="'Tip1'" [top]="500" [left]="500" [width]="200">
      </mini-toolbar>
   `
})
class TestApp {
}

describe("MiniToolbar Tests", () => {
   let fixture: ComponentFixture<TestApp>;
   let miniToolbarDebugEl: DebugElement;
   let dataTipService: any;

   function toolbarDiv(): HTMLElement {
      return miniToolbarDebugEl.nativeElement.querySelector(".mini-toolbar");
   }

   beforeEach(() => {
      dataTipService = {
         isDataTip: vi.fn((name: string) => name === "Tip1"),
         isCurrentDataTip: vi.fn(() => false),
         isDataTipVisible: vi.fn(() => false),
         isFrozen: vi.fn(() => false),
         getVSObjectId: vi.fn(() => "Tip1_main"),
         dataTipX: 100,
         dataTipY: 200,
         dataTipName: "Tip1",
         dataTipAlpha: 1,
         scrolled: new Subject<void>(),
         viewerOffset: {width: 1000, height: 1000, scrollLeft: 0, scrollTop: 0}
      };

      TestBed.configureTestingModule({
         imports: [TestApp],
         providers: [
            {provide: DataTipService, useValue: dataTipService},
            {
               provide: PopComponentService,
               useValue: {
                  getPopComponent: vi.fn(() => null),
                  hasPopUpComponentShowing: vi.fn(() => false),
                  getPopInfo: vi.fn(() => null),
                  isPopComponentShow: vi.fn(() => false)
               }
            },
            {provide: DebounceService, useValue: {debounce: vi.fn(), cancel: vi.fn()}},
            {
               provide: ContextProvider,
               useValue: {composer: false, vsWizard: false, binding: false}
            },
            MiniToolbarService
         ]
      });

      fixture = TestBed.createComponent(TestApp);
      miniToolbarDebugEl = fixture.debugElement.query(By.directive(MiniToolbar));
   });

   // Bug #76399: while this mini-toolbar is the active, visible data tip, VSDataTipDirective
   // (co-located via [VSDataTip][miniToolbar]=true) imperatively owns its position. Before the
   // fix, MiniToolbar's own [style.top.px]/[style.left.px] template bindings independently wrote
   // the static `top`/`left` Inputs to the same element and -- because MiniToolbar's child view
   // refreshes after the parent-view directive's ngDoCheck on the very same change detection
   // pass -- clobbered the directive's correct value back to the static one whenever the static
   // binding's own value changed (e.g. because a toolbar action's visibility changed the
   // computed `left`). This asserts the directive's mouse-tracked value wins and stays applied,
   // not the static `top`/`left` Inputs (500/500), when the tip is active.
   it("lets VSDataTipDirective own the position while this is the active data tip", () => {
      dataTipService.isCurrentDataTip.mockReturnValue(true);
      dataTipService.isDataTipVisible.mockReturnValue(true);

      fixture.detectChanges();

      const div = toolbarDiv();
      expect(div.style.left).toBe("101px");
      expect(div.style.top).toBe("173px");
      expect(div.style.left).not.toBe("500px");
   });

   // Control: when this mini-toolbar is not an active data tip (the ordinary case for the vast
   // majority of mini-toolbars), the static top/left Inputs still drive its position exactly as
   // before this fix.
   it("uses the static top/left Inputs when not an active data tip", () => {
      fixture.detectChanges();

      const div = toolbarDiv();
      expect(div.style.left).toBe("500px");
      // topY = top - miniToolbarHeight(28, gate off) since top(500) > minTop(20)
      expect(div.style.top).toBe("472px");
   });
});

// The topY math is pure and depends only on `top`, the context flags, and the pop-component check,
// so we construct the component directly with light mocks rather than through TestBed.
function makeToolbar(): MiniToolbar {
   const contextProvider: any = { composer: false, vsWizard: false, binding: false };
   const element: any = { nativeElement: {} };
   const miniToolbarService: any = {};
   const popComponentService: any = { isPopComponentShow: () => false };
   // No co-located [VSDataTip] directive, so isActiveDataTip is false and topY stays numeric.
   const comp = new MiniToolbar(contextProvider, element, miniToolbarService, popComponentService,
                                null);
   comp.assembly = "Chart1";
   return comp;
}

describe("MiniToolbar.topY", () => {
   afterEach(() => {
      document.body.classList.remove("viz-modern");
   });

   it("positions the toolbar 28px above the object when the gate is off", () => {
      const comp = makeToolbar();
      comp.top = 100;
      expect(comp.topY).toBe(72); // 100 - 28 - 0
   });

   it("positions the toolbar 24px above the object under .viz-modern", () => {
      document.body.classList.add("viz-modern");
      const comp = makeToolbar();
      comp.top = 100;
      expect(comp.topY).toBe(76); // 100 - 24 - 0
   });
});
