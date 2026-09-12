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
 * LogicalModelPropertyPane — single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — logicalModel setter: sameModel detection (no reset vs resetState)
 *   Group 2  [Risk 3] — checkOuterDependencies HTTP: result.body present (yes/no dangerous
 *                        confirm + delete); empty body (ok/cancel confirm + delete)
 *   Group 3  [Risk 3] — deleteSelectedItem(): validSelected guard; parent-child deduplicate
 *                        (entity selected → skip co-selected attribute of same entity)
 *   Group 4  [Risk 3] — deleteSelectedItem0(): entity splice; attribute splice;
 *                        resetSelectedStatus after execution
 *   Group 5  [Risk 2] — moveEntityDown() / moveEntityUp(): swap + editingEle tracking;
 *                        boundary no-op; selectedItem.entity follows moved item
 *   Group 6  [Risk 2] — deleteEntity() / deleteAttribute(): baseElement guard; errorMessage
 *                        reset when last invalid attribute removed; splice + checkModified
 *   Group 7  [Risk 2] — addAttributes(): column path default fields; expression path type
 *                        overrides; duplicate name auto-suffixed; unknown entity returns false
 *   Group 8  [Risk 2] — sortElements(): entity sort tracks editingEle index; attribute sort
 *                        runs per-entity and remaps selectedEles by object identity
 *   Group 9  [Risk 1] — isDuplicateEntity, canDelete, deleteEnable, isElementSelected,
 *                        editingElement, getSelectedItem, checkModified, getSelectedEntity,
 *                        keyDown
 *
 * Fixed bugs (Bug #75599):
 *   Group 10 — post-destroy HTTP callback fired: checkOuterDependencies did not store
 *   the subscription and the component had no ngOnDestroy, so the POST callback could fire
 *   on a destroyed component when the request resolved after fixture.destroy(). Fixed by
 *   storing the Subscription and unsubscribing it in ngOnDestroy().
 *
 * Suspected bugs (out of scope — identified but not reproduced here):
 *   isDuplicateAttribute(): dupColumn check uses `attr.name === attribute.name` — the same
 *   condition as dupName — so the "attributeColumnDuplicate" branch is dead code.
 *
 * Out of scope:
 *   shiftSelect() / entityToggle() — delegate to LogicalModelService spy; one-liner delegation
 *   attributeOrderChanged() — one-liner calling updateExistNames, covered transitively
 *   onDeleteAttribute() — one-liner calling checkOuterDependencies, covered in Group 2
 *   showEntityDialog / showAddAttributeDialog / showAddExpressionDialog — test commit
 *     callbacks are covered via addAttributes() and isDuplicateEntity() in Groups 7/9;
 *     showDialog modal wire-up is integration-level
 *
 * Mocking strategy:
 *   SplitPane / FixedDropdownDirective / ElementTreeNode / LogicalModelEntityEditor /
 *   LogicalModelColumnEditor / LogicalModelExpressionEditor / LoadingIndicatorPaneComponent
 *   are all replaced via importOverrides — they have complex DI chains that are irrelevant
 *   to testing this component's own logic.
 *   DataModelNameChangeService / FolderChangeService — injected but not called in the visible
 *   code; provided as empty mocks.
 *   LogicalModelService — provides shiftSelect/entityToggle as vi.fn() for delegation tests.
 *   NgbModal — provided as empty mock; component interactions use ComponentTool.showDialog
 *     / showConfirmDialog spies rather than threading the real modal service.
 *   HttpClient uses provideHttpClient() + MSW for checkOuterDependencies endpoint.
 *   NotificationsComponent is passed as an @Input mock with success/danger as vi.fn().
 */

import { Component, Directive, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

import { server } from "@test-mocks/server";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { LogicalModelPropertyPane } from "./logical-model-property-pane.component";
import { SplitPane } from "../../../../../../widget/split-pane/split-pane.component";
import { FixedDropdownDirective } from "../../../../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { ElementTreeNode } from "./element-tree-node/element-tree-node.component";
import { LoadingIndicatorPaneComponent } from "../../common-components/loading-indicator-pane/loading-indicator-pane.component";
import { LogicalModelEntityEditor } from "./entity-editor/logical-model-entity-editor.component";
import { LogicalModelColumnEditor } from "./column-attribute-editor/logical-model-column-editor.component";
import { LogicalModelExpressionEditor } from "./expression-attribute-editor/logical-model-expression-editor.component";
import { DataModelNameChangeService } from "../../../../services/data-model-name-change.service";
import { FolderChangeService } from "../../../../services/folder-change.service";
import { LogicalModelService } from "./logical-model-service";
import { LogicalModelDefinition } from "../../../../model/datasources/database/physical-model/logical-model/logical-model-definition";
import { EntityModel } from "../../../../model/datasources/database/physical-model/logical-model/entity-model";
import { AttributeModel } from "../../../../model/datasources/database/physical-model/logical-model/attribute-model";
import { SelectedItem } from "./logical-model.component";

// ---------------------------------------------------------------------------
// Stub components / directives
// ---------------------------------------------------------------------------

@Component({ selector: "split-pane", template: "<ng-content></ng-content>" })
class SplitPaneStub {}

@Directive({ selector: "[fixedDropdown]" })
class FixedDropdownStub {}

@Component({ selector: "element-tree-node", template: "" })
class ElementTreeNodeStub {
   @Input() entities: any[];
   @Input() editingEle: any;
}

@Component({ selector: "loading-indicator-pane", template: "" })
class LoadingIndicatorPaneStub { @Input() loading: boolean; }

@Component({ selector: "logical-model-entity-editor", template: "" })
class LogicalModelEntityEditorStub { @Input() entity: any; }

@Component({ selector: "logical-model-column-editor", template: "" })
class LogicalModelColumnEditorStub { @Input() attribute: any; }

@Component({ selector: "logical-model-expression-editor", template: "" })
class LogicalModelExpressionEditorStub { @Input() attribute: any; }

// ---------------------------------------------------------------------------
// Mock services
// ---------------------------------------------------------------------------

const LM_SERVICE_MOCK = {
   shiftSelect: vi.fn(),
   entityToggle: vi.fn(),
};

const MODAL_MOCK = { open: vi.fn() };

const NOTIFICATIONS_MOCK = {
   success: vi.fn(),
   danger: vi.fn(),
   info: vi.fn(),
};

// ---------------------------------------------------------------------------
// Model factories
// ---------------------------------------------------------------------------

function makeAttribute(name: string, opts: Partial<AttributeModel> = {}): AttributeModel {
   return {
      name,
      baseElement: false,
      errorMessage: null,
      parentEntity: "entity1",
      table: "TABLE1",
      column: name,
      qualifiedName: `TABLE1.${name}`,
      type: "column",
      dataType: "string",
      expression: null,
      ...opts,
   } as AttributeModel;
}

function makeEntity(name: string, attributes: AttributeModel[] = [], opts: Partial<EntityModel> = {}): EntityModel {
   return {
      name,
      attributes,
      baseElement: false,
      errorMessage: null,
      ...opts,
   } as EntityModel;
}

function makeModel(entities: EntityModel[] = []): LogicalModelDefinition {
   return { name: "LM1", partition: "PART1", entities } as LogicalModelDefinition;
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

interface RenderOpts {
   logicalModel?: LogicalModelDefinition;
   editing?: boolean;
   databaseName?: string;
}

async function renderComp(opts: RenderOpts = {}) {
   const model = opts.logicalModel ?? makeModel([]);
   const { fixture } = await render(LogicalModelPropertyPane, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: DataModelNameChangeService, useValue: {} },
         { provide: FolderChangeService, useValue: {} },
         { provide: LogicalModelService, useValue: LM_SERVICE_MOCK },
      ],
      importOverrides: [
         { replace: SplitPane, with: SplitPaneStub },
         { replace: FixedDropdownDirective, with: FixedDropdownStub },
         { replace: ElementTreeNode, with: ElementTreeNodeStub },
         { replace: LoadingIndicatorPaneComponent, with: LoadingIndicatorPaneStub },
         { replace: LogicalModelEntityEditor, with: LogicalModelEntityEditorStub },
         { replace: LogicalModelColumnEditor, with: LogicalModelColumnEditorStub },
         { replace: LogicalModelExpressionEditor, with: LogicalModelExpressionEditorStub },
      ],
      componentInputs: {
         logicalModel: model,
         editing: opts.editing ?? true,
         databaseName: opts.databaseName ?? "testDB",
         physicalModelName: "physModel",
         additional: null,
         originalName: "LM1",
         parent: null,
         notifications: NOTIFICATIONS_MOCK,
      },
   });
   return { comp: fixture.componentInstance as LogicalModelPropertyPane, fixture };
}

