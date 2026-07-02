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
 * ElementTreeNode - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - expansion state from selected items and expandedNodes
 *   Group 2 [Risk 2] - selection behavior and drag payload creation
 *   Group 3 [Risk 2] - keyboard/attribute reorder state updates
 *   Group 4 [Risk 1] - icon and entity helper branches
 */

import { Tool } from "../../../../../../../../../../shared/util/tool";
import { DragService } from "../../../../../../../widget/services/drag.service";
import { AttributeModel } from "../../../../../model/datasources/database/physical-model/logical-model/attribute-model";
import { ElementModel } from "../../../../../model/datasources/database/physical-model/logical-model/element-model";
import { EntityModel } from "../../../../../model/datasources/database/physical-model/logical-model/entity-model";
import {
   DRAG_SEPARATOR,
   ElementTreeNode,
} from "./element-tree-node.component";

function makeAttribute(overrides: Partial<AttributeModel> = {}): AttributeModel {
   return {
      name: "order_id",
      type: "column",
      baseElement: false,
      elementType: "column",
      oldName: "order_id",
      visible: true,
      dataType: "integer",
      table: "orders",
      qualifiedName: "orders.order_id",
      ...overrides,
   };
}

function makeEntity(attributes: AttributeModel[] = [], overrides: Partial<EntityModel> = {}): EntityModel {
   return {
      name: "Orders",
      type: "entity",
      baseElement: false,
      elementType: "entity",
      oldName: "Orders",
      visible: true,
      attributes,
      ...overrides,
   };
}

function createComponent(overrides: Partial<ElementTreeNode> = {}) {
   const dragService = {
      reset: vi.fn(),
      put: vi.fn(),
   } as unknown as DragService;
   const comp = new ElementTreeNode(dragService);
   Object.assign(comp, overrides);
   return { comp, dragService };
}

afterEach(() => {
   vi.useRealTimers();
   vi.restoreAllMocks();
});

describe("ElementTreeNode - single pass", () => {
   describe("Group 1 - expansion state", () => {
      it("should expand an entity when one of its attributes is selected", () => {
         const { comp } = createComponent({
            node: makeEntity([makeAttribute()]),
            entityIndex: 2,
            selected: [{ entity: 2, attribute: 0 }],
         });

         comp.ngOnChanges({});

         expect(comp.expanded).toBe(true);
      });

      it("should expand an entity when expandedNodes contains the same name and type", () => {
         const entity = makeEntity();
         const { comp } = createComponent({
            node: entity,
            entityIndex: 1,
            selected: [],
            expandedNodes: [{ name: "Orders", type: "entity" } as ElementModel],
         });

         comp.ngOnChanges({});

         expect(comp.expanded).toBe(true);
      });
   });

   describe("Group 2 - selection and drag", () => {
      it("should replace the selection and emit onOpenNode on plain click", () => {
         const { comp } = createComponent({
            node: makeAttribute(),
            entityIndex: 3,
            attrIndex: 1,
            selected: [{ entity: 9, attribute: 9 }],
         });
         const emitSpy = vi.spyOn(comp.onOpenNode, "emit");

         comp.select(new MouseEvent("click"));

         expect(comp.selected).toEqual([{ entity: 3, attribute: 1 }]);
         expect(emitSpy).toHaveBeenCalledWith({ entity: 3, attribute: 1 });
      });

      it("should toggle the current item on ctrl+click", () => {
         const { comp } = createComponent({
            node: makeAttribute(),
            entityIndex: 1,
            attrIndex: 0,
            selected: [{ entity: 1, attribute: 0 }],
         });

         comp.select(new MouseEvent("click", { ctrlKey: true }));

         expect(comp.selected).toEqual([]);
      });

      it("should serialize all selected items into the drag payload", () => {
         const selected = [
            { entity: 0, attribute: 0 },
            { entity: 1, attribute: 2 },
         ];
         const { comp, dragService } = createComponent({
            node: makeAttribute(),
            entityIndex: 0,
            attrIndex: 0,
            selected,
         });
         const setTransferDataSpy = vi.spyOn(Tool, "setTransferData").mockImplementation(() => {});
         const emitSpy = vi.spyOn(comp.nodeDrag, "emit");
         const event = { dataTransfer: {} } as DragEvent;

         comp.dragStarted(event);

         expect(dragService.reset).toHaveBeenCalledTimes(1);
         expect(dragService.put).toHaveBeenCalledWith(`0${DRAG_SEPARATOR}0`, JSON.stringify(selected[0]));
         expect(dragService.put).toHaveBeenCalledWith(`1${DRAG_SEPARATOR}2`, JSON.stringify(selected[1]));
         expect(setTransferDataSpy).toHaveBeenCalledWith(
            event.dataTransfer,
            expect.objectContaining({
               dragName: [`0${DRAG_SEPARATOR}0`, `1${DRAG_SEPARATOR}2`],
            }),
         );
         expect(emitSpy).toHaveBeenCalledWith(event);
      });
   });

   describe("Group 3 - movement and reorder", () => {
      it("should move the selected attribute index down on ArrowDown", () => {
         vi.useFakeTimers();
         const selected = [{ entity: 1, attribute: 0 }];
         const { comp } = createComponent({
            node: makeAttribute(),
            entityIndex: 1,
            attrIndex: 0,
            selected,
            lastNode: false,
         });

         comp.moveSelection({ keyCode: 40 } as KeyboardEvent);
         vi.runAllTimers();

         expect(selected[0].attribute).toBe(1);
      });

      it("should swap entity attributes and update selected indexes when moving down", () => {
         const first = makeAttribute({ name: "first" });
         const second = makeAttribute({ name: "second" });
         const entity = makeEntity([first, second]);
         const selected = [{ entity: 4, attribute: 0 }];
         const { comp } = createComponent({
            node: entity,
            entityIndex: 4,
            selected,
         });
         const emitSpy = vi.spyOn(comp.onAttributeOrderChanged, "emit");

         comp.moveAttributeDown(0);

         expect(entity.attributes.map(attribute => attribute.name)).toEqual(["second", "first"]);
         expect(selected).toEqual([{ entity: 4, attribute: 0 }]);
         expect(emitSpy).toHaveBeenCalledWith({ entityIndex: 4, oldAttrIndex: 0, newAttrIndex: 1 });
      });
   });

   describe("Group 4 - helpers", () => {
      it("should return the drill icon for a drilled column", () => {
         const { comp } = createComponent({
            node: makeAttribute({
               drillInfo: { paths: ["Orders/Detail"] } as any,
            }),
         });

         expect(comp.getIcon()).toBe("drill-up-icon");
      });

      it("should expand the node and emit onToggleEntity on first toggle", () => {
         const entity = makeEntity();
         const { comp } = createComponent({ node: entity, expanded: false });
         const emitSpy = vi.spyOn(comp.onToggleEntity, "emit");

         comp.toggleNode();

         expect(comp.expanded).toBe(true);
         expect(emitSpy).toHaveBeenCalledWith({ entity, toggle: true });
      });

      it("should return caret-down-icon from getToggleIcon when expanded", () => {
         const { comp } = createComponent({ node: makeEntity(), expanded: true });

         expect(comp.getToggleIcon()).toBe("caret-down-icon");
      });

      it("should expose the entity node through the entityNode getter", () => {
         const entity = makeEntity();
         const { comp } = createComponent({ node: entity });

         expect(comp.entityNode).toBe(entity);
      });
   });
});
