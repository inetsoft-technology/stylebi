/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import {
   ChangeDetectionStrategy,
   Component,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   Output
} from "@angular/core";
import { UntypedFormControl, ValidationErrors, Validators } from "@angular/forms";
import { Notification } from "../../../../common/data/notification";
import { Tool } from "../../../../../../../shared/util/tool";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Worksheet } from "../../../data/ws/worksheet";
import { WSAssembly } from "../../../data/ws/ws-assembly";
import { WSTableAssembly } from "../../../data/ws/ws-table-assembly";
import { WSTableAssemblyInfo } from "../../../data/ws/ws-table-assembly-info";
import { WSAssemblyIcon } from "../ws-assembly-icon";
import { AssemblyActionGroup } from "../../../../common/action/assembly-action-group";

@Component({
   selector: "ws-assembly-thumbnail-title",
   templateUrl: "ws-assembly-thumbnail-title.component.html",
   styleUrls: ["ws-assembly-thumbnail-title.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class WSAssemblyThumbnailTitleComponent {
   @Input() editable: boolean = false;
   @Input() worksheet: Worksheet;
   @Input() menuActions: AssemblyActionGroup[];
   @Output() onEditName = new EventEmitter<string>();
   @Output() onStartEditName = new EventEmitter<void>();
   @Output() onNotify = new EventEmitter<Notification>();
   @Input() isFrontMenu: boolean = false;
   @Input() isVertical: boolean = false;
   inputNameControl: UntypedFormControl;
   iconCss: string;
   tooltip: string;
   private _assembly: WSAssembly;
   nameString: string;
   hidden: boolean;

   @Input() set assembly(assembly: WSAssembly) {
      this._assembly = assembly;
      this.setNameString();
      this.setIcon();
      this.setTooltip();
      this.setHidden();
   }

   get assembly(): WSAssembly {
      return this._assembly;
   }

   constructor(private zone: NgZone) {
   }

   @HostListener("dblclick", ["$event"])
   onDblclick(event: MouseEvent): void {
      if(this.editable) {
         event.stopPropagation();
         this.startEditingName();
      }
   }

   oozInputNameKeydown(event: KeyboardEvent) {
      const keyCode = Tool.getKeyCode(event);

      if(!event.repeat && keyCode === 13) { // Enter
         this.zone.run(() => {
            const success = this.updateAssemblyName();

            if(success) {
               this.stopEditingName();
            }
         });
      }
      else if(keyCode === 27) { // Escape
         this.zone.run(() => {
            this.stopEditingName();
         });
      }
   }

   blurInputName() {
      this.updateAssemblyName();
      this.stopEditingName();
   }

   /**
    * @returns true if edit name is successful, false otherwise
    */
   private updateAssemblyName(): boolean {
      if(this.inputNameControl != null && this.inputNameControl.errors) {
         this.logErrors(this.inputNameControl.errors);
      }

      if(this.inputNameControl == null || this.inputNameControl.errors) {
         return false;
      }

      const newName = this.inputNameControl.value.trim();

      if(newName === this.assembly.name) {
         return false;
      }

      this.onEditName.emit(newName);
      return true;
   }

   private startEditingName() {
      const assemblyNames =
         this.worksheet ? this.worksheet.assemblyNames(this.assembly.name) : [];
      const variable = this.assembly.classType == "VariableAssembly" ||
         this.assembly.classType == "GroupingAssembly";

      this.inputNameControl = new UntypedFormControl(this.assembly.name, [
         Validators.required,
         variable ? FormValidators.variableSpecialCharacters : FormValidators.nameSpecialCharacters,
         FormValidators.notWhiteSpace,
         FormValidators.exists(assemblyNames,
            {
               trimSurroundingWhitespace: true,
               ignoreCase: true,
               originalValue: this.assembly.name
            })
      ]);

      this.onStartEditName.emit();
   }

   private stopEditingName() {
      this.inputNameControl = null;
   }

   private logErrors(errors: ValidationErrors) {
      let message = "";

      if(errors.hasOwnProperty("required")) {
         message += "Name is required.\n";
      }

      if(errors.hasOwnProperty("nameSpecialCharacters")) {
         message += "_#(js:viewer.worksheet.Grouping.SpecialChar)";
      }

      if(errors.hasOwnProperty("variableSpecialCharacters")) {
         message += "_#(js:viewer.worksheet.Grouping.nameSpecialChar)";
      }

      if(errors.hasOwnProperty("notWhiteSpace")) {
         message += "The name cannot be whitespace.\n";
      }

      if(errors.hasOwnProperty("exists")) {
         message += "Name already exists in worksheet.\n";
      }

      this.onNotify.emit({type: "danger", message});
   }

   private setNameString() {
      this.nameString = this.assembly.name;

      if(this.assembly.primary) {
         this.nameString += " (_#(js:Primary))";
      }
      else if(this.assembly.classType == "TableAssembly" &&
         !(<WSTableAssemblyInfo>this.assembly.info).visibleTable)
      {
         // this.nameString += " (_#(js:Hidden))";
      }
   }

   private setIcon() {
      this.iconCss = WSAssemblyIcon.getIcon(this.assembly);
   }

   private setTooltip() {
      switch(this.assembly.classType) {
         case "TableAssembly":
            this.setTableTooltip();
            break;
         case "VariableAssembly":
            this.setVariableTooltip();
            break;
         case "GroupingAssembly":
            this.setGroupingTooltip();
            break;
         default:
         // no-op
      }
   }

   private setHidden() {
      this.hidden = (this.assembly.classType == "TableAssembly" &&
         !(<WSTableAssemblyInfo>this.assembly.info).visibleTable);
   }

   private setTableTooltip() {
      const table = this.assembly as WSTableAssembly;
      let tooltip: string;

      switch(table.tableClassType) {
         case "BoundTableAssembly":
            tooltip = "_#(js:Bound Table)";
            break;
         case "ComposedTableAssembly":
            tooltip = "_#(js:Composed Table)";
            break;
         case "CompositeTableAssembly":
            tooltip = "_#(js:Composite Table)";
            break;
         case "ConcatenatedTableAssembly":
            tooltip = "_#(js:Concatenated Table)";
            break;
         case "CubeTableAssembly":
            tooltip = "_#(js:Cube Table)";
            break;
         case "DataTableAssembly":
            tooltip = "_#(js:Data Table)";
            break;
         case "EmbeddedTableAssembly":
            tooltip = "_#(js:Embedded Table)";
            break;
         case "MergeJoinTableAssembly":
            tooltip = "_#(js:Merge Join Table)";
            break;
         case "MirrorTableAssembly":
            tooltip = "_#(js:Mirror Table)";
            break;
         case "PhysicalBoundTableAssembly":
            tooltip = "_#(js:Physical Bound Table)";
            break;
         case "QueryBoundTableAssembly":
            tooltip = "_#(js:Query Bound Table)";
            break;
         case "RelationalJoinTableAssembly":
            tooltip = "_#(js:Relational Join Table)";
            break;
         case "RotatedTableAssembly":
            tooltip = "_#(js:Rotated Table)";
            break;
         case "SnapshotEmbeddedTableAssembly":
            tooltip = "_#(js:Snapshot Embedded Table)";
            break;
         case "SQLBoundTableAssembly":
            tooltip = "_#(js:SQL Bound Table)";
            break;
         case "TabularTableAssembly":
            tooltip = "_#(js:Tabular Table)";
            break;
         case "UnpivotTableAssembly":
            tooltip = "_#(js:Unpivot Table)";
            break;
         default:
            tooltip = null;
      }

      this.tooltip = tooltip;
   }

   private setVariableTooltip() {
      this.tooltip = "Variable";
   }

   private setGroupingTooltip() {
      this.tooltip = "Grouping";
   }
}