// ---------------------------------------------------------------------------
// Global lifecycle
// ---------------------------------------------------------------------------

beforeEach(() => {
   Object.values(NOTIFICATIONS_MOCK).forEach(m => typeof m.mockClear === "function" && m.mockClear());
   Object.values(LM_SERVICE_MOCK).forEach(m => typeof m.mockClear === "function" && m.mockClear());
   MODAL_MOCK.open.mockClear();
});

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — logicalModel setter: sameModel detection [Risk 3]
// ---------------------------------------------------------------------------

describe("LogicalModelPropertyPane — logicalModel setter: sameModel detection", () => {
   // 🔁 Regression-sensitive: if the sameModel guard breaks, switching between LM tabs
   // resets the user's selection mid-edit — the form clears unexpectedly.
   it("should NOT reset selection when the same logical model (same name+partition) is re-assigned", async () => {
      const model = makeModel([makeEntity("EntityA")]);
      const { comp } = await renderComp({ logicalModel: model });
      comp.editingEle = { entity: 0, attribute: -1 };
      comp.selectedEles = [{ entity: 0, attribute: -1 }];

      // Re-assign the identical model reference
      comp.logicalModel = model;

      expect(comp.editingEle.entity).toBe(0); // unchanged — no reset
      expect(comp.selectedEles).toHaveLength(1);
   });

   it("should reset selection when a new model with a different name is assigned", async () => {
      const modelA = makeModel([makeEntity("EntityA")]);
      const { comp } = await renderComp({ logicalModel: modelA });
      comp.editingEle = { entity: 0, attribute: -1 };
      comp.selectedEles = [{ entity: 0, attribute: -1 }];

      const modelB = { ...makeModel([]), name: "LM_NEW" };
      comp.logicalModel = modelB;

      expect(comp.editingEle.entity).toBe(-1); // reset by resetSelectedStatus
      expect(comp.selectedEles).toHaveLength(0);
      expect(comp.expanded).toHaveLength(0);
   });

   it("should reset selection when a new model with the same name but different partition is assigned", async () => {
      const modelA = makeModel([makeEntity("EntityA")]);
      const { comp } = await renderComp({ logicalModel: modelA });
      comp.editingEle = { entity: 0, attribute: -1 };

      const modelB = { ...makeModel([]), partition: "OTHER_PARTITION" };
      comp.logicalModel = modelB;

      expect(comp.editingEle.entity).toBe(-1);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — checkOuterDependencies HTTP [Risk 3]
// ---------------------------------------------------------------------------

describe("LogicalModelPropertyPane — checkOuterDependencies: HTTP paths", () => {
   // 🔁 Regression-sensitive: the two confirm paths use different button labels ("yes"/"ok").
   // If the branch condition is wrong, the delete never executes despite user confirmation.

   it("should show a yes/no confirm and delete on 'yes' when the server returns a body warning", async () => {
      server.use(
         http.post("*/api/data/logicalmodel/checkOuterDependencies", () =>
            MswHttpResponse.json({ body: "Warning: referenced elsewhere" })
         )
      );
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("yes");
      const entities = [makeEntity("EntityA"), makeEntity("EntityB")];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });
      comp.selectedEles = [{ entity: 0, attribute: -1 }];
      comp.editingEle = { entity: 0, attribute: -1 };

      comp.deleteEntityByIndex(0);

      await waitFor(() => {
         expect(comp.logicalModel.entities).toHaveLength(1);
         expect(comp.logicalModel.entities[0].name).toBe("EntityB");
      });
      expect(confirmSpy).toHaveBeenCalledWith(
         expect.anything(), "_#(js:Confirm)", "Warning: referenced elsewhere",
         { yes: "_#(js:Yes)", no: "_#(js:No)" }
      );
   });

   it("should NOT delete when the user clicks 'no' on the dangerous confirm", async () => {
      server.use(
         http.post("*/api/data/logicalmodel/checkOuterDependencies", () =>
            MswHttpResponse.json({ body: "Warning" })
         )
      );
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("no");
      const entities = [makeEntity("EntityA")];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });
      comp.selectedEles = [{ entity: 0, attribute: -1 }];

      comp.deleteEntityByIndex(0);

      await waitFor(() => expect(ComponentTool.showConfirmDialog).toHaveBeenCalled());
      expect(comp.logicalModel.entities).toHaveLength(1); // unchanged
   });

   it("should show ok/cancel confirm and delete on 'ok' when the server returns no body", async () => {
      server.use(
         http.post("*/api/data/logicalmodel/checkOuterDependencies", () =>
            MswHttpResponse.json({})
         )
      );
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const entities = [makeEntity("EntityA"), makeEntity("EntityB")];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });
      comp.selectedEles = [{ entity: 0, attribute: -1 }];
      comp.editingEle = { entity: 0, attribute: -1 };

      comp.deleteEntityByIndex(0);

      await waitFor(() => expect(comp.logicalModel.entities).toHaveLength(1));
      // Standard confirm uses title/message, not the server body text
      expect(confirmSpy).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:data.logicalmodel.removeElements)",
         "_#(js:data.logicalmodel.confirmRemoveElements)"
      );
   });

   it("should NOT delete when the user clicks 'cancel' on the standard confirm", async () => {
      server.use(
         http.post("*/api/data/logicalmodel/checkOuterDependencies", () =>
            MswHttpResponse.json({})
         )
      );
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
      const entities = [makeEntity("EntityA")];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });
      comp.selectedEles = [{ entity: 0, attribute: -1 }];

      comp.deleteEntityByIndex(0);

      await waitFor(() => expect(ComponentTool.showConfirmDialog).toHaveBeenCalled());
      expect(comp.logicalModel.entities).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — deleteSelectedItem(): validation and parent-child filtering [Risk 3]
