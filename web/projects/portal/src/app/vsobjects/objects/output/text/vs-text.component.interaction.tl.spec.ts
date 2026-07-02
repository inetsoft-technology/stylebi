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
 * VSText – Single Pass: Interaction
 *
 * Coverage:
 *   Group 1  - ngOnInit registers message listener; ngOnDestroy flushes pending + removes listener;
 *              ngOnChanges: textChanged / changeHeightOfTextarea / registerOnClickFlagged / updateUrlText;
 *              action routing: annotate / show-hyperlink / show-format-pane;
 *              action subscription teardown; updateOnChange flush; selected=false deselect;
 *              shadowText class (TestBed DOM)
 *   Group 2  - changeText: model.text / debounce args; editable-layout sendEvent;
 *              clicked() side-effects: setPopLocation / toggle / clickHyperlink /
 *              changeHeightOfTextarea / sendEvent onclick;
 *              clickHyperlink: single-link path / stopPropagation path;
 *              onKeyDown nav; onEnterDown: preventDefault / sendChangeEvent /
 *              deselectAssembly / createdByDblClick / updateWidth;
 *              ForceEditModeCommand via commandsSubject
 *   Group 3  - tooltip priority: customTooltip / hyperlink / annotation / overflow / tooltipVisible=false /
 *              detectChanges call; getEllipsisText: null / html-entity / no-space / space;
 *              isHTMLContent; changeHeightOfTextarea; getContentSize; getAutoTextModel;
 *              getNoWrapMaxWidth; presenter; width; whiteSpace; isForceTab;
 *              isPopupOrDataTipSource; sendExternalUrls + processUpdateExternalUrlCommand
 *
 * Mocking strategy:
 *   - direct instantiation for the constructor-heavy component and inherited command processor
 *   - seeded ViewChild refs (objectContainer, objectContentTd, etc.) so tooltip, autosize,
 *     and iframe paths can run without render()
 *   - one TestBed fixture (Group 1 last test) for DOM-class assertion that needs change detection
 */

