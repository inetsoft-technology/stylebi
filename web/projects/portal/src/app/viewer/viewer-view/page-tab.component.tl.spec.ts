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
 * PageTabComponent — single pass (+race-condition +memory-leak)
 *
 * Risk-first coverage:
 *   Group 1  [baseline]       — tabs getter: delegates to pageTabService.tabs
 *   Group 2  [baseline]       — onTabSelect: calls changeCurrentTab; DOM click wires up
 *   Group 3  [baseline]       — closeTab: calls pageTabService.closeTab; stopPropagation guards
 *   Group 4  [Risk 2]         — refreshScroll(): scroll-state flags computed from DOM metrics
 *   Group 5  [Risk 3]         — refreshScroll(tabAdded=true): auto-scrolls to newly added tab
 *   Group 6  [race-condition] — constructor subscription: onTabAddedRemoved → refreshScroll
 *                               (wired in constructor, not ngOnInit, to avoid missing events
 *                               fired before Angular lifecycle hooks complete)
 *   Group 7  [memory-leak]    — ngOnDestroy: unsubscribes so post-destroy emissions are ignored
 *   Group 8  [Risk 2]         — scroll(): non-left-button guard; immediate scroll; interval setup
 *   Group 9  [baseline]       — mouseup/touchend HostListener: clears scroll interval
 *   Group 10 [baseline]       — template: tab list visibility, scroll buttons, active class
 */

import { Directive, EventEmitter, Output } from "@angular/core";
import { fireEvent, render, screen, waitFor } from "@testing-library/angular";
import { Subject } from "rxjs";

import { PageTabComponent } from "./page-tab.component";
import { PageTabService, TabInfoModel } from "../services/page-tab.service";
import { ResizedDirective } from "../../../../../shared/resize-event/resized.directive";

// ResizeObserver is used internally by ResizedDirective; must be a class (called with new)
vi.stubGlobal("ResizeObserver", class {
   observe = vi.fn();
   disconnect = vi.fn();
   constructor(_cb: any) {}
});

// ---------------------------------------------------------------------------
// ResizedDirective stub — prevents ResizeObserver lifecycle from interfering
// ---------------------------------------------------------------------------

@Directive({ selector: "[resized]", standalone: true })
class ResizedDirectiveStub {
   @Output() resized = new EventEmitter<any>();
}

// ---------------------------------------------------------------------------
// Fake PageTabService — no HTTP dependency
// ---------------------------------------------------------------------------

class FakePageTabService {
   _tabs: TabInfoModel[] = [];
   onTabAddedRemoved = new Subject<boolean>();

   get tabs(): TabInfoModel[] {
      return this._tabs;
   }