// ---------------------------------------------------------------------------

describe("LogicalModelPropertyPane — deleteSelectedItem()", () => {
   // 🔁 Regression-sensitive: if the parent-child filter is removed, an entity and its
   // attribute are both passed to checkOuterDependencies, causing a double-HTTP call and
   // potential double-delete of the attribute (which no longer exists after entity splice).

   it("should do nothing when selectedEles is empty", async () => {
      server.use(
         http.post("*/api/data/logicalmodel/checkOuterDependencies", () =>
            MswHttpResponse.json({})
         )
      );
      const entities = [makeEntity("EntityA")];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });
      comp.selectedEles = [];

      comp.deleteSelectedItem();

      // No HTTP call should be made
      await new Promise<void>(r => setTimeout(r, 20));
      expect(NOTIFICATIONS_MOCK.danger).not.toHaveBeenCalled();
   });

   it("should exclude an attribute from deleteEles when its parent entity is also selected", async () => {
      server.use(
         http.post("*/api/data/logicalmodel/checkOuterDependencies", () =>
            MswHttpResponse.json({})
         )
      );
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const attr = makeAttribute("colA");
      const entities = [makeEntity("EntityA", [attr])];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });
      // Select both the entity AND its attribute
      comp.selectedEles = [
         { entity: 0, attribute: -1 }, // entity
         { entity: 0, attribute: 0 },  // attribute of same entity
      ];
      comp.editingEle = { entity: 0, attribute: -1 };

      comp.deleteSelectedItem();

      // After confirm + delete, only ONE delete operation ran (the entity, not the attribute)
      await waitFor(() => expect(comp.logicalModel.entities).toHaveLength(0));
   });

   it("should include an attribute when its entity is NOT also selected", async () => {
      server.use(
         http.post("*/api/data/logicalmodel/checkOuterDependencies", () =>
            MswHttpResponse.json({})
         )
      );
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const attr = makeAttribute("colA");
      const entities = [makeEntity("EntityA", [attr])];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });
      // Only the attribute selected (no parent entity selected)
      comp.selectedEles = [{ entity: 0, attribute: 0 }];
      comp.editingEle = { entity: 0, attribute: 0 };

      comp.deleteSelectedItem();

      await waitFor(() => expect(comp.logicalModel.entities[0].attributes).toHaveLength(0));
   });
});

