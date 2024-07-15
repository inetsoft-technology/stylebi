/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import { EntityModel } from "../../../../model/datasources/database/physical-model/logical-model/entity-model";
import { AttributeModel } from "../../../../model/datasources/database/physical-model/logical-model/attribute-model";
import { SelectedItem } from "./logical-model.component";
import { EditLogicalModelEvent } from "../../../../model/datasources/database/events/edit-logical-model-event";
import { LogicalModelDefinition } from "../../../../model/datasources/database/physical-model/logical-model/logical-model-definition";
import { Tool } from "../../../../../../../../../shared/util/tool";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { SortOptions } from "../../../../../../../../../shared/util/sort/sort-options";
import { LogicalModelEntityDialog } from "./entity-dialog/logical-model-entity-dialog.component";
import { DataModelNameChangeService } from "../../../../services/data-model-name-change.service";
import { FolderChangeService } from "../../../../services/folder-change.service";
import { HttpClient } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { NotificationsComponent } from "../../../../../../widget/notifications/notifications.component";
import { LogicalModelAttributeDialog } from "./attribute-dialog/logical-model-attribute-dialog.component";
import { LogicalModelExpressionDialog } from "./expression-dialog/logical-model-expression-dialog.component";
import { AttributeFormatInfoModel } from "../../../../model/datasources/database/physical-model/logical-model/attribute-format-info-model";
import { RefTypeModel } from "../../../../model/datasources/database/physical-model/logical-model/ref-type-model";
import { AutoDrillInfoModel } from "../../../../model/datasources/database/physical-model/logical-model/auto-drill-info-model";
import { ElementModel } from "../../../../model/datasources/database/physical-model/logical-model/element-model";
import { LogicalModelService } from "./logical-model-service";
import { UntypedFormGroup } from "@angular/forms";
import { CheckDependenciesEvent } from "../../../../model/datasources/database/events/check-dependencies-event";

const LOGICAL_MODEL_CHECK_DEPENDENCIES_URI: string = "../api/data/logicalmodel/checkOuterDependencies";

@Component({
   selector: "logical-model-property-pane",
   templateUrl: "logical-model-property-pane.component.html",
   styleUrls: ["../database-model-pane.scss", "logical-model-property-pane.component.scss"]
})
export class LogicalModelPropertyPane implements OnInit {
   @Input() databaseName: string;
   @Input() physicalModelName: string;
   @Input() additional: string;
   @Input() originalName: string;
   @Input() editing: boolean;
   @Input() parent: string;
   @Input() loading: boolean = false;
   @Input() form: UntypedFormGroup;
   @Input() notifications: NotificationsComponent;
   @Output() checkModify = new EventEmitter();

   @Input()
   set logicalModel(logicalModel: LogicalModelDefinition) {
      let sameModel = logicalModel && this._logicalModel &&
         logicalModel.partition === this._logicalModel.partition &&
         logicalModel.name === this._logicalModel.name;
      this._logicalModel = logicalModel;

      if(!sameModel) {
         this.resetState();
      }
   }

   get logicalModel(): LogicalModelDefinition {
      return this._logicalModel;
   }

   readonly DEFAULT_SIZE: number[] = [35, 65];
   _editingEle: SelectedItem = {
      entity: -1,
      attribute: -1
   };
   selectedEles: SelectedItem[] = [];
   existNames: string[] = [];
   entity: number = -1;
   newEntity = false;
   expanded: EntityModel[] = []; // for multi-select with shift.
   private _logicalModel: LogicalModelDefinition;

   constructor(private dataModelNameChangeService: DataModelNameChangeService,
               private folderChangeService: FolderChangeService,
               private lmService: LogicalModelService,
               private httpClient: HttpClient,
               private modalService: NgbModal)
   {
   }

   ngOnInit(): void {
      this.editingEle = {
         entity: -1,
         attribute: -1
      };
   }

   get editingEle() {
      return this._editingEle;
   }

   set editingEle(editingEle: SelectedItem) {
      this._editingEle = editingEle;
      this.updateExistNames();
   }

