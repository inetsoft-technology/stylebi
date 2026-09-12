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
 * VSAnnotation — single pass
 *
 * Risk-first coverage:
 *   Group 3 [Risk 3] — actions setter: unsubscribe/resubscribe race, edit/remove/format dispatch,
 *                       dialog initialContent guard
 *   Group 7 [Risk 3] — isLineInContainer(): tether/restrict bounds math, overflowXTetherHidden
 *                       clamp, additionalRestriction intersection
 *   Group 2 [Risk 2] — model setter: content sanitization + contentPadding derivation guards
 *   Group 6 [Risk 2] — checkOverflow(): DOM-derived state, change-gated detectChanges
 *   Group 8 [Risk 2] — updateAnnotation(): event fields, Math.max(10, ...) clamp
 *   Group 10 [Risk 2] — onRectangleDragMove/onRectangleResizeMove: scale math, edges branch
 *   Group 11 [Risk 2] — onLineEndDragMove/onLineEndDragEnd: scale math, event fields
 *   Group 12 [Risk 2] — getSrc(): URL construction
 *   Group 1 [Risk 1] — constructor: scale subscription
 *   Group 4 [Risk 1] — mouseSelectAnnotation (HostListener): emit
 *   Group 5 [Risk 1] — ngOnDestroy: subscription cleanup
 *   Group 9 [Risk 1] — toggleAnnotationStatus(): event status
 *   Group 13 [Risk 1] — isViewsheetAnnotation()/isAssemblyAnnotation(): bidirectional type dispatch
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope this pass:
 *   getContainerBounds() Element branch — jsdom's getBoundingClientRect() always returns an
 *     all-zero rect; Group 7 covers the Rectangular-object branch (the one actually used by
 *     production callers restrictTo/tetherTo/additionalRestriction in this component's own
 *     call sites) and one stubbed-getBoundingClientRect case for the Element branch.
 *   getContentPadding() — private helper with no independent entry point; exercised via the
 *     model setter (Group 2).
 *   openFormatDialog() — private helper with no independent entry point; exercised via the
 *     actions setter "annotation format" branch (Group 3).
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { DomSanitizer } from "@angular/platform-browser";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of, Subject } from "rxjs";
import { VSAnnotation } from "./vs-annotation.component";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { ContextProvider } from "../../context-provider.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { RichTextService } from "../../dialog/rich-text-dialog/rich-text.service";
import { TestUtils } from "../../../common/test/test-utils";
import { Tool } from "../../../../../../shared/util/tool";
import { VSAnnotationModel } from "../../model/annotation/vs-annotation-model";
import { AnnotatableActions } from "../../action/annotatable-actions";
import { AssemblyActionEvent } from "../../../common/action/assembly-action-event";
import { Rectangle } from "../../../common/data/rectangle";

const commandsSubject = new Subject<any>();
const clientServiceMock = {
   sendEvent: vi.fn(),
   runtimeId: "vs-test",
   commands: commandsSubject,
};
const domSanitizerMock = {
   bypassSecurityTrustHtml: vi.fn((html: string) => html as any),
};
const scaleSubject = new Subject<number>();
const scaleServiceMock = {
   getScale: vi.fn(() => scaleSubject.asObservable()),
};
const richTextServiceMock = {
   showAnnotationDialog: vi.fn(),
};
const dataTipServiceMock = {
   isDataTip: vi.fn().mockReturnValue(false),
   hasDataTipShowing: vi.fn().mockReturnValue(false),
};

function makeContextProvider(overrides: Partial<Record<string, boolean>> = {}) {
   return { viewer: true, preview: false, composer: false, binding: false, embedAssembly: false, ...overrides };
}

function makeModel(overrides: Partial<VSAnnotationModel> = {}): VSAnnotationModel {
   return Object.assign(TestUtils.createMockVSAnnotationModel("Annotation1"), overrides);
}

function makeActions(): AnnotatableActions {
   return { onAssemblyActionEvent: new Subject<any>() } as any;
}