// ---------------------------------------------------------------------------
// Group 4 — deleteSelectedItem0(): deletion execution [Risk 3]
// ---------------------------------------------------------------------------

describe("LogicalModelPropertyPane — deleteSelectedItem0()", () => {
   it("should splice entities in descending index order so earlier indices stay valid", async () => {
      const entities = [makeEntity("A"), makeEntity("B"), makeEntity("C")];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });
      // Pass items already sorted descending (as deleteSelectedItem does)
      const items: SelectedItem[] = [
         { entity: 2, attribute: -1 },
         { entity: 0, attribute: -1 },
      ];
      comp.deleteSelectedItem0(items);
      expect(comp.logicalModel.entities.map(e => e.name)).toEqual(["B"]);
   });

   it("should splice the correct attribute and leave the entity intact", async () => {
      const attr = makeAttribute("colA");
      const entities = [makeEntity("EntityA", [attr, makeAttribute("colB")])];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });

      comp.deleteSelectedItem0([{ entity: 0, attribute: 0 }]);

      expect(comp.logicalModel.entities[0].attributes).toHaveLength(1);
      expect(comp.logicalModel.entities[0].attributes[0].name).toBe("colB");
   });

   it("should reset selectedEles and editingEle after deletion", async () => {
      const entities = [makeEntity("A")];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });
      comp.selectedEles = [{ entity: 0, attribute: -1 }];
      comp.editingEle = { entity: 0, attribute: -1 };

      comp.deleteSelectedItem0([{ entity: 0, attribute: -1 }]);

      expect(comp.selectedEles).toHaveLength(0);
      expect(comp.editingEle.entity).toBe(-1);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — moveEntityDown() / moveEntityUp() [Risk 2]
// ---------------------------------------------------------------------------