   attributeOrderChanged() {
      this.updateExistNames();
   }

   private updateExistNames() {
      let editingEle = this.editingEle;
      this.existNames = [];

      if(!editingEle) {
         return;
      }

      if(this.isEntity(editingEle)) {
         this.existNames = this.logicalModel.entities
            .filter(entity => !!entity && entity.name != this.editingElement.name)
            .map(entity => entity.name);
      }
      else {
         if(editingEle && editingEle.entity >= 0 &&
            editingEle.entity < this.logicalModel.entities.length)
         {
            let entity = this.logicalModel.entities[editingEle.entity];
            this.existNames = entity.attributes
               .filter(attribute => !!attribute && attribute.name != this.editingElement.name)
               .map(attribute => attribute.name);
         }
      }
   }

   getSelectedItem(entityIndex: number): SelectedItem {
      if(!this.selectedEles || this.selectedEles.length == 0) {
         return null;
      }

      return this.selectedEles.find(item => item.entity == entityIndex);
   }

   /**
    * Check if any element is selected.
    * @returns {boolean}
    */
   isElementSelected(): boolean {
      return this.editingEle.entity >= 0;
   }

   /**
    * Retreive the selected element.
    * @returns {any}
    */
   get editingElement(): any {
      if(this.editingEle.entity >= 0) {
         const entity: EntityModel = this.logicalModel.entities[this.editingEle.entity];

         if(this.editingEle.attribute >= 0) {
            return entity.attributes[this.editingEle.attribute];
         }
         else {
            return entity;
         }
      }
   }

   /**
    * Move entity node up and update selection if necessary.
    * @param index
    */
   moveEntityDown(index: number): void {
      if(index < this.logicalModel.entities.length - 1) {
         let selectedItem = this.getSelectedItem(index);

         if(!!selectedItem && selectedItem.entity == index) {
            selectedItem.entity++;
         }

         const temp: any = Tool.clone(this.logicalModel.entities[index]);
         this.logicalModel.entities[index] = this.logicalModel.entities[index + 1];
         this.logicalModel.entities[index + 1] = temp;
         this.editingEle.entity = index + 1;
         this.editingEle.attribute = -1;
         this.checkModified();
      }
   }

   /**
    * Move enitity node down and update selection if necessary.
    * @param index
    */
   moveEntityUp(index: number): void {
      if(index > 0) {
         let selectedItem = this.getSelectedItem(index);

         if(!!selectedItem && selectedItem.entity == index) {
            selectedItem.entity--;
         }

         const temp: any = this.logicalModel.entities[index];
         this.logicalModel.entities[index] = this.logicalModel.entities[index - 1];
         this.logicalModel.entities[index - 1] = temp;
         this.editingEle.entity = index - 1;
         this.editingEle.attribute = -1;
         this.checkModified();
      }
   }

   deleteEntityByIndex(index: number): void {
      let item: SelectedItem = {entity: index, attribute: -1};
      this.checkOuterDependencies([item]);
   }

   keyDown(event: KeyboardEvent): void {
      if(event.key == "Delete") {
         this.deleteSelectedItem();
      }
   }

   /**
    * Delete selected item.
    */
   deleteSelectedItem(): void {
      if(!this.validSelected() || !this.canDelete()) {
         return;
      }

      let deleteEles: SelectedItem[]  = [];

      // remove attributes when their entity is selected.
      this.selectedEles.forEach((ele, index) => {
         if(!this.isEntity(ele)) {
            let selectedParent: boolean = this.selectedEles.some(item => item &&
               this.isEntity(item) && item.entity == ele.entity);

            if(!selectedParent) {
               deleteEles.push(ele);
            }
         }
         else {
            deleteEles.push(ele);
         }
      });

      // sort selected items by desc.
      deleteEles.sort((item1, item2) => {
         if(item1.entity > item2.entity){
            return -1;
         }
         else if(item1.entity < item2.entity) {
            return 1;
         }
         else {
            return item1.attribute > item2.attribute ? -1 : 1;
         }
      });

      this.checkOuterDependencies(deleteEles);
   }

