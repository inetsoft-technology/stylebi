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
 * SlideOutComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — escKey: dismiss only when keyboard enabled and no open popups
 *   Group 2 [Risk 2] — toggle/setExpanded: dropdown observer open/close pairing
 *   Group 3 [Risk 2] — ngOnDestroy: closes dropdown observer when still open
 *   Group 4 [Risk 1] — z-index, renameTitle, actualWidth, sizeClass getters
 *   Group 5 [Risk 3] — startResize: document listener cleanup (mousemove/mouseup unlisten called)
 *
 * Out of scope: startResize with component destroyed before mouseup fires (ngOnDestroy does not
 *   remove the in-flight mousemove/mouseup listeners, so they leak until the next pointer event)
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { Renderer2 } from "@angular/core";
import { render } from "@testing-library/angular";
import { ModalDismissReasons } from "@ng-bootstrap/ng-bootstrap";
import { SlideOutComponent } from "./slide-out.component";
import { DialogService } from "./dialog-service.service";
import { DropdownObserver } from "../services/dropdown-observer.service";
import { SlideOutRef } from "./slide-out-ref";

function makeDialogServiceMock(overrides: Partial<DialogService> = {}) {
   return {
      openPopups: 0,
      getCurrentSlideOuts: vi.fn(() => [] as SlideOutRef[]),
      dismissAllSlideouts: vi.fn(),
      showSlideout: vi.fn(),
      isSlideoutOnTop: vi.fn(() => false),
      ...overrides,
   };
}

function makeDropdownObserverMock() {
   return {
      onDropdownOpened: vi.fn(),
      onDropdownClosed: vi.fn(),
   };
}

async function renderSlideOut(
   props: Record<string, unknown> = {},
   dialogService = makeDialogServiceMock(),
   dropdownObserver = makeDropdownObserverMock(),
   renderer?: Partial<Renderer2>,
) {
   const rendererMock = {
      listen: vi.fn(() => vi.fn()),
      setStyle: vi.fn(),
      ...renderer,
   };
   const result = await render(SlideOutComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: DialogService, useValue: dialogService },
         { provide: DropdownObserver, useValue: dropdownObserver },
         { provide: Renderer2, useValue: rendererMock },
      ],
      componentProperties: {
         keyboard: true,
         title: "Panel Title",
         ...props,
      },
   });
   return {
      comp: result.fixture.componentInstance,
      dialogService,
      dropdownObserver,
      renderer: rendererMock,
      fixture: result.fixture,
   };
}

describe("SlideOutComponent — escKey — dismiss guard [Group 1, Risk 3]", () => {

   it("should dismiss with ESC reason when keyboard enabled and no popups open", async () => {
      const { comp } = await renderSlideOut();
      const emitSpy = vi.spyOn(comp.dismiss, "emit");
      const event = { defaultPrevented: false };

      comp.escKey(event);

      expect(emitSpy).toHaveBeenCalledWith(ModalDismissReasons.ESC);
   });

   it("should not dismiss when event.defaultPrevented is true", async () => {
      const { comp } = await renderSlideOut();
      const emitSpy = vi.spyOn(comp.dismiss, "emit");

      comp.escKey({ defaultPrevented: true });

      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should not dismiss when dialogService has open popups", async () => {
      const dialogService = makeDialogServiceMock({ openPopups: 1 });
      const { comp } = await renderSlideOut({}, dialogService);
      const emitSpy = vi.spyOn(comp.dismiss, "emit");

      comp.escKey({ defaultPrevented: false });

      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should not dismiss when keyboard input is disabled", async () => {
      const { comp } = await renderSlideOut({ keyboard: false });
      const emitSpy = vi.spyOn(comp.dismiss, "emit");

      comp.escKey({ defaultPrevented: false });

      expect(emitSpy).not.toHaveBeenCalled();
   });
});

describe("SlideOutComponent — expand/collapse — dropdown observer [Group 2, Risk 2]", () => {

   it("should notify dropdown opened on ngAfterViewInit", async () => {
      const dropdownObserver = makeDropdownObserverMock();
      await renderSlideOut({}, makeDialogServiceMock(), dropdownObserver);

      expect(dropdownObserver.onDropdownOpened).toHaveBeenCalled();
   });

   it("should toggle open state and notify observer", async () => {
      const dropdownObserver = makeDropdownObserverMock();
      const { comp } = await renderSlideOut({}, makeDialogServiceMock(), dropdownObserver);
      comp.open = true;

      comp.toggle();

      expect(comp.open).toBe(false);
      expect(dropdownObserver.onDropdownClosed).toHaveBeenCalled();

      comp.toggle();

      expect(comp.open).toBe(true);
      expect(dropdownObserver.onDropdownOpened).toHaveBeenCalledTimes(2);
   });

   it("should call onDropdownOpened only once when setExpanded(true) is repeated", async () => {
      const dropdownObserver = makeDropdownObserverMock();
      const { comp } = await renderSlideOut({}, makeDialogServiceMock(), dropdownObserver);
      comp.open = false;
      dropdownObserver.onDropdownOpened.mockClear();

      comp.setExpanded(true);
      comp.setExpanded(true);

      expect(comp.open).toBe(true);
      expect(dropdownObserver.onDropdownOpened).toHaveBeenCalledTimes(1);
   });

   it("should call onDropdownClosed when collapsing via setExpanded(false)", async () => {
      const dropdownObserver = makeDropdownObserverMock();
      const { comp } = await renderSlideOut({}, makeDialogServiceMock(), dropdownObserver);
      comp.open = true;
      dropdownObserver.onDropdownClosed.mockClear();

      comp.setExpanded(false);

      expect(comp.open).toBe(false);
      expect(dropdownObserver.onDropdownClosed).toHaveBeenCalledTimes(1);
   });
});