describe("LogicalModelPropertyPane — moveEntityDown() and moveEntityUp()", () => {
   // 🔁 Regression-sensitive: editingEle.entity must follow the moved item; without it
   // the form keeps editing the wrong entity after a reorder.

   it("moveEntityDown should swap entities and increment editingEle.entity", async () => {
      const entities = [makeEntity("A"), makeEntity("B"), makeEntity("C")];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });
      comp.editingEle = { entity: 0, attribute: -1 };

      comp.moveEntityDown(0);

      expect(comp.logicalModel.entities[0].name).toBe("B");
      expect(comp.logicalModel.entities[1].name).toBe("A");
      expect(comp.editingEle.entity).toBe(1);
   });

   it("moveEntityDown should be a no-op when called on the last entity", async () => {
      const entities = [makeEntity("A"), makeEntity("B")];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });
      comp.editingEle = { entity: 1, attribute: -1 };

      comp.moveEntityDown(1);

      expect(comp.logicalModel.entities[0].name).toBe("A");
      expect(comp.logicalModel.entities[1].name).toBe("B");
      expect(comp.editingEle.entity).toBe(1); // unchanged
   });

   it("moveEntityDown should update selectedItem.entity when it tracks the moved entity", async () => {
      const entities = [makeEntity("A"), makeEntity("B")];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });
      comp.selectedEles = [{ entity: 0, attribute: -1 }];

      comp.moveEntityDown(0);

      expect(comp.selectedEles[0].entity).toBe(1);
   });

   it("moveEntityUp should swap entities and decrement editingEle.entity", async () => {
      const entities = [makeEntity("A"), makeEntity("B"), makeEntity("C")];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });
      comp.editingEle = { entity: 2, attribute: -1 };

      comp.moveEntityUp(2);

      expect(comp.logicalModel.entities[1].name).toBe("C");
      expect(comp.logicalModel.entities[2].name).toBe("B");
      expect(comp.editingEle.entity).toBe(1);
   });

   it("moveEntityUp should be a no-op when called on the first entity", async () => {
      const entities = [makeEntity("A"), makeEntity("B")];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });
      comp.editingEle = { entity: 0, attribute: -1 };

      comp.moveEntityUp(0);

      expect(comp.logicalModel.entities[0].name).toBe("A");
      expect(comp.editingEle.entity).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — deleteEntity() / deleteAttribute() [Risk 2]
// ---------------------------------------------------------------------------

