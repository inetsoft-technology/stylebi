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
 * VSImage — Pass 1: Interaction
 *
 * Direct instantiation (no ATL render) — all 11 constructor deps mocked; component issues
 * no HTTP calls of its own. Covers lifecycle hooks, the click flow, and action dispatch not
 * covered by vs-image.component.spec.ts (hyperlink click routing, annotation overflow, shadow
 * CSS class, alpha binding).
 *
 *   Group 1 [Risk 2] — ngOnInit: opacity/src init, finishLoad(tiled+loading) branch, debounce key
 *   Group 2 [Risk 2] — modelChanged tooltip resolution (customTooltipString > hyperlink tooltip >
 *                       defaultAnnotationContent > empty), exercised via ngOnInit
 *   Group 3 [Risk 2] — ngOnChanges: popComponent onClickFlagged registration + debounce
 *                       cancellation when absoluteName changes
 *   Group 4 [Risk 3] — clicked(): popLocation always set; toggle/sendEvent gated on
 *                       viewer + popComponent + !isDataTip; clickHyperlink always invoked
 *   Group 5 [Risk 2] — onAssemblyActionEvent: annotate / show-hyperlink / show-format-pane
 *                       switch dispatch
 *   Group 6 [Risk 1] — ngOnDestroy: debounce cancel with correct key
 *
 * Out of scope this pass: getSrc, getOpacity, onImageLoad, hasPopComponentChange (dedicated
 * unit), isForceTab, finishLoad (dedicated unit), isPopupOrDataTipSource — covered in
 * vs-image.component.display.tl.spec.ts.
 */