import { Component, Directive, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { By } from "@angular/platform-browser";
import { SecurityContext } from "@angular/core";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { Subject, of } from "rxjs";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { TestUtils } from "../../../../common/test/test-utils";
import { GuiTool } from "../../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { FixedDropdownService } from "../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../../widget/services/debounce.service";
import { ViewsheetInfo } from "../../../data/viewsheet-info";
import { ContextProvider } from "../../../context-provider.service";
import { PrintLayoutSection } from "../../../model/layout/print-layout-section";
import { VSTextModel } from "../../../model/output/vs-text-model";
import { ShowHyperlinkService } from "../../../show-hyperlink.service";
import { DataTipService } from "../../data-tip/data-tip.service";
import { PopComponentService } from "../../data-tip/pop-component.service";
import { RichTextService } from "../../../dialog/rich-text-dialog/rich-text.service";
import { VSText } from "./vs-text.component";

@Directive({ selector: "[VSDataTip]", standalone: true })
class VSDataTipStubDirective {
   @Input() dataTipName: string;
}

@Directive({ selector: "[VSPopComponent]", standalone: true })
class VSPopComponentStubDirective {
   @Input() popComponentName: string;
   @Input() popContainerName: string;
   @Input() popZIndex: number;
}

@Directive({ selector: "[outOfZone]", standalone: true })
class OutOfZoneStubDirective {
   @Output() onDocKeydown = new EventEmitter<KeyboardEvent>();
}

@Directive({ selector: "[wTooltip]", standalone: true })
class TooltipStubDirective {
   @Input() wTooltip: string;
}

@Directive({ selector: "[safeFont]", standalone: true })
class SafeFontStubDirective {
   @Input() safeFont: string;
}

@Component({ selector: "auto-complete-text", standalone: true, template: "" })
class AutoCompleteTextStubComponent {
   @Input() model: any;
   @Output() commitText = new EventEmitter<string>();
}

@Component({ selector: "vs-hidden-annotation", standalone: true, template: "" })
class VSHiddenAnnotationStubComponent {
   @Input() annotations: any;
}

@Component({ selector: "vs-annotation", standalone: true, template: "" })
class VSAnnotationStubComponent {
   @Input() actions: any;
   @Input() model: any;
   @Input() restrictTo: any;
   @Input() vsInfo: any;
   @Input() selected: boolean;
   @Output() remove = new EventEmitter<void>();
   @Output() mouseSelect = new EventEmitter<any>();
}

@Component({ selector: "vs-loading-display", standalone: true, template: "" })
class VSLoadingDisplayStubComponent {
   @Input() justShowIcon: boolean;
   @Input() allowInteraction: boolean;
   @Input() assemblyLoading: boolean;
}

function makeTextModel(overrides: Partial<VSTextModel> = {}): VSTextModel {
   const model = TestUtils.createMockVSTextModel("Text1");
   const format = TestUtils.createMockVSFormatModel();

   return Object.assign(model, {
      absoluteName: "Text1",
      container: "Container1",
      visible: true,
      enabled: true,
      text: "Hello world",
      shadow: false,
      autoSize: false,
      url: false,
      presenter: false,
      hyperlinks: [],
      customTooltipString: "",
      defaultAnnotationContent: "",
      tooltipVisible: true,
      popComponent: "",
      popLocation: "top",
      popAlpha: 0.6,
      hasOnClick: false,
      breakAll: false,
      parameters: ["${param1}"],
      selectedAnnotations: [],
      objectFormat: {
         ...format,
         width: 140,
         height: 40,
         top: 10,
         left: 15,
         foreground: "#111111",
         background: "#ffffff",
         lineHeight: 18,
         hAlign: "left",
         vAlign: "middle",
         decoration: "underline",
         zIndex: 3,
         font: "12px Arial",
         wrapping: {
            whiteSpace: "normal",
            wordWrap: "break-word",
         },
         border: {
            top: "1px solid #111111",
            right: "2px solid #222222",
            bottom: "3px solid #333333",
            left: "4px solid #444444",
         },
      },
   } as any, overrides) as VSTextModel;
}

function createTextActions(): any {
   return {
      onAssemblyActionEvent: new Subject<any>(),
      clickAction: { id: "click-action" },
   };
}

interface TextTestOverrides {
   model?: Partial<VSTextModel>;
   contextProvider?: any;
   viewsheetClient?: any;
   dataTipService?: any;
   popComponentService?: any;
   hyperlinkService?: any;
   richTextService?: any;
   domSanitizer?: any;
   debounceService?: any;
}

function createTextComponent(overrides: TextTestOverrides = {}) {
   // E6: expose commandsSubject so tests can dispatch via STOMP instead of calling
   // processXCommand methods directly
   const commandsSubject = new Subject<any>();
   const viewsheetClient = overrides.viewsheetClient ?? {
      sendEvent: vi.fn(),
      runtimeId: "viewsheet1",
      commands: commandsSubject.asObservable(),
   };
   const popComponentService = overrides.popComponentService ?? {
      setPopLocation: vi.fn(),
      toggle: vi.fn(),
      registerOnClickFlagged: vi.fn(),
      isPopSource: vi.fn().mockReturnValue(false),
   };
   const dataTipService = overrides.dataTipService ?? {
      isDataTip: vi.fn().mockReturnValue(false),
      isDataTipSource: vi.fn().mockReturnValue(false),
   };
   const modalService = { open: vi.fn() };
   const dropdownService = { open: vi.fn() };
   const contextProvider = overrides.contextProvider ?? {
      viewer: true,
      preview: false,
      binding: false,
      composer: false,
      vsWizard: false,
      vsWizardPreview: false,
      embedAssembly: false,
   };
   const changeDetectionRef = {
      detectChanges: vi.fn(),
      markForCheck: vi.fn(),
   };
   const hyperlinkService = overrides.hyperlinkService ?? {
      singleClick: true,
      showHyperlinks: vi.fn(),
      clickLink: vi.fn(),
   };
   const domSanitizer = overrides.domSanitizer ?? {
      sanitize: vi.fn((_: SecurityContext, value: any) => value),
      bypassSecurityTrustResourceUrl: vi.fn((value: string) => value),
   };
   const debounceService = overrides.debounceService ?? {
      debounce: vi.fn((_: string, fn: () => void) => fn()),
   };
   const zone = {
      run: (fn: () => void) => fn(),
      runOutsideAngular: (fn: () => void) => fn(),
   };
   const renderer = {};
   const richTextService = overrides.richTextService ?? {
      showAnnotationDialog: vi.fn().mockReturnValue(of({ initialContent: "" })),
   };

   const comp = new VSText(
      viewsheetClient as any,
      popComponentService as any,
      dataTipService as any,
      modalService as any,
      dropdownService as any,
      contextProvider as any,
      changeDetectionRef as any,
      hyperlinkService as any,
      domSanitizer as any,
      debounceService as any,
      zone as any,
      renderer as any,
      richTextService as any,
      document,
   );

   const postMessage = vi.fn();
   const objectContainer = document.createElement("div");
   objectContainer.style.borderTopWidth = "1px";
   objectContainer.style.borderBottomWidth = "6px";
   Object.defineProperty(objectContainer, "getBoundingClientRect", {
      value: () => ({ left: 20, top: 30 }),
   });
   const objectContentTd = document.createElement("div");
   Object.defineProperty(objectContentTd, "clientWidth", { value: 90, configurable: true });
   Object.defineProperty(objectContentTd, "clientHeight", { value: 16, configurable: true });
   Object.defineProperty(objectContentTd, "scrollHeight", { value: 22, configurable: true });
   const objectContentTextarea = document.createElement("textarea");
   Object.defineProperty(objectContentTextarea, "scrollHeight", { value: 26, configurable: true });
   Object.defineProperty(objectContentTextarea, "scrollWidth", { value: 180, configurable: true });
   const externalFrame = document.createElement("iframe");
   Object.defineProperty(externalFrame, "contentWindow", {
      value: { postMessage },
      configurable: true,
   });

   // E2: ViewChild refs cannot be set via template in direct-instantiation mode;
   // these stubs let tooltip, autosize, and iframe code paths run without render()
   (comp as any).objectContainer = { nativeElement: objectContainer };
   (comp as any).objectContentTd = { nativeElement: objectContentTd };
   (comp as any).objectContentTextarea = { nativeElement: objectContentTextarea };
   (comp as any).externalFrame = { nativeElement: externalFrame };

   comp.vsInfo = new ViewsheetInfo([], "http://example.com/", false, "viewsheet1");
   comp.model = makeTextModel(overrides.model ?? {});

   return {
      comp,
      commandsSubject,
      viewsheetClient,
      popComponentService,
      dataTipService,
      dropdownService,
      contextProvider,
      changeDetectionRef,
      hyperlinkService,
      domSanitizer,
      debounceService,
      richTextService,
      postMessage,
   };
}

async function renderTextFixture(modelOverrides: Partial<VSTextModel> = {}): Promise<ComponentFixture<VSText>> {
   TestBed.resetTestingModule();
   TestBed.overrideComponent(VSText, {
      set: {
         imports: [
            VSDataTipStubDirective,
            VSPopComponentStubDirective,
            OutOfZoneStubDirective,
            TooltipStubDirective,
            SafeFontStubDirective,
            AutoCompleteTextStubComponent,
            VSHiddenAnnotationStubComponent,
            VSAnnotationStubComponent,
            VSLoadingDisplayStubComponent,
         ],
      },
   });

   await TestBed.configureTestingModule({
      imports: [
         ReactiveFormsModule,
         FormsModule,
         NgbModule,
         HttpClientTestingModule,
         VSText,
      ],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ViewsheetClientService, useValue: {} },
         { provide: FixedDropdownService, useValue: {} },
         {
            provide: ContextProvider,
            useValue: {
               viewer: false,
               preview: false,
               binding: false,
               composer: false,
               vsWizard: false,
               vsWizardPreview: false,
               embedAssembly: false,
            },
         },
         {
            provide: PopComponentService,
            useValue: {
               setPopLocation: vi.fn(),
               toggle: vi.fn(),
               registerOnClickFlagged: vi.fn(),
               isPopSource: vi.fn().mockReturnValue(false),
            },
         },
         {
            provide: DataTipService,
            useValue: {
               isDataTip: vi.fn().mockReturnValue(false),
               isDataTipSource: vi.fn().mockReturnValue(false),
            },
         },
         {
            provide: ShowHyperlinkService,
            useValue: {
               singleClick: true,
               showHyperlinks: vi.fn(),
               clickLink: vi.fn(),
            },
         },
         { provide: DebounceService, useValue: { debounce: vi.fn((_: string, fn: () => void) => fn()) } },
         {
            provide: RichTextService,
            useValue: { showAnnotationDialog: vi.fn().mockReturnValue(of({ initialContent: "" })) },
         },
         { provide: AppInfoService, useValue: {} },
      ],
   }).compileComponents();

   const fixture = TestBed.createComponent(VSText);
   fixture.componentInstance.vsInfo = new ViewsheetInfo([], "http://example.com/", false, "viewsheet1");
   fixture.componentInstance.model = makeTextModel({
      text: "text\n123",
      shadow: false,
      ...modelOverrides,
   });
   fixture.componentInstance.tooltip = "mock tooltip";
   fixture.detectChanges();
   await fixture.whenStable();
   return fixture;
}

