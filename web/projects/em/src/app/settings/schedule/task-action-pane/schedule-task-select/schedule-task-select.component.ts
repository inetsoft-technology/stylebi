/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { FlatTreeControl } from "@angular/cdk/tree";
import { HttpClient } from "@angular/common/http";
import {
   Component,
   EventEmitter,
   forwardRef,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import {
   ControlValueAccessor,
   UntypedFormBuilder,
   UntypedFormControl,
   UntypedFormGroup,
   NG_VALUE_ACCESSOR,
   ValidationErrors,
   ValidatorFn,
   Validators
} from "@angular/forms";
import { MatTreeFlatDataSource, MatTreeFlattener } from "@angular/material/tree";
import { Observable, of as observableOf } from "rxjs";
import { ExpandStringDirective } from "../../../../../../../portal/src/app/widget/expand-string/expand-string.directive";
import { ScheduleTaskModel } from "../../../../../../../shared/schedule/model/schedule-task-model";
import { removeOrganization } from "../../../security/users/identity-id";
import { ScheduleTaskListComponent } from "../../schedule-task-list/schedule-task-list.component";

export const SCHEDULE_TASK_SELECT_VALUE_ACCESSOR: any = {
   provide: NG_VALUE_ACCESSOR,
   useExisting: forwardRef(() => ScheduleTaskSelectComponent), // eslint-disable-line @typescript-eslint/no-use-before-define
   multi: true
};

export class ScheduleTaskNode {
   children: ScheduleTaskNode[];

   constructor(public task: ScheduleTaskModel, public name: string, public folder: boolean) {
   }
}

export class ScheduleTaskFlatNode {
   constructor(public expandable: boolean, public task: ScheduleTaskModel, public name: string,
               public level: number, folder: boolean)
   {
   }
}

@Component({
   selector: "em-schedule-task-select",
   templateUrl: "./schedule-task-select.component.html",
   styleUrls: ["./schedule-task-select.component.scss"],
   providers: [SCHEDULE_TASK_SELECT_VALUE_ACCESSOR]
})
export class ScheduleTaskSelectComponent implements OnInit, OnChanges, ControlValueAccessor {
   @Input() tasks: ScheduleTaskModel[];
   @Output() selectedChange = new EventEmitter<string>();

   treeControl: FlatTreeControl<ScheduleTaskFlatNode>;
   treeFlattener: MatTreeFlattener<ScheduleTaskNode, ScheduleTaskFlatNode>;
   dataSource: MatTreeFlatDataSource<ScheduleTaskNode, ScheduleTaskFlatNode>;
   form: UntypedFormGroup;
   currOrg: string;

   @Input()
   get selectedTaskName(): string {
      return this._selectedTaskName;
   }

   set selectedTaskName(val: string) {
      this._selectedTaskName = val;
      this.updateScheduleTaskTree();
   }

   private _selectedTaskName: string;

   constructor(private fb: UntypedFormBuilder, private http: HttpClient) {
      this.treeFlattener = new MatTreeFlattener<ScheduleTaskNode, ScheduleTaskFlatNode>(
         this.transformer, this.getLevel, this.isExpandable, this.getChildren);
      this.treeControl = new FlatTreeControl<ScheduleTaskFlatNode>(this.getLevel, this.isExpandable);
      this.dataSource = new MatTreeFlatDataSource<ScheduleTaskNode, ScheduleTaskFlatNode>(
         this.treeControl, this.treeFlattener);
   }

   ngOnInit(): void {
      this.init();
   }

   ngOnChanges(changes: SimpleChanges): void {
      this.init();
      this.updateScheduleTaskTree();
   }

   init(): void {
      this.form = new UntypedFormGroup({
         selectedTaskName: new UntypedFormControl(this.selectedTaskName, [Validators.required,
            this.notExists(this.tasks != null ?
               this.tasks.map((task) => ScheduleTaskListComponent.getTaskName(task)) : [])])
      });
      this.http.get<string>("../api/em/navbar/organization")
         .subscribe((org) => this.currOrg = org);
   }

   public notExists(names: string[]): ValidatorFn {
      return (control: UntypedFormControl): ValidationErrors => {
         const value = control.value;

         if(!value || !names) {
            return null;
         }

         return names.indexOf(value) == -1 ? {notExists: true} : null;
      };
   }

   onChange: (value: any) => void = (value: any) => {
      this.selectedTaskName = value;
      this.selectedChange.emit(this.selectedTaskName);
   };

   onTouched = () => {
   };

   registerOnChange(fn: (value: any) => void): void {
      this.onChange = fn;
   }

   registerOnTouched(fn: () => {}): void {
      this.onTouched = fn;
   }

   writeValue(val: any): void {
      this.selectedTaskName = val;
   }

   setDisabledState(isDisabled: boolean): void {
   }

   transformer = (node: ScheduleTaskNode, level: number) => {
      return new ScheduleTaskFlatNode(!!node.children, node.task, node.name, level, node.folder);
   };

   private getLevel = (node: ScheduleTaskFlatNode) => node.level;
   private isExpandable = (node: ScheduleTaskFlatNode) => node.expandable;
   private getChildren = (node: ScheduleTaskNode): Observable<ScheduleTaskNode[]> => observableOf(node.children);
   hasChild = (n: number, nodeData: ScheduleTaskFlatNode) => nodeData.expandable;

   private updateScheduleTaskTree(): void {
      this.dataSource.data =
         this.buildTreeData(this.tasks || [])
            .sort((a, b) => {
               const aFolder = a.folder ? 1 : 0;
               const bFolder = b.folder ? 1 : 0;
               return bFolder - aFolder;
            });

      if(this.selectedTaskName) {
         this.treeControl.expandAll();
         const node = this.treeControl.dataNodes.find(n => !!n.task &&
            n.task.name === this.selectedTaskName);

         if(node) {
            let parents = [];
            this.getParents(node, parents);
            this.treeControl.collapseAll();
            parents = parents.reverse();
            parents.forEach((p) => this.treeControl.expand(p));
         }
      }
   }

   private buildTreeData(tasks: ScheduleTaskModel[]): ScheduleTaskNode[] {
      const roots: ScheduleTaskNode[] = [];
      const nodes = new Map<string, ScheduleTaskNode>();

      for(let i = 0; i < tasks.length; i++) {
         const task = tasks[i];
         const path = task.path === "/" ? (task.label || task.name) :
            task.path + "/" + (task.label || task.name);
         const pathElements = path.split("/");
         let parent: ScheduleTaskNode = null;

         for(let j = 0; j < pathElements.length - 1; j++) {
            const key = pathElements.slice(0, j + 1).join("/");
            let current = nodes.get(key);

            if(!!current) {
               parent = current;
               continue;
            }

            current = new ScheduleTaskNode(null, pathElements[j], true);
            nodes.set(key, current);

            if(!!parent) {
               if(!parent.children) {
                  parent.children = [];
               }

               parent.children.push(current);
            }
            else {
               roots.push(current);
            }

            parent = current;
         }

         const node = new ScheduleTaskNode(task, pathElements[pathElements.length - 1], false);

         if(!!parent) {
            if(!parent.children) {
               parent.children = [];
            }

            parent.children.push(node);
         }
         else {
            roots.push(node);
         }
      }

      return roots;
   }

   /**
    * Recursively get all parents of the passed node.
    */
   private getParents(node: ScheduleTaskFlatNode, parents: ScheduleTaskFlatNode[]) {
      const parent = this.getParent(node);

      if(parent) {
         parents.push(parent);
      }

      if(parent && parent.level > 0) {
         this.getParents(parent, parents);
      }
   }

   /**
    * Iterate over each node in reverse order and return the first node that
    * has a lower level than the passed node.
    */
   private getParent(node: ScheduleTaskFlatNode) {
      const currentLevel = this.treeControl.getLevel(node);

      if(currentLevel < 1) {
         return null;
      }

      const startIndex = this.treeControl.dataNodes.indexOf(node) - 1;

      for(let i = startIndex; i >= 0; i--) {
         const currentNode = this.treeControl.dataNodes[i];

         if(this.treeControl.getLevel(currentNode) < currentLevel) {
            return currentNode;
         }
      }

      return null;
   }

   getOrgParsedLabel(taskName: string): string {
      return removeOrganization(taskName, this.currOrg);
   }

   getTaskName(task: ScheduleTaskModel): string {
      return ScheduleTaskListComponent.getTaskName(task);
   }

   notExistsMessage() {
      return ExpandStringDirective.expandString(
         "_#(js:em.scheduleBatchAction.taskNotExists)", [this.selectedTaskName]);
   }
}
