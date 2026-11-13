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
 * The general property panes name the dynamic value their combo edits (bug #80459 follow-up):
 * Visible = `visible`, Enabled = `enabled`, Title = `title`. The names are the plugin's own
 * `set_assembly_properties` keys; the combos get the assembly and runtime id from the pane's
 * inputs, and a pane in the print layout editor (`layoutObject`) names nothing.
 */
import { UntypedFormGroup } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { render } from "@testing-library/angular";
import { DynamicComboBox } from "../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { BasicGeneralPaneModel } from "../model/basic-general-pane-model";
import { GeneralPropPaneModel } from "../model/general-prop-pane-model";
import { BasicGeneralPane } from "./basic-general-pane.component";
import { GeneralPropPane } from "./general-prop-pane.component";
import { TitlePropPane } from "./title-prop-pane.component";

function basicModel(): BasicGeneralPaneModel {
   return {
      name: "Text1", visible: "Show", shadow: false, enabled: true, sendSelections: false,
      primary: true, refresh: false, editable: false, nameEditable: true,
      showShadowCheckbox: false, showEnabledCheckbox: true, showRefreshCheckbox: false,
      showEditableCheckbox: false, showSendSelectionsCheckbox: false, objectNames: []
   } as any;
}

function generalModel(): GeneralPropPaneModel {
   return {
      basicGeneralPaneModel: basicModel(), showSubmitCheckbox: false, submitOnChange: true,
      showEnabledGroup: true, enabled: "True", popLocation: null
   } as any;
}

async function combos(component: any, props: Record<string, any>): Promise<DynamicComboBox[]> {
   const { fixture } = await render(component, { componentProperties: props });
   return fixture.debugElement.queryAll(By.directive(DynamicComboBox))
      .map(d => d.componentInstance);
}

describe("dynamic property names of the general panes", () => {
   it("BasicGeneralPane names its Visible combo `visible`", async () => {
      const [visible] = await combos(BasicGeneralPane, {
         model: basicModel(), form: new UntypedFormGroup({}), vsId: "rt-1", assemblyName: "Text1"
      });

      expect(visible.dynamicProperty).toBe("visible");
      expect(visible.assemblyName).toBe("Text1");
      expect(visible.vsId).toBe("rt-1");
   });

   it("BasicGeneralPane names nothing in the print layout editor", async () => {
      const [visible] = await combos(BasicGeneralPane, {
         model: basicModel(), form: new UntypedFormGroup({}), vsId: "rt-1", assemblyName: "Text1",
         layoutObject: true
      });

      expect(visible.dynamicProperty).toBeNull();
   });

   it("GeneralPropPane names its Visible combo `visible` and its Enabled combo `enabled`", async () => {
      const found = await combos(GeneralPropPane, {
         model: generalModel(), form: new UntypedFormGroup({}), vsId: "rt-1",
         assemblyName: "Chart1"
      });

      expect(found.map(c => c.dynamicProperty).sort()).toEqual(["enabled", "visible"]);
      expect(found.every(c => c.assemblyName === "Chart1" && c.vsId === "rt-1")).toBe(true);
   });

   it("GeneralPropPane names nothing in the print layout editor", async () => {
      const found = await combos(GeneralPropPane, {
         model: generalModel(), form: new UntypedFormGroup({}), vsId: "rt-1",
         assemblyName: "Chart1", layoutObject: true
      });

      expect(found.map(c => c.dynamicProperty)).toEqual([null, null]);
   });

   it("TitlePropPane names its combo `title` and passes it the assembly", async () => {
      const [title] = await combos(TitlePropPane, {
         model: { visible: true, title: "T" }, vsId: "rt-1", assemblyName: "Table1"
      });

      expect(title.dynamicProperty).toBe("title");
      expect(title.assemblyName).toBe("Table1");
      expect(title.vsId).toBe("rt-1");
   });
});
