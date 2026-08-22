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

import { of as observableOf } from "rxjs";
import { CalcTableActionHandlerDirective } from "./calc-table-action-handler.directive";
import { CalendarActionHandlerDirective } from "./calendar-action-handler.directive";
import { GroupContainerActionHandlerDirective } from "./group-container-action-handler.directive";
import { LineActionHandlerDirective } from "./line-action-handler.directive";
import { RectangleActionHandlerDirective } from "./rectangle-action-handler.directive";
import { SelectionContainerActionHandlerDirective } from "./selection-container-action-handler.directive";
import { SelectionListActionHandlerDirective } from "./selection-list-action-handler.directive";
import { SelectionTreeActionHandlerDirective } from "./selection-tree-action-handler.directive";
import { SliderActionHandlerDirective } from "./slider-action-handler.directive";
import { SpinnerActionHandlerDirective } from "./spinner-action-handler.directive";
import { TabActionHandlerDirective } from "./tab-action-handler.directive";
import { TextInputActionHandlerDirective } from "./text-input-action-handler.directive";
import { ViewsheetActionHandlerDirective } from "./viewsheet-action-handler.directive";
import { VSCrosstabActionHandler } from "../../../../vsobjects/binding/vs-crosstab-action-handler";
import { VSTableActionHandler } from "../../../../vsobjects/binding/vs-table-action-handler";

/**
 * Table-driven regression test for the assemblyName sweep documented in
 * docs/superpowers/plans/2026-08-22-follow-focus-missing-assembly-name-fix-plan.md.
 *
 * 17 action-handler call sites imperatively instantiate a PropertyDialog (or
 * equivalent) with a script pane but never assigned `dialog.assemblyName`,
 * the Input that ClickableScriptPane/VSAssemblyScriptPane read to populate
 * EditorContext.assembly for both Follow Focus's auto-push and G2's manual
 * pane-scoped pairing. submit-action-handler (the reported case) and
 * oval-action-handler (which already had a socketConnection regression test
 * from the prior G2 Task 1 fix round) have their own dedicated spec files;
 * this file covers the remaining 15 - the 12 "simple" directives that share
 * an identical (modelService, modalService, context) constructor and a
 * private showPropertyDialog(), plus calc-table-action-handler.directive.ts
 * (constructor also takes an injector) and the two vsobjects/binding classes
 * (VSCrosstabActionHandler / VSTableActionHandler), which are plain classes
 * rather than directives and delegate to a nested AbstractActionHandler
 * subclass's own showDialog() instead of calling it directly.
 */

function stubModelService(modelResponse: any = {}): any {
   return { getModel: vi.fn(() => observableOf(modelResponse)) };
}

function stubModalService(componentInstance: any): any {
   return {
      open: vi.fn(() => ({
         componentInstance,
         result: new Promise(() => { /* never resolves - not exercised here */ })
      }))
   };
}

const context: any = { viewer: false, preview: false };

