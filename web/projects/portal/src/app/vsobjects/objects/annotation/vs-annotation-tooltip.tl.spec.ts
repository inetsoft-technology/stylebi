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

/**
 * Bug #81352 — annotation tooltips must keep the annotation's own fill.
 *
 * The real components, TooltipDirective, TooltipService and TooltipComponent are used (and the
 * real DomSanitizer), so the tooltip DOM is what a user would see. The global scss is not loaded
 * by jsdom, so the fill is read from the inline style of the card the text sits in.
 *
 * Sites covered:
 *   A. overflow tooltip of a visible annotation (VSAnnotation)
 *   B. toggle-button tooltip of a hidden viewsheet annotation (VSAnnotation)
 *   C. hidden-annotation icon tooltip (VSHiddenAnnotation)
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";
import { VSAnnotation } from "./vs-annotation.component";
import { VSHiddenAnnotation } from "./vs-hidden-annotation.component";
import { TooltipDirective } from "../../../widget/tooltip/tooltip.directive";
import { OutOfZoneDirective } from "../../../widget/directive/out-of-zone.directive";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { ContextProvider } from "../../context-provider.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { RichTextService } from "../../dialog/rich-text-dialog/rich-text.service";
import { TestUtils } from "../../../common/test/test-utils";
import { VSAnnotationModel } from "../../model/annotation/vs-annotation-model";

const CONTENT = `<p><span style="color:#ffffff">white on navy</span></p>`;
const TEXT = "white on navy";
const NAVY = "#000080";
const NAVY_RGB = "rgb(0, 0, 128)";
const WHITE_RGB = "rgb(255, 255, 255)";

const clientServiceMock = {
   sendEvent: vi.fn(),
   runtimeId: "vs-test",
   commands: new Subject<any>(),
};
const scaleServiceMock = {
   getScale: vi.fn(() => new Subject<number>().asObservable()),
};
const dataTipServiceMock = {
   isDataTip: vi.fn().mockReturnValue(false),
   hasDataTipShowing: vi.fn().mockReturnValue(false),
};

function makeModel(background: string, overrides: Partial<VSAnnotationModel> = {})
   : VSAnnotationModel
{
   const model = Object.assign(TestUtils.createMockVSAnnotationModel("Annotation1"), overrides);
   model.visible = true;
   model.annotationLineModel = null;
   model.contentModel = { content: CONTENT } as any;
   model.annotationRectangleModel.objectFormat.background = background;
   model.annotationRectangleModel.objectFormat.height = 10;
   return model;
}

async function renderAnnotation(model: VSAnnotationModel) {
   const result = await render(VSAnnotation, {
      schemas: [NO_ERRORS_SCHEMA],
      // wInteractable needs an InteractService this test does not care about
      componentImports: [TooltipDirective, OutOfZoneDirective],
      providers: [
         { provide: ViewsheetClientService, useValue: clientServiceMock },
         { provide: NgbModal, useValue: {} },
         { provide: ScaleService, useValue: scaleServiceMock },
         { provide: ContextProvider, useValue: { viewer: true } },
         { provide: DataTipService, useValue: dataTipServiceMock },
         { provide: RichTextService, useValue: { showAnnotationDialog: vi.fn() } },
      ],
   });
   result.fixture.componentRef.setInput("model", model);
   result.fixture.detectChanges();
   return result;
}

function hover(host: Element): void {
   host.dispatchEvent(new MouseEvent("mouseenter", { bubbles: false }));
   host.dispatchEvent(new MouseEvent("mousemove", { bubbles: true, clientX: 5, clientY: 5 }));
}

// the tooltip opens after TooltipDirective.waitTime (500ms)
const waitForTooltip = () => new Promise<void>(resolve => setTimeout(resolve, 700));

/** The fill of the element the text sits in: first ancestor with a non-transparent background. */
function fillBehind(text: Element): string {
   for(let e = text as Element; e; e = e.parentElement) {
      const bg = getComputedStyle(e).backgroundColor;

      if(bg && bg !== "transparent" && bg !== "rgba(0, 0, 0, 0)") {
         return bg;
      }
   }

   return null;
}

function tooltipTexts(): Element[] {
   return Array.from(document.body.querySelectorAll(".tooltip-container"))
      .flatMap(c => Array.from(c.querySelectorAll("span, p, div")))
      .filter(e => e.children.length === 0 && e.textContent.trim() === TEXT);
}

afterEach(() => {
   document.body.querySelectorAll("w-tooltip").forEach(e => e.remove());
   vi.clearAllMocks();
});

async function overflowTooltip(background: string) {
   const { fixture } = await renderAnnotation(makeModel(background));
   const comp = fixture.componentInstance as VSAnnotation;
   // jsdom has no layout; make checkOverflow() see the content taller than the rectangle
   comp.contentContainer = {
      nativeElement: {
         innerText: TEXT,
         getBoundingClientRect: () => ({ height: 500 }),
      }
   } as any;
   comp.checkOverflow();
   expect(comp.isOverflowing).toBe(true);

   const host = fixture.nativeElement
      .querySelector(".vs-annotation__rectangle-content-padding-box");
   hover(host);
   await waitForTooltip();
   return fixture;
}