   private checkOuterDependencies(deleteEles: SelectedItem[]): void {
      let models: ElementModel[] = [];

      for(let i = 0; i < deleteEles.length; i++) {
         let item = deleteEles[i];

         if(this.isEntity(item)) {
            models[i] = this.getEntity(item);
         }
         else {
            let entity = this.logicalModel.entities[item.entity] as EntityModel;
            models[i] = entity.attributes[item.attribute];
         }
      }

      let event = new CheckDependenciesEvent();
      event.databaseName = this.databaseName;
      event.parent = this.parent;
      event.modelName = this.logicalModel.name;
      event.modelElements = models;
      event.newCreate = !this.editing;

      this.httpClient.post(LOGICAL_MODEL_CHECK_DEPENDENCIES_URI, event).subscribe((result: any) => {
         if(!!result && !!result.body) {
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", result.body,
               {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
               .then((btn) => {
                  if(btn == "yes") {
                     this.deleteSelectedItem0(deleteEles);
                  }
               });
         }
         else {
            const title: string = "_#(js:data.logicalmodel.removeElements)";
            const message: string = "_#(js:data.logicalmodel.confirmRemoveElements)";

            ComponentTool.showConfirmDialog(this.modalService, title, message)
               .then((buttonClicked: string) => {
                  if(buttonClicked == "ok") {
                     this.deleteSelectedItem0(deleteEles);
                  }
               });
         }
      });
   }

   deleteSelectedItem0(deleteEles: SelectedItem[]): void {
      deleteEles.forEach((item) => {
         if(this.isEntity(item)) {
            this.deleteEntity(item);
         }
         else {
            this.deleteAttribute(item);
         }
      });

      this.resetSelectedStatus();
   }

   /**
    * Reset the tree selected status.
    */
   private resetSelectedStatus(): void {
      this.selectedEles = [];
      this.editingEle.entity = -1;
      this.editingEle.attribute = -1;
   }


   /**
    * Check is this node is an entity type.
    * @returns {boolean}
    */
   private isEntity(selected: SelectedItem): boolean {
      return selected && selected.entity >= 0 && selected.attribute == -1;
   }

   private hasSelected() {
      return this.selectedEles && this.selectedEles.length > 0;
   }

   /**
    * Whether is selected item valid .
    */
   private validSelected(): boolean {
      if(!this.hasSelected()) {
         return false;
      }

      for(let ele of this.selectedEles) {
         if(ele.entity < 0 || ele.entity >= this.logicalModel.entities.length) {
            return false;
         }

         let selectedEntity = this.logicalModel.entities[ele.entity];

         if(ele.attribute >= 0 && ele.attribute >= selectedEntity.attributes.length) {
            return false;
         }
      }

      return true;
   }


   /**
    * Delete the entity at index. Confirmation happens in tree node component.
    * @param index
    */
   deleteEntity(item: SelectedItem): void {
      let entity: EntityModel = this.getEntity(item);

      if(entity.baseElement) {
         return;
      }

      this.logicalModel.entities.splice(item.entity, 1);
      this.checkModified();
   }

   private getEntity(item: SelectedItem): EntityModel {
      let index = item.entity;

      if(index >= 0 && index < this.logicalModel.entities.length) {
         return this.logicalModel.entities[index];
      }

      return null;
   }

   private getAttribute(item: SelectedItem): AttributeModel {
      let entryIndex = item.entity;

      if(entryIndex >= 0 && entryIndex < this.logicalModel.entities.length) {
         let attributeIndex = item.attribute;
         let resultEntry = this.logicalModel.entities[entryIndex];

         if(attributeIndex >= 0 && attributeIndex < resultEntry.attributes.length) {
            return resultEntry.attributes[attributeIndex];
         }
      }

      return null;
   }

   /**
    * Delete a specific attribute.
    * @param node
    * @param index
    */
   deleteAttribute(item: SelectedItem): void {
      let entity = this.logicalModel.entities[item.entity] as EntityModel;
      let attribute = entity.attributes[item.attribute];

      if(attribute && attribute.baseElement) {
         return;
      }

      if(attribute.errorMessage) {
         attribute.errorMessage = null;
         this.checkInvalidAttributes(entity);
      }

      entity.attributes.splice(item.attribute, 1);
   }

   /**
    * Check if the entity has any attributes with errors and update its status.
    */
   private checkInvalidAttributes(entity: EntityModel): void {
      for(let i = 0; i < entity.attributes.length; i++) {
         if(entity.attributes[i].errorMessage) {
            return;
         }
      }

      entity.errorMessage = null;
   }

   sortElements(entry: boolean): void {
      let selectedEntities: EntityModel[] = [];
      let selectedAttributes: AttributeModel[] = [];
      let editModel;

      if(this.editingEle && this.editingEle.entity != -1) {
         if(this.isEntity(this.editingEle)) {
            editModel = this.getEntity(this.editingEle);
         }
         else {
            editModel = this.getAttribute(this.editingEle);
         }
      }

      this.selectedEles.forEach(item => {
         if(this.isEntity(item)) {
            selectedEntities.push(this.getEntity(item));
         }
         else {
            selectedAttributes.push(this.getAttribute(item));
         }
      });

      this.selectedEles = [];

      if(entry) {
         this.sortEntities();
      }
      else {
         this.sortAttributes(selectedAttributes, selectedEntities);
      }

      for(let i = 0; i < this.logicalModel.entities.length; i++) {
         let entryModel = this.logicalModel.entities[i];
         let isSelected = selectedEntities.some(e => e === entryModel);

         if(isSelected) {
            this.selectedEles.push({
               entity: i,
               attribute: -1
            });
         }

         if(entryModel === editModel) {
            this.editingEle.entity = i;
            this.editingEle.attribute = -1;
         }

         for(let j = 0; j < entryModel.attributes.length; j++) {
            let attributeModel = entryModel.attributes[j];
            isSelected = selectedAttributes.some(attri => attri === attributeModel);

            if(isSelected) {
               if(isSelected) {
                  this.selectedEles.push({
                     entity: i,
                     attribute: j
                  });
               }
            }

            if(attributeModel === editModel) {
               this.editingEle.entity = i;
               this.editingEle.attribute = j;
            }
         }
      }

      this.updateExistNames();
   }

   /**
    * Sort the entities.
    */
   sortEntities(): void {
      this.logicalModel.entities = Tool.sortObjects(this.logicalModel.entities, new SortOptions(["name"]));
   }

   /**
    * Sort attributes.
    */
   sortAttributes(selectedAttributes: AttributeModel[], selectedEntities: EntityModel[]): void {
      let entities: EntityModel[] = [...selectedEntities];
      selectedAttributes.forEach(ele => {
         const entity: EntityModel =
            this.logicalModel.entities.find((model) => model.name == ele.parentEntity);

         if(!entities.find((model) => model.name == entity.name)) {
            entities.push(entity);
         }
      });

      entities.forEach((ele) => {
         const entity: EntityModel =
            this.logicalModel.entities.find((model) => ele.name == model.name);
         entity.attributes = Tool.sortObjects(entity.attributes, new SortOptions(["name"]));
      });

      this.checkModified();
   }

   showEntityDialog(): void {
      const dialog = ComponentTool.showDialog(this.modalService, LogicalModelEntityDialog,
         (data: {entity: EntityModel, next: boolean}) => {
            const entity: EntityModel = data.entity;
            entity.name = !!entity.name ? entity.name.trim() : entity.name;

            if(this.isDuplicateEntity(entity)) {
               return;
            }

            this.logicalModel.entities.push(entity);
            this.checkModified();

            if(entity && data.next) {
               this.entity = this.logicalModel.entities.length - 1;
               this.newEntity = true;
               this.showAddAttributeDialog();
            }
            else if(entity) {
               this.notifications.success("_#(js:data.logicalmodel.saveEntitySuccess)");
            }

            this.entity = -1;
         }
      );

      const currentEntity = this.entity >= 0 ? this.logicalModel.entities[this.entity] : null;
      dialog.entity = currentEntity;
      dialog.existNames = this.logicalModel.entities
         .filter(e => e != currentEntity)
         .map(e => e.name);
   }

   /**
    * Open the new attribute dialog.
    */
   showAddAttributeDialog(): void {
      let dialog = ComponentTool.showDialog(this.modalService, LogicalModelAttributeDialog,
         (data: {entity: EntityModel, attributes: AttributeModel[]}) => {
            if(!data.attributes) {
               for(let i = 0; i < this.logicalModel.entities.length; i++) {
                  if(this.logicalModel.entities[i].name == data.entity.name) {
                     this.logicalModel.entities.splice(i, 1);
                     this.checkModified();
                     break;
                  }
               }
            }
            else {
               if(this.addAttributes(data.entity, data.attributes)) {
                  this.notifications.success("_#(js:data.logicalmodel.saveAttributesSuccess)");
               }
               else {
                  this.notifications.danger("_#(js:data.logicalmodel.saveAttributesError)");
               }
            }

            this.entity = -1;
            this.newEntity = false;
         },
         {},
         () => {
            this.newEntity = false;
         });

      dialog.entities = this.logicalModel.entities;
      dialog.parent = this.getSelectedEntity();
      dialog.newParent = this.newEntity;
      dialog.databaseName = this.databaseName;
      dialog.physicalModelName = this.physicalModelName;
      dialog.additional = this.additional;
      dialog.logicalModelName = this.originalName;
      dialog.parentName = this.parent;
   }

   getSelectedEntity(): number {
      if(this.entity != -1) {
         return this.entity;
      }

      for(let i = 0; i < this.selectedEles.length; i++) {
         if(this.selectedEles[i].entity != -1) {
            return this.selectedEles[i].entity;
         }
      }

      return this.entity;
   }

   /**
    * Open new expression dialog.
    */
   showAddExpressionDialog(): void {
      let dialog = ComponentTool.showDialog(this.modalService, LogicalModelExpressionDialog,
         (data: {entity: EntityModel, attributes: AttributeModel[]}) => {
            if(data.attributes && data.attributes.length) {
               this.addAttributes(data.entity, data.attributes);
            }
         }, {size: "lg"});

      dialog.entities = this.logicalModel.entities;
      dialog.databaseName = this.databaseName;
      dialog.physicalModelName = this.physicalModelName;
      dialog.additional = this.additional;

      if(!!this._editingEle && this._editingEle.entity != -1) {
         dialog.parent = this._editingEle.entity;
      }
   }

   /**
    * Add new attributes to the entity.
    * @param entity
    * @param attributes
    * @returns {boolean}
    */
   private addAttributes(entity: EntityModel, attributes: AttributeModel[]): boolean {
      let index: number = -1;

      for(let i = 0; i < this.logicalModel.entities.length; i++) {
         if(this.logicalModel.entities[i].name === entity.name) {
            index = i;
            break;
         }
      }

      if(index == -1) {
         return false;
      }

      entity = this.logicalModel.entities[index];

      attributes.forEach((value: AttributeModel) => {
         value.description = null;
         value.leaf = true;
         value.parentEntity = entity.name;
         value.browseData = true;
         value.aggregate = false;
         value.parseable = true;
         value.browseQuery = null;
         value.depth = 1;
         value.errorMessage = null;
         value.format = <AttributeFormatInfoModel>{
            format: null,
            formatSpec: null
         };
         value.refType = <RefTypeModel>{
            type: 0,
            formula: "None"
         };
         value.drillInfo = <AutoDrillInfoModel>{
            paths: []
         };
         value.elementType = "attributeElement";

         let duplicateName: number = 1;
         const originalName: string = value.name;

         for(let i = 0; i < entity.attributes.length; i++) {
            if(value.name === entity.attributes[i].name) {
               value.name = originalName + duplicateName;
               duplicateName++;
               i = 0;
            }
         }

         if(value.table) {
            value.column = originalName;
            value.type = "column";
            value.expression = null;

            if(value.dataType == "bigdecimal") {
               value.dataType = "double";
            }
         }
         else {
            value.type = "expression";
            value.dataType = "double";
            value.column = null;
            value.table = null;
            value.qualifiedName = null;
         }

         entity.attributes.push(value);
      });

      if(attributes[0].type == "expression") {
         this.editingEle.entity = index;
         this.editingEle.attribute = entity.attributes.length - attributes.length;
      }

      this.checkModified();

      return true;
   }

   checkModified() {
      this.checkModify.emit();
   }

   /**
    * Returns true and shows an error if the logical model already contains the specified entity.
    *
    * Formally, filters the logical model entities array by non-selected entities and returns true
    * if the new array contains at least one entity such that (o.name != null && o.name === e.name).
    *
    * @param {EntityModel} entity the entity to be tested
    * @return {boolean} true if the entity is already contained within the list
    */
   private isDuplicateEntity(entity: EntityModel): boolean {
      const duplicate = this.logicalModel.entities
         .filter((_, i) => i !== this.editingEle.entity)
         .some((ent) => ent.name === entity.name);

      if(duplicate) {
         this.notifications.danger("_#(js:data.logicalmodel.entityNameDuplicate)");
      }

      return duplicate;
   }

   /**
    * Return true and shows an error if an entity already contains the specified attribute
    *
    * Formally, creates a filtered array from an entity's non-selected attributes and returns true
    * if the new array contains at least one attribute such that:
    * (o.name != null && (o.name === a.name || o.qualifiedName === a.qualifiedName)
    * where name is the attribute name and qualifiedName is the qualified name of the column.
    *
    * @param entity the entity whose attribute are to be tested
    * @param {AttributeModel} attribute the attribute to test for existence within the list
    * @return {boolean} true if the attribute is already contained within the entity
    */
   private isDuplicateAttribute(entity: EntityModel, attribute: AttributeModel): boolean {
      const attributes = entity.attributes.filter((_, i) => i !== this.editingEle.attribute);
      const dupName = attributes.some((attr) => attr.name === attribute.name);

      if(dupName) {
         this.notifications.danger("_#(js:data.logicalmodel.attributeNameDuplicate)");
      }

      const dupColumn = attributes.some((attr) => attr.name === attribute.name);

      if(!dupName && dupColumn) {
         this.notifications.danger("_#(js:data.logicalmodel.attributeColumnDuplicate)");
      }

      return dupName || dupColumn;
   }

   /**
    * Multi-select by shift.
    * @param element selecected element.
    */
   shiftSelect(element: ElementModel): void {
      this.lmService.shiftSelect(element, this.selectedEles, this.logicalModel, this.expanded);
   }

   entityToggle(data: {entity: EntityModel, toggle: boolean}): void {
      this.lmService.entityToggle(data, this.expanded);
   }

   onDeleteAttribute(item: SelectedItem) {
      this.checkOuterDependencies([item]);
   }

   /**
    * Whether the delete button is available.
    */
   canDelete(): boolean {
      return this.selectedEles && this.selectedEles.length > 0 &&
         this.selectedEles.some(item => this.itemDeletable(item));
   }

   deleteEnable = (entityIndex: number, attrIndex: number = -1): boolean => {
      let entity: EntityModel = this.logicalModel.entities[entityIndex];

      if (!entity) {
         return false;
      }

      if (entity && !entity.baseElement && attrIndex < 0) {
         return true;
      }

      let attribute: AttributeModel = entity.attributes[attrIndex];

      if (attribute && !attribute.baseElement) {
         return true;
      }

      return false;
   };

   itemDeletable(item: SelectedItem) {
      return this.deleteEnable(item.entity, item.attribute);
   }

   /**
    * Rest the pane state.
    */
   private resetState() {
      this.updateExistNames();
      this.expanded = [];
      this.resetSelectedStatus();
   }
}
