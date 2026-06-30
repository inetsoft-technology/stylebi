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
 * ConditionFieldComboComponent - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - fieldsModel lifecycle, None item, alias remap, and selected-node expansion
 *   Group 2 [Risk 3] - selection emit/close, list-tree toggle, dropdown open-change, search focus flow
 *   Group 3 [Risk 2] - getTooltip classType dispatch and CSS/dropdown sizing helpers
 *
 * Direct instantiation - child list/tree/dropdown directives are stubbed.
 */

import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { TreeNodeModel } from "../tree/tree-node-model";
import { ConditionFieldComboModel } from "./condition-field-combo-model";
import { ConditionFieldComboComponent } from "./condition-field-combo.component";

afterEach(() => {
   vi.useRealTimers();
   vi.restoreAllMocks();
});

describe("ConditionFieldComboComponent - lifecycle and model shaping [Group 1, Risk 3]", () => {
   it("should add or remove the None item from list and tree models", () => {
      const { comp } = createCombo();
      const field = makeRef("Orders.state");
      comp.fieldsModel = makeFieldsModel([field]);

      comp.addNoneItem = true;
      comp.ngOnChanges({ addNoneItem: {} as any, fieldsModel: {} as any });

      expect(comp.defaultValue).toBe("_#(js:None)");
      expect(comp.listModel[0]).toBe(comp.noneItem);
      expect(comp.treeModel.children[0].label).toBe("_#(js:None)");

      comp.addNoneItem = false;
      comp.ngOnChanges({ addNoneItem: {} as any, fieldsModel: {} as any });

      expect(comp.defaultValue).toBe("");
      expect(comp.listModel).toEqual([field]);
      expect(comp.treeModel.children[0].data).toBe(field);
   });

   it("should expand the parent node containing the selected field on init", () => {
      const { comp } = createCombo();
      const selected = makeRef("Orders.state");
      const model = makeFieldsModel([selected]);
      model.tree.children = [{
         label: "Orders",
         children: [{ label: "state", data: selected, leaf: true }]
      }, {
         label: "Customers",
         children: [{ label: "name", data: makeRef("Customers.name"), leaf: true }]
      }];
      comp.field = selected;
      comp.fieldsModel = model;

      comp.ngOnInit();

      expect(model.tree.children[0].expanded).toBe(true);
      expect(model.tree.children[1].expanded).toBeFalsy();
      expect(comp.selectedNodes).toEqual([model.tree.children[0].children[0]]);
   });

   it("should remap aliased field to the matching fieldsModel entry and emit the replacement", () => {
      const { comp, changeRef } = createCombo();
      const matching = makeRef("alias", {
         attribute: "state",
         entity: "Orders",
         classType: "ColumnRef"
      });
      comp.field = makeRef("Orders.state", {
         attribute: "state",
         entity: "Orders",
         classType: "ColumnRef"
      });
      comp.fieldsModel = makeFieldsModel([matching]);
      const emitSpy = vi.spyOn(comp.onSelectField, "emit");

      comp.ngOnChanges({ field: {} as any, fieldsModel: {} as any });

      expect(comp.field).toBe(matching);
      expect(emitSpy).toHaveBeenCalledWith(matching);
      expect(changeRef.detectChanges).toHaveBeenCalled();
   });
});

describe("ConditionFieldComboComponent - selection toggle and search [Group 2, Risk 3]", () => {
   it("should emit selected tree nodes or None and close the dropdown", () => {
      const { comp, dropdown } = createCombo();
      const field = makeRef("Orders.state");
      const emitSpy = vi.spyOn(comp.onSelectField, "emit");

      comp.showSearch = true;
      comp.selectNode({ label: "state", data: field, leaf: true });
      expect(emitSpy).toHaveBeenCalledWith(field);
      expect(dropdown.close).toHaveBeenCalled();
      expect(comp.showSearch).toBe(false);

      comp.showSearch = true;
      comp.selectNode({ label: "_#(js:None)", data: null, leaf: true });
      expect(emitSpy).toHaveBeenLastCalledWith(null);

      comp.selectNode({ label: "folder", data: null, children: [] });
      expect(emitSpy).toHaveBeenCalledTimes(2);
   });

   it("should map the synthetic None list item to null and detect selected fields", () => {
      const { comp, dropdown } = createCombo();
      const field = makeRef("Orders.state");
      const emitSpy = vi.spyOn(comp.onSelectField, "emit");

      comp.field = null;
      comp.addNoneItem = true;
      expect(comp.isSelectedField(comp.noneItem)).toBe(true);

      comp.selectField(comp.noneItem);
      expect(emitSpy).toHaveBeenCalledWith(null);
      expect(dropdown.close).toHaveBeenCalled();

      comp.field = field;
      expect(comp.isSelectedField(field)).toBe(true);
      expect(comp.isSelectedField(makeRef("Other.field"))).toBe(false);
   });

   it("should toggle list tree display state and clear search on dropdown close", () => {
      const { comp } = createCombo();

      comp.displayList = true;
      expect(comp.convertSwitchBtnTitle()).toBe("_#(js:Switch to Tree)");

      comp.convertDropDownStyle();
      expect(comp.displayList).toBe(false);
      expect(comp.convertSwitchBtnTitle()).toBe("_#(js:Switch to List)");

      comp.showSearch = true;
      comp.dropDownOpenChange(false);
      expect(comp.showSearch).toBe(false);
   });

   it("should open search focus input after timeout and close search by clearing the query", () => {
      vi.useFakeTimers();
      const { comp, dropdown, renderer, focus } = createCombo();
      const event = new MouseEvent("mousedown");
      comp.searchInput = { nativeElement: document.createElement("input") } as any;

      comp.startSearch(event);

      expect(comp.showSearch).toBe(true);
      expect(dropdown.open).toHaveBeenCalledWith(event);

      vi.advanceTimersByTime(200);

      expect(renderer.selectRootElement).toHaveBeenCalledWith(comp.searchInput.nativeElement);
      expect(focus).toHaveBeenCalled();

      comp.searchStr = "state";
      comp.closeSearch(event);

      expect(comp.showSearch).toBe(false);
      expect(comp.searchStr).toBe("");
   });
});