   changeCurrentTab = vi.fn();
   closeTab = vi.fn();
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeTab(overrides: Partial<TabInfoModel> = {}): TabInfoModel {
   return { id: "tab-1", label: "Sheet 1", isFocused: true, ...overrides };
}

interface RenderOpts {
   tabs?: TabInfoModel[];
}

async function renderComponent(opts: RenderOpts = {}) {
   const fakeSvc = new FakePageTabService();
   fakeSvc._tabs = opts.tabs ?? [];

   const result = await render(PageTabComponent, {
      providers: [{ provide: PageTabService, useValue: fakeSvc }],
      importOverrides: [{ replace: ResizedDirective, with: ResizedDirectiveStub }],
   });

   const comp = result.fixture.componentInstance;
   return { comp, fakeSvc, fixture: result.fixture, ...result };
}

// ---------------------------------------------------------------------------
// Group 1 — tabs getter
// ---------------------------------------------------------------------------

describe("PageTabComponent — tabs getter", () => {
   it("should return the same reference as pageTabService.tabs", async () => {
      const tab = makeTab();
      const { comp, fakeSvc } = await renderComponent({ tabs: [tab] });
      expect(comp.tabs).toBe(fakeSvc.tabs);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — onTabSelect
// ---------------------------------------------------------------------------

describe("PageTabComponent — onTabSelect()", () => {
   it("should call pageTabService.changeCurrentTab with the selected tab", async () => {
      const tab = makeTab();
      const { comp, fakeSvc } = await renderComponent({ tabs: [tab] });
      comp.onTabSelect(tab);
      expect(fakeSvc.changeCurrentTab).toHaveBeenCalledWith(tab);
   });

   it("should call changeCurrentTab when a nav-link is clicked", async () => {
      const tab1 = makeTab();
      const tab2 = makeTab({ id: "tab-2", label: "Sheet 2", isFocused: false });
      const { fakeSvc } = await renderComponent({ tabs: [tab1, tab2] });

      const links = document.querySelectorAll("a.nav-link");
      fireEvent.click(links[1]);
      expect(fakeSvc.changeCurrentTab).toHaveBeenCalledWith(tab2);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — closeTab
// ---------------------------------------------------------------------------

describe("PageTabComponent — closeTab()", () => {
   it("should call pageTabService.closeTab with the given tab", async () => {
      const tab = makeTab();
      const { comp, fakeSvc } = await renderComponent({ tabs: [tab] });
      comp.closeTab(tab);
      expect(fakeSvc.closeTab).toHaveBeenCalledWith(tab);
   });

   it("should close the tab without selecting it when the close icon is clicked", async () => {
      // Template: <span class="close-icon" (click)="$event.stopPropagation(); closeTab(tab)">
      // stopPropagation prevents the parent <a> (onTabSelect) from firing.
      const tab1 = makeTab();
      const tab2 = makeTab({ id: "tab-2", label: "Sheet 2", isFocused: false });
      const { fakeSvc } = await renderComponent({ tabs: [tab1, tab2] });

      const closeIcons = document.querySelectorAll("span.close-icon");
      fireEvent.click(closeIcons[0]);

      expect(fakeSvc.closeTab).toHaveBeenCalledWith(tab1);
      expect(fakeSvc.changeCurrentTab).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 4 — refreshScroll(): scroll-state flags
// ---------------------------------------------------------------------------

describe("PageTabComponent — refreshScroll() scroll state", () => {
   it("should default scrollButtonsVisible to false when content fits within the viewport", async () => {
      const { comp } = await renderComponent();
      // jsdom: scrollWidth == offsetWidth == 0 → condition (scrollWidth > offsetWidth) is false
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));
   });

   it("should set scrollButtonsVisible=true when scrollWidth exceeds offsetWidth", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));

      const scroller = comp.tabScroller.nativeElement;
      Object.defineProperty(scroller, "scrollWidth", { configurable: true, get: () => 400 });
      Object.defineProperty(scroller, "offsetWidth", { configurable: true, get: () => 200 });

      comp.refreshScroll();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(true));
   });

   it("should set leftScrollEnabled=true when scrollLeft > 0", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));

      comp.tabScroller.nativeElement.scrollLeft = 50;
      comp.refreshScroll();
      await waitFor(() => expect(comp.leftScrollEnabled).toBe(true));
   });

   it("should set rightScrollEnabled=true when remaining scroll distance > 1", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));

      const scroller = comp.tabScroller.nativeElement;
      Object.defineProperty(scroller, "scrollWidth", { configurable: true, get: () => 300 });
      Object.defineProperty(scroller, "offsetWidth", { configurable: true, get: () => 200 });
      scroller.scrollLeft = 0; // 300 - 200 - 0 = 100 > 1

      comp.refreshScroll();
      await waitFor(() => expect(comp.rightScrollEnabled).toBe(true));
   });

   it("should set rightScrollEnabled=false when (scrollWidth - offsetWidth - scrollLeft) <= 1", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));

      const scroller = comp.tabScroller.nativeElement;
      Object.defineProperty(scroller, "scrollWidth", { configurable: true, get: () => 300 });
      Object.defineProperty(scroller, "offsetWidth", { configurable: true, get: () => 200 });

      // Prime rightScrollEnabled to true so the change to false is observable
      scroller.scrollLeft = 0; // 300 - 200 - 0 = 100 > 1 → true
      comp.refreshScroll();
      await waitFor(() => expect(comp.rightScrollEnabled).toBe(true));

      // Apply boundary condition: 300 - 200 - 99 = 1, NOT > 1
      scroller.scrollLeft = 99;
      comp.refreshScroll();
      await waitFor(() => expect(comp.rightScrollEnabled).toBe(false));
   });

   it("should update scroll state when the native scroll event fires on the scroller", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));

      const scroller = comp.tabScroller.nativeElement;
      scroller.scrollLeft = 30;

      fireEvent.scroll(scroller);
      await waitFor(() => expect(comp.leftScrollEnabled).toBe(true));
   });
});

