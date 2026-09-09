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
import { DebounceService } from "../../../widget/services/debounce.service";
import { DataTipService } from "./data-tip.service";
import { PopComponentService } from "./pop-component.service";
import { VSDataTipDirective } from "./vs-data-tip.directive";

// Mirrors the ".table-container"/".calc-table-container" root element that vs-crosstab,
// vs-table, and vs-calctable apply [VSDataTip] to directly. Bug #75820: these elements
// never carry the "focus-assembly" class, so they must still be repositioned.
@Component({
   standalone: true,
   imports: [VSDataTipDirective],
   template: `<div class="table-container" VSDataTip [dataTipName]="'Tip1'"></div>`
})
class TestApp {
}

describe("VSDataTip Directive Tests", () => {
   let fixture: ComponentFixture<TestApp>;
   let div: DebugElement;
   let dataTipService: any;
   let popService: any;
   let debounceService: any;
   let viewerOffsetGetCount: number;
   const viewerOffsetValue = {width: 1000, height: 1000, scrollLeft: 0, scrollTop: 0};

   beforeEach(() => {
      viewerOffsetGetCount = 0;

      dataTipService = {
         isDataTip: vi.fn((name: string) => name === "Tip1"),
         isCurrentDataTip: vi.fn(() => true),
         isDataTipVisible: vi.fn(() => true),
         isFrozen: vi.fn(() => false),
         getVSObjectId: vi.fn(() => "Tip1_main"),
         dataTipX: 100,
         dataTipY: 200,
         dataTipName: "Tip1",
         dataTipAlpha: 1,
         scrolled: new Subject<void>(),
         get viewerOffset() {
            viewerOffsetGetCount++;
            return viewerOffsetValue;
         }
      };
      popService = {
         getPopComponent: vi.fn(() => null),
         hasPopUpComponentShowing: vi.fn(() => false),
         getPopInfo: vi.fn(() => null)
      };
      debounceService = {
         debounce: vi.fn(),
         cancel: vi.fn()
      };

      fixture = TestBed.configureTestingModule({
         imports: [TestApp, VSDataTipDirective],
         providers: [
            {provide: DataTipService, useValue: dataTipService},
            {provide: PopComponentService, useValue: popService},
            {provide: DebounceService, useValue: debounceService}
         ]
      }).createComponent(TestApp);

      div = fixture.debugElement.query(By.directive(VSDataTipDirective));
   });

   // Bug #75820: the directive is applied directly to VSCrosstab/VSTable/VSCalcTable's
   // own OnPush root element, which never has the "focus-assembly" class. Positioning
   // must not depend on that class.
   it("should reposition a non-focus-assembly element when it is the active, visible data tip", () => {
      fixture.detectChanges();
      const el: HTMLElement = div.nativeElement;

      expect(el.classList.contains("focus-assembly")).toBe(false);
      expect(el.style.position).toBe("absolute");
      expect(el.style.display).toBe("block");
      expect(el.style.left).toBe("101px");
      expect(el.style.top).toBe("201px");
   });

   it("should hide the element when it is not the current data tip", () => {
      dataTipService.isCurrentDataTip.mockReturnValue(false);
      fixture.detectChanges();
      const el: HTMLElement = div.nativeElement;

      expect(el.style.display).toBe("none");
   });

   // Bug #75512: DataTipService.viewerOffset may measure the DOM, so ngDoCheck must
   // read it at most once per invocation, both when repositioning and when the cache
   // check short-circuits because nothing changed.
   it("should read viewerOffset only once per ngDoCheck invocation", () => {
      fixture.detectChanges();
      expect(viewerOffsetGetCount).toBe(1);

      fixture.detectChanges();
      expect(viewerOffsetGetCount).toBe(2);
   });

   it("should mark the host for check on scroll while it is the current data tip", () => {
      fixture.detectChanges();
      const directive = div.injector.get(VSDataTipDirective);
      const markForCheckSpy = vi.spyOn((directive as any).changeRef, "markForCheck");

      dataTipService.scrolled.next();

      expect(markForCheckSpy).toHaveBeenCalled();
   });

   it("should not mark the host for check on scroll when it is not the current data tip", () => {
      fixture.detectChanges();
      dataTipService.isCurrentDataTip.mockReturnValue(false);
      const directive = div.injector.get(VSDataTipDirective);
      const markForCheckSpy = vi.spyOn((directive as any).changeRef, "markForCheck");

      dataTipService.scrolled.next();

      expect(markForCheckSpy).not.toHaveBeenCalled();
   });

   // Bug #76506: on a fresh trigger (transitioning from the "not active" branch's
   // display:none), the boundary check must measure the element's size only after display
   // has been set back to "block" -- otherwise clientWidth/clientHeight read 0 and the
   // overflow correction never applies.
   it("should measure size only after display is set to block, not while still display:none", () => {
      fixture.detectChanges();
      dataTipService.isCurrentDataTip.mockReturnValue(false);
      fixture.detectChanges();
      const el: HTMLElement = div.nativeElement;
      expect(el.style.display).toBe("none");

      let displayWhenWidthMeasured: string | undefined;
      let displayWhenHeightMeasured: string | undefined;
      Object.defineProperty(el, "clientWidth", {
         configurable: true,
         get: () => {
            displayWhenWidthMeasured = el.style.display;
            return 500;
         }
      });
      Object.defineProperty(el, "clientHeight", {
         configurable: true,
         get: () => {
            displayWhenHeightMeasured = el.style.display;
            return 500;
         }
      });

      dataTipService.isCurrentDataTip.mockReturnValue(true);
      fixture.detectChanges();

      expect(displayWhenWidthMeasured).toBe("block");
      expect(displayWhenHeightMeasured).toBe("block");
      expect(el.style.display).toBe("block");
   });
});

