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
 * DynamicComboBox pairing inputs (bug #80459): `dynamicProperty`, `dynamicAssembly` and
 * `pairingRuntimeId` decide whether the formula dialog it opens gets a Connect to Claude
 * transport. The dialog gets NOTHING unless the property, the assembly, the runtime id and a
 * registered client are all present and the combo is editable.
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { DynamicComboBox } from "./dynamic-combo-box.component";
import { ComponentTool } from "../../common/util/component-tool";
import { SheetClientRegistry } from "../../common/viewsheet-client/sheet-client-registry.service";

const MODAL_MOCK = { open: vi.fn() };

function client(runtimeId: string): any {
   return { runtimeId };
}

async function openDialog(props: Partial<Record<string, any>>,
                          registry?: SheetClientRegistry): Promise<any> {
   const { fixture } = await render(DynamicComboBox, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: NgbModal, useValue: MODAL_MOCK },
         ...(registry ? [{ provide: SheetClientRegistry, useValue: registry }] : [])
      ],
      componentProperties: { variables: [], values: [], grayedOutValues: [], ...props },
   });
   const dialog: any = {};
   vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialog);
   (fixture.componentInstance as DynamicComboBox).showFormulaEditor();
   return dialog;
}

function registryWith(c: any, sheetType: "VIEWSHEET" | "WORKSHEET" = "VIEWSHEET") {
   const registry = new SheetClientRegistry();
   registry.register(c, sheetType);
   return registry;
}

const FULL = { dynamicProperty: "visible", assemblyName: "Text1", vsId: "rt-1" };

afterEach(() => vi.restoreAllMocks());

describe("DynamicComboBox pairing", () => {
   it("passes the whole pairing to the dialog when everything is present", async () => {
      const c = client("rt-1");
      const dialog = await openDialog(FULL, registryWith(c));

      expect(dialog.dynamicProperty).toBe("visible");
      expect(dialog.contextTable).toBe("Text1");
      expect(dialog.runtimeId).toBe("rt-1");
      expect(dialog.socketConnection).toBe(c);
      expect(dialog.isVSContext).toBe(true);
   });

   it("derives isVSContext from the registered sheet type", async () => {
      const dialog = await openDialog(FULL, registryWith(client("rt-1"), "WORKSHEET"));
      expect(dialog.isVSContext).toBe(false);
   });

   it("prefers dynamicAssembly over assemblyName and pairingRuntimeId over vsId", async () => {
      const c = client("rt-9");
      const dialog = await openDialog(
         { ...FULL, dynamicAssembly: "Chart1", pairingRuntimeId: "rt-9" }, registryWith(c));

      expect(dialog.contextTable).toBe("Chart1");
      expect(dialog.runtimeId).toBe("rt-9");
      expect(dialog.socketConnection).toBe(c);
   });

   it("uses pairingRuntimeId when the combo binds no vsId", async () => {
      const c = client("rt-5");
      const dialog = await openDialog(
         { dynamicProperty: "min", assemblyName: "Cal1", pairingRuntimeId: "rt-5" },
         registryWith(c));

      expect(dialog.runtimeId).toBe("rt-5");
   });

   it.each([
      ["no property", { dynamicProperty: undefined }],
      ["a null property", { dynamicProperty: null }],
      ["an empty property", { dynamicProperty: "" }],
      ["no assembly", { assemblyName: undefined }],
      ["an empty assembly", { assemblyName: "" }],
      ["no runtime id", { vsId: undefined }],
      ["an empty runtime id", { vsId: "" }],
      ["a disabled combo", { disable: true }],
   ])("passes NO pairing input with %s", async (_label, override) => {
      const dialog = await openDialog({ ...FULL, ...override }, registryWith(client("rt-1")));

      expect(dialog.dynamicProperty).toBeUndefined();
      expect(dialog.contextTable).toBeUndefined();
      expect(dialog.runtimeId).toBeUndefined();
      expect(dialog.socketConnection).toBeUndefined();
      expect(dialog.isVSContext).toBeUndefined();
   });

   it("passes NO pairing input when no client owns the runtime id", async () => {
      const dialog = await openDialog(FULL, registryWith(client("other")));

      expect(dialog.dynamicProperty).toBeUndefined();
      expect(dialog.socketConnection).toBeUndefined();
   });

   it("passes NO pairing input when the registry is ambiguous", async () => {
      vi.spyOn(console, "warn").mockImplementation(() => {});
      const registry = registryWith(client("rt-1"));
      registry.register(client("rt-1"), "VIEWSHEET");

      const dialog = await openDialog(FULL, registry);

      expect(dialog.dynamicProperty).toBeUndefined();
      expect(dialog.socketConnection).toBeUndefined();
   });

   it("still opens the dialog with its existing inputs when no registry is provided", async () => {
      const dialog = await openDialog({ ...FULL, isCondition: true });

      expect(dialog.vsId).toBe("rt-1");
      expect(dialog.assemblyName).toBe("Text1");
      expect(dialog.isCondition).toBe(true);
      expect(dialog.socketConnection).toBeUndefined();
      expect(dialog.dynamicProperty).toBeUndefined();
   });

   it("keeps passing the existing dialog inputs unchanged when pairing is on", async () => {
      const dialog = await openDialog({ ...FULL, isCondition: true }, registryWith(client("rt-1")));

      expect(dialog.vsId).toBe("rt-1");
      expect(dialog.assemblyName).toBe("Text1");
      expect(dialog.isCondition).toBe(true);
   });

   it("looks the client up when the dialog opens, not at construction", async () => {
      const registry = new SheetClientRegistry();
      const { fixture } = await render(DynamicComboBox, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [{ provide: NgbModal, useValue: MODAL_MOCK },
                     { provide: SheetClientRegistry, useValue: registry }],
         componentProperties: { variables: [], values: [], grayedOutValues: [], ...FULL },
      });
      const late = client("rt-1");
      registry.register(late, "VIEWSHEET");
      const dialog: any = {};
      vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialog);

      (fixture.componentInstance as DynamicComboBox).showFormulaEditor();

      expect(dialog.socketConnection).toBe(late);
   });
});