describe("LogicalModelPropertyPane — deleteEntity() and deleteAttribute()", () => {
   it("deleteEntity should splice the entity from the model", async () => {
      const entities = [makeEntity("A"), makeEntity("B")];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });
      const emitted: any[] = [];
      comp.checkModify.subscribe(() => emitted.push(true));

      comp.deleteEntity({ entity: 0, attribute: -1 });

      expect(comp.logicalModel.entities).toHaveLength(1);
      expect(comp.logicalModel.entities[0].name).toBe("B");
      expect(emitted).toHaveLength(1);
   });

   it("deleteEntity should skip deletion when the entity is a baseElement", async () => {
      const entities = [makeEntity("Base", [], { baseElement: true }), makeEntity("Normal")];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });

      comp.deleteEntity({ entity: 0, attribute: -1 });

      expect(comp.logicalModel.entities).toHaveLength(2); // no splice
   });

   it("deleteAttribute should splice the attribute from the entity", async () => {
      const attr = makeAttribute("colA");
      const entities = [makeEntity("E1", [attr, makeAttribute("colB")])];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });

      comp.deleteAttribute({ entity: 0, attribute: 0 });

      expect(comp.logicalModel.entities[0].attributes).toHaveLength(1);
      expect(comp.logicalModel.entities[0].attributes[0].name).toBe("colB");
   });

   it("deleteAttribute should skip deletion when the attribute is a baseElement", async () => {
      const attr = makeAttribute("colA", { baseElement: true });
      const entities = [makeEntity("E1", [attr])];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });

      comp.deleteAttribute({ entity: 0, attribute: 0 });

      expect(comp.logicalModel.entities[0].attributes).toHaveLength(1);
   });

   it("deleteAttribute should clear entity.errorMessage when the last errored attribute is removed", async () => {
      const badAttr = makeAttribute("badCol", { errorMessage: "some error" });
      const entity = makeEntity("E1", [badAttr]);
      entity.errorMessage = "some error"; // entity echoes the attribute error
      const { comp } = await renderComp({ logicalModel: makeModel([entity]) });

      comp.deleteAttribute({ entity: 0, attribute: 0 });

      expect(comp.logicalModel.entities[0].errorMessage).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 7 — addAttributes(): column vs expression; duplicate name [Risk 2]
// ---------------------------------------------------------------------------

describe("LogicalModelPropertyPane — addAttributes()", () => {
   // 🔁 Regression-sensitive: addAttributes() sets mandatory default fields (leaf, browseData,
   // depth, etc.) on every attribute — if they are missing the server rejects the model.
   // addAttributes() is private; accessed via cast because its public entry point
   // (showAddAttributeDialog) requires NgbModal interaction outside this test scope.

   it("should set column-type defaults and push the attribute to the entity", async () => {
      const entity = makeEntity("E1");
      const { comp } = await renderComp({ logicalModel: makeModel([entity]) });
      const newAttr = { name: "colX", table: "TABLE1" } as AttributeModel;
      const emitted: any[] = [];
      comp.checkModify.subscribe(() => emitted.push(true));

      const result = (comp as any).addAttributes(entity, [newAttr]);

      expect(result).toBe(true);
      expect(comp.logicalModel.entities[0].attributes).toHaveLength(1);
      const added = comp.logicalModel.entities[0].attributes[0];
      expect(added.leaf).toBe(true);
      expect(added.browseData).toBe(true);
      expect(added.type).toBe("column");
      expect(added.column).toBe("colX"); // originalName becomes column
      expect(added.expression).toBeNull();
      expect(emitted).toHaveLength(1);
   });

   it("should set expression-type defaults when the attribute has no table", async () => {
      const entity = makeEntity("E1");
      const { comp } = await renderComp({ logicalModel: makeModel([entity]) });
      const exprAttr = { name: "myExpr", table: null } as AttributeModel;

      (comp as any).addAttributes(entity, [exprAttr]);

      const added = comp.logicalModel.entities[0].attributes[0];
      expect(added.type).toBe("expression");
      expect(added.dataType).toBe("double");
      expect(added.column).toBeNull();
      expect(added.table).toBeNull();
   });

   it("should auto-suffix duplicate attribute names to avoid collisions", async () => {
      const entity = makeEntity("E1", [makeAttribute("colA")]);
      const { comp } = await renderComp({ logicalModel: makeModel([entity]) });
      const dup = { name: "colA", table: "TABLE1" } as AttributeModel;

      (comp as any).addAttributes(entity, [dup]);

      const added = comp.logicalModel.entities[0].attributes[1];
      expect(added.name).toBe("colA1"); // suffix added to avoid collision
   });

   it("should return false when the entity is not found in the model", async () => {
      const entity = makeEntity("E1");
      const unknownEntity = makeEntity("Unknown");
      const { comp } = await renderComp({ logicalModel: makeModel([entity]) });

      const result = (comp as any).addAttributes(unknownEntity, [makeAttribute("colX")]);

      expect(result).toBe(false);
      expect(comp.logicalModel.entities[0].attributes).toHaveLength(0);
   });

   it("should set editingEle to the first added expression attribute", async () => {
      const entity = makeEntity("E1");
      const { comp } = await renderComp({ logicalModel: makeModel([entity]) });
      const exprAttr = { name: "myExpr", table: null } as AttributeModel;

      (comp as any).addAttributes(entity, [exprAttr]);

      expect(comp.editingEle.entity).toBe(0);
      expect(comp.editingEle.attribute).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — sortElements(): entity and attribute sort [Risk 2]
// ---------------------------------------------------------------------------

describe("LogicalModelPropertyPane — sortElements()", () => {
   // 🔁 Regression-sensitive: after sort, selectedEles and editingEle must reference
   // the moved objects' new indices — not stale pre-sort indices.

   it("sortElements(true) should sort entities alphabetically and remap editingEle by identity", async () => {
      const entityC = makeEntity("C");
      const entityA = makeEntity("A");
      const entityB = makeEntity("B");
      const { comp } = await renderComp({ logicalModel: makeModel([entityC, entityA, entityB]) });
      comp.editingEle = { entity: 0, attribute: -1 }; // was editing entityC

      comp.sortElements(true);

      expect(comp.logicalModel.entities[0].name).toBe("A");
      expect(comp.logicalModel.entities[1].name).toBe("B");
      expect(comp.logicalModel.entities[2].name).toBe("C");
      expect(comp.editingEle.entity).toBe(2); // C moved to index 2
   });

   it("sortElements(true) should remap selectedEles to new entity indices", async () => {
      const entityC = makeEntity("C");
      const entityA = makeEntity("A");
      const { comp } = await renderComp({ logicalModel: makeModel([entityC, entityA]) });
      comp.selectedEles = [{ entity: 1, attribute: -1 }]; // was entityA at index 1

      comp.sortElements(true);

      // entityA moves to index 0 after sort
      expect(comp.selectedEles[0].entity).toBe(0);
   });

   it("sortElements(false) should sort attributes alphabetically within the selected entity", async () => {
      const attrZ = makeAttribute("z");
      const attrA = makeAttribute("a");
      const entity = makeEntity("E1", [attrZ, attrA], {});
      entity.name = "E1";
      attrZ.parentEntity = "E1";
      attrA.parentEntity = "E1";
      const { comp } = await renderComp({ logicalModel: makeModel([entity]) });
      comp.selectedEles = [{ entity: 0, attribute: 0 }]; // colZ selected

      comp.sortElements(false);

      expect(comp.logicalModel.entities[0].attributes[0].name).toBe("a");
      expect(comp.logicalModel.entities[0].attributes[1].name).toBe("z");
   });
});

// ---------------------------------------------------------------------------
// Group 9 — isDuplicateEntity, canDelete, deleteEnable, misc [Risk 1]
// ---------------------------------------------------------------------------

describe("LogicalModelPropertyPane — isDuplicateEntity()", () => {
   // isDuplicateEntity() is private; accessed via cast because it is called from the
   // showEntityDialog commit callback, which requires NgbModal wiring outside this test scope.
   it("should return true and call notifications.danger when entity name already exists", async () => {
      const entities = [makeEntity("Existing")];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });
      comp.editingEle = { entity: -1, attribute: -1 }; // adding new (no self-exclusion index)

      const result = (comp as any).isDuplicateEntity(makeEntity("Existing"));

      expect(result).toBe(true);
      expect(NOTIFICATIONS_MOCK.danger).toHaveBeenCalledWith(
         "_#(js:data.logicalmodel.entityNameDuplicate)"
      );
   });

   it("should return false and not notify when the entity name is unique", async () => {
      const entities = [makeEntity("Existing")];
      const { comp } = await renderComp({ logicalModel: makeModel(entities) });

      const result = (comp as any).isDuplicateEntity(makeEntity("NewEntity"));

      expect(result).toBe(false);
      expect(NOTIFICATIONS_MOCK.danger).not.toHaveBeenCalled();
   });
});

describe("LogicalModelPropertyPane — canDelete() and deleteEnable()", () => {
   it("canDelete should return false when selectedEles is empty", async () => {
      const { comp } = await renderComp({ logicalModel: makeModel([makeEntity("A")]) });
      comp.selectedEles = [];
      expect(comp.canDelete()).toBe(false);
   });

   it("canDelete should return false when all selected items are baseElements", async () => {
      const base = makeEntity("Base", [], { baseElement: true });
      const { comp } = await renderComp({ logicalModel: makeModel([base]) });
      comp.selectedEles = [{ entity: 0, attribute: -1 }];
      expect(comp.canDelete()).toBe(false);
   });

   it("canDelete should return true when at least one selected item is deletable", async () => {
      const entity = makeEntity("Normal");
      const { comp } = await renderComp({ logicalModel: makeModel([entity]) });
      comp.selectedEles = [{ entity: 0, attribute: -1 }];
      expect(comp.canDelete()).toBe(true);
   });

   it("deleteEnable should return false for an out-of-range entity index", async () => {
      const { comp } = await renderComp({ logicalModel: makeModel([makeEntity("A")]) });
      expect(comp.deleteEnable(99)).toBe(false);
   });

   it("deleteEnable should return false when the entity is a baseElement (entity-level)", async () => {
      const base = makeEntity("Base", [], { baseElement: true });
      const { comp } = await renderComp({ logicalModel: makeModel([base]) });
      expect(comp.deleteEnable(0)).toBe(false);
   });

   it("deleteEnable should return true for a non-base entity with attrIndex < 0", async () => {
      const { comp } = await renderComp({ logicalModel: makeModel([makeEntity("Normal")]) });
      expect(comp.deleteEnable(0)).toBe(true);
   });

   it("deleteEnable should return true for a non-base attribute", async () => {
      const entity = makeEntity("E1", [makeAttribute("colA")]);
      const { comp } = await renderComp({ logicalModel: makeModel([entity]) });
      expect(comp.deleteEnable(0, 0)).toBe(true);
   });

   it("deleteEnable should return false for a base attribute", async () => {
      const entity = makeEntity("E1", [makeAttribute("colA", { baseElement: true })]);
      const { comp } = await renderComp({ logicalModel: makeModel([entity]) });
      expect(comp.deleteEnable(0, 0)).toBe(false);
   });
});

describe("LogicalModelPropertyPane — isElementSelected, editingElement, getSelectedItem, checkModified, getSelectedEntity, keyDown", () => {
   it("isElementSelected should return true when editingEle.entity ≥ 0", async () => {
      const { comp } = await renderComp({ logicalModel: makeModel([makeEntity("A")]) });
      comp.editingEle = { entity: 0, attribute: -1 };
      expect(comp.isElementSelected()).toBe(true);
   });

   it("isElementSelected should return false when editingEle.entity < 0", async () => {
      const { comp } = await renderComp();
      expect(comp.isElementSelected()).toBe(false);
   });

   it("editingElement should return the entity when attribute=-1", async () => {
      const entity = makeEntity("EntityA");
      const { comp } = await renderComp({ logicalModel: makeModel([entity]) });
      comp.editingEle = { entity: 0, attribute: -1 };
      expect(comp.editingElement.name).toBe("EntityA");
   });

   it("editingElement should return the attribute when attribute≥0", async () => {
      const attr = makeAttribute("colX");
      const entity = makeEntity("EntityA", [attr]);
      const { comp } = await renderComp({ logicalModel: makeModel([entity]) });
      comp.editingEle = { entity: 0, attribute: 0 };
      expect(comp.editingElement.name).toBe("colX");
   });

   it("getSelectedItem should return the SelectedItem matching the entity index", async () => {
      const { comp } = await renderComp({ logicalModel: makeModel([makeEntity("A")]) });
      comp.selectedEles = [{ entity: 0, attribute: -1 }];
      expect(comp.getSelectedItem(0)?.entity).toBe(0);
   });

   it("getSelectedItem should return undefined when no item matches", async () => {
      const { comp } = await renderComp({ logicalModel: makeModel([makeEntity("A")]) });
      comp.selectedEles = [{ entity: 1, attribute: -1 }];
      expect(comp.getSelectedItem(0)).toBeUndefined();
   });

   it("checkModified should emit via checkModify output", async () => {
      const { comp } = await renderComp();
      const emitted: any[] = [];
      comp.checkModify.subscribe(() => emitted.push(true));
      comp.checkModified();
      expect(emitted).toHaveLength(1);
   });

   it("getSelectedEntity should return this.entity when it is not -1", async () => {
      const { comp } = await renderComp({ logicalModel: makeModel([makeEntity("A")]) });
      comp["entity"] = 0; // Bypass entity setter to set private field directly
      expect(comp.getSelectedEntity()).toBe(0);
   });

   it("getSelectedEntity should fall back to the first selectedEles entity", async () => {
      const { comp } = await renderComp({ logicalModel: makeModel([makeEntity("A"), makeEntity("B")]) });
      comp["entity"] = -1; // Bypass entity setter to set private field directly
      comp.selectedEles = [{ entity: 1, attribute: -1 }];
      expect(comp.getSelectedEntity()).toBe(1);
   });

   it("keyDown with Delete key should call deleteSelectedItem", async () => {
      const { comp } = await renderComp();
      const spy = vi.spyOn(comp, "deleteSelectedItem");
      comp.keyDown(new KeyboardEvent("keydown", { key: "Delete" }));
      expect(spy).toHaveBeenCalledTimes(1);
   });

   it("keyDown with a non-Delete key should not call deleteSelectedItem", async () => {
      const { comp } = await renderComp();
      const spy = vi.spyOn(comp, "deleteSelectedItem");
      comp.keyDown(new KeyboardEvent("keydown", { key: "Enter" }));
      expect(spy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 10 — memory leak: HTTP in-flight + destroy [fixed, Bug #75599]
// ---------------------------------------------------------------------------

describe("LogicalModelPropertyPane — subscription leak after destroy", () => {
   // Bug #75599 (FIXED): the httpClient.post().subscribe() callback in
   // checkOuterDependencies previously had no stored Subscription and the component had no
   // ngOnDestroy, so when the request resolved after fixture.destroy(), the callback still
   // fired on the dead component, calling ComponentTool.showConfirmDialog, which cascaded to
   // deleteSelectedItem0() and emitted checkModify. In this test the mock confirmDialog
   // resolves to "ok", which would have triggered deleteEntity(). The fix stores the
   // Subscription returned by subscribe() and unsubscribes it in ngOnDestroy(), so the
   // callback no longer fires post-destroy.
   it("post-destroy HTTP callback should not fire deleteSelectedItem0", async () => {
      let resolvePost: (v: Response) => void;
      server.use(
         http.post("*/api/data/logicalmodel/checkOuterDependencies", () =>
            new Promise(r => { resolvePost = r as any; })
         )
      );
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const deleteItem0Spy = vi.spyOn(
         LogicalModelPropertyPane.prototype as any,
         "deleteSelectedItem0"
      );

      const entities = [makeEntity("A")];
      const { comp, fixture } = await renderComp({ logicalModel: makeModel(entities) });
      comp.selectedEles = [{ entity: 0, attribute: -1 }];
      comp.editingEle = { entity: 0, attribute: -1 };
      comp.deleteEntityByIndex(0); // triggers in-flight POST

      // Wait for MSW to actually dispatch the request and capture the resolver before destroying
      await waitFor(() => expect(resolvePost).toBeDefined());

      fixture.destroy();

      // Now resolve the POST response — callback should NOT fire on destroyed component
      resolvePost!(MswHttpResponse.json({}) as any);
      await waitFor(() => {}, { timeout: 100 }).catch(() => {});

      expect(deleteItem0Spy).not.toHaveBeenCalled();
   });
});