describe("Bug #81352 — VSAnnotation overflow tooltip (site A)", () => {
   it("draws the white inline text on the annotation's own fill", async () => {
      await overflowTooltip(NAVY);
      const texts = tooltipTexts();
      expect(texts).toHaveLength(1);
      expect(fillBehind(texts[0])).toBe(NAVY_RGB);
   });

   it("falls back to a white fill when the annotation has no background", async () => {
      await overflowTooltip(null);
      const texts = tooltipTexts();
      expect(texts).toHaveLength(1);
      expect(fillBehind(texts[0])).toBe(WHITE_RGB);
   });

   it("does not use an injected background value as a style declaration", async () => {
      await overflowTooltip("red; position: fixed; background-image: url(x)");
      const texts = tooltipTexts();
      expect(texts).toHaveLength(1);
      expect(fillBehind(texts[0])).toBe(WHITE_RGB);
      expect(document.body.querySelector("w-tooltip [style*='position']")).toBeNull();
   });

   // the server serializes the background as rgba(r,g,b,alpha)
   it.each([
      ["alpha 0 gives white, not a transparent card", "rgba(0,0,128,0)", WHITE_RGB],
      ["partial alpha is composited over white", "rgba(0,0,128,0.5)", "rgb(128, 128, 192)"],
      ["alpha 1 keeps the color", "rgba(0,0,128,1)", NAVY_RGB],
   ])("rgba fill: %s", async (_name, background, expected) => {
      await overflowTooltip(background);
      const texts = tooltipTexts();
      expect(texts).toHaveLength(1);
      expect(fillBehind(texts[0])).toBe(expected);
   });

   it("hidden-annotation tooltip composites an alpha 0 fill to white", async () => {
      const { fixture } = await render(VSHiddenAnnotation, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [{ provide: ViewsheetClientService, useValue: clientServiceMock }],
      });
      fixture.componentRef.setInput("annotations",
         [makeModel("rgba(0,0,128,0)", { hidden: true })]);
      fixture.detectChanges();
      hover(fixture.nativeElement.querySelector(".vs-annotation__hidden-icon"));
      await waitForTooltip();
      expect(fillBehind(tooltipTexts()[0])).toBe(WHITE_RGB);
   });

   it("shows no tooltip while the content is not overflowing", async () => {
      const { fixture } = await renderAnnotation(makeModel(NAVY));
      const host = fixture.nativeElement
         .querySelector(".vs-annotation__rectangle-content-padding-box");
      hover(host);
      await waitForTooltip();
      expect(document.body.querySelector("w-tooltip")).toBeNull();
   });
});

describe("Bug #81352 — VSAnnotation toggle-button tooltip (site B)", () => {
   async function toggleTooltip(background: string) {
      const model = makeModel(background, { hidden: true, annotationType: 0 });
      const { fixture } = await renderAnnotation(model);
      hover(fixture.nativeElement.querySelector("button.annotition-button"));
      await waitForTooltip();
   }

   it("draws the white inline text on the annotation's own fill", async () => {
      await toggleTooltip(NAVY);
      const texts = tooltipTexts();
      expect(texts).toHaveLength(1);
      expect(fillBehind(texts[0])).toBe(NAVY_RGB);
   });

   it("falls back to a white fill when the annotation has no background", async () => {
      await toggleTooltip("");
      const texts = tooltipTexts();
      expect(texts).toHaveLength(1);
      expect(fillBehind(texts[0])).toBe(WHITE_RGB);
   });
});

describe("Bug #81352 — VSHiddenAnnotation tooltip (site C)", () => {
   async function hiddenTooltip(annotations: VSAnnotationModel[]) {
      const { fixture } = await render(VSHiddenAnnotation, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [{ provide: ViewsheetClientService, useValue: clientServiceMock }],
      });
      fixture.componentRef.setInput("annotations", annotations);
      fixture.detectChanges();
      hover(fixture.nativeElement.querySelector(".vs-annotation__hidden-icon"));
      await waitForTooltip();
   }

   it("draws the author's inline text on the annotation's own fill", async () => {
      await hiddenTooltip([makeModel(NAVY, { hidden: true })]);
      const texts = tooltipTexts();
      expect(texts).toHaveLength(1);
      expect(fillBehind(texts[0])).toBe(NAVY_RGB);
      // the author's inline color survives (it is not the tooltip default)
      expect(getComputedStyle(texts[0]).color).toBe(WHITE_RGB);
   });

   it("falls back to a white fill when the annotation has no background", async () => {
      await hiddenTooltip([makeModel(undefined, { hidden: true })]);
      const texts = tooltipTexts();
      expect(texts).toHaveLength(1);
      expect(fillBehind(texts[0])).toBe(WHITE_RGB);
   });

   it("gives each hidden annotation its own card and fill", async () => {
      const second = makeModel("#ffff00", { hidden: true, absoluteName: "Annotation2" });
      second.contentModel = { content: `<p><span>plain yellow</span></p>` } as any;
      await hiddenTooltip([makeModel(NAVY, { hidden: true }), second]);
      expect(fillBehind(tooltipTexts()[0])).toBe(NAVY_RGB);
      const yellow = Array.from(document.body.querySelectorAll(".tooltip-container span"))
         .find(e => e.textContent === "plain yellow");
      expect(fillBehind(yellow)).toBe("rgb(255, 255, 0)");
   });

   it("does not show annotations that are not hidden, and no icon without any", async () => {
      const { fixture } = await render(VSHiddenAnnotation, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [{ provide: ViewsheetClientService, useValue: clientServiceMock }],
      });
      fixture.componentRef.setInput("annotations", [makeModel(NAVY, { hidden: false })]);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector(".vs-annotation__hidden-icon")).toBeNull();
   });
});
