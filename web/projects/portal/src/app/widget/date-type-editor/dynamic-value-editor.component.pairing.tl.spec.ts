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
 * DynamicValueEditorComponent forwards the pairing inputs (bug #80459) to its one inner
 * dynamic-combo-box, so a host that has no vsId (date comparison, calendar) can still name the
 * property, the assembly and the runtime id.
 */
import { By } from "@angular/platform-browser";
import { render } from "@testing-library/angular";
import { DynamicComboBox } from "../dynamic-combo-box/dynamic-combo-box.component";
import { DynamicValueEditorComponent } from "./dynamic-value-editor.component";
import { makeValueModel } from "./dynamic-value-editor.test-helpers";

async function innerCombo(props: Partial<Record<string, any>>): Promise<DynamicComboBox> {
   const { fixture } = await render(DynamicValueEditorComponent, {
      componentProperties: { valueModel: makeValueModel(), ...props },
   });
   return fixture.debugElement.query(By.directive(DynamicComboBox)).componentInstance;
}

describe("DynamicValueEditorComponent pairing inputs", () => {
   it("forwards dynamicProperty, dynamicAssembly and pairingRuntimeId to the inner combo", async () => {
      const combo = await innerCombo({
         dynamicProperty: "dateComparison:periodsBack", dynamicAssembly: "Chart1",
         pairingRuntimeId: "rt-1"
      });

      expect(combo.dynamicProperty).toBe("dateComparison:periodsBack");
      expect(combo.dynamicAssembly).toBe("Chart1");
      expect(combo.pairingRuntimeId).toBe("rt-1");
   });

   it("passes nothing by default", async () => {
      const combo = await innerCombo({});

      expect(combo.dynamicProperty).toBeNull();
      expect(combo.dynamicAssembly).toBeNull();
      expect(combo.pairingRuntimeId).toBeNull();
   });
});
