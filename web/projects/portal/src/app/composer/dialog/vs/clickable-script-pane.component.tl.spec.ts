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
 * ClickableScriptPane — editorContext
 *
 * G2 Task 2: pins the assembly wiring for the onClick/onEnter script pane so a
 * pane-minted pairing code names the assembly it was opened for. Direct
 * instantiation — ScriptPane child not rendered.
 */

import { ClickableScriptPane } from "./clickable-script-pane.component";
import { ClickableScriptPaneModel } from "../../data/vs/clickable-script-pane-model";

const uiContextServiceMock: any = {
   getDefaultTab: () => "false",
   setDefaultTab: () => {},
};

function createPane(): ClickableScriptPane {
   const comp = new ClickableScriptPane(uiContextServiceMock);
   comp.model = {
      scriptExpression: "",
      onClickExpression: "",
      scriptEnabled: true,
   } as ClickableScriptPaneModel;
   return comp;
}

describe("ClickableScriptPane — editorContext", () => {
   it("should report assemblyMain when the Script radio is selected", () => {
      const comp = createPane();
      comp.assembly = "Table1";
      comp.onClick = false;

      expect(comp.editorContext).toEqual({ kind: "assemblyMain", assembly: "Table1" });
   });

   it("should report assemblyOnClick when the onClick/onEnter radio is selected", () => {
      const comp = createPane();
      comp.assembly = "Table1";
      comp.onClick = true;

      expect(comp.editorContext).toEqual({ kind: "assemblyOnClick", assembly: "Table1" });
   });

   it("should follow the radio when it is toggled", () => {
      const comp = createPane();
      comp.assembly = "Text1";
      comp.onClick = false;

      comp.toggleTextarea();
      expect(comp.editorContext.kind).toBe("assemblyOnClick");

      comp.toggleTextarea();
      expect(comp.editorContext.kind).toBe("assemblyMain");
   });
});