import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { VSImageModel } from "../../../model/output/vs-image-model";
import {
   createImageComponent,
   makeModelChange,
   makeMockImageModel,
} from "./vs-image.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("VSImage — Pass 1: Interaction", () => {

   // ── Group 1 — ngOnInit ────────────────────────────────────────────────────
   describe("Group 1 — ngOnInit", () => {
      it("should initialize opacity and src from the model", () => {
         const { comp } = createImageComponent();

         comp.ngOnInit();

         expect(comp.opacity).toBe(comp.model.alpha);
         expect(comp.src).toContain("getAssemblyImage");
      });

      it("should finish loading when the image is tiled and still loading", () => {
         const { comp, changeDetectorRef } = createImageComponent({
            model: { scaleInfo: { tiled: true, scaleImage: false, preserveAspectRatio: false } },
         });

         comp.ngOnInit();

         expect(comp.loading).toBe(false);
         expect(changeDetectorRef.detectChanges).toHaveBeenCalled();
      });

      it("should leave loading unresolved when the image is not tiled", () => {
         const { comp } = createImageComponent({
            model: { scaleInfo: { tiled: false, scaleImage: false, preserveAspectRatio: false } },
         });

         comp.ngOnInit();

         // getSrc() sets loading=true (oldSrc changed, not noImageFlag, linkUri present);
         // finishLoad() is only reached on the tiled branch so loading stays true here.
         expect(comp.loading).toBe(true);
      });

      it("should debounce the image-resize recompute with the assembly-derived key", () => {
         const { comp, debounceService } = createImageComponent();

         comp.ngOnInit();

         expect(debounceService.debounce).toHaveBeenCalledWith(
            "image-resize-Image1", expect.any(Function), 200, []);
      });
   });

   // ── Group 2 — modelChanged tooltip resolution (via ngOnInit) ────────────────
   describe("Group 2 — tooltip resolution", () => {
      it("should use customTooltipString when tooltipVisible and a custom string is set", () => {
         const { comp } = createImageComponent({
            model: { tooltipVisible: true, customTooltipString: "Custom Tip" },
         });

         comp.ngOnInit();

         expect(comp.tooltip).toBe("Custom Tip");
      });

      it("should fall back to the first hyperlink tooltip when no custom string is set", () => {
         const { comp } = createImageComponent({
            model: {
               tooltipVisible: true,
               customTooltipString: null,
               hyperlinks: [{ tooltip: "Link Tip" } as any],
            },
         });

         comp.ngOnInit();

         expect(comp.tooltip).toBe("Link Tip");
      });

      it("should fall back to defaultAnnotationContent when no custom or hyperlink tooltip is set", () => {
         const { comp } = createImageComponent({
            model: {
               tooltipVisible: true,
               customTooltipString: null,
               hyperlinks: [],
               defaultAnnotationContent: "Annotation content",
            } as any,
         });

         comp.ngOnInit();

         expect(comp.tooltip).toBe("Annotation content");
      });

      it("should clear the tooltip when tooltipVisible is false", () => {
         const { comp } = createImageComponent({
            model: { tooltipVisible: false, customTooltipString: "Custom Tip" },
         });

         comp.ngOnInit();

         expect(comp.tooltip).toBe("");
      });
   });

   // ── Group 3 — ngOnChanges ─────────────────────────────────────────────────
   describe("Group 3 — ngOnChanges", () => {
      it("should register onClickFlagged when popComponent changes to a new truthy value", () => {
         const { comp, popComponentService } = createImageComponent();
         const prev = makeMockImageModel({ popComponent: null } as any);
         const curr = { ...comp.model, popComponent: "Popup1" } as VSImageModel;

         comp.ngOnChanges({ model: makeModelChange(prev, curr) });

         expect(popComponentService.registerOnClickFlagged).toHaveBeenCalledWith("Popup1");
      });

      it("should not register onClickFlagged when the popComponent value is unchanged", () => {
         const { comp, popComponentService } = createImageComponent();
         const prev = makeMockImageModel({ popComponent: "Popup1" } as any);
         const curr = { ...comp.model, popComponent: "Popup1" } as VSImageModel;

         comp.ngOnChanges({ model: makeModelChange(prev, curr) });

         expect(popComponentService.registerOnClickFlagged).not.toHaveBeenCalled();
      });

      it("should not register onClickFlagged when the new popComponent value is falsy", () => {
         const { comp, popComponentService } = createImageComponent();
         const prev = makeMockImageModel({ popComponent: "Popup1" } as any);
         const curr = { ...comp.model, popComponent: null } as VSImageModel;

         comp.ngOnChanges({ model: makeModelChange(prev, curr) });

         expect(popComponentService.registerOnClickFlagged).not.toHaveBeenCalled();
      });

      it("should cancel the previous debounce key when absoluteName changes between updates", () => {
         const { comp, debounceService } = createImageComponent();
         const prev = makeMockImageModel({ absoluteName: "OldImage" } as any);
         const curr = { ...comp.model, absoluteName: "Image1" } as VSImageModel;

         comp.ngOnChanges({ model: makeModelChange(prev, curr) });

         expect(debounceService.cancel).toHaveBeenCalledWith("image-resize-OldImage");
      });
   });

   // ── Group 4 — clicked() ───────────────────────────────────────────────────
   describe("Group 4 — clicked", () => {
      function mockClickEvent(overrides: Partial<MouseEvent> = {}): MouseEvent {
         return {
            clientX: 10, clientY: 20, offsetX: 5, offsetY: 7, button: 0,
            stopPropagation: vi.fn(), ...overrides,
         } as unknown as MouseEvent;
      }

      it("should always set the pop location on click", () => {
         const { comp, popComponentService } = createImageComponent({
            model: { popLocation: "CENTER" as any },
         });

         comp.clicked(mockClickEvent());

         expect(popComponentService.setPopLocation).toHaveBeenCalledWith("CENTER");
      });

      it("should toggle the pop component and send an onclick event when in viewer with popComponent set and not a data tip", () => {
         const { comp, popComponentService, viewsheetClient } = createImageComponent({
            contextProvider: { viewer: true, preview: false, composer: false, binding: false },
            model: { popComponent: "Popup1", popAlpha: 0.5 } as any,
         });

         comp.clicked(mockClickEvent());

         expect(popComponentService.toggle).toHaveBeenCalledWith(
            "Popup1", 10, 20, 0.5, "Image1");
         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith("/events/onclick/Image1/5/7");
      });

      it("should not toggle or send a click event when not in viewer", () => {
         const { comp, popComponentService, viewsheetClient } = createImageComponent({
            contextProvider: { viewer: false, preview: false, composer: false, binding: false },
            model: { popComponent: "Popup1" } as any,
         });

         comp.clicked(mockClickEvent());

         expect(popComponentService.toggle).not.toHaveBeenCalled();
         expect(viewsheetClient.sendEvent).not.toHaveBeenCalled();
      });

      it("should not toggle when the model has no popComponent", () => {
         const { comp, popComponentService } = createImageComponent({
            model: { popComponent: null } as any,
         });

         comp.clicked(mockClickEvent());

         expect(popComponentService.toggle).not.toHaveBeenCalled();
      });

      it("should not toggle when the current assembly is an active data tip", () => {
         const { comp, popComponentService, dataTipService } = createImageComponent({
            model: { popComponent: "Popup1" } as any,
         });
         dataTipService.isDataTip.mockReturnValue(true);

         comp.clicked(mockClickEvent());

         expect(popComponentService.toggle).not.toHaveBeenCalled();
      });

      it("should always invoke clickHyperlink regardless of viewer state", () => {
         const { comp } = createImageComponent({
            contextProvider: { viewer: false, preview: false, composer: false, binding: false },
         });
         const spy = vi.spyOn(comp, "clickHyperlink").mockImplementation(() => {});

         const event = mockClickEvent();
         comp.clicked(event);

         expect(spy).toHaveBeenCalledWith(event);
      });
   });

   // ── Group 5 — onAssemblyActionEvent ───────────────────────────────────────
   describe("Group 5 — onAssemblyActionEvent", () => {
      // Bypass: onAssemblyActionEvent is `protected` (invoked in production via the
      // `actions.onAssemblyActionEvent` subscription set up by the `actions` setter); this
      // helper casts to `any` to dispatch it directly without wiring up a real actions object.
      function dispatch(comp: any, id: string, event: MouseEvent = null) {
         comp.onAssemblyActionEvent(new AssemblyActionEvent(id, comp.model, event));
      }

      it("should show the annotation dialog for 'image annotate'", () => {
         const { comp, richTextService } = createImageComponent();
         const event = { type: "click" } as MouseEvent;

         dispatch(comp, "image annotate", event);

         expect(richTextService.showAnnotationDialog).toHaveBeenCalled();
      });

      it("should show hyperlinks for 'image show-hyperlink'", () => {
         const { comp, hyperlinkService, dropdownService, viewsheetClient } = createImageComponent({
            contextProvider: { viewer: false, preview: false, composer: true, binding: false },
            model: { hyperlinks: [{ tooltip: "Home" } as any] },
         });
         const event = { type: "click" } as MouseEvent;

         dispatch(comp, "image show-hyperlink", event);

         expect(hyperlinkService.showHyperlinks).toHaveBeenCalledWith(
            event, comp.model.hyperlinks, dropdownService, "Viewsheet1", "/link/", true);
      });

      it("should emit onOpenFormatPane for 'image show-format-pane'", () => {
         const { comp } = createImageComponent();
         const emitted: VSImageModel[] = [];
         comp.onOpenFormatPane.subscribe(m => emitted.push(m));

         dispatch(comp, "image show-format-pane");

         expect(emitted).toEqual([comp.model]);
      });

      it("should do nothing for an unrecognized action id", () => {
         const { comp, richTextService, hyperlinkService } = createImageComponent();
         const emitted: VSImageModel[] = [];
         comp.onOpenFormatPane.subscribe(m => emitted.push(m));

         dispatch(comp, "image unknown-action");

         expect(richTextService.showAnnotationDialog).not.toHaveBeenCalled();
         expect(hyperlinkService.showHyperlinks).not.toHaveBeenCalled();
         expect(emitted).toEqual([]);
      });
   });

   // ── Group 6 — ngOnDestroy ─────────────────────────────────────────────────
   describe("Group 6 — ngOnDestroy", () => {
      it("should cancel the image-resize debounce key for the current assembly", () => {
         const { comp, debounceService } = createImageComponent();

         comp.ngOnDestroy();

         expect(debounceService.cancel).toHaveBeenCalledWith("image-resize-Image1");
      });
   });
});