// Bug #76506: when the data tip content belongs to a registered container, the boundary
// check must use the container's *current* on-screen size, not the PopInfo snapshot taken
// once when the container was added (which is never refreshed, e.g. when the container
// later enters max mode and grows well past its original design-time size).
describe("VSDataTip Directive container boundary check (#76506)", () => {
   @Component({
      standalone: true,
      imports: [VSDataTipDirective],
      template: `
         <div id="Container1_main"></div>
         <div class="table-container" VSDataTip [dataTipName]="'Tip1'"
              [popContainerName]="'Container1'"></div>
      `
   })
   class ContainerTestApp {
   }

   let fixture: ComponentFixture<ContainerTestApp>;
   let div: DebugElement;
   let dataTipService: any;
   let popService: any;
   const viewerOffsetValue = {width: 1000, height: 1000, scrollLeft: 0, scrollTop: 0};

   // Stale snapshot captured at Add*ObjectCommand time -- much smaller than the container's
   // actual current size (simulating the container having since entered max mode).
   const staleContainerInfo = {
      top: 0, left: 0, width: 100, height: 100,
      absoluteName: "Container1", vsObject: {}
   };

   beforeEach(() => {
      dataTipService = {
         isDataTip: vi.fn((name: string) => name === "Tip1"),
         isCurrentDataTip: vi.fn(() => true),
         isDataTipVisible: vi.fn(() => true),
         isFrozen: vi.fn(() => false),
         getVSObjectId: vi.fn((name: string) => name === "Tip1" ? "Tip1_main" : "Container1_main"),
         dataTipX: 300,
         dataTipY: 300,
         dataTipName: "Tip1",
         dataTipAlpha: 1,
         scrolled: new Subject<void>(),
         get viewerOffset() {
            return viewerOffsetValue;
         }
      };
      popService = {
         getPopComponent: vi.fn(() => null),
         hasPopUpComponentShowing: vi.fn(() => false),
         getPopInfo: vi.fn((name: string) => {
            if(name === "Tip1") {
               return {top: 0, left: 0, width: 50, height: 50, absoluteName: "Tip1", vsObject: {}};
            }

            if(name === "Container1") {
               return staleContainerInfo;
            }

            return null;
         })
      };

      fixture = TestBed.configureTestingModule({
         imports: [ContainerTestApp, VSDataTipDirective],
         providers: [
            {provide: DataTipService, useValue: dataTipService},
            {provide: PopComponentService, useValue: popService},
            {provide: DebounceService, useValue: {debounce: vi.fn(), cancel: vi.fn()}}
         ]
      }).createComponent(ContainerTestApp);

      div = fixture.debugElement.query(By.directive(VSDataTipDirective));
   });

   it("should use the container's live on-screen size instead of the stale PopInfo snapshot", () => {
      const containerEl = fixture.nativeElement.querySelector("#Container1_main");
      Object.defineProperty(containerEl, "clientWidth", {configurable: true, get: () => 800});
      Object.defineProperty(containerEl, "clientHeight", {configurable: true, get: () => 800});

      fixture.detectChanges();
      const el: HTMLElement = div.nativeElement;

      // With the stale 100x100 PopInfo size, tipX/tipY of 300 would never overflow a
      // 1000x1000 viewport and no correction would be applied (left/top would be 301px).
      // The live 800x800 container size does overflow, so a leftward/upward correction
      // must be applied instead.
      expect(el.style.left).not.toBe("301px");
      expect(el.style.top).not.toBe("301px");
      expect(el.style.left).toBe("1px");
      expect(el.style.top).toBe("1px");
   });
});