// ---------------------------------------------------------------------------
// Group 5 — refreshScroll(tabAdded=true): auto-scroll to newly added tab
// ---------------------------------------------------------------------------

describe("PageTabComponent — refreshScroll(tabAdded=true)", () => {
   // 🔁 Regression-sensitive: newly added tab must scroll into view. If the tabAdded
   // branch is silently skipped, the tab strip stays at its previous position and the
   // new tab appears off-screen.
   it("should set tabScroller.scrollLeft to scrollWidth-offsetWidth when tabAdded=true", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));

      const scroller = comp.tabScroller.nativeElement;
      Object.defineProperty(scroller, "scrollWidth", { configurable: true, get: () => 400 });
      Object.defineProperty(scroller, "offsetWidth", { configurable: true, get: () => 200 });
      scroller.scrollLeft = 0;

      comp.refreshScroll(true);
      await waitFor(() => expect(scroller.scrollLeft).toBe(200));
   });

   it("should not mutate tabScroller.scrollLeft when tabAdded=false", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));

      const scroller = comp.tabScroller.nativeElement;
      scroller.scrollLeft = 50;

      comp.refreshScroll(false);
      // Gate: with scrollLeft=50, leftScrollEnabled becomes true once the timeout fires,
      // proving refreshScroll(false) ran to completion without modifying scrollLeft.
      await waitFor(() => expect(comp.leftScrollEnabled).toBe(true));
      expect(scroller.scrollLeft).toBe(50);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — Constructor subscription: onTabAddedRemoved → refreshScroll
// ---------------------------------------------------------------------------

