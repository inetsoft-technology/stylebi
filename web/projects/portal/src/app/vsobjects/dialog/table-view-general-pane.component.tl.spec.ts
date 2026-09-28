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
import { render, screen, within } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { UntypedFormGroup } from "@angular/forms";
import { TableViewGeneralPane } from "./table-view-general-pane.component";

function paneModel(followsDefault: boolean | null) {
   return {
      generalPropPaneModel: { enabled: "True", basicGeneralPaneModel: {} },
      titlePropPaneModel: { visible: true, title: "Table1" },
      tableStylePaneModel: {},
      sizePositionPaneModel: { top: 0, left: 0, width: 400, height: 300 },
      paddingPaneModel: { top: 12, left: 12, bottom: 12, right: 12, followsDefault },
      cellPaddingPaneModel: { top: 4, left: 6, bottom: 4, right: 6, followsDefault },
      showMaxRows: false,
      showSubmitOnChange: false
   } as any;
}

// a marked table follows the default, so followsDefault is a real boolean
const markedTableModel = () => paneModel(true);
// an unmarked one has no default to follow, so the server sends null and the checkbox hides
const unmarkedTableModel = () => paneModel(null);

async function renderPane(model: any) {
   await render(TableViewGeneralPane, {
      componentInputs: { model, form: new UntypedFormGroup({}) }
   });
}

describe("TableViewGeneralPane cell padding", () => {
   it("should show the cell padding group", async () => {
      await renderPane(markedTableModel());

      // the localization macro is not expanded in a unit test, so the raw token is what renders
      // (see padding-pane.component.spec.ts); the template passes this literal label to
      // padding-pane's legend
      expect(screen.getByText("_#(Cell Padding)")).toBeInTheDocument();
   });

   it("should hide the follow-default checkbox on an unmarked table", async () => {
      await renderPane(unmarkedTableModel());   // cellPaddingPaneModel.followsDefault === null

      expect(screen.queryByLabelText(/follow.*default/i)).toBeNull();
   });

   it("should disable the four steppers while following the default", async () => {
      await renderPane(markedTableModel());     // followsDefault === true

      // two padding-pane instances now render a "_#(Top)" stepper; scope to the group whose
      // legend is "_#(Cell Padding)" so it is unambiguous which pane is under test here
      const cellPaddingGroup = screen.getByText("_#(Cell Padding)").closest("fieldset") as HTMLElement;
      expect(within(cellPaddingGroup).getByLabelText("_#(Top)")).toBeDisabled();
   });
});

describe("TableViewGeneralPane padding", () => {
   it("should set the card inset through the Padding group", async () => {
      const model = markedTableModel();
      await renderPane(model);

      // "_#(js:Padding)" is padding-pane's default legend, used when no label is supplied (the
      // new group under test here); it is distinct from the "_#(Cell Padding)" pane already
      // covered above
      const paddingGroup = screen.getByText("_#(js:Padding)").closest("fieldset") as HTMLElement;

      await userEvent.click(within(paddingGroup).getByLabelText(/follow.*default/i));
      await userEvent.clear(within(paddingGroup).getByLabelText("_#(Top)"));
      await userEvent.type(within(paddingGroup).getByLabelText("_#(Top)"), "20");
      // number-stepper only commits its value to the model on blur
      await userEvent.tab();

      expect(model.paddingPaneModel.top).toBe(20);
      expect(model.paddingPaneModel.followsDefault).toBe(false);
   });
});