async function renderComponent(props: Partial<VSAnnotation> = {}, contextOverrides: Record<string, boolean> = {}) {
   // Same pattern as vs-viewsheet.component.tl.spec.ts: skip componentProperties and assign
   // directly on the instance after render(). `model` here IS declared on VSAnnotation's own
   // prototype (not inherited), so the ATL inherited-accessor shadow bug does not apply — but
   // VSAnnotation also has no ngOnChanges hook, so there is no auto-invoke risk either way.
   // Kept consistent for safety and readability across the two files.
   const { fixture } = await render(VSAnnotation, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ViewsheetClientService, useValue: clientServiceMock },
         { provide: DomSanitizer, useValue: domSanitizerMock },
         { provide: NgbModal, useValue: {} },
         { provide: ScaleService, useValue: scaleServiceMock },
         { provide: ContextProvider, useValue: makeContextProvider(contextOverrides) },
         { provide: DataTipService, useValue: dataTipServiceMock },
         { provide: RichTextService, useValue: richTextServiceMock },
      ],
   });
   const comp = fixture.componentInstance as VSAnnotation;
   Object.assign(comp, { model: makeModel(), ...props });
   return { comp, fixture };
}

beforeEach(() => {
   vi.clearAllMocks();
   scaleServiceMock.getScale.mockImplementation(() => scaleSubject.asObservable());
   dataTipServiceMock.isDataTip.mockReturnValue(false);
   dataTipServiceMock.hasDataTipShowing.mockReturnValue(false);
   domSanitizerMock.bypassSecurityTrustHtml.mockImplementation((html: string) => html as any);
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: constructor — scale subscription [Risk 1]
// ---------------------------------------------------------------------------

describe("VSAnnotation — constructor", () => {
   // Bypass: `scale` is a private field with no public getter; the scale subscription's only
   // observable effect is on other methods' internal math (Groups 7/10/11), so this test reads
   // it directly to verify the subscription wiring itself in isolation.
   it("should update the internal scale when the scale service emits", async () => {
      const { comp } = await renderComponent();
      scaleSubject.next(2);
      expect((comp as any).scale).toBe(2);
      scaleSubject.next(0.5);
      expect((comp as any).scale).toBe(0.5);
   });
});

// ---------------------------------------------------------------------------
// Group 2: model setter [Risk 2]
// ---------------------------------------------------------------------------

describe("VSAnnotation — model setter", () => {
   it("should sanitize and store content when contentModel is present", async () => {
      const { comp } = await renderComponent();
      const model = makeModel({ contentModel: { content: "<b>hi</b>" } as any });
      comp.model = model;
      expect(domSanitizerMock.bypassSecurityTrustHtml).toHaveBeenCalledWith("<b>hi</b>");
      expect(comp.content).toBe("<b>hi</b>");
   });

   it("should NOT touch content when contentModel is absent", async () => {
      const { comp } = await renderComponent();
      comp.content = "previous" as any;
      domSanitizerMock.bypassSecurityTrustHtml.mockClear();
      const model = makeModel({ contentModel: null });
      comp.model = model;
      expect(domSanitizerMock.bypassSecurityTrustHtml).not.toHaveBeenCalled();
      expect(comp.content).toBe("previous");
   });

   it("should compute contentPadding from annotationRectangleModel.roundCornerValue when present", async () => {
      const { comp } = await renderComponent();
      const rect = TestUtils.createMockVSRectangleModel("rec1");
      rect.roundCornerValue = 10;
      const model = makeModel({ annotationRectangleModel: rect });
      comp.model = model;
      const expected = (10 / 2) * (1 - Math.sin(Math.PI / 4)) + 3;
      expect(comp.contentPadding).toBeCloseTo(expected, 10);
   });

   it("should NOT touch contentPadding when annotationRectangleModel is absent", async () => {
      const { comp } = await renderComponent();
      comp.contentPadding = 99;
      const model = makeModel({ annotationRectangleModel: null });
      comp.model = model;
      expect(comp.contentPadding).toBe(99);
   });

   it("should always store the model on the backing field", async () => {
      const { comp } = await renderComponent();
      const model = makeModel({ absoluteName: "Annotation2" });
      comp.model = model;
      expect(comp.model).toBe(model);
   });
});

// ---------------------------------------------------------------------------
// Group 3: actions setter [Risk 3]
// ---------------------------------------------------------------------------

describe("VSAnnotation — actions setter", () => {
   it("should ignore a null assignment and keep the previous subscription untouched", async () => {
      const { comp } = await renderComponent();
      const actions = makeActions();
      comp.actions = actions;
      const before = (comp as any).actionSubscription;
      comp.actions = null;
      expect((comp as any).actionSubscription).toBe(before);
      expect(before.closed).toBe(false);
   });

   it("should unsubscribe the previous action subscription when reassigned (no stale-event leak)", async () => {
      const { comp } = await renderComponent({ model: makeModel({ absoluteName: "Annotation1" }) });
      const firstActions = makeActions();
      comp.actions = firstActions;
      const secondActions = makeActions();
      comp.actions = secondActions;

      const removed: any[] = [];
      comp.remove.subscribe(v => removed.push(v));
      // Old subscription must be dead — an event on the FIRST actions object must not fire.
      (firstActions.onAssemblyActionEvent as Subject<any>).next(
         new AssemblyActionEvent("annotation remove" + comp.getAssemblyName(), comp.model)
      );
      expect(removed).toHaveLength(0);

      (secondActions.onAssemblyActionEvent as Subject<any>).next(
         new AssemblyActionEvent("annotation remove" + comp.getAssemblyName(), comp.model)
      );
      expect(removed).toHaveLength(1);
   });

   it("should emit remove with the model on an 'annotation remove<assemblyName>' event", async () => {
      const { comp } = await renderComponent({ model: makeModel({ absoluteName: "Annotation1" }) });
      const actions = makeActions();
      comp.actions = actions;
      const removed: any[] = [];
      comp.remove.subscribe(v => removed.push(v));

      (actions.onAssemblyActionEvent as Subject<any>).next(
         new AssemblyActionEvent("annotation remove" + comp.getAssemblyName(), comp.model)
      );

      expect(removed).toEqual([comp.model]);
   });

   it("should send OpenAnnotationFormatDialogEvent on an 'annotation format<assemblyName>' event", async () => {
      const { comp } = await renderComponent({ model: makeModel({ absoluteName: "Annotation1" }) });
      const actions = makeActions();
      comp.actions = actions;

      (actions.onAssemblyActionEvent as Subject<any>).next(
         new AssemblyActionEvent("annotation format" + comp.getAssemblyName(), comp.model)
      );

      expect(clientServiceMock.sendEvent).toHaveBeenCalledWith(
         "/events/annotation/open-format-dialog", expect.objectContaining({ name: "Annotation1" })
      );
   });

   it("should do nothing when the event id matches neither edit, remove, nor format", async () => {
      const { comp } = await renderComponent({ model: makeModel({ absoluteName: "Annotation1" }) });
      const actions = makeActions();
      comp.actions = actions;
      const removed: any[] = [];
      comp.remove.subscribe(v => removed.push(v));

      (actions.onAssemblyActionEvent as Subject<any>).next(
         new AssemblyActionEvent("some other event", comp.model)
      );

      expect(removed).toHaveLength(0);
      expect(clientServiceMock.sendEvent).not.toHaveBeenCalled();
   });

   it("should open the rich text dialog with the edit callback and rectangle background on an 'annotation edit<assemblyName>' event", async () => {
      const rect = TestUtils.createMockVSRectangleModel("rec1");
      rect.objectFormat.background = "#ff0000";
      const model = makeModel({ absoluteName: "Annotation1", annotationRectangleModel: rect });
      const { comp } = await renderComponent({ model });
      const dialogSubject = new Subject<any>();
      richTextServiceMock.showAnnotationDialog.mockReturnValue(dialogSubject.asObservable());
      const actions = makeActions();
      comp.actions = actions;

      (actions.onAssemblyActionEvent as Subject<any>).next(
         new AssemblyActionEvent("annotation edit" + comp.getAssemblyName(), comp.model)
      );

      expect(richTextServiceMock.showAnnotationDialog).toHaveBeenCalledWith(
         expect.any(Function), "#ff0000"
      );
   });

   it("should set dialog.initialContent when the model has a contentModel", async () => {
      const model = makeModel({
         absoluteName: "Annotation1",
         contentModel: { content: "existing text" } as any
      });
      const { comp } = await renderComponent({ model });
      const dialogSubject = new Subject<any>();
      richTextServiceMock.showAnnotationDialog.mockReturnValue(dialogSubject.asObservable());
      const actions = makeActions();
      comp.actions = actions;

      (actions.onAssemblyActionEvent as Subject<any>).next(
         new AssemblyActionEvent("annotation edit" + comp.getAssemblyName(), comp.model)
      );
      const dialog: any = {};
      dialogSubject.next(dialog);

      expect(dialog.initialContent).toBe("existing text");
   });

   it("should NOT set dialog.initialContent when the model has no contentModel", async () => {
      const model = makeModel({ absoluteName: "Annotation1", contentModel: null });
      const { comp } = await renderComponent({ model });
      const dialogSubject = new Subject<any>();
      richTextServiceMock.showAnnotationDialog.mockReturnValue(dialogSubject.asObservable());
      const actions = makeActions();
      comp.actions = actions;

      (actions.onAssemblyActionEvent as Subject<any>).next(
         new AssemblyActionEvent("annotation edit" + comp.getAssemblyName(), comp.model)
      );
      const dialog: any = {};
      dialogSubject.next(dialog);

      expect(dialog.initialContent).toBeUndefined();
   });

   it("should call updateAnnotation with the dialog content when the edit callback fires", async () => {
      const model = makeModel({ absoluteName: "Annotation1" });
      const { comp } = await renderComponent({ model });
      let capturedCallback: (content: string) => void;
      richTextServiceMock.showAnnotationDialog.mockImplementation((onCommit: any) => {
         capturedCallback = onCommit;
         return of({} as any);
      });
      const actions = makeActions();
      comp.actions = actions;

      (actions.onAssemblyActionEvent as Subject<any>).next(
         new AssemblyActionEvent("annotation edit" + comp.getAssemblyName(), comp.model)
      );
      capturedCallback("new content");

      expect(clientServiceMock.sendEvent).toHaveBeenCalledWith(
         "/events/annotation/update-annotation", expect.anything()
      );
   });
});

// ---------------------------------------------------------------------------
// Group 4: mouseSelectAnnotation (HostListener) [Risk 1]
// ---------------------------------------------------------------------------

describe("VSAnnotation — mouseSelectAnnotation", () => {
   it("should emit mouseSelect with [model, event] on click", async () => {
      const { comp } = await renderComponent();
      const emitted: any[] = [];
      comp.mouseSelect.subscribe(v => emitted.push(v));
      const event = new MouseEvent("click");

      comp.mouseSelectAnnotation(event);

      expect(emitted).toEqual([[comp.model, event]]);
   });

   it("should emit mouseSelect with [model, event] on contextmenu", async () => {
      const { comp } = await renderComponent();
      const emitted: any[] = [];
      comp.mouseSelect.subscribe(v => emitted.push(v));
      const event = new MouseEvent("contextmenu");

      comp.mouseSelectAnnotation(event);

      expect(emitted).toEqual([[comp.model, event]]);
   });
});

// ---------------------------------------------------------------------------
// Group 5: ngOnDestroy [Risk 1]
// ---------------------------------------------------------------------------

describe("VSAnnotation — ngOnDestroy", () => {
   // Bypass: scaleSubscription/actionSubscription are private fields with no public accessor —
   // the only way to verify ngOnDestroy() actually unsubscribes both is to grab the live
   // Subscription objects directly and spy on their unsubscribe() methods.
   it("should unsubscribe the scale and action subscriptions when destroyed", async () => {
      const { comp, fixture } = await renderComponent();
      comp.actions = makeActions();
      const scaleSub = (comp as any).scaleSubscription;
      const actionSub = (comp as any).actionSubscription;
      const scaleUnsubSpy = vi.spyOn(scaleSub, "unsubscribe");
      const actionUnsubSpy = vi.spyOn(actionSub, "unsubscribe");

      fixture.destroy();

      expect(scaleUnsubSpy).toHaveBeenCalled();
      expect(actionUnsubSpy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6: checkOverflow() [Risk 2]
// ---------------------------------------------------------------------------

// Bypass: `changeRef` (ChangeDetectorRef) is a private field with no public accessor; spying on
// it directly is the only way to verify checkOverflow() requests change detection without
// letting a real detectChanges() re-render the template (see per-it comments on why that crashes).
describe("VSAnnotation — checkOverflow", () => {
   function stubContentContainer(comp: VSAnnotation, innerText: string, height: number) {
      (comp as any).contentContainer = {
         nativeElement: {
            innerText,
            getBoundingClientRect: () => ({ height } as DOMRect)
         }
      };
   }

   it("should set isOverflowing=true and call detectChanges when content exceeds the rectangle height", async () => {
      const rect = TestUtils.createMockVSRectangleModel("rec1");
      rect.objectFormat.height = 20;
      const { comp } = await renderComponent({ model: makeModel({ annotationRectangleModel: rect }) });
      stubContentContainer(comp, "long overflowing text", 50);
      // mockImplementation suppresses the real detectChanges() — a real call re-renders the
      // template, which needs InteractService (wInteractable) that this test module doesn't
      // provide. The test only cares whether detectChanges was requested, not its side effects.
      const detectChangesSpy = vi.spyOn((comp as any).changeRef, "detectChanges").mockImplementation(() => {});

      comp.checkOverflow();

      expect(comp.isOverflowing).toBe(true);
      expect(detectChangesSpy).toHaveBeenCalled();
   });

   it("should stay isOverflowing=false when content fits within the rectangle height", async () => {
      const rect = TestUtils.createMockVSRectangleModel("rec1");
      rect.objectFormat.height = 100;
      const { comp } = await renderComponent({ model: makeModel({ annotationRectangleModel: rect }) });
      stubContentContainer(comp, "short text", 50);
      // mockImplementation suppresses the real detectChanges() — a real call re-renders the
      // template, which needs InteractService (wInteractable) that this test module doesn't
      // provide. The test only cares whether detectChanges was requested, not its side effects.
      const detectChangesSpy = vi.spyOn((comp as any).changeRef, "detectChanges").mockImplementation(() => {});

      comp.checkOverflow();

      expect(comp.isOverflowing).toBe(false);
      expect(detectChangesSpy).not.toHaveBeenCalled();
   });

   // `contentContainer.innerText && contentContainer.innerText.trim() && height < ...` — the
   // first operand's own falsy path (empty/null innerText) is distinct from the second operand's
   // falsy path (whitespace-only, tested below): an empty string never reaches `.trim()` at all,
   // so this also proves the first guard alone is sufficient to short-circuit.
   it("should treat empty innerText as not overflowing regardless of height", async () => {
      const rect = TestUtils.createMockVSRectangleModel("rec1");
      rect.objectFormat.height = 1;
      const { comp } = await renderComponent({ model: makeModel({ annotationRectangleModel: rect }) });
      stubContentContainer(comp, "", 500);

      comp.checkOverflow();

      expect(comp.isOverflowing).toBe(false);
   });

   it("should treat whitespace-only innerText as not overflowing regardless of height", async () => {
      const rect = TestUtils.createMockVSRectangleModel("rec1");
      rect.objectFormat.height = 1;
      const { comp } = await renderComponent({ model: makeModel({ annotationRectangleModel: rect }) });
      stubContentContainer(comp, "   ", 500);

      comp.checkOverflow();

      expect(comp.isOverflowing).toBe(false);
   });

   it("should NOT call detectChanges when isOverflowing does not change", async () => {
      const rect = TestUtils.createMockVSRectangleModel("rec1");
      rect.objectFormat.height = 100;
      const { comp } = await renderComponent({ model: makeModel({ annotationRectangleModel: rect }) });
      stubContentContainer(comp, "short text", 50);
      comp.checkOverflow();
      // mockImplementation suppresses the real detectChanges() — a real call re-renders the
      // template, which needs InteractService (wInteractable) that this test module doesn't
      // provide. The test only cares whether detectChanges was requested, not its side effects.
      const detectChangesSpy = vi.spyOn((comp as any).changeRef, "detectChanges").mockImplementation(() => {});

      comp.checkOverflow();

      expect(detectChangesSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 7: isLineInContainer() [Risk 3]
// ---------------------------------------------------------------------------

describe("VSAnnotation — isLineInContainer", () => {
   async function setupWithScale(scale = 1) {
      const { comp } = await renderComponent();
      scaleSubject.next(scale);
      return comp;
   }

   it("should return true when the model has no annotationLineModel", async () => {
      const comp = await setupWithScale();
      comp.model = makeModel({ annotationLineModel: null });
      expect(comp.isLineInContainer()).toBe(true);
   });

   it("should return true when tetherTo is not set", async () => {
      const comp = await setupWithScale();
      comp.restrictTo = { x: 0, y: 0, width: 100, height: 100 };
      comp.tetherTo = undefined;
      expect(comp.isLineInContainer()).toBe(true);
   });

   it("should return true when restrictTo is not set", async () => {
      const comp = await setupWithScale();
      comp.tetherTo = { x: 0, y: 0, width: 100, height: 100 };
      comp.restrictTo = undefined;
      expect(comp.isLineInContainer()).toBe(true);
   });

   it("should return true when the scaled line endpoint is within the restrictTo bounds", async () => {
      const comp = await setupWithScale(1);
      comp.tetherTo = { x: 0, y: 0, width: 0, height: 0 };
      comp.restrictTo = { x: 0, y: 0, width: 100, height: 100 };
      comp.model.annotationLineModel.objectFormat.left = 50;
      comp.model.annotationLineModel.objectFormat.top = 50;
      expect(comp.isLineInContainer()).toBe(true);
   });

   it("should return false when the scaled line endpoint is outside the restrictTo bounds", async () => {
      const comp = await setupWithScale(1);
      comp.tetherTo = { x: 0, y: 0, width: 0, height: 0 };
      comp.restrictTo = { x: 0, y: 0, width: 100, height: 100 };
      comp.model.annotationLineModel.objectFormat.left = 200;
      comp.model.annotationLineModel.objectFormat.top = 50;
      expect(comp.isLineInContainer()).toBe(false);
   });

   it("should apply offsetX/offsetY before checking containment", async () => {
      const comp = await setupWithScale(1);
      comp.tetherTo = { x: 0, y: 0, width: 0, height: 0 };
      comp.restrictTo = { x: 0, y: 0, width: 100, height: 100 };
      comp.model.annotationLineModel.objectFormat.left = 90;
      comp.offsetX = 20; // 90 + 20 = 110, outside the 100-wide bound
      expect(comp.isLineInContainer()).toBe(false);
   });

   it("should shift the restrictTo bounds by restrictXAdjust", async () => {
      const comp = await setupWithScale(1);
      comp.tetherTo = { x: 0, y: 0, width: 0, height: 0 };
      comp.restrictTo = { x: 100, y: 0, width: 100, height: 100 };
      comp.model.annotationLineModel.objectFormat.left = 50;
      // Without adjustment 50 is outside [100,200]; adjust shifts the bound left to [0,100].
      comp.restrictXAdjust = -100;
      expect(comp.isLineInContainer()).toBe(true);
   });

   it("should clamp the restrictTo width to the tether's right edge when overflowXTetherHidden is true", async () => {
      const comp = await setupWithScale(1);
      comp.tetherTo = { x: 0, y: 0, width: 50, height: 100 };
      comp.restrictTo = { x: 0, y: 0, width: 100, height: 100 };
      comp.model.annotationLineModel.objectFormat.left = 70;
      comp.overflowXTetherHidden = true;
      expect(comp.isLineInContainer()).toBe(false);
   });

   it("should NOT clamp the restrictTo width when overflowXTetherHidden is false (same geometry)", async () => {
      const comp = await setupWithScale(1);
      comp.tetherTo = { x: 0, y: 0, width: 50, height: 100 };
      comp.restrictTo = { x: 0, y: 0, width: 100, height: 100 };
      comp.model.annotationLineModel.objectFormat.left = 70;
      comp.overflowXTetherHidden = false;
      expect(comp.isLineInContainer()).toBe(true);
   });

   it("should return false when contained in restrictTo but not in additionalRestriction", async () => {
      const comp = await setupWithScale(1);
      comp.tetherTo = { x: 0, y: 0, width: 0, height: 0 };
      comp.restrictTo = { x: 0, y: 0, width: 100, height: 100 };
      comp.additionalRestriction = { x: 0, y: 0, width: 10, height: 10 };
      comp.model.annotationLineModel.objectFormat.left = 50;
      expect(comp.isLineInContainer()).toBe(false);
   });

   it("should return true when contained in both restrictTo and additionalRestriction", async () => {
      const comp = await setupWithScale(1);
      comp.tetherTo = { x: 0, y: 0, width: 0, height: 0 };
      comp.restrictTo = { x: 0, y: 0, width: 100, height: 100 };
      comp.additionalRestriction = { x: 0, y: 0, width: 100, height: 100 };
      comp.model.annotationLineModel.objectFormat.left = 50;
      expect(comp.isLineInContainer()).toBe(true);
   });

   it("should use getBoundingClientRect() when restrictTo is a real DOM Element", async () => {
      const comp = await setupWithScale(1);
      comp.tetherTo = { x: 0, y: 0, width: 0, height: 0 };
      const el = document.createElement("div");
      vi.spyOn(el, "getBoundingClientRect").mockReturnValue(
         { left: 0, top: 0, width: 100, height: 100 } as DOMRect
      );
      comp.restrictTo = el;
      comp.model.annotationLineModel.objectFormat.left = 50;
      expect(comp.isLineInContainer()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 8: updateAnnotation() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSAnnotation — updateAnnotation", () => {
   it("should send an update event with the current rectangle bounds and content", async () => {
      const rect = TestUtils.createMockVSRectangleModel("rec1");
      rect.objectFormat.left = 10;
      rect.objectFormat.top = 20;
      rect.objectFormat.width = 30;
      rect.objectFormat.height = 40;
      const { comp } = await renderComponent({
         model: makeModel({ absoluteName: "Annotation1", annotationRectangleModel: rect })
      });

      comp.updateAnnotation(new Rectangle(0, 0, 0, 0), "new content");

      expect(clientServiceMock.sendEvent).toHaveBeenCalledWith(
         "/events/annotation/update-annotation",
         expect.objectContaining({
            name: "Annotation1",
            content: "new content",
            newBounds: expect.objectContaining({ x: 10, y: 20, width: 30, height: 40 })
         })
      );
   });

   it("should clamp width and height up to a minimum of 10", async () => {
      const rect = TestUtils.createMockVSRectangleModel("rec1");
      rect.objectFormat.width = 2;
      rect.objectFormat.height = 3;
      const { comp } = await renderComponent({ model: makeModel({ annotationRectangleModel: rect }) });

      comp.updateAnnotation(new Rectangle(0, 0, 0, 0));

      expect(clientServiceMock.sendEvent).toHaveBeenCalledWith(
         "/events/annotation/update-annotation",
         expect.objectContaining({ newBounds: expect.objectContaining({ width: 10, height: 10 }) })
      );
   });

   it("should NOT clamp width and height when already above the minimum", async () => {
      const rect = TestUtils.createMockVSRectangleModel("rec1");
      rect.objectFormat.width = 50;
      rect.objectFormat.height = 60;
      const { comp } = await renderComponent({ model: makeModel({ annotationRectangleModel: rect }) });

      comp.updateAnnotation(new Rectangle(0, 0, 0, 0));

      expect(clientServiceMock.sendEvent).toHaveBeenCalledWith(
         "/events/annotation/update-annotation",
         expect.objectContaining({ newBounds: expect.objectContaining({ width: 50, height: 60 }) })
      );
   });
});

// ---------------------------------------------------------------------------
// Group 9: toggleAnnotationStatus() [Risk 1]
// ---------------------------------------------------------------------------

describe("VSAnnotation — toggleAnnotationStatus", () => {
   it("should send a ToggleAnnotationStatusEvent with status=true", async () => {
      const { comp } = await renderComponent();
      comp.toggleAnnotationStatus();
      expect(clientServiceMock.sendEvent).toHaveBeenCalledWith(
         "/events/annotation/toggle-status", expect.objectContaining({ status: true })
      );
   });
});

// ---------------------------------------------------------------------------
// Group 10: onRectangleDragMove / onRectangleResizeMove [Risk 2]
// ---------------------------------------------------------------------------

describe("VSAnnotation — onRectangleDragMove", () => {
   it("should move the rectangle by dx/dy scaled by the current scale", async () => {
      const rect = TestUtils.createMockVSRectangleModel("rec1");
      rect.objectFormat.left = 10;
      rect.objectFormat.top = 20;
      const { comp } = await renderComponent({ model: makeModel({ annotationRectangleModel: rect }) });
      scaleSubject.next(2);

      comp.onRectangleDragMove({ dx: 10, dy: 20 });

      expect(rect.objectFormat.left).toBe(15); // 10 + 10/2
      expect(rect.objectFormat.top).toBe(30);  // 20 + 20/2
   });
});

describe("VSAnnotation — onRectangleResizeMove", () => {
   it("should grow width/height by the scaled delta without translating when resizing from bottom-right", async () => {
      const rect = TestUtils.createMockVSRectangleModel("rec1");
      rect.objectFormat.left = 10;
      rect.objectFormat.top = 20;
      rect.objectFormat.width = 50;
      rect.objectFormat.height = 60;
      const { comp } = await renderComponent({ model: makeModel({ annotationRectangleModel: rect }) });
      scaleSubject.next(2);

      comp.onRectangleResizeMove({
         deltaRect: { width: 10, height: 20, top: 4, left: 6 },
         edges: { top: false, left: false }
      });

      expect(rect.objectFormat.width).toBe(55);  // 50 + 10/2
      expect(rect.objectFormat.height).toBe(70); // 60 + 20/2
      expect(rect.objectFormat.left).toBe(10);   // unchanged
      expect(rect.objectFormat.top).toBe(20);    // unchanged
   });

   it("should also translate top when resizing from the top edge", async () => {
      const rect = TestUtils.createMockVSRectangleModel("rec1");
      rect.objectFormat.left = 10;
      rect.objectFormat.top = 20;
      rect.objectFormat.width = 50;
      rect.objectFormat.height = 60;
      const { comp } = await renderComponent({ model: makeModel({ annotationRectangleModel: rect }) });
      scaleSubject.next(2);

      comp.onRectangleResizeMove({
         deltaRect: { width: 10, height: 20, top: 4, left: 0 },
         edges: { top: true, left: false }
      });

      expect(rect.objectFormat.top).toBe(22); // 20 + 4/2
      expect(rect.objectFormat.left).toBe(10); // left edge not active — unchanged
   });

   it("should also translate left when resizing from the left edge", async () => {
      const rect = TestUtils.createMockVSRectangleModel("rec1");
      rect.objectFormat.left = 10;
      rect.objectFormat.top = 20;
      rect.objectFormat.width = 50;
      rect.objectFormat.height = 60;
      const { comp } = await renderComponent({ model: makeModel({ annotationRectangleModel: rect }) });
      scaleSubject.next(2);

      comp.onRectangleResizeMove({
         deltaRect: { width: 10, height: 20, top: 0, left: 6 },
         edges: { top: false, left: true }
      });

      expect(rect.objectFormat.left).toBe(13); // 10 + 6/2
      expect(rect.objectFormat.top).toBe(20);  // top edge not active — unchanged
   });
});

// ---------------------------------------------------------------------------
// Group 11: onLineEndDragMove / onLineEndDragEnd [Risk 2]
// ---------------------------------------------------------------------------

describe("VSAnnotation — onLineEndDragMove", () => {
   it("should move the line endpoint by dx/dy scaled by the current scale", async () => {
      const line = TestUtils.createMockVSLineModel("line1");
      line.endLeft = 5;
      line.endTop = 8;
      const { comp } = await renderComponent({ model: makeModel({ annotationLineModel: line }) });
      scaleSubject.next(2);

      comp.onLineEndDragMove({ dx: 10, dy: 20 });

      expect(line.endLeft).toBe(10); // 5 + 10/2
      expect(line.endTop).toBe(18);  // 8 + 20/2
   });
});

describe("VSAnnotation — onLineEndDragEnd", () => {
   it("should send an update-annotation-endpoint event with the absolute endpoint position", async () => {
      const line = TestUtils.createMockVSLineModel("line1");
      line.endLeft = 5;
      line.endTop = 8;
      line.objectFormat.left = 100;
      line.objectFormat.top = 200;
      const { comp } = await renderComponent({
         model: makeModel({ absoluteName: "Annotation1", annotationLineModel: line })
      });

      comp.onLineEndDragEnd(new Rectangle(0, 0, 0, 0));

      expect(clientServiceMock.sendEvent).toHaveBeenCalledWith(
         "/events/annotation/update-annotation-endpoint",
         expect.objectContaining({ newBounds: expect.objectContaining({ x: 105, y: 208 }) })
      );
   });
});

// ---------------------------------------------------------------------------
// Group 12: getSrc() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSAnnotation — getSrc", () => {
   it("should build the assembly image URL from vsInfo.linkUri, runtimeId, and model dimensions", async () => {
      const rect = TestUtils.createMockVSRectangleModel("Rect1");
      const model = makeModel({ annotationRectangleModel: rect });
      model.objectFormat.width = 30;
      model.objectFormat.height = 40;
      model.genTime = 12345;
      const { comp } = await renderComponent({ model, vsInfo: { linkUri: "/link/" } as any });

      const src = comp.getSrc();

      const expected = "/link/getAssemblyImage" +
         "/" + Tool.byteEncode(clientServiceMock.runtimeId) +
         "/" + Tool.byteEncode(rect.absoluteName) +
         "/30/40?12345";
      expect(src).toBe(expected);
   });
});

// ---------------------------------------------------------------------------
// Group 13: isViewsheetAnnotation / isAssemblyAnnotation [Risk 1]
// ---------------------------------------------------------------------------

describe("VSAnnotation — isViewsheetAnnotation / isAssemblyAnnotation", () => {
   it("should report viewsheet-only for annotationType=VIEWSHEET (0)", async () => {
      const { comp } = await renderComponent({ model: makeModel({ annotationType: 0 }) });
      expect(comp.isViewsheetAnnotation()).toBe(true);
      expect(comp.isAssemblyAnnotation()).toBe(false);
   });

   it("should report assembly-only for annotationType=ASSEMBLY (1)", async () => {
      const { comp } = await renderComponent({ model: makeModel({ annotationType: 1 }) });
      expect(comp.isViewsheetAnnotation()).toBe(false);
      expect(comp.isAssemblyAnnotation()).toBe(true);
   });

   it("should report neither for annotationType=DATA (2)", async () => {
      const { comp } = await renderComponent({ model: makeModel({ annotationType: 2 }) });
      expect(comp.isViewsheetAnnotation()).toBe(false);
      expect(comp.isAssemblyAnnotation()).toBe(false);
   });
});