describe("PageTabComponent — onTabAddedRemoved subscription", () => {
   // 🔁 Race-condition: the subscription is wired in the constructor so it can receive
   // tab-added events that fire before ngAfterViewInit (e.g. during parent init).
   // Moving the subscription to ngOnInit would miss those events.
   it("should invoke refreshScroll(true) when onTabAddedRemoved emits true", async () => {
      const { comp, fakeSvc } = await renderComponent();
      const spy = vi.spyOn(comp, "refreshScroll");
      try {
         fakeSvc.onTabAddedRemoved.next(true);
         expect(spy).toHaveBeenCalledWith(true);
      } finally {
         spy.mockRestore();
      }
   });

   it("should invoke refreshScroll(false) when onTabAddedRemoved emits false", async () => {
      const { comp, fakeSvc } = await renderComponent();
      const spy = vi.spyOn(comp, "refreshScroll");
      try {
         fakeSvc.onTabAddedRemoved.next(false);
         expect(spy).toHaveBeenCalledWith(false);
      } finally {
         spy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 7 — ngOnDestroy: subscription cleanup
// ---------------------------------------------------------------------------

describe("PageTabComponent — ngOnDestroy() subscription cleanup", () => {
   it("should not invoke refreshScroll after destruction when onTabAddedRemoved emits", async () => {
      const { comp, fakeSvc } = await renderComponent();
      comp.ngOnDestroy();

      const spy = vi.spyOn(comp, "refreshScroll");
      try {
         fakeSvc.onTabAddedRemoved.next(true);
         expect(spy).not.toHaveBeenCalled();
      } finally {
         spy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 8 — scroll(): non-left-button guard; immediate scroll; interval setup
// ---------------------------------------------------------------------------

describe("PageTabComponent — scroll()", () => {
   it("should not modify scrollLeft when a non-left mouse button fires the event", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));
      const scroller = comp.tabScroller.nativeElement;
      scroller.scrollLeft = 50;

      comp.scroll(new MouseEvent("mousedown", { button: 2 }), true);

      expect(scroller.scrollLeft).toBe(50);
   });

   it("should not start a repeat-scroll interval when a non-left mouse button fires the event", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));

      comp.scroll(new MouseEvent("mousedown", { button: 2 }), true);

      // buttonHoldIntervalId starts undefined; the guard causes an early return before setInterval
      expect(comp.buttonHoldIntervalId).toBeUndefined();
   });

   it("should scroll left by SCROLL_ADJ on left-button press", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));
      const scroller = comp.tabScroller.nativeElement;
      scroller.scrollLeft = 50;

      comp.scroll(new MouseEvent("mousedown", { button: 0 }), true);

      expect(scroller.scrollLeft).toBe(30); // 50 - SCROLL_ADJ(20)
   });

   it("should scroll right by SCROLL_ADJ on left-button press", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));
      const scroller = comp.tabScroller.nativeElement;
      Object.defineProperty(scroller, "scrollWidth", { configurable: true, get: () => 400 });
      scroller.scrollLeft = 0;

      comp.scroll(new MouseEvent("mousedown", { button: 0 }), false);

      expect(scroller.scrollLeft).toBe(20); // 0 + SCROLL_ADJ(20)
   });

   it("should start a repeat-scroll interval on left-button press", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));
      const scroller = comp.tabScroller.nativeElement;
      scroller.scrollLeft = 50;

      comp.scroll(new MouseEvent("mousedown", { button: 0 }), true);

      expect(comp.buttonHoldIntervalId).not.toBeNull();

      comp.mouseup(new MouseEvent("mouseup")); // cleanup
   });

   it("should clamp scrollLeft to 0 when scrolling left past the beginning", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));
      const scroller = comp.tabScroller.nativeElement;
      scroller.scrollLeft = 10;

      comp.scroll(new MouseEvent("mousedown", { button: 0 }), true);

      expect(scroller.scrollLeft).toBe(0); // Math.max(0, 10 - 20) = 0
   });

   it("should clear an existing interval before starting a new one on a second press", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));
      const scroller = comp.tabScroller.nativeElement;
      scroller.scrollLeft = 100;

      // Set up first interval; capture its ID before the second scroll replaces it
      comp.scroll(new MouseEvent("mousedown", { button: 0 }), true);
      const firstIntervalId = comp.buttonHoldIntervalId;

      // Spy only after first interval is captured, to isolate the clearInterval call from second scroll
      const clearIntervalSpy = vi.spyOn(globalThis, "clearInterval");
      try {
         scroller.scrollLeft = 100;
         comp.scroll(new MouseEvent("mousedown", { button: 0 }), true);
         expect(clearIntervalSpy).toHaveBeenCalledWith(firstIntervalId);
      } finally {
         clearIntervalSpy.mockRestore();
         comp.mouseup(new MouseEvent("mouseup")); // cleanup
      }
   });

   it("should perform the scroll when a TouchEvent is used (no left-button guard applies)", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));
      const scroller = comp.tabScroller.nativeElement;
      scroller.scrollLeft = 50;

      comp.scroll(new TouchEvent("touchstart"), true);

      expect(scroller.scrollLeft).toBe(30); // 50 - SCROLL_ADJ(20)

      comp.mouseup(new MouseEvent("mouseup")); // cleanup
   });

   it("should advance scroll position on each interval tick", async () => {
      const { comp } = await renderComponent();
      // Flush the ngAfterViewInit setTimeout before activating fake timers
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));

      const scroller = comp.tabScroller.nativeElement;
      Object.defineProperty(scroller, "scrollWidth", { configurable: true, get: () => 400 });
      scroller.scrollLeft = 100;

      vi.useFakeTimers();
      try {
         comp.scroll(new MouseEvent("mousedown", { button: 0 }), true);
         expect(scroller.scrollLeft).toBe(80); // initial: 100 - 20
         vi.advanceTimersByTime(20);
         expect(scroller.scrollLeft).toBe(60); // tick 1: 80 - 20
         vi.advanceTimersByTime(20);
         expect(scroller.scrollLeft).toBe(40); // tick 2: 60 - 20
         // Clear while fake timers are still active
         comp.mouseup(new MouseEvent("mouseup"));
      } finally {
         vi.useRealTimers();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 9 — mouseup/touchend HostListener: interval cleanup
// ---------------------------------------------------------------------------

describe("PageTabComponent — mouseup() interval cleanup", () => {
   it("should set buttonHoldIntervalId to null after clearing an active scroll interval", async () => {
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.scrollButtonsVisible).toBe(false));
      const scroller = comp.tabScroller.nativeElement;
      scroller.scrollLeft = 50;

      // Arrange: start a scroll to create an active interval (scroll() is covered in Group 8)
      comp.scroll(new MouseEvent("mousedown", { button: 0 }), true);

      comp.mouseup(new MouseEvent("mouseup"));
      expect(comp.buttonHoldIntervalId).toBeNull();
   });

   it("should not throw when mouseup fires without a prior scroll press", async () => {
      const { comp } = await renderComponent();
      expect(() => comp.mouseup(new MouseEvent("mouseup"))).not.toThrow();
      // No interval was set; buttonHoldIntervalId remains undefined (not null)
      expect(comp.buttonHoldIntervalId).toBeUndefined();
   });
});

