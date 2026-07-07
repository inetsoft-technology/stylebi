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
 * VSImage — Pass 3: Display
 *
 * Direct instantiation (no ATL render) — pure conditional/display computation methods.
 *
 *   Group 1 [Risk 3] — getSrc: 3-branch URL construction (layout image / image-hash /
 *                       assembly image) + loading flag side effect
 *   Group 2 [Risk 2] — getOpacity: data-tip/pop-component alpha override vs normal alpha
 *   Group 3 [Risk 2] — onImageLoad: scaleImage/noImageFlag size computation, preserveAspectRatio
 *                       scaling, border subtraction
 *   Group 4 [Risk 2] — hasPopComponentChange: popComponent value diffing
 *   Group 5 [Risk 1] — isForceTab: composer-context boolean getter
 *   Group 6 [Risk 1] — finishLoad: loading cleared + change detection
 *   Group 7 [Risk 1] — isPopupOrDataTipSource: pop-source boolean getter
 *   Group 8 [Risk 3] — Legacy DOM regressions ported verbatim from vs-image.component.spec.ts
 *                       (bugs #17807, #17228, #20755): uses a real TestBed render (not direct
 *                       instantiation) because these assertions are about real template output
 *                       (ancestor CSS overflow, actions.clickAction -> real HyperlinkService ->
 *                       window.open, shadow CSS class) that cannot be observed through
 *                       method-level unit tests on a directly-instantiated component.
 *
 * Out of scope this pass: ngOnInit, ngOnChanges, ngOnDestroy, clicked, onAssemblyActionEvent —
 * covered in vs-image.component.interaction.tl.spec.ts.
 *
 * Not ported from vs-image.component.spec.ts: the two it.skip cases (bug #19028/#20250 alpha,
 * bug #20479 border) — already inactive in the old file, so there is no active coverage to
 * preserve.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { TestUtils } from "../../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { ViewDataService } from "../../../../viewer/services/view-data.service";
import { FixedDropdownService } from "../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../../widget/services/debounce.service";
import { ModelService } from "../../../../widget/services/model.service";
import { DefaultScaleService } from "../../../../widget/services/scale/default-scale-service";
import { ScaleService } from "../../../../widget/services/scale/scale-service";
import { AssemblyActionFactory } from "../../../action/assembly-action-factory.service";
import { ImageActions } from "../../../action/image-actions";
import { ContextProvider, ViewerContextProviderFactory } from "../../../context-provider.service";
import { ViewsheetInfo } from "../../../data/viewsheet-info";
import { RichTextService } from "../../../dialog/rich-text-dialog/rich-text.service";
import { LinkType } from "../../../../common/data/hyperlink-model";
import { ShowHyperlinkService } from "../../../show-hyperlink.service";
import { VSImageModel } from "../../../model/output/vs-image-model";
import { VSAnnotation } from "../../annotation/vs-annotation.component";
import { DataTipService } from "../../data-tip/data-tip.service";
import { PopComponentService } from "../../data-tip/pop-component.service";
import { createImageComponent } from "./vs-image.component.test-helpers";
import { VSImage } from "./vs-image.component";

declare const window;

afterEach(() => vi.restoreAllMocks());

describe("VSImage — Pass 3: Display", () => {

   // ── Group 1 — getSrc ──────────────────────────────────────────────────────
   describe("Group 1 — getSrc", () => {
      it("should build a layout image URL when the layout is focused and the region matches", () => {
         const { comp, viewsheetClient } = createImageComponent({
            viewsheetClient: {
               sendEvent: vi.fn(), runtimeId: "Viewsheet1", isLayoutFocused: true,
            },
            model: { absoluteName: "CONTENT_Image1" } as any,
         });
         comp.layoutName = "Layout1";
         comp.layoutRegion = "CONTENT";

         const src = comp.getSrc();

         expect(src).toBe(
            "/link/getLayoutImage/Layout1/CONTENT/Viewsheet1/CONTENT_Image1/200/100?1");
      });

      it("should build a hash-based image URL when imageHash is set", () => {
         const { comp } = createImageComponent({
            model: { imageHash: "abc123" } as any,
         });

         const src = comp.getSrc();

         expect(src).toBe("/link//getImageFromHash/Viewsheet1/abc123");
      });

      it("should build the default assembly image URL when not layout-focused and no imageHash", () => {
         const { comp } = createImageComponent();

         const src = comp.getSrc();

         expect(src).toBe("/link/getAssemblyImage/Viewsheet1/Image1/200/100?1");
      });

      it("should set loading=true when the src changes and the image is not a placeholder", () => {
         const { comp } = createImageComponent();
         comp.loading = false;

         comp.getSrc();

         expect(comp.loading).toBe(true);
      });

      it("should not set loading when noImageFlag is true", () => {
         const { comp } = createImageComponent({ model: { noImageFlag: true } as any });
         comp.loading = false;

         comp.getSrc();

         expect(comp.loading).toBe(false);
      });

      it("should not toggle loading again when the src is unchanged", () => {
         const { comp } = createImageComponent();
         comp.getSrc();
         comp.loading = false;

         comp.getSrc();

         expect(comp.loading).toBe(false);
      });
   });

   // ── Group 2 — getOpacity ──────────────────────────────────────────────────
   describe("Group 2 — getOpacity", () => {
      it("should return the objectFormat alpha when the assembly is a data tip and in preview", () => {
         const { comp, dataTipService } = createImageComponent({
            contextProvider: { viewer: false, preview: true, composer: false, binding: false },
         });
         dataTipService.isDataTip.mockReturnValue(true);
         comp.model.objectFormat.alpha = 0.3;
         comp.model.alpha = "0.6";

         expect(comp.getOpacity()).toBe(0.3);
      });

      it("should return the objectFormat alpha when the assembly is a pop component and in viewer", () => {
         const { comp, popComponentService } = createImageComponent({
            contextProvider: { viewer: true, preview: false, composer: false, binding: false },
         });
         popComponentService.isPopComponent.mockReturnValue(true);
         comp.model.objectFormat.alpha = 0.3;
         comp.model.alpha = "0.6";

         expect(comp.getOpacity()).toBe(0.3);
      });

      it("should return the model alpha when the assembly is neither a data tip nor a pop component", () => {
         const { comp } = createImageComponent();
         comp.model.objectFormat.alpha = 0.3;
         comp.model.alpha = "0.6";

         expect(comp.getOpacity()).toBe("0.6");
      });

      it("should return the model alpha when a data tip but not in viewer or preview", () => {
         const { comp, dataTipService } = createImageComponent({
            contextProvider: { viewer: false, preview: false, composer: false, binding: false },
         });
         dataTipService.isDataTip.mockReturnValue(true);
         comp.model.objectFormat.alpha = 0.3;
         comp.model.alpha = "0.6";

         expect(comp.getOpacity()).toBe("0.6");
      });
   });

   // ── Group 3 — onImageLoad ─────────────────────────────────────────────────
   describe("Group 3 — onImageLoad", () => {
      it("should reset imageSize to null dimensions and finish loading when neither scaleImage nor noImageFlag are set", () => {
         const { comp } = createImageComponent({
            model: { scaleInfo: { scaleImage: false, tiled: false, preserveAspectRatio: false } } as any,
         });

         comp.onImageLoad();

         expect(comp.imageSize).toEqual({ width: null, height: null });
         expect(comp.loading).toBe(false);
      });

      it("should compute a border-adjusted size when scaleImage is true", () => {
         const { comp } = createImageComponent({
            model: { scaleInfo: { scaleImage: true, tiled: false, preserveAspectRatio: false } } as any,
         });
         comp.model.objectFormat.width = 200;
         comp.model.objectFormat.height = 100;
         comp.model.objectFormat.border = {
            left: "3px solid #000", right: "2px solid #000",
            top: "1px solid #000", bottom: "2px solid #000",
         };

         comp.onImageLoad();

         expect(comp.imageSize).toEqual({ width: 195, height: 97 });
      });

      it("should scale imageSize by the natural image aspect ratio when preserveAspectRatio is set", () => {
         const { comp } = createImageComponent({
            model: {
               scaleInfo: { scaleImage: true, tiled: false, preserveAspectRatio: true },
            } as any,
         });
         (comp as any).imageElement = { nativeElement: { width: 100, height: 100 } };

         comp.onImageLoad();

         expect(comp.imageSize).toEqual({ width: 100, height: 100 });
      });

      it("should compute a sized dimension when noImageFlag is true even if scaleImage is false", () => {
         const { comp } = createImageComponent({
            model: {
               noImageFlag: true,
               scaleInfo: { scaleImage: false, tiled: false, preserveAspectRatio: false },
            } as any,
         });

         comp.onImageLoad();

         expect(comp.imageSize).toEqual({ width: 200, height: 100 });
      });
   });

   // ── Group 4 — hasPopComponentChange ───────────────────────────────────────
   describe("Group 4 — hasPopComponentChange", () => {
      it("should return true when the popComponent value changed", () => {
         const { comp } = createImageComponent();

         expect(comp.hasPopComponentChange({
            currentValue: { popComponent: "Popup1" }, previousValue: { popComponent: "Popup2" },
         } as any)).toBe(true);
      });

      it("should return false when the popComponent value is unchanged", () => {
         const { comp } = createImageComponent();

         expect(comp.hasPopComponentChange({
            currentValue: { popComponent: "Popup1" }, previousValue: { popComponent: "Popup1" },
         } as any)).toBe(false);
      });

      it("should return true when popComponent changed from a value to undefined", () => {
         const { comp } = createImageComponent();

         expect(comp.hasPopComponentChange({
            currentValue: {}, previousValue: { popComponent: "Popup1" },
         } as any)).toBe(true);
      });
   });

   // ── Group 5 — isForceTab ──────────────────────────────────────────────────
   describe("Group 5 — isForceTab", () => {
      it("should return true when in composer context", () => {
         const { comp } = createImageComponent({
            contextProvider: { viewer: false, preview: false, composer: true, binding: false },
         });

         expect(comp.isForceTab()).toBe(true);
      });

      it("should return false when not in composer context", () => {
         const { comp } = createImageComponent({
            contextProvider: { viewer: true, preview: false, composer: false, binding: false },
         });

         expect(comp.isForceTab()).toBe(false);
      });
   });

   // ── Group 6 — finishLoad ──────────────────────────────────────────────────
   describe("Group 6 — finishLoad", () => {
      it("should clear the loading flag and trigger change detection", () => {
         const { comp, changeDetectorRef } = createImageComponent();
         comp.loading = true;

         comp.finishLoad();

         expect(comp.loading).toBe(false);
         expect(changeDetectorRef.detectChanges).toHaveBeenCalled();
      });
   });

   // ── Group 7 — isPopupOrDataTipSource ──────────────────────────────────────
   // Bypass: isPopupOrDataTipSource is `protected` with no public caller exercised in this
   // pass, so both tests below cast to `any` to invoke it directly.
   describe("Group 7 — isPopupOrDataTipSource", () => {
      it("should return true when the assembly is the current pop source", () => {
         const { comp, popComponentService } = createImageComponent();
         popComponentService.isPopSource.mockReturnValue(true);

         expect((comp as any).isPopupOrDataTipSource()).toBe(true);
      });

      it("should return false when the assembly is not the current pop source", () => {
         const { comp, popComponentService } = createImageComponent();
         popComponentService.isPopSource.mockReturnValue(false);

         expect((comp as any).isPopupOrDataTipSource()).toBe(false);
      });
   });

   // ── Group 8 — Legacy DOM regressions ported from vs-image.component.spec.ts ─
   describe("Group 8 — legacy DOM regressions", () => {
      function renderRealComponent(model: VSImageModel, actions: ImageActions = null) {
         const viewsheetClient: any = { sendEvent: vi.fn() };
         viewsheetClient.runtimeId = "Viewsheet1";
         const dataTipService = { isDataTip: vi.fn().mockReturnValue(false) };
         const router: any = { navigate: vi.fn(), events: new Subject<any>() };

         TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, VSImage, VSAnnotation],
            schemas: [NO_ERRORS_SCHEMA],
            providers: [
               { provide: ViewsheetClientService, useValue: viewsheetClient },
               { provide: NgbModal, useValue: { open: vi.fn() } },
               { provide: FixedDropdownService, useValue: { open: vi.fn() } },
               { provide: AssemblyActionFactory, useValue: { createActions: vi.fn() } },
               { provide: ContextProvider, useValue: { viewer: true, composer: false, preview: false, binding: false } },
               { provide: DataTipService, useValue: dataTipService },
               { provide: ModelService, useValue: { sendModel: vi.fn() } },
               { provide: ScaleService, useClass: DefaultScaleService },
               { provide: Router, useValue: router },
               ShowHyperlinkService, DebounceService, ViewDataService, AppInfoService,
               PopComponentService,
               { provide: RichTextService, useValue: { showAnnotationDialog: vi.fn() } },
            ],
         });
         TestBed.compileComponents();

         const fixture = TestBed.createComponent(VSImage);

         if(actions) {
            fixture.componentInstance.actions = actions;
         }

         fixture.componentInstance.model = model;
         fixture.componentInstance.vsInfo = new ViewsheetInfo([], "/link/");
         fixture.detectChanges();
         return fixture;
      }

      function makeBaseModel(): VSImageModel {
         return Object.assign(
            { hyperlinks: [], noImageFlag: false, alpha: "0.6" },
            TestUtils.createMockVSImageModel("Image1")) as VSImageModel;
      }

      // Bug #17807
      it("should not have annotations with ancestor elements that hide overflow", () => {
         const model = makeBaseModel();
         model.assemblyAnnotationModels = [
            <any> TestUtils.createMockVSObjectModel("VSAnnotation", "mockAnnotation"),
         ];

         const fixture = renderRealComponent(model);
         let debugElement = fixture.debugElement.query(By.directive(VSAnnotation));
         expect(debugElement).not.toBeNull();

         while(debugElement && debugElement.nativeElement) {
            if(debugElement.nativeElement instanceof Element) {
               const style = window.getComputedStyle(debugElement.nativeElement);

               if(style != null && Object.keys(style).length > 0) {
                  expect(style.getPropertyValue("overflow")).not.toBe("hidden");
               }
            }

            debugElement = debugElement.parent;
         }
      });

      // Bug #17228 — the "image show-hyperlink" click action routes through the real
      // ShowHyperlinkService/HyperlinkService chain, so this needs a real TestBed render
      // rather than the mocked hyperlinkService used in the interaction pass.
      it("should open hyperlink with self target in same window", () => {
         const oldOpen = window.open;

         try {
            const model = makeBaseModel();
            model.hyperlinks = [{
               name: "Home", label: "Home", linkType: LinkType.WEB_LINK,
               link: "http://www.inetsoft.com", targetFrame: "self", query: null,
               wsIdentifier: null, tooltip: null, bookmarkName: null, bookmarkUser: null,
               parameterValues: [], sendReportParameters: false, sendSelectionParameters: false,
               disablePrompting: false,
            }];
            const actions = new ImageActions(model, ViewerContextProviderFactory(false));

            renderRealComponent(model, actions);
            window.open = vi.fn();

            const action = actions.clickAction;
            expect(action).toBeTruthy();
            expect(action.id()).toBe("image show-hyperlink");
            action.action(null);

            expect(window.open).toHaveBeenCalledWith("http://www.inetsoft.com/", "self");
         }
         finally {
            window.open = oldOpen;
         }
      });

      it("should open hyperlink with non-self target in new window", () => {
         const oldOpen = window.open;

         try {
            const model = makeBaseModel();
            model.hyperlinks = [{
               name: "Home", label: "Home", linkType: LinkType.WEB_LINK,
               link: "http://www.inetsoft.com", targetFrame: "inetsoft", query: null,
               wsIdentifier: null, tooltip: null, bookmarkName: null, bookmarkUser: null,
               parameterValues: [], sendReportParameters: false, sendSelectionParameters: false,
               disablePrompting: false,
            }];
            const actions = new ImageActions(model, ViewerContextProviderFactory(false));

            renderRealComponent(model, actions);
            window.open = vi.fn();

            const action = actions.clickAction;
            expect(action).toBeTruthy();
            expect(action.id()).toBe("image show-hyperlink");
            action.action(null);

            expect(window.open).toHaveBeenCalledWith("http://www.inetsoft.com/", "inetsoft");
         }
         finally {
            window.open = oldOpen;
         }
      });

      // Bug #20755
      it("should apply the shadow CSS class when shadow and animateGif are both set", () => {
         const model = makeBaseModel();
         model.shadow = true;
         model.animateGif = true;
         model.noImageFlag = false;

         const fixture = renderRealComponent(model);
         const image = fixture.nativeElement.querySelector("img.image-content");

         expect(image.classList).toContain("image-content-shadow");
      });

      it("should not apply the shadow CSS class when animateGif is false", () => {
         const model = makeBaseModel();
         model.shadow = true;
         model.animateGif = false;
         model.noImageFlag = false;

         const fixture = renderRealComponent(model);
         const image = fixture.nativeElement.querySelector("img.image-content");

         expect(image.classList).not.toContain("image-content-shadow");
      });
   });
});