describe("assembly-name wiring - simple (modelService, modalService, context) directives", () => {
   const cases: Array<{
      name: string;
      Ctor: new (modelService: any, modalService: any, context: any) => any;
      absoluteName: string;
      modelResponse?: any;
   }> = [
      { name: "CalendarActionHandlerDirective", Ctor: CalendarActionHandlerDirective, absoluteName: "Calendar1" },
      { name: "GroupContainerActionHandlerDirective", Ctor: GroupContainerActionHandlerDirective, absoluteName: "GroupContainer1" },
      { name: "LineActionHandlerDirective", Ctor: LineActionHandlerDirective, absoluteName: "Line1" },
      { name: "RectangleActionHandlerDirective", Ctor: RectangleActionHandlerDirective, absoluteName: "Rectangle1" },
      { name: "SelectionContainerActionHandlerDirective", Ctor: SelectionContainerActionHandlerDirective, absoluteName: "SelectionContainer1" },
      // SelectionListActionHandlerDirective additionally reaches into
      // dialog.model.selectionGeneralPaneModel.generalPropPaneModel right after
      // assignment, so the stubbed getModel response needs that shape present.
      {
         name: "SelectionListActionHandlerDirective", Ctor: SelectionListActionHandlerDirective,
         absoluteName: "SelectionList1",
         modelResponse: { selectionGeneralPaneModel: { generalPropPaneModel: { basicGeneralPaneModel: {} } } }
      },
      { name: "SelectionTreeActionHandlerDirective", Ctor: SelectionTreeActionHandlerDirective, absoluteName: "SelectionTree1" },
      { name: "SliderActionHandlerDirective", Ctor: SliderActionHandlerDirective, absoluteName: "Slider1" },
      { name: "SpinnerActionHandlerDirective", Ctor: SpinnerActionHandlerDirective, absoluteName: "Spinner1" },
      { name: "TabActionHandlerDirective", Ctor: TabActionHandlerDirective, absoluteName: "Tab1" },
      { name: "TextInputActionHandlerDirective", Ctor: TextInputActionHandlerDirective, absoluteName: "TextInput1" },
      { name: "ViewsheetActionHandlerDirective", Ctor: ViewsheetActionHandlerDirective, absoluteName: "Viewsheet1" },
   ];

   it.each(cases)("$name sets dialog.assemblyName to the model's absoluteName", ({ Ctor, absoluteName, modelResponse }) => {
      const vsInfo: any = {
         runtimeId: "vs-123",
         socketConnection: { sendEvent: vi.fn() },
         vsObjects: []
      };
      const model: any = { absoluteName };
      const componentInstance: any = {};
      const modalService = stubModalService(componentInstance);

      const directive = new Ctor(stubModelService(modelResponse ?? {}), modalService, context);
      directive.model = model;
      directive.vsInfo = vsInfo;

      (directive as any).showPropertyDialog();

      expect(modalService.open).toHaveBeenCalledTimes(1);
      expect(componentInstance.assemblyName).toBe(absoluteName);
   });
});

describe("CalcTableActionHandlerDirective - property dialog assemblyName wiring", () => {
   it("gives the constructed CalcTablePropertyDialog an assemblyName matching the model's absoluteName", () => {
      const vsInfo: any = {
         runtimeId: "vs-calc-123",
         socketConnection: { sendEvent: vi.fn() },
         vsObjects: []
      };
      const model: any = { absoluteName: "CalcTable1" };
      const componentInstance: any = {};
      const modalService = stubModalService(componentInstance);
      const injector: any = { get: vi.fn() };

      const directive = new CalcTableActionHandlerDirective(
         stubModelService(), modalService, injector, context);
      directive.model = model;
      directive.vsInfo = vsInfo;

      (directive as any).showPropertyDialog();

      expect(modalService.open).toHaveBeenCalledTimes(1);
      expect(componentInstance.assemblyName).toBe("CalcTable1");
   });
});

describe("VSCrosstabActionHandler - property dialog assemblyName wiring", () => {
   it("gives the constructed CrosstabPropertyDialog an assemblyName matching the model's absoluteName", () => {
      const viewsheetClient: any = { runtimeId: "vs-crosstab-123", sendEvent: vi.fn() };
      const model: any = { absoluteName: "Crosstab1" };
      const componentInstance: any = {};
      const modalService = stubModalService(componentInstance);
      const injector: any = { get: vi.fn() };

      const handler = new VSCrosstabActionHandler(
         stubModelService(), viewsheetClient, modalService, injector, context);

      (handler as any).showCrosstabPropertiesDialog(model, []);

      expect(modalService.open).toHaveBeenCalledTimes(1);
      expect(componentInstance.assemblyName).toBe("Crosstab1");
   });
});

describe("VSTableActionHandler - property dialog assemblyName wiring", () => {
   it("gives the constructed TableViewPropertyDialog an assemblyName matching the model's absoluteName", () => {
      const viewsheetClient: any = { runtimeId: "vs-table-123", sendEvent: vi.fn() };
      const model: any = { absoluteName: "Table1" };
      const componentInstance: any = {};
      const modalService = stubModalService(componentInstance);
      const injector: any = { get: vi.fn() };

      const handler = new VSTableActionHandler(
         stubModelService(), viewsheetClient, modalService, injector, context);

      (handler as any).showTablePropertiesDialog(model, []);

      expect(modalService.open).toHaveBeenCalledTimes(1);
      expect(componentInstance.assemblyName).toBe("Table1");
   });
});