// ---------------------------------------------------------------------------
// Group 10 — Template rendering
// ---------------------------------------------------------------------------

describe("PageTabComponent — template rendering", () => {
   it("should not render the tab list when there is only one tab", async () => {
      const { fixture } = await renderComponent({ tabs: [makeTab()] });
      fixture.detectChanges();
      expect(document.querySelector("ul.nav-tabs")).toBeNull();
   });

   it("should render the tab list when there are two or more tabs", async () => {
      const { fixture } = await renderComponent({
         tabs: [makeTab(), makeTab({ id: "tab-2", label: "Sheet 2" })],
      });
      fixture.detectChanges();
      expect(document.querySelector("ul.nav-tabs")).not.toBeNull();
   });

   it("should render a list item for each tab with the correct label", async () => {
      await renderComponent({
         tabs: [
            makeTab({ label: "Alpha" }),
            makeTab({ id: "tab-2", label: "Beta" }),
         ],
      });
      expect(screen.getByText("Alpha")).toBeTruthy();
      expect(screen.getByText("Beta")).toBeTruthy();
   });

   it("should apply the active class only to the focused tab", async () => {
      const { fixture } = await renderComponent({
         tabs: [
            makeTab({ isFocused: true }),
            makeTab({ id: "tab-2", label: "Sheet 2", isFocused: false }),
         ],
      });
      fixture.detectChanges();
      const items = document.querySelectorAll("li.nav-item");
      expect(items[0].classList.contains("active")).toBe(true);
      expect(items[1].classList.contains("active")).toBe(false);
   });

   it("should not render scroll buttons when scrollButtonsVisible is false", async () => {
      const { comp, fixture } = await renderComponent();
      comp.scrollButtonsVisible = false;
      fixture.detectChanges();
      expect(document.querySelector(".tab-scroller-button")).toBeNull();
   });

   it("should render both scroll buttons when scrollButtonsVisible is true", async () => {
      const { comp, fixture } = await renderComponent();
      comp.scrollButtonsVisible = true;
      fixture.detectChanges();
      expect(document.querySelectorAll(".tab-scroller-button").length).toBe(2);
   });

   it("should apply disabled-grayout to the left arrow when leftScrollEnabled is false", async () => {
      const { comp, fixture } = await renderComponent();
      comp.scrollButtonsVisible = true;
      comp.leftScrollEnabled = false;
      fixture.detectChanges();
      expect(document.querySelector(".left-arrow")?.classList.contains("disabled-grayout")).toBe(true);
   });

   it("should apply disabled-grayout to the right arrow when rightScrollEnabled is false", async () => {
      const { comp, fixture } = await renderComponent();
      comp.scrollButtonsVisible = true;
      comp.rightScrollEnabled = false;
      fixture.detectChanges();
      expect(document.querySelector(".right-arrow")?.classList.contains("disabled-grayout")).toBe(true);
   });
});
