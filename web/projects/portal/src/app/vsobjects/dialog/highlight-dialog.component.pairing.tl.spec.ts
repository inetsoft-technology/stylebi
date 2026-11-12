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
 * HighlightDialog - the nested Conditions dialog it opens for a highlight gets the socket and
 * the selected highlight's name (bug #80459), which is what scopes the Connect to Claude pairing
 * of a highlight's expression value to exactly that highlight.
 */
// The recording stubs below bind many inputs of the component they replace; listing them in
// `inputs` is far shorter than one @Input() per property.
/* eslint-disable @angular-eslint/no-inputs-metadata-property */
import { Component, NO_ERRORS_SCHEMA, ViewContainerRef } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { HighlightDialogModel } from "../../widget/highlight/highlight-dialog-model";
import { HighlightModel } from "../../widget/highlight/highlight-model";
import { VSTrapService } from "../util/vs-trap.service";
import { HighlightDialog } from "./highlight-dialog.component";

@Component({ selector: "vs-condition-dialog", template: "",
             inputs: ["model", "runtimeId", "assemblyName", "variableValues", "supportApply",
                      "isHighlight", "resizeable", "nonSupportBrowseFields", "socketConnection",
                      "highlightName"] })
class StubVSConditionDialog {
   static instances: StubVSConditionDialog[] = [];
   socketConnection: any;
   highlightName: string;
   isHighlight: boolean;
   runtimeId: string;
   assemblyName: string;
   constructor() { StubVSConditionDialog.instances.push(this); }
}

describe("HighlightDialog - nested condition dialog pairing (bug #80459)", () => {
   afterEach(() => StubVSConditionDialog.instances.length = 0);

   it("passes the socket and the selected highlight's name to the Conditions dialog", async () => {
      const socket = { commands: null };
      const highlight = <HighlightModel> { name: "High Sales", vsConditionDialogModel: null };
      const model = <HighlightDialogModel> {
         highlights: [highlight], fields: [], nonsupportBrowseFields: [],
         usedHighlightNames: [], tableName: "WsTable",
      };
      const { fixture } = await render(HighlightDialog, {
         componentImports: [StubVSConditionDialog],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [{ provide: NgbModal, useValue: { open: vi.fn() } },
                     { provide: VSTrapService, useValue: { checkTrap: vi.fn() } }],
         componentProperties: {
            model, runtimeId: "rt1", assemblyName: "Chart1", socketConnection: socket,
         } as any,
      });

      fixture.componentInstance.selectHighlight(highlight);
      const view = fixture.componentRef.injector.get(ViewContainerRef)
         .createEmbeddedView(fixture.componentInstance.vsConditionDialog,
                             { close: () => {}, dismiss: () => {} });
      view.detectChanges();

      const dialog = StubVSConditionDialog.instances[0];
      expect(dialog.socketConnection).toBe(socket);
      expect(dialog.highlightName).toBe("High Sales");
      expect(dialog.isHighlight).toBe(true);
      expect(dialog.runtimeId).toBe("rt1");
      expect(dialog.assemblyName).toBe("Chart1");
   });
});