afterEach(() => vi.restoreAllMocks());

describe("VSText - single-pass interaction", () => {
   // ─── Group 1: lifecycle and actions ──────────────────────────────────────

   describe("Group 1 - lifecycle and actions", () => {
      it("should register a message event listener in ngOnInit when model.url is true", () => {
         const addEventListenerSpy = vi.spyOn(document.defaultView!, "addEventListener");
         const { comp } = createTextComponent({ model: { url: true, text: "../page" } });

         comp.ngOnInit();
         comp.ngOnDestroy();

         expect(addEventListenerSpy).toHaveBeenCalledWith("message", expect.any(Function), false);
      });

      it("should call sendChangeEvent in ngOnDestroy when pendingChange is true", () => {
         const { comp } = createTextComponent({ model: { url: true, text: "../page" } });
         const sendChangeEventSpy = vi.spyOn(comp as any, "sendChangeEvent")
            .mockImplementation(() => {});
         // E2: pendingChange is private state set when text changes without immediate flush
         (comp as any).pendingChange = true;

         comp.ngOnInit();
         comp.ngOnDestroy();

         expect(sendChangeEventSpy).toHaveBeenCalled();
      });

      it("should remove the message event listener in ngOnDestroy", () => {
         const removeEventListenerSpy = vi.spyOn(document.defaultView!, "removeEventListener");
         const { comp } = createTextComponent({ model: { url: true, text: "../page" } });

         comp.ngOnInit();
         comp.ngOnDestroy();

         expect(removeEventListenerSpy).toHaveBeenCalledWith("message", expect.any(Function));
      });

      it("should call textChanged on ngOnChanges when model changes", () => {
         const { comp } = createTextComponent({ model: { url: true, text: "../new-url", popComponent: "pop-1" } });
         const textChangedSpy = vi.spyOn(comp, "textChanged");
         vi.spyOn(comp as any, "changeHeightOfTextarea").mockReturnValue(true);
         vi.spyOn(comp as any, "updateUrlText").mockImplementation(() => {});

         comp.ngOnChanges({
            model: {
               previousValue: makeTextModel({ url: true, text: "../old-url" }),
               currentValue: comp.model,
               firstChange: false,
               isFirstChange: () => false,
            },
         });

         expect(textChangedSpy).toHaveBeenCalled();
      });

      it("should call changeHeightOfTextarea on ngOnChanges for url components", () => {
         const { comp } = createTextComponent({ model: { url: true, text: "../new-url", popComponent: "pop-1" } });
         const changeHeightSpy = vi.spyOn(comp as any, "changeHeightOfTextarea").mockReturnValue(true);
         vi.spyOn(comp as any, "updateUrlText").mockImplementation(() => {});

         comp.ngOnChanges({
            model: {
               previousValue: makeTextModel({ url: true, text: "../old-url" }),
               currentValue: comp.model,
               firstChange: false,
               isFirstChange: () => false,
            },
         });

         expect(changeHeightSpy).toHaveBeenCalled();
      });

      it("should call registerOnClickFlagged with popComponent on ngOnChanges", () => {
         const { comp, popComponentService } = createTextComponent({
            model: { url: true, text: "../new-url", popComponent: "pop-1" },
         });
         vi.spyOn(comp as any, "changeHeightOfTextarea").mockReturnValue(true);
         vi.spyOn(comp as any, "updateUrlText").mockImplementation(() => {});

         comp.ngOnChanges({
            model: {
               previousValue: makeTextModel({ url: true, text: "../old-url" }),
               currentValue: comp.model,
               firstChange: false,
               isFirstChange: () => false,
            },
         });

         expect(popComponentService.registerOnClickFlagged).toHaveBeenCalledWith("pop-1");
      });

      it("should call updateUrlText with new url on ngOnChanges", () => {
         const { comp } = createTextComponent({ model: { url: true, text: "../new-url" } });
         vi.spyOn(comp as any, "changeHeightOfTextarea").mockReturnValue(true);
         const updateUrlTextSpy = vi.spyOn(comp as any, "updateUrlText").mockImplementation(() => {});

         comp.ngOnChanges({
            model: {
               previousValue: makeTextModel({ url: true, text: "../old-url" }),
               currentValue: comp.model,
               firstChange: false,
               isFirstChange: () => false,
            },
         });

         expect(updateUrlTextSpy).toHaveBeenCalledWith("../new-url");
      });

      it("should route 'text annotate' action to showAnnotateDialog", () => {
         const { comp } = createTextComponent();
         const actions = createTextActions();
         const annotateSpy = vi.spyOn(comp as any, "showAnnotateDialog").mockImplementation(() => {});
         comp.actions = actions as any;
         const evt = new MouseEvent("click");

         actions.onAssemblyActionEvent.next({ id: "text annotate", event: evt });

         expect(annotateSpy).toHaveBeenCalledWith(evt);
      });

      it("should route 'text show-hyperlink' action to showHyperlinks", () => {
         const { comp, hyperlinkService } = createTextComponent({
            model: { hyperlinks: [{ url: "http://a", tooltip: "tip" } as any] },
         });
         const actions = createTextActions();
         comp.actions = actions as any;
         const evt = new MouseEvent("click");

         actions.onAssemblyActionEvent.next({ id: "text show-hyperlink", event: evt });

         expect(hyperlinkService.showHyperlinks).toHaveBeenCalledWith(
            evt,
            comp.model.hyperlinks,
            expect.anything(),
            "viewsheet1",
            "http://example.com/",
            false,
         );
      });

      it("should route 'text show-format-pane' action to onOpenFormatPane emitter", () => {
         const { comp } = createTextComponent();
         const actions = createTextActions();
         comp.actions = actions as any;
         const emittedModels: any[] = [];
         comp.onOpenFormatPane.subscribe(m => emittedModels.push(m));

         actions.onAssemblyActionEvent.next({ id: "text show-format-pane", event: new MouseEvent("click") });

         expect(emittedModels).toEqual([comp.model]);
      });

      it("should unsubscribe the previous action subscription when actions are replaced", () => {
         const { comp } = createTextComponent();
         const firstActions = createTextActions();
         comp.actions = firstActions as any;
         // E2: actionSubscription is private — no public API to test unsubscription otherwise
         const unsubscribeSpy = vi.spyOn((comp as any).actionSubscription, "unsubscribe");

         comp.actions = createTextActions() as any;

         expect(unsubscribeSpy).toHaveBeenCalled();
      });

      it("should flush pending changes when updateOnChange switches back to true", () => {
         const { comp } = createTextComponent({
            contextProvider: {
               viewer: false, preview: false, binding: false, composer: true,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false,
            },
         });
         const sendChangeEventSpy = vi.spyOn(comp as any, "sendChangeEvent").mockImplementation(() => {});

         comp.updateOnChange = false;
         comp.changeText("changed");
         comp.updateOnChange = true;

         expect(sendChangeEventSpy).toHaveBeenCalled();
      });

      it("should clear editing state and emit width update when deselected after dblclick creation", () => {
         const { comp } = createTextComponent();
         const widthUpdates: number[] = [];
         comp.updateWidth.subscribe(w => widthUpdates.push(w));
         comp.model.editing = true;
         comp.model.selectedAnnotations = ["anno1"];
         // E2: createdByDblClick is private; set to simulate dblclick-created text flow
         (comp as any).createdByDblClick = true;

         comp.selected = false;

         expect(comp.model.selectedAnnotations).toEqual([]);
         expect(comp.model.editing).toBe(false);
         expect((comp as any).createdByDblClick).toBe(false);
         expect(widthUpdates).toEqual([comp.model.objectFormat.width]);
      });

      it("should apply the shadowText class in the rendered view when model.shadow is true", async () => {
         const fixture = await renderTextFixture();
         try {
            const textView = fixture.debugElement.query(By.css(".text-view")).nativeElement as HTMLElement;

            expect(textView.classList.contains("shadowText")).toBe(false);

            fixture.componentInstance.model.shadow = true;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(textView.classList.contains("shadowText")).toBe(true);
         } finally {
            fixture.destroy(); // C6: must destroy to clean up subscriptions and event listeners
         }
      });
   });

   // ─── Group 2: editing and click flows ────────────────────────────────────

   describe("Group 2 - editing and click flows", () => {
      it("should update model.text synchronously on changeText()", () => {
         const { comp } = createTextComponent({
            contextProvider: {
               viewer: false, preview: false, binding: false, composer: true,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false,
            },
         });
         vi.spyOn(comp as any, "sendChangeEvent").mockImplementation(() => {});

         comp.changeText("new value");

         expect(comp.model.text).toBe("new value");
      });

      it("should call debounce with correct key and delay on changeText()", () => {
         const { comp, debounceService } = createTextComponent({
            contextProvider: {
               viewer: false, preview: false, binding: false, composer: true,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false,
            },
         });
         vi.spyOn(comp as any, "sendChangeEvent").mockImplementation(() => {});

         comp.changeText("new value");

         expect(debounceService.debounce).toHaveBeenCalledWith(
            "ChangeVSObjectTextEvent.Text1",
            expect.any(Function),
            500,
            [],
         );
      });

      it("should send text change events to the editable-layout route when needed", () => {
         const { comp, viewsheetClient } = createTextComponent({
            contextProvider: {
               viewer: false, preview: false, binding: false, composer: true,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false,
            },
         });
         comp.editableLayout = true;
         comp.layoutRegion = PrintLayoutSection.FOOTER;
         comp.model.text = "Footer text";

         (comp as any).sendChangeEvent();

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            `/events/composer/viewsheet/printLayout/vsText/changeText/${PrintLayoutSection.FOOTER}`,
            expect.objectContaining({ name: "Text1", text: "Footer text" }),
         );
      });

      it("should call setPopLocation with the model popLocation on clicked()", () => {
         const { comp, popComponentService } = createTextComponent({
            model: { popComponent: "pop-1", hasOnClick: false, hyperlinks: [] },
         });
         vi.spyOn(comp, "clickHyperlink").mockImplementation(() => {});
         vi.spyOn(comp as any, "changeHeightOfTextarea").mockReturnValue(false);

         comp.clicked({ clientX: 100, clientY: 120, offsetX: 8, offsetY: 9 } as MouseEvent);

         expect(popComponentService.setPopLocation).toHaveBeenCalledWith("top");
      });

      it("should call toggle with correct pop args on clicked()", () => {
         const { comp, popComponentService } = createTextComponent({
            model: { popComponent: "pop-1", hasOnClick: false, hyperlinks: [] },
         });
         vi.spyOn(comp, "clickHyperlink").mockImplementation(() => {});
         vi.spyOn(comp as any, "changeHeightOfTextarea").mockReturnValue(false);

         comp.clicked({ clientX: 100, clientY: 120, offsetX: 8, offsetY: 9 } as MouseEvent);

         expect(popComponentService.toggle).toHaveBeenCalledWith("pop-1", 100, 120, 0.6, "Text1");
      });

      it("should call clickHyperlink with the event on clicked()", () => {
         const { comp } = createTextComponent({
            model: { popComponent: "pop-1", hasOnClick: false, hyperlinks: [{ url: "http://x" } as any] },
         });
         const clickHyperlinkSpy = vi.spyOn(comp, "clickHyperlink").mockImplementation(() => {});
         vi.spyOn(comp as any, "changeHeightOfTextarea").mockReturnValue(false);
         const event = { clientX: 100, clientY: 120, offsetX: 8, offsetY: 9 } as MouseEvent;

         comp.clicked(event);

         expect(clickHyperlinkSpy).toHaveBeenCalledWith(event);
      });

      it("should call changeHeightOfTextarea on clicked()", () => {
         const { comp } = createTextComponent({
            model: { popComponent: "pop-1", hasOnClick: false, hyperlinks: [] },
         });
         vi.spyOn(comp, "clickHyperlink").mockImplementation(() => {});
         const changeHeightSpy = vi.spyOn(comp as any, "changeHeightOfTextarea").mockReturnValue(true);

         comp.clicked({ clientX: 100, clientY: 120, offsetX: 8, offsetY: 9 } as MouseEvent);

         expect(changeHeightSpy).toHaveBeenCalled();
      });

      it("should send an onclick event with offset coordinates on clicked() when hasOnClick=true", () => {
         const { comp, viewsheetClient } = createTextComponent({
            model: { popComponent: "pop-1", hasOnClick: true, hyperlinks: [] },
         });
         vi.spyOn(comp, "clickHyperlink").mockImplementation(() => {});
         vi.spyOn(comp as any, "changeHeightOfTextarea").mockReturnValue(false);

         comp.clicked({ clientX: 100, clientY: 120, offsetX: 8, offsetY: 9 } as MouseEvent);

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith("/events/onclick/Text1/8/9");
      });

      it("should call clickLink for the single hyperlink in viewer context", () => {
         const { comp, hyperlinkService } = createTextComponent({
            model: { hyperlinks: [{ url: "http://one", targetFrame: "" } as any] },
            contextProvider: {
               viewer: true, preview: true, binding: false, composer: false,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false,
            },
         });
         const leftClick = { button: 0, stopPropagation: vi.fn() } as any;

         comp.clickHyperlink(leftClick);

         expect(hyperlinkService.clickLink).toHaveBeenCalledWith(
            expect.objectContaining({ url: "http://one", targetFrame: "previewTab" }),
            "viewsheet1",
            "http://example.com/",
         );
      });

      it("should call stopPropagation when clickHyperlink has no hyperlinks and alwaysAllowClickPropagation=false", () => {
         const { comp } = createTextComponent({ model: { hyperlinks: [] } });
         comp.actions = createTextActions() as any;
         comp.alwaysAllowClickPropagation = false;
         const leftClick = { button: 0, stopPropagation: vi.fn() } as any;

         comp.clickHyperlink(leftClick);

         expect(leftClick.stopPropagation).toHaveBeenCalled();
      });

      it("should stop propagation for editing navigation keys only (backspace not stopped)", () => {
         const { comp } = createTextComponent();
         comp.model.editing = true;
         const stopPropagation = vi.fn();

         comp.onKeyDown({ keyCode: 8, stopPropagation } as any);
         comp.onKeyDown({ keyCode: 13, stopPropagation } as any);

         expect(stopPropagation).toHaveBeenCalledTimes(1);
      });

      it("should call preventDefault on enter key in onEnterDown when createdByDblClick", () => {
         const { comp } = createTextComponent();
         vi.spyOn(comp as any, "sendChangeEvent").mockImplementation(() => {});
         vi.spyOn(comp.vsInfo, "deselectAssembly");
         // E2: createdByDblClick is private; set to simulate the double-click creation flow
         (comp as any).createdByDblClick = true;
         const event = { keyCode: 13, preventDefault: vi.fn() } as any;

         comp.onEnterDown(event);

         expect(event.preventDefault).toHaveBeenCalled();
      });

      it("should call sendChangeEvent on enter key in onEnterDown when createdByDblClick", () => {
         const { comp } = createTextComponent();
         const sendChangeEventSpy = vi.spyOn(comp as any, "sendChangeEvent").mockImplementation(() => {});
         vi.spyOn(comp.vsInfo, "deselectAssembly");
         (comp as any).createdByDblClick = true;

         comp.onEnterDown({ keyCode: 13, preventDefault: vi.fn() } as any);

         expect(sendChangeEventSpy).toHaveBeenCalled();
      });

      it("should call deselectAssembly on enter key in onEnterDown when createdByDblClick", () => {
         const { comp } = createTextComponent();
         vi.spyOn(comp as any, "sendChangeEvent").mockImplementation(() => {});
         const deselectSpy = vi.spyOn(comp.vsInfo, "deselectAssembly");
         (comp as any).createdByDblClick = true;

         comp.onEnterDown({ keyCode: 13, preventDefault: vi.fn() } as any);

         expect(deselectSpy).toHaveBeenCalledWith(comp.model);
      });

      it("should clear createdByDblClick on enter key in onEnterDown", () => {
         const { comp } = createTextComponent();
         vi.spyOn(comp as any, "sendChangeEvent").mockImplementation(() => {});
         vi.spyOn(comp.vsInfo, "deselectAssembly");
         (comp as any).createdByDblClick = true;

         comp.onEnterDown({ keyCode: 13, preventDefault: vi.fn() } as any);

         expect((comp as any).createdByDblClick).toBe(false);
      });

      it("should emit updateWidth on enter key in onEnterDown when createdByDblClick", () => {
         const { comp } = createTextComponent();
         vi.spyOn(comp as any, "sendChangeEvent").mockImplementation(() => {});
         vi.spyOn(comp.vsInfo, "deselectAssembly");
         const widthUpdates: number[] = [];
         comp.updateWidth.subscribe(w => widthUpdates.push(w));
         (comp as any).createdByDblClick = true;

         comp.onEnterDown({ keyCode: 13, preventDefault: vi.fn() } as any);

         expect(widthUpdates).toEqual([comp.model.objectFormat.width]);
      });

      it("should enter force-edit mode and disable interaction on ForceEditModeCommand", () => {
         // E6: dispatch via commandsSubject (STOMP) instead of calling processForceEditModeCommand directly
         const { comp, commandsSubject } = createTextComponent({
            contextProvider: {
               viewer: false, preview: false, binding: false, composer: true,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false,
            },
         });
         const selectAssemblySpy = vi.spyOn(comp.vsInfo, "selectAssembly");

         // CommandProcessor subscribes in the constructor; dispatch needs assembly to pass the filter
         // (condition: !handleGlobal && this.getAssemblyName() === message.assembly)
         commandsSubject.next({ type: "ForceEditModeCommand", assembly: "Text1", command: {} });
         comp.ngOnDestroy(); // cleanup subscription

         expect((comp as any).createdByDblClick).toBe(true);
         expect(comp.model.editing).toBe(true);
         expect(selectAssemblySpy).toHaveBeenCalledWith(comp.model);
         expect(comp.model.objectFormat.wrapping.whiteSpace).toBe("nowrap");
         expect(comp.model.text).toBe("");
         expect(comp.model.interactionDisabled).toBe(true);
      });
   });

   // ─── Group 3: tooltip, display, and external urls ─────────────────────────

   describe("Group 3 - tooltip, display, and external urls", () => {
      it("should use customTooltipString as tooltip when it is set", () => {
         const { comp } = createTextComponent({
            model: { customTooltipString: "custom", tooltipVisible: true },
         });

         comp.modelChanged();

         expect(comp.tooltip).toBe("custom");
      });

      it("should use hyperlink.tooltip when customTooltipString is empty", () => {
         const { comp } = createTextComponent({
            model: { customTooltipString: "", hyperlinks: [{ tooltip: "hyper" } as any], tooltipVisible: true },
         });

         comp.modelChanged();

         expect(comp.tooltip).toBe("hyper");
      });

      it("should use defaultAnnotationContent as tooltip when no hyperlink tooltip", () => {
         const { comp } = createTextComponent({
            model: { hyperlinks: [], defaultAnnotationContent: "annotation", tooltipVisible: true },
         });

         comp.modelChanged();

         expect(comp.tooltip).toBe("annotation");
      });

      it("should show overflow tooltip text (stripped of html entities) when content overflows", () => {
         const { comp } = createTextComponent({
            model: { hyperlinks: [], defaultAnnotationContent: "", text: "visible&nbsp;", tooltipVisible: true },
         });
         comp.displayText = "short";
         vi.spyOn(comp, "getContentSize").mockReturnValue({ width: 50, height: 10 });
         // E2: _clientWidth/_clientHeight are private cached values; set so overflow check triggers
         (comp as any).updateClientSize = vi.fn(() => {
            (comp as any)._clientWidth = 80;
            (comp as any)._clientHeight = 18;
         });

         comp.modelChanged();

         expect(comp.tooltip).toBe("visible");
      });

      it("should set tooltip to empty string when tooltipVisible is false", () => {
         const { comp } = createTextComponent({ model: { tooltipVisible: false } });

         comp.modelChanged();

         expect(comp.tooltip).toBe("");
      });

      it("should call detectChanges after modelChanged", () => {
         const { comp, changeDetectionRef } = createTextComponent();

         comp.modelChanged();

         expect(changeDetectionRef.detectChanges).toHaveBeenCalled();
      });

      it("should set displayText via getEllipsisText on textChanged()", () => {
         const { comp } = createTextComponent();
         const ellipsisSpy = vi.spyOn(comp, "getEllipsisText").mockReturnValue("trimmed");

         comp.textChanged();

         expect(comp.displayText).toBe("trimmed");
         expect(ellipsisSpy).toHaveBeenCalled();
      });

      it("should set wordWrap=true on textChanged()", () => {
         const { comp } = createTextComponent();
         vi.spyOn(comp, "getEllipsisText").mockReturnValue("trimmed");

         comp.textChanged();

         expect(comp.wordWrap).toBe(true);
      });

      it("should return null from getEllipsisText when text is null", () => {
         const { comp } = createTextComponent();
         comp.model.objectFormat.height = 20;

         expect(comp.getEllipsisText(null, "12px Arial")).toBeNull();
      });

      it("should use noWordWrap path in getEllipsisText for html-entity text (no spaces)", () => {
         const { comp } = createTextComponent();
         const noWordSpy = vi.spyOn(comp, "getNoWordString").mockReturnValue("abc...");
         comp.model.objectFormat.height = 20;

         const createElementSpy = vi.spyOn(window.document, "createElement").mockImplementation(() => {
            const el = { style: {}, innerHTML: "" } as any;
            Object.defineProperty(el, "clientHeight", {
               get: () => el.style.width === "99999999px" ? 10 : 30,
            });
            return el;
         });
         const bodyAppendSpy = vi.spyOn(window.document.body, "appendChild")
            .mockImplementation(() => null as any);
         const bodyRemoveSpy = vi.spyOn(window.document.body, "removeChild")
            .mockImplementation(() => null as any);

         try {
            expect(comp.getEllipsisText("abc&nbsp;", "12px Arial")).toBe("abc...");
            expect(noWordSpy).toHaveBeenCalled();
         } finally {
            createElementSpy.mockRestore();
            bodyAppendSpy.mockRestore();
            bodyRemoveSpy.mockRestore();
         }
      });

      it("should use noWordWrap path in getEllipsisText for text without spaces", () => {
         const { comp } = createTextComponent();
         const noWordSpy = vi.spyOn(comp, "getNoWordString").mockReturnValue("abc...");
         comp.model.objectFormat.height = 20;

         const createElementSpy = vi.spyOn(window.document, "createElement").mockImplementation(() => {
            const el = { style: {}, innerHTML: "" } as any;
            Object.defineProperty(el, "clientHeight", {
               get: () => el.style.width === "99999999px" ? 10 : 30,
            });
            return el;
         });
         const bodyAppendSpy = vi.spyOn(window.document.body, "appendChild")
            .mockImplementation(() => null as any);
         const bodyRemoveSpy = vi.spyOn(window.document.body, "removeChild")
            .mockImplementation(() => null as any);

         try {
            expect(comp.getEllipsisText("abcdefgh", "12px Arial")).toBe("abc...");
            expect(noWordSpy).toHaveBeenCalled();
         } finally {
            createElementSpy.mockRestore();
            bodyAppendSpy.mockRestore();
            bodyRemoveSpy.mockRestore();
         }
      });

      it("should use wordWrap path in getEllipsisText for text with spaces", () => {
         const { comp } = createTextComponent();
         const wordSpy = vi.spyOn(comp, "getWordString").mockReturnValue("word...");
         comp.model.objectFormat.height = 20;

         const createElementSpy = vi.spyOn(window.document, "createElement").mockImplementation(() => {
            const el = { style: {}, innerHTML: "" } as any;
            Object.defineProperty(el, "clientHeight", {
               get: () => {
                  if(el.style.width === "99999999px") return 10;
                  return el.innerHTML.includes(" ") ? 50 : 30;
               },
            });
            return el;
         });
         const bodyAppendSpy = vi.spyOn(window.document.body, "appendChild")
            .mockImplementation(() => null as any);
         const bodyRemoveSpy = vi.spyOn(window.document.body, "removeChild")
            .mockImplementation(() => null as any);

         try {
            expect(comp.getEllipsisText("alpha beta", "12px Arial")).toBe("word...");
            expect(wordSpy).toHaveBeenCalled();
         } finally {
            createElementSpy.mockRestore();
            bodyAppendSpy.mockRestore();
            bodyRemoveSpy.mockRestore();
         }
      });

      it("should detect html-tagged text and return false for plain text with br", () => {
         const { comp } = createTextComponent();

         expect(comp.isHTMLContent("<span>html</span>")).toBe(true);
         expect(comp.isHTMLContent("plain<br>text")).toBe(false);
      });

      it("should return true from changeHeightOfTextarea when textarea height changes", () => {
         const { comp } = createTextComponent({ model: { autoSize: true, shadow: true } });
         vi.spyOn(GuiTool, "measureText").mockImplementation((text: string) => text.length * 10);

         const changed = (comp as any).changeHeightOfTextarea();

         expect(changed).toBe(true);
      });

      it("should return content size from getContentSize based on objectContentTd", () => {
         const { comp } = createTextComponent({ model: { autoSize: true, shadow: true } });
         vi.spyOn(GuiTool, "measureText").mockImplementation((text: string) => text.length * 10);
         (comp as any).changeHeightOfTextarea();

         const contentSize = comp.getContentSize();

         expect(contentSize).toEqual({ width: 134, height: 29 });
      });

      it("should return getAutoTextModel with correct text and shadow fields", () => {
         const { comp } = createTextComponent({ model: { autoSize: true, shadow: true } });

         const autoTextModel = comp.getAutoTextModel();

         expect(autoTextModel.text).toBe("Hello world");
         expect(autoTextModel.shadow).toBe(true);
      });

      it("should return getNoWrapMaxWidth based on objectContentTd clientWidth", () => {
         const { comp } = createTextComponent({ model: { autoSize: true } });
         // global beforeEach returns constant 10; override to length-proportional for this test
         // C3: per-it spy wrapped in try/finally
         const measureSpy = vi.spyOn(GuiTool, "measureText").mockImplementation(
            (text: string) => text.length * 10,
         );
         try {
            // "Hello world" = 11 chars → 11 * 10 = 110
            expect((comp as any).getNoWrapMaxWidth()).toBe(110);
         } finally {
            measureSpy.mockRestore();
         }
      });

      it("should include correct path and runtimeId in presenter property", () => {
         const { comp } = createTextComponent({
            contextProvider: {
               viewer: false, preview: false, binding: false, composer: true,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false,
            },
         });
         comp.model.genTime = 7;
         comp.model.url = false;

         expect(comp.presenter).toContain("vs/text/presenter/Text1/140/40/false/0/viewsheet1?7");
      });

      it("should return correct width derived from objectFormat and borders", () => {
         const { comp } = createTextComponent({
            contextProvider: {
               viewer: false, preview: false, binding: false, composer: true,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false,
            },
         });
         comp.model.url = false;
         // width getter: if createdByDblClick, objectFormat.width = max(width, textarea.scrollWidth)
         // max(140, 180) = 180; without this flag it would return objectFormat.width=140
         (comp as any).createdByDblClick = true;

         expect(comp.width).toBe(180);
      });

      it("should return null for whiteSpace when createdByDblClick is true", () => {
         const { comp } = createTextComponent({
            contextProvider: {
               viewer: false, preview: false, binding: false, composer: true,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false,
            },
         });
         comp.model.objectFormat.wrapping.whiteSpace = "normal";
         // E2: createdByDblClick is private; set to simulate force-edit mode
         (comp as any).createdByDblClick = true;

         expect(comp.whiteSpace).toBeNull();
      });

      it("should return true from isForceTab when createdByDblClick is true", () => {
         const { comp } = createTextComponent({
            contextProvider: {
               viewer: false, preview: false, binding: false, composer: true,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false,
            },
         });
         (comp as any).createdByDblClick = true;

         expect(comp.isForceTab()).toBe(true);
      });

      it("should return true from isPopupOrDataTipSource when popComponentService.isPopSource is true", () => {
         const { comp, popComponentService } = createTextComponent({
            contextProvider: {
               viewer: false, preview: false, binding: false, composer: true,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false,
            },
         });
         // C8: use mockReturnValueOnce so the override applies only to the assertion call
         vi.spyOn(popComponentService, "isPopSource").mockReturnValueOnce(true);

         expect((comp as any).isPopupOrDataTipSource()).toBe(true);
      });

      it("should sanitize and post external urls to the iframe origin via sendExternalUrls", () => {
         const { comp, domSanitizer, postMessage } = createTextComponent({
            model: {
               url: true,
               externalUrls: {
                  rel: "../api/path",
                  abs: "https://remote.example.com/a",
               },
            },
         });
         Object.defineProperty(document, "cookie", {
            value: "XSRF-TOKEN=test-token",
            configurable: true,
         });
         comp.safeUrlText = "https://frame.example.com/path/page";
         domSanitizer.sanitize = vi.fn((ctx: SecurityContext, value: any) => {
            if(ctx === SecurityContext.RESOURCE_URL) return "https://frame.example.com/path/page";
            return value;
         });
         vi.spyOn(GuiTool, "resolveUrl").mockImplementation((url: string) => {
            if(url === "../api/path") return "https://resolved.example.com/api/path";
            return "https://frame.example.com/path/page";
         });

         (comp as any).sendExternalUrls();

         expect(postMessage).toHaveBeenCalledWith(
            {
               type: "inetsoftExternalUrls",
               token: "test-token",
               urls: {
                  rel: "https://resolved.example.com/api/path",
                  abs: "https://remote.example.com/a",
               },
            },
            "https://frame.example.com",
         );
      });

      it("should post a single url update to the iframe via processUpdateExternalUrlCommand", () => {
         const { comp, domSanitizer, postMessage } = createTextComponent({
            model: { url: true, externalUrls: {} },
         });
         comp.safeUrlText = "https://frame.example.com/path/page";
         domSanitizer.sanitize = vi.fn((ctx: SecurityContext, value: any) => {
            if(ctx === SecurityContext.RESOURCE_URL) return "https://frame.example.com/path/page";
            return value;
         });
         vi.spyOn(GuiTool, "resolveUrl").mockImplementation((url: string) => {
            if(url === "../single") return "https://resolved.example.com/single";
            return "https://frame.example.com/path/page";
         });

         (comp as any).processUpdateExternalUrlCommand({
            type: "UpdateExternalUrlCommand",
            name: "one",
            url: "../single",
         });

         expect(postMessage).toHaveBeenCalledWith(
            {
               type: "inetsoftExternalUrls",
               token: expect.any(String),
               urls: { one: "https://resolved.example.com/single" },
            },
            "https://frame.example.com",
         );
      });
   });
});
