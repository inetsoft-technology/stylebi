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
 * VSSelectionContainerChildren - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - vsObject setter and service subscriptions initialize actions and cached layout
 *   Group 2 [Risk 3] - drag/drop flows: internal reorder, viewer tableBinding insert, invalid drop payload
 *   Group 3 [Risk 2] - drag helpers and context-menu/remove-child delegation
 *   Group 4 [Risk 2] - keyNavigation forwarding/focus and layout helper methods
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope:
 *   - focusSub teardown: component has no ngOnDestroy hook, so this is a broader lifecycle bug candidate
 *     that should be handled alongside keyboard-navigation cleanup work.
 *
 * Mocking strategy:
 *   - no direct HttpClient usage here; trap and viewsheet services are stubbed
 *   - imported standalone children/directive are replaced with lightweight stubs
 */

import { Component, Directive, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";

import { Tool } from "../../../../../../shared/util/tool";
import { TestUtils } from "../../../common/test/test-utils";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { OutOfZoneDirective } from "../../../widget/directive/out-of-zone.directive";
import { DomService } from "../../../widget/dom-service/dom.service";
import { AssemblyActionFactory } from "../../action/assembly-action-factory.service";
import { CurrentSelection } from "./current-selection.component";
import { SelectionContainerChildrenService } from "./services/selection-container-children.service";
import { VSRangeSlider } from "../range-slider/vs-range-slider.component";
import { VSSelection } from "./vs-selection.component";
import { ContextProvider } from "../../context-provider.service";
import { VSTrapService } from "../../util/vs-trap.service";
import { FocusObjectEventModel } from "../../model/focus-object-event-model";
import { NavigationKeys } from "../navigation-keys";
import { OuterSelection, VSSelectionContainerModel } from "../../model/vs-selection-container-model";
import { VSObjectModel } from "../../model/vs-object-model";
import { VSSelectionContainerChildren } from "./vs-selection-container-children.component";

@Directive({
   selector: "[outOfZone]",
   standalone: true,
})
class OutOfZoneDirectiveStub {
   @Output() onDragover = new EventEmitter<any>();
}

@Component({
   selector: "current-selection",
   template: "",
   standalone: true,
})
class CurrentSelectionStub {
   @Input() selection: any;
   @Input() model: any;
   @Input() titleHeight: number;
   @Input() titleFormat: any;
   @Input() titleRatio: number;
   @Input() actions: any;
   @Input() draggable: boolean;
}

@Component({
   selector: "vs-range-slider",
   template: "",
   standalone: true,
})
class VSRangeSliderStub {
   @Input() actions: any;
   @Input() model: any;
   @Input() vsInfo: any;
   @Input() keyNavigation: any;
}

@Component({
   selector: "vs-selection",
   template: "",
   standalone: true,
})
class VSSelectionStub {
   @Input() actions: any;
   @Input() model: any;
   @Input() vsInfo: any;
   @Input() keyNavigation: any;
}

function makeSelectionChild(
   name: string,
   objectType: VSObjectModel["objectType"] = "VSSelectionList",
   overrides: Partial<VSObjectModel> = {},
): VSObjectModel {
   return Object.assign(TestUtils.createMockVSObjectModel(objectType, name), {
      absoluteName: name,
      objectType,
      titleFormat: { height: 24 },
      objectFormat: {
         ...TestUtils.createMockVSFormatModel(),
         width: 120,
         height: 36,
      },
      dropdown: false,
   }, overrides);
}

function makeOuterSelection(name: string, overrides: Partial<OuterSelection> = {}): OuterSelection {
   return {
      name,
      title: name,
      value: name,
      ...overrides,
   };
}

function makeVsObject(overrides: Partial<VSSelectionContainerModel> = {}): VSSelectionContainerModel {
   return Object.assign(TestUtils.createMockVSSelectionContainerModel("SelectionContainer1"), {
      absoluteName: "SelectionContainer1",
      titleVisible: true,
      titleRatio: 0.5,
      dataRowHeight: 22,
      outerSelections: [],
      childObjects: [],
      supportRemoveChild: true,
      objectFormat: {
         ...TestUtils.createMockVSFormatModel(),
         top: 10,
         left: 20,
         width: 300,
         height: 180,
         zIndex: 3,
         font: "12px Arial",
         border: {
            top: "1px solid #111",
            left: "2px solid #111",
            right: "3px solid #111",
            bottom: "4px solid #111",
         },
      },
      titleFormat: { height: 30 },
   }, overrides);
}

async function renderComponent(opts: {
   model?: VSSelectionContainerModel;
   viewer?: boolean;
   preview?: boolean;
} = {}) {
   const childUpdateSubject = new Subject<number>();
   const childModelUpdateSubject = new Subject<any>();
   const viewsheetClient = {
      runtimeId: "runtime-1",
      sendEvent: vi.fn(),
      commands: new Subject<any>().asObservable(),
   };
   const actionFactory = {
      createActions: vi.fn((model: VSObjectModel | undefined) => ({ actionFor: model?.absoluteName })),
      createCurrentSelectionActions: vi.fn(() => ({ type: "current-selection-action" })),
   };
   const selectionContainerChildrenService = {
      onChildUpdate: childUpdateSubject.asObservable(),
      onChildModelUpdate: childModelUpdateSubject.asObservable(),
      updateChild: vi.fn(),
      updateChildModel: vi.fn(),
   };
   const trapService = { checkTrap: vi.fn() };
   const context = {
      viewer: opts.viewer ?? false,
      preview: opts.preview ?? false,
   };
   const containerRef = {
      getBoundingClientRect: vi.fn(() => ({ top: 15, left: 25 })),
   };
   const placeholderDragElementModel = {
      height: 0,
      text: "",
      top: 0,
      left: 0,
      width: 0,
      font: "",
      visible: false,
   };

   vi.spyOn(GuiTool, "measureScrollbars").mockReturnValue(12);
   vi.spyOn(GuiTool, "measureText").mockReturnValue(96);
   vi.spyOn(GuiTool, "setDragImage").mockResolvedValue();
   vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(false);

   const { fixture } = await render(VSSelectionContainerChildren, {
      providers: [
         { provide: ViewsheetClientService, useValue: viewsheetClient },
         { provide: AssemblyActionFactory, useValue: actionFactory },
         { provide: SelectionContainerChildrenService, useValue: selectionContainerChildrenService },
         { provide: ContextProvider, useValue: context },
         { provide: VSTrapService, useValue: trapService },
         { provide: NgbModal, useValue: { open: vi.fn() } },
         { provide: DomService, useValue: {} },
      ],
      componentInputs: {
      vsInfo: { isAssemblyFocused: vi.fn(() => false) },
         runtimeId: "runtime-1",
         containerRef,
         placeholderDragElementModel,
         vsObject: opts.model ?? makeVsObject(),
      },
      importOverrides: [
         { replace: OutOfZoneDirective, with: OutOfZoneDirectiveStub },
         { replace: CurrentSelection, with: CurrentSelectionStub },
         { replace: VSRangeSlider, with: VSRangeSliderStub },
         { replace: VSSelection, with: VSSelectionStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   return {
      comp: fixture.componentInstance,
      fixture,
      childUpdateSubject,
      childModelUpdateSubject,
      viewsheetClient,
      actionFactory,
      trapService,
      placeholderDragElementModel,
   };
}

afterEach(() => vi.restoreAllMocks());

describe("VSSelectionContainerChildren - setter and subscriptions", () => {
   it("should create child actions and cache layout metrics when vsObject is assigned", async () => {
      const model = makeVsObject({
         outerSelections: [makeOuterSelection("Current Selection")],
         childObjects: [
            makeSelectionChild("SelectionA"),
            makeSelectionChild("SliderB", "VSRangeSlider"),
         ],
      });
      const { comp, actionFactory } = await renderComponent({ model });

      expect(actionFactory.createActions).toHaveBeenCalledWith(model.childObjects[0]);
      expect(actionFactory.createActions).toHaveBeenCalledWith(model.childObjects[1]);
      expect(actionFactory.createCurrentSelectionActions).toHaveBeenCalledTimes(1);
      expect(comp.childActions).toEqual([
         { actionFor: "SelectionA" },
         { actionFor: "SliderB" },
      ]);
      expect(comp.currentSelectionActions).toEqual([{ type: "current-selection-action" }]);
      expect(comp.cachedBodyHeight).toBe(150);
      expect(comp.cachedBodyTop).toBe(40);
      expect(comp.cachedBodyLeft).toBe(20);
      expect(comp.cachedBodyWidth).toBe(300);
      expect(comp.cachedInnerWidth).toBe(295);
   });

   it("should refresh a child action when onChildUpdate emits an index", async () => {
      const model = makeVsObject({
         childObjects: [makeSelectionChild("SelectionA"), makeSelectionChild("SelectionB")],
      });
      const { comp, childUpdateSubject, actionFactory } = await renderComponent({ model });

      actionFactory.createActions.mockClear();
      childUpdateSubject.next(1);

      expect(actionFactory.createActions).toHaveBeenCalledWith(model.childObjects[1]);
      expect(comp.childActions[1]).toEqual({ actionFor: "SelectionB" });
   });

   it("should replace the current model when onChildModelUpdate emits", async () => {
      const oldModel = makeVsObject({ childObjects: [makeSelectionChild("OldChild")] });
      const newModel = makeVsObject({
         absoluteName: "SelectionContainer2",
         childObjects: [makeSelectionChild("NewChild")],
      });
      const { comp, childModelUpdateSubject } = await renderComponent({ model: oldModel });

      childModelUpdateSubject.next(newModel);

      expect(comp.vsObject.absoluteName).toBe("SelectionContainer2");
      expect(comp.childActions[0]).toEqual({ actionFor: "NewChild" });
   });
});

describe("VSSelectionContainerChildren - delegation helpers", () => {
   it("should prevent default, stop propagation, and emit the context-menu payload", async () => {
      const { comp } = await renderComponent();
      const preventDefault = vi.fn();
      const stopPropagation = vi.fn();
      const emitted: Array<{ actions: unknown; event: unknown }> = [];

      comp.containerChildContextMenu.subscribe(value => emitted.push(value));
      comp.showContextMenu({ preventDefault, stopPropagation } as any, { id: "menu-actions" } as any);

      expect(preventDefault).toHaveBeenCalled();
      expect(stopPropagation).toHaveBeenCalled();
      expect(emitted).toEqual([{ actions: { id: "menu-actions" }, event: { preventDefault, stopPropagation } }]);
   });

   it("should send a remove event and remove the target child when more than one child exists", async () => {
      const model = makeVsObject({
         childObjects: [makeSelectionChild("Child1"), makeSelectionChild("Child2")],
      });
      const { comp, viewsheetClient } = await renderComponent({ model });

      comp.onRemoveChild(0);

      expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
         "/events/composer/viewsheet/objects/remove",
         expect.any(Object),
      );
      expect(comp.vsObject.childObjects.map(child => child.absoluteName)).toEqual(["Child2"]);
      expect(comp.childActions).toEqual([{ actionFor: "Child2" }]);
   });

   it("should clear child arrays when the last child is removed", async () => {
      const model = makeVsObject({
         childObjects: [makeSelectionChild("OnlyChild")],
      });
      const { comp } = await renderComponent({ model });

      comp.onRemoveChild(0);

      expect(comp.vsObject.childObjects).toEqual([]);
      expect(comp.childActions).toEqual([]);
   });
});

describe("VSSelectionContainerChildren - drag helpers", () => {
   it("should populate placeholder state for current-selection drag starts", async () => {
      const model = makeVsObject({
         outerSelections: [makeOuterSelection("Current Selection A")],
      });
      const { comp, placeholderDragElementModel } = await renderComponent({ model });
      const dataTransfer = { getData: vi.fn(() => ""), setData: vi.fn() };

      comp.onDragStart({ pageX: 140, pageY: 90, dataTransfer } as any, 0, true);

      expect(placeholderDragElementModel.height).toBe(22);
      expect(placeholderDragElementModel.text).toBe("Current Selection A");
      expect(placeholderDragElementModel.top).toBe(76);
      expect(placeholderDragElementModel.left).toBe(116);
      expect(placeholderDragElementModel.width).toBe(96);
      expect(placeholderDragElementModel.visible).toBe(true);
      expect(dataTransfer.setData).not.toHaveBeenCalled();
      expect(comp.childDragModel).toEqual(expect.objectContaining({
         dragging: true,
         fromIndex: 0,
         isCurrentSelection: true,
         isContainerChild: true,
      }));
   });

   it("should write transfer data and child placeholder state for child drag starts", async () => {
      const model = makeVsObject({
         childObjects: [makeSelectionChild("ChildA")],
      });
      const { comp, placeholderDragElementModel } = await renderComponent({ model });
      const dataStore: Record<string, string> = {};
      const dataTransfer = {
         getData: vi.fn((key: string) => dataStore[key] ?? ""),
         setData: vi.fn((key: string, value: string) => {
            dataStore[key] = value;
         }),
      };

      comp.onDragStart({ pageX: 140, pageY: 90, dataTransfer } as any, 0, false);

      expect(placeholderDragElementModel.height).toBe(24);
      expect(placeholderDragElementModel.text).toBe("ChildA");
      expect(JSON.parse(dataStore.text)).toEqual(expect.objectContaining({
         dragName: ["tableBinding"],
         objectName: "ChildA",
         container: "SelectionContainer1",
       }));
      expect(comp.childDragModel).toEqual(expect.objectContaining({
         dragging: true,
         fromIndex: 0,
         isCurrentSelection: false,
         isContainerChild: true,
      }));
   });

   it("should move the placeholder while dragging and reset it on drag end", async () => {
      const { comp, placeholderDragElementModel } = await renderComponent();
      comp.childDragModel = {
         dragging: true,
         eventX: 50,
         eventY: 60,
         originalX: 20,
         originalY: 30,
         isContainerChild: true,
      } as any;
      placeholderDragElementModel.visible = true;

      comp.onDrag({ pageX: 65, pageY: 78 } as any);

      expect(placeholderDragElementModel.left).toBe(35);
      expect(placeholderDragElementModel.top).toBe(48);

      comp.onDragEnd({} as any);

      expect(comp.childDragModel.dragging).toBe(false);
      expect(comp.childDragModel.isContainerChild).toBe(false);
      expect(placeholderDragElementModel.visible).toBe(false);
   });
});

describe("VSSelectionContainerChildren - drop flows", () => {
   it("should send a move-child event for internal drops when fromIndex and toIndex differ", async () => {
      const model = makeVsObject({
         childObjects: [makeSelectionChild("Child1"), makeSelectionChild("Child2")],
      });
      const { comp, viewsheetClient } = await renderComponent({ model });
      const stopPropagation = vi.fn();

      comp.childDragModel = {
         dragging: true,
         isContainerChild: true,
         fromIndex: 0,
         toIndex: 1,
         isCurrentSelection: false,
      } as any;

      comp.onDrop({ preventDefault: vi.fn(), stopPropagation } as any);

      expect(stopPropagation).toHaveBeenCalled();
      expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
         "/events/selectionContainer/moveChild/SelectionContainer1",
         expect.objectContaining({ fromIndex: 0, toIndex: 1, currentSelection: false }),
      );
   });

   it("should send an insert-child event when a viewer tableBinding drop passes trap checking", async () => {
      const model = makeVsObject({
         childObjects: [makeSelectionChild("Child1")],
      });
      const { comp, trapService, viewsheetClient } = await renderComponent({ model, viewer: true });

         trapService.checkTrap.mockImplementation(
            (_info: unknown, continueFn?: () => void) => continueFn?.(),
         );
      comp.onDrop({
         preventDefault: vi.fn(),
         stopPropagation: vi.fn(),
         dataTransfer: {
            getData: vi.fn(() => JSON.stringify({
               dragName: ["tableBinding"],
               dragSource: { type: "details", table: null, objectName: "DraggedSource" },
            })),
         },
      } as any);

      expect(trapService.checkTrap).toHaveBeenCalledWith(
         expect.objectContaining({
            controllerURI: "../api/viewsheet/objects/checkSelectionTrap/runtime-1",
         }),
         expect.any(Function),
         expect.any(Function),
         expect.any(Function),
      );
      expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
         "/events/viewsheet/selectionContainer/insertChild/SelectionContainer1",
         expect.objectContaining({ toIndex: 1 }),
      );
   });

   it("should send an insert-child event through the noTrap callback when trap checking reports no trap", async () => {
      const model = makeVsObject({
         childObjects: [makeSelectionChild("Child1"), makeSelectionChild("Child2")],
      });
      const { comp, trapService, viewsheetClient } = await renderComponent({ model, viewer: true });

      trapService.checkTrap.mockImplementation(
         (
            _info: unknown,
            _continueFn?: () => void,
            _stopFn?: () => void,
            noTrapFn?: () => void,
         ) => noTrapFn?.(),
      );
      comp.onDrop({
         preventDefault: vi.fn(),
         stopPropagation: vi.fn(),
         dataTransfer: {
            getData: vi.fn(() => JSON.stringify({
               dragName: ["tableBinding"],
               dragSource: { type: "details", table: null, objectName: "DraggedSource" },
            })),
         },
      } as any);

      expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
         "/events/viewsheet/selectionContainer/insertChild/SelectionContainer1",
         expect.objectContaining({ toIndex: 2 }),
      );
   });

   it("should ignore malformed external drop payloads", async () => {
      const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
      const { comp, viewsheetClient, trapService } = await renderComponent({ viewer: true });

      try {
         comp.onDrop({
            preventDefault: vi.fn(),
            stopPropagation: vi.fn(),
            dataTransfer: {
               getData: vi.fn(() => "{not-json"),
            },
         } as any);

         expect(warnSpy).toHaveBeenCalled();
         expect(trapService.checkTrap).not.toHaveBeenCalled();
         expect(viewsheetClient.sendEvent).not.toHaveBeenCalled();
      } finally {
         warnSpy.mockRestore();
      }
   });
});

describe("VSSelectionContainerChildren - drag targeting and navigation", () => {
   it("should update toIndex and request change detection during same-type drag over", async () => {
      const model = makeVsObject({
         childObjects: [makeSelectionChild("Child1"), makeSelectionChild("Child2")],
      });
      const { comp } = await renderComponent({ model });
      const detectChangesSpy = vi.spyOn(comp["changeRef"], "detectChanges");

      comp.childDragModel = {
         dragging: true,
         toIndex: -1,
         fromIndex: 0,
         isCurrentSelection: false,
      } as any;

      comp.onDragOver({ preventDefault: vi.fn(), stopPropagation: vi.fn() } as any, 1, false);

      expect(comp.childDragModel.toIndex).toBe(1);
      expect(detectChangesSpy).toHaveBeenCalled();
   });

   it("should route cross-type drag over to the outer-selection boundary", async () => {
      const model = makeVsObject({
         outerSelections: [makeOuterSelection("Outer1"), makeOuterSelection("Outer2")],
         childObjects: [makeSelectionChild("Child1")],
      });
      const { comp } = await renderComponent({ model });

      comp.childDragModel = {
         dragging: true,
         toIndex: -1,
         fromIndex: 0,
         isCurrentSelection: false,
      } as any;

      comp.onDragOver({ preventDefault: vi.fn(), stopPropagation: vi.fn() } as any, 0, true);

      expect(comp.childDragModel.toIndex).toBe(0);
   });

   it("should move container drag-over to the last child index and expose drop markers", async () => {
      const model = makeVsObject({
         childObjects: [makeSelectionChild("Child1"), makeSelectionChild("Child2")],
      });
      const { comp } = await renderComponent({ model });
      const detectChangesSpy = vi.spyOn(comp["changeRef"], "detectChanges");

      comp.childDragModel = {
         dragging: true,
         toIndex: -1,
         fromIndex: 0,
         isCurrentSelection: false,
      } as any;

      comp.onDragOverContainer({ preventDefault: vi.fn() } as any);

      expect(comp.childDragModel.toIndex).toBe(1);
      expect(comp.isDropChildBottom(1, false)).toBe(true);
      expect(comp.isDropChildTop(1, false)).toBe(false);
      expect(detectChangesSpy).toHaveBeenCalled();
   });

   it("should focus the addressed child element and forward a cloned focused child on key navigation", async () => {
      const model = makeVsObject({
         childObjects: [makeSelectionChild("Child1"), makeSelectionChild("Child2")],
      });
      const { comp } = await renderComponent({ model });
      const focus = vi.fn();
      const forwarded: FocusObjectEventModel[] = [];
      const keyNavigation = new Subject<FocusObjectEventModel>();

      comp.containerBody = {
         nativeElement: {
            children: [{ focus }, { focus: vi.fn() }],
         },
      } as any;
      comp.innerKeyNavigation.subscribe(evt => forwarded.push(evt));
      comp.keyNavigation = keyNavigation.asObservable();

      keyNavigation.next({
         focused: { absoluteName: "SelectionContainer1" } as any,
         key: NavigationKeys.TAB,
         index: 0,
      });

      expect(focus).toHaveBeenCalled();
      expect(forwarded[0].focused.absoluteName).toBe("Child1");
      expect(forwarded[0].focused).not.toBe(model.childObjects[0]);
      expect(forwarded[0].key).toBe(NavigationKeys.TAB);
   });
});

describe("VSSelectionContainerChildren - layout helpers", () => {
   it("should calculate body and inner dimensions from the container model", async () => {
      const model = makeVsObject();
      const { comp } = await renderComponent({ model });

      expect(comp.getAssemblyName()).toBe("SelectionContainer1");
      expect(comp.getBodyHeight()).toBe(150);
      expect(comp.getBodyWidth()).toBe(300);
      expect(comp.getBodyTop()).toBe(40);
      expect(comp.getBodyLeft()).toBe(20);
      expect(comp.getInnerWidth()).toBe(
         300 - Tool.getMarginSize(model.objectFormat.border.left) - Tool.getMarginSize(model.objectFormat.border.right),
      );
   });
});
