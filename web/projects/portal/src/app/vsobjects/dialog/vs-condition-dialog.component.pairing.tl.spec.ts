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
 * VSConditionDialog - the condition-value pairing scope it hands down to the condition pane
 * (bug #80459): the owning assembly plus, for a highlight, the highlight's name. Built once, so
 * the child inputs keep a stable identity; absent for a highlight with no name, so the Connect
 * button hides rather than minting `highlight:`.
 */
// The recording stubs below bind many inputs of the component they replace; listing them in
// `inputs` is far shorter than one @Input() per property.
/* eslint-disable @angular-eslint/no-inputs-metadata-property */
import { HttpClient } from "@angular/common/http";
import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { VSConditionDialogModel } from "../../common/data/condition/vs-condition-dialog-model";
import { ConditionPane } from "../../widget/condition/condition-pane.component";
import { VSConditionDialog } from "./vs-condition-dialog.component";

function createDialog(opts: { isHighlight?: boolean, highlightName?: string,
                              socketConnection?: any } = {})
{
   const comp = new VSConditionDialog(
      {} as any, {} as any, {} as any,
      { dirtyCondition: null, dirtyJunction: null } as any, {} as any,
      { nativeElement: {} } as any);
   comp.resizeable = false;
   comp.runtimeId = "rt1";
   comp.assemblyName = "Chart1";
   comp.isHighlight = !!opts.isHighlight;
   comp.highlightName = opts.highlightName;
   comp.socketConnection = opts.socketConnection;
   comp.checkTrap = () => {};
   comp.model = <VSConditionDialogModel> { tableName: "WsTable", fields: [], conditionList: [] };
   return comp;
}

describe("VSConditionDialog - pairing scope (bug #80459)", () => {
   it("scopes a filter dialog to its assembly with no highlight name", () => {
      const comp = createDialog();
      comp.ngOnInit();

      expect(comp.pairingScope).toEqual({ assembly: "Chart1", highlightName: null });
   });

   it("scopes a highlight dialog to the highlight's name", () => {
      const comp = createDialog({ isHighlight: true, highlightName: "High Sales" });
      comp.ngOnInit();

      expect(comp.pairingScope).toEqual({ assembly: "Chart1", highlightName: "High Sales" });
   });

   it("ignores a highlight name on a filter dialog", () => {
      const comp = createDialog({ highlightName: "High Sales" });
      comp.ngOnInit();

      expect(comp.pairingScope).toEqual({ assembly: "Chart1", highlightName: null });
   });

   it("hands down no scope for a highlight with a blank name", () => {
      for(const name of [undefined, null, "", "  "]) {
         const comp = createDialog({ isHighlight: true, highlightName: name });
         comp.ngOnInit();

         expect(comp.pairingScope).toBeNull();
      }
   });

   it("hands down no scope without an assembly", () => {
      const comp = createDialog();
      comp.assemblyName = null;
      comp.ngOnInit();

      expect(comp.pairingScope).toBeNull();
   });

   it("keeps one scope object across change detection", () => {
      const comp = createDialog();
      comp.ngOnInit();

      expect(comp.pairingScope).toBe(comp.pairingScope);
   });
});

@Component({ selector: "condition-pane", template: "",
             inputs: ["provider", "fields", "table", "conditionList", "isHighlight", "vsId",
                      "runtimeId", "socketConnection", "vsConditionScope"] })
class StubConditionPane {
   static instances: StubConditionPane[] = [];
   runtimeId: string;
   socketConnection: any;
   vsConditionScope: any;
   vsId: string;
   constructor() { StubConditionPane.instances.push(this); }
}

describe("VSConditionDialog - template (bug #80459)", () => {
   afterEach(() => StubConditionPane.instances.length = 0);

   it("passes the runtime, the socket and the scope to the condition pane", async () => {
      const socket = { commands: null };
      await render(VSConditionDialog, {
         componentImports: [StubConditionPane],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [{ provide: HttpClient, useValue: {} }],
         componentProperties: {
            model: <VSConditionDialogModel> { tableName: "WsTable", fields: [], conditionList: [] },
            runtimeId: "rt1", assemblyName: "Table1", socketConnection: socket,
            checkTrap: () => {}, resizeable: false,
         } as any,
      });

      const pane = StubConditionPane.instances[0];
      expect(pane.runtimeId).toBe("rt1");
      expect(pane.vsId).toBe("rt1");
      expect(pane.socketConnection).toBe(socket);
      expect(pane.vsConditionScope).toEqual({ assembly: "Table1", highlightName: null });
   });
});