describe("SlideOutComponent — ngOnDestroy — observer cleanup [Group 3, Risk 2]", () => {

   it("should close dropdown observer when destroyed while open", async () => {
      const dropdownObserver = makeDropdownObserverMock();
      const { comp } = await renderSlideOut({}, makeDialogServiceMock(), dropdownObserver);
      comp.open = true;

      comp.ngOnDestroy();

      expect(dropdownObserver.onDropdownClosed).toHaveBeenCalled();
   });

   it("should not close dropdown observer when already collapsed", async () => {
      const dropdownObserver = makeDropdownObserverMock();
      const { comp } = await renderSlideOut({}, makeDialogServiceMock(), dropdownObserver);
      comp.open = false;
      dropdownObserver.onDropdownClosed.mockClear();

      comp.ngOnDestroy();

      expect(dropdownObserver.onDropdownClosed).not.toHaveBeenCalled();
   });
});

describe("SlideOutComponent — display helpers [Group 4, Risk 1]", () => {

   it("should raise z-index when setOnTop(true)", async () => {
      const { comp } = await renderSlideOut();

      comp.setOnTop(true);

      expect(comp.zIndex).toBe(10490);
      expect(comp.isOnTop()).toBe(true);

      comp.setOnTop(false);

      expect(comp.zIndex).toBe(10480);
      expect(comp.isOnTop()).toBe(false);
   });

   it("should return modal size class from size input", async () => {
      const { comp } = await renderSlideOut({ size: "lg" });

      expect(comp.sizeClass).toBe("modal-lg");
   });

   it("should replace object id in title via renameTitle", async () => {
      const { comp } = await renderSlideOut({ title: "Edit Chart1 properties" });

      comp.renameTitle("Chart1", "Chart2");

      expect(comp.title).toBe("Edit Chart2 properties");
   });

   it("should enforce minWidth in actualWidth getter", async () => {
      const { comp } = await renderSlideOut();
      comp.explicitWidth = 50;

      expect(comp.actualWidth).toBe(200);

      comp.explicitWidth = 300;

      expect(comp.actualWidth).toBe(300);
   });

   it("should not mutate title when newObjectId is empty", async () => {
      const { comp } = await renderSlideOut({ title: "Edit Chart1 properties" });

      comp.renameTitle("Chart1", "");

      expect(comp.title).toBe("Edit Chart1 properties");
   });

   it("should delegate slideout stack operations to DialogService", async () => {
      const dialogService = makeDialogServiceMock();
      const { comp } = await renderSlideOut({}, dialogService);

      comp.dismissAll();
      comp.showSlideout(2);
      comp.getCurrentSlideouts();
      comp.isSlideoutOnTop(0);

      expect(dialogService.dismissAllSlideouts).toHaveBeenCalled();
      expect(dialogService.showSlideout).toHaveBeenCalledWith(2);
      expect(dialogService.getCurrentSlideOuts).toHaveBeenCalled();
      expect(dialogService.isSlideoutOnTop).toHaveBeenCalledWith(0);
   });
});

describe("SlideOutComponent — startResize — document listener cleanup [Group 5, Risk 3]", () => {

   // 🔁 Regression-sensitive: leaked mousemove listeners cause jank on every pointer move in the app
   it("should detach document mousemove and mouseup listeners when resize ends", async () => {
      const cleanupMove = vi.fn();
      const cleanupUp = vi.fn();
      const { comp } = await renderSlideOut();
      const listen = vi.spyOn(comp["renderer"], "listen")
         .mockReturnValueOnce(cleanupMove)
         .mockReturnValueOnce(cleanupUp);
      comp.contentContainer = {
         nativeElement: { getBoundingClientRect: () => ({ width: 280 }) },
      } as typeof comp.contentContainer;

      comp.startResize({ preventDefault: vi.fn(), pageX: 40 } as unknown as MouseEvent);

      expect(listen).toHaveBeenCalledTimes(2);
      const mouseupHandler = listen.mock.calls.find(([, event]) => event === "mouseup")?.[2];
      expect(mouseupHandler).toBeTypeOf("function");
      mouseupHandler!({} as MouseEvent);

      expect(cleanupMove).toHaveBeenCalled();
      expect(cleanupUp).toHaveBeenCalled();
   });
});