describe("ConditionFieldComboComponent - tooltip and display helpers [Group 3, Risk 2]", () => {
   it("should dispatch tooltips for GroupRef AggregateRef ColumnRef and generic refs", () => {
      const { comp } = createCombo();
      const column = makeColumnRef("Alias", "Column description");
      const group = {
         classType: "GroupRef",
         view: "Group alias",
         description: "Group description",
         ref: column
      } as any;
      const aggregate = {
         classType: "AggregateRef",
         view: "Sum(alias)",
         description: "Aggregate description",
         ref: column
      } as any;

      expect(comp.getTooltip(null)).toBe("");
      expect(comp.getTooltip(group)).toBe("Column description");
      expect(comp.getTooltip(aggregate)).toBe("Column description");
      expect(comp.getTooltip(column)).toBe("Column description");
      expect(comp.getTooltip(makeRef("Generic", {
         classType: "ExpressionRef",
         description: "Expression description"
      }))).toBe("Expression description");

      comp.showOriginalName = true;
      expect(comp.getTooltip(group)).toContain("Alias: Group alias (Orders.state)");
      expect(comp.getTooltip(aggregate)).toContain("Alias: Sum(alias) (Orders.state)");
      expect(comp.getTooltip(column)).toContain("Alias: Alias (Orders.state)");
   });

   it("should expose css icon and dropdown sizing helpers", () => {
      const { comp } = createCombo();
      const dropdownBody = document.createElement("div");
      Object.defineProperty(dropdownBody, "offsetWidth", { value: 240 });
      comp.dropdownBody = { nativeElement: dropdownBody } as any;
      comp.treeModel = { children: [] };

      expect(comp.getCSSIcon({ data: makeRef("Orders.state") })).toBe("column-icon");
      expect(comp.getCSSIcon({ label: "leaf", data: null, children: [] })).toBe("column-icon");
      expect(comp.getCSSIcon({ label: "folder", data: null, children: [{ label: "child" }] })).toBeNull();
      expect(comp.dropdownMinWidth).toBe(240);
      expect(comp.dropdownHeight).toBe(300);

      comp.treeModel = null;
      expect(comp.dropdownHeight).toBeNull();
   });

   it("should pass expand and collapse events through the virtual scroll datasource", () => {
      const { comp } = createCombo();
      const node: TreeNodeModel = { label: "Orders", children: [] };
      const expanded = vi.spyOn(comp.virtualScrollTreeDatasource, "nodeExpanded").mockImplementation(() => {});
      const collapsed = vi.spyOn(comp.virtualScrollTreeDatasource, "nodeCollapsed").mockImplementation(() => {});

      comp.nodeStateChanged(node, true);
      comp.nodeStateChanged(node, false);

      expect(expanded).toHaveBeenCalledWith(comp.treeModel, node);
      expect(collapsed).toHaveBeenCalledWith(comp.treeModel, node);
   });
});

function createCombo() {
   const focus = vi.fn();
   const changeRef = { detectChanges: vi.fn() };
   const renderer = {
      selectRootElement: vi.fn(() => ({ focus }))
   };
   const comp = new ConditionFieldComboComponent(changeRef as any, renderer as any);
   const dropdown = {
      open: vi.fn(),
      close: vi.fn()
   };

   comp.fieldsDropdown = dropdown as any;
   comp.displayList = true;
   comp.addNoneItem = true;
   comp.fieldsModel = makeFieldsModel([makeRef("Orders.state")]);
   comp.treeModel = comp.createTreeModel();
   comp.listModel = comp.createListModel();

   return { comp, changeRef, renderer, dropdown, focus };
}

function makeFieldsModel(fields: DataRef[]): ConditionFieldComboModel {
   return {
      list: fields,
      tree: {
         label: "_#(js:root)",
         children: fields.map(field => ({
            label: field.view,
            data: field,
            leaf: true
         }))
      }
   };
}

function makeRef(view: string, overrides: Partial<DataRef> = {}): DataRef {
   const parts = view.split(".");

   return {
      classType: "ColumnRef",
      name: view,
      view,
      attribute: parts[1] || view,
      entity: parts.length > 1 ? parts[0] : "Orders",
      dataType: XSchema.STRING,
      description: `${view} description`,
      ...overrides
   };
}

function makeColumnRef(view: string, description: string): DataRef {
   return makeRef(view, {
      classType: "ColumnRef",
      entity: "Orders",
      attribute: "state",
      description,
      dataRefModel: {
         classType: "AttributeRef",
         view: "Orders.state"
      } as any
   } as any);
}
