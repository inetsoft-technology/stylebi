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
import { animate, state, style, transition, trigger } from "@angular/animations";
import { SelectionModel } from "@angular/cdk/collections";
import { FlatTreeControl } from "@angular/cdk/tree";
import { HttpClient, HttpErrorResponse, HttpParams } from "@angular/common/http";
import {
   AfterViewInit,
   Component,
   ElementRef,
   OnDestroy,
   OnInit,
   TemplateRef,
   ViewChild
} from "@angular/core";
import {
   UntypedFormBuilder,
   UntypedFormControl,
   UntypedFormGroup,
   FormGroupDirective,
   NgForm,
   ValidationErrors,
   Validators
} from "@angular/forms";
import { MatBottomSheet } from "@angular/material/bottom-sheet";
import { ErrorStateMatcher } from "@angular/material/core";
import { MatDialog } from "@angular/material/dialog";
import { MatPaginator } from "@angular/material/paginator";
import { MatSnackBar } from "@angular/material/snack-bar";
import { MatSort } from "@angular/material/sort";
import { MatTableDataSource } from "@angular/material/table";
import { MatTreeFlatDataSource, MatTreeFlattener } from "@angular/material/tree";
import { DomSanitizer, SafeResourceUrl } from "@angular/platform-browser";
import { ActivatedRoute, ParamMap, Router } from "@angular/router";
import { Observable, of as observableOf, Subject, throwError, timer } from "rxjs";
import { catchError, finalize, map, takeUntil, tap } from "rxjs/operators";
import { GuiTool } from "../../../../../../portal/src/app/common/util/gui-tool";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { ScheduleTaskDialogModel } from "../../../../../../shared/schedule/model/schedule-task-dialog-model";
import { ScheduleTaskList } from "../../../../../../shared/schedule/model/schedule-task-list";
import { ScheduleTaskModel } from "../../../../../../shared/schedule/model/schedule-task-model";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { FlatTreeSelectNodeEvent } from "../../../common/util/tree/flat-tree-view.component";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { Secured } from "../../../secured";
import {
   RepositoryFlatNode,
   RepositoryTreeNode
} from "../../content/repository/repository-tree-node";
import { IdentityId, KEY_DELIMITER } from "../../security/users/identity-id";
import { ExportTaskDialogComponent } from "../import-export/export-task-dialog/export-task-dialog.component";
import { ImportTaskDialogComponent } from "../import-export/import-task-dialog/import-task-dialog.component";
import { DistributionChart, DistributionChartValue } from "../model/distribution-model";
import { MoveTaskFolderRequest } from "../model/move-task-folder-request";
import { ScheduleTaskChange } from "../model/schedule-task-change";
import { TaskDependencyModel } from "../model/task-dependency-model";
import { TaskListModel } from "../model/task-list-model";
import { MoveTaskFolderDialogComponent } from "../move-folder-dialog/move-task-folder-dialog.component";
import { ScheduleFolderTreeComponent } from "../schedule-folder-tree/schedule-folder-tree.component";
import { EmScheduleChangeService } from "./em-schedule-change.service";
import { FlatTreeNodeMenu, FlatTreeNodeMenuItem } from "../../../common/util/tree/flat-tree-model";
import { ToggleTaskResponse } from "../model/toggle-task-response";
import { ScheduleTaskDragService } from "./schedule-task-drag.service";
import { ScheduleFolderTreeAction } from "../schedule-folder-tree/schedule-folder-tree-action";

const GET_SCHEDULED_TASKS_URI = "../api/em/schedule/scheduled-tasks";
const NEW_TASKS_URI = "../api/em/schedule/new";
const TASKS_MOVE_URI = "../api/em/schedule/move-folder";
const REMOVE_TASKS_URI = "../api/em/schedule/remove";
const EXPORT_TASKS_URI = "../em/schedule/export";
const GET_EXPORT_DEPENDENT_TASKS_URI = "../api/em/schedule/export/get-dependent-tasks";
const CHECK_TASK_DEPENDENCY = "../api/em/schedule/check-dependency";
const ENABLE_TASK_URI = "../api/em/schedule/enable/task";
const RUN_SELECTED_TASKS = "../api/em/schedule/run-tasks";
const STOP_SELECTED_TASKS = "../api/em/schedule/stop-tasks";
const DISTRIBUTION_CHART_URI = "../api/em/schedule/distribution/chart";
const CHANGE_SHOW_TYPE_URI = "../api/em/schedule/change-show-type";
const CHECK_ROOT_PERMISSION_URI = "../api/em/schedule/folder/checkRootPermission";
const ASSET_FILE_BACKUP = "__asset file backup__";
const BALANCE_TASKS = "__balance tasks__";
const UPDATE_ASSETS_DEPENDENCIES = "__update assets dependencies__";
const SYSTEM_USER = "INETSOFT_SYSTEM";

export enum DistributionType {
   WEEK, DAY, HOUR
}

@Secured({
   route: "/settings/schedule/tasks",
   label: "Tasks"
})
@Searchable({
   route: "/settings/schedule/tasks",
   title: "Schedule Tasks",
   keywords: ["em.keyword.schedule", "em.keyword.task"]
})
@ContextHelp({
   route: "/settings/schedule/tasks",
   link: "EMSettingsScheduleTaskList"
})
@Component({
   selector: "em-schedule-task-list",
   templateUrl: "./schedule-task-list.component.html",
   styleUrls: ["./schedule-task-list.component.scss"],
   animations: [
      trigger("detailExpand", [
         state("collapsed", style({height: "0px", minHeight: "0"})),
         state("expanded", style({height: "*"})),
         transition("expanded <=> collapsed", animate("225ms cubic-bezier(0.4, 0.0, 0.2, 1)")),
      ]),
   ],
   providers: [
      EmScheduleChangeService,
      ScheduleTaskDragService
   ]
})
export class ScheduleTaskListComponent implements OnInit, AfterViewInit, OnDestroy {
   @ViewChild("chartDiv", { static: true }) chartDiv: ElementRef;
   @ViewChild("redistributeParams") redistributeParams: TemplateRef<any>;
   @ViewChild(MatPaginator, { static: true }) paginator: MatPaginator;
   @ViewChild(MatSort, { static: true }) sort: MatSort;
   @ViewChild("folderTree") folderTree: ScheduleFolderTreeComponent;

   loading: boolean = true;
   tasks: ScheduleTaskModel[] = [];
   expandedElement: ScheduleTaskModel | null;
   selectedNodes: RepositoryFlatNode[] = [];
   showTasksAsList: boolean = false;
   noRootPermission: boolean = false;


   // selected items go into selection model
   selection = new SelectionModel<ScheduleTaskModel>(true, []);
   excludeOwnerColumns: string[] = ["select", "name", "lastRunStatus", "nextRunStart"];
   includeOwnerColumns: string[] = ["select", "name", "user", "lastRunStatus", "nextRunStart"];
   displayedColumns: string[] = this.excludeOwnerColumns;
   expandingColumns: string[] = ["lastRunStart", "lastRunEnd", "nextRunStatus"];
   expandingHeaders: string[] = ["_#(js:Last Run Start)",
      "_#(js:Last Run End)", "_#(js:Next Run Status)"
   ];
   dataSource = new MatTableDataSource<ScheduleTaskModel>([]);

   distributionChartUrl: SafeResourceUrl;
   distributionChart: DistributionChart;
   distributionType = DistributionType.WEEK;
   distributionWeekday = 0;
   distributionHour = -1;
   distributionMinute = -1;
   DistributionType = DistributionType;

   redistributeForm: UntypedFormGroup;
   endTimeErrorMatcher: ErrorStateMatcher;
   timeZone = "";
   serverTime = "";

   private timeZoneId: string = null;
   private dateTimeFormat: string = null;
   private destroy$ = new Subject<void>();
   private loadingChart = false;
   public path = "/";
   public taskName = "";

   public fTreeControl: FlatTreeControl<RepositoryFlatNode>;
   public fTreeSource: MatTreeFlatDataSource<any, RepositoryFlatNode>;
   public fTreeFlattener: MatTreeFlattener<any, RepositoryFlatNode>;
   private getLevel = (node: RepositoryFlatNode) => node.level;
   private isExpandable = (node: RepositoryFlatNode) => node.expandable;
   private getChildren = (node: any): Observable<any[]> => observableOf(node.children);
   transformer = (node: any, level: number) => new RepositoryFlatNode(
      node.label, level, !!node.children, node, false, true,
      this.getContextMenu(node, level), this.getIcon);

   private readonly getIcon = function(expanded: boolean) {
      return expanded ? "folder-open-icon" : "folder-icon";
   };

   constructor(private http: HttpClient, private router: Router, private route: ActivatedRoute,
               public dialog: MatDialog, private pageTitle: PageHeaderService,
               private snackBar: MatSnackBar, private changeService: EmScheduleChangeService,
               private domSanitizer: DomSanitizer,
               private usersService: ScheduleUsersService,
               private bottomSheet: MatBottomSheet, fb: UntypedFormBuilder,
               defaultErrorMatcher: ErrorStateMatcher,
               private downloadService: DownloadService,
               private dragService: ScheduleTaskDragService)
   {
      this.redistributeForm = fb.group(
         {
            startTime: ["00:00:00", [Validators.required]],
            endTime: ["23:59:00", [Validators.required]],
            concurrency: [1, [Validators.required, FormValidators.positiveNonZeroIntegerInRange, FormValidators.isInteger]]
         },
         {
            validator: this.timeChronological
         }
      );

      this.endTimeErrorMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            !!this.redistributeForm.errors && !!this.redistributeForm.errors.timeChronological ||
            defaultErrorMatcher.isErrorState(control, form)
      };
      this.dataSource.data = [];

      this.fTreeControl =
         new FlatTreeControl<RepositoryFlatNode>(this.getLevel, this.isExpandable);
      this.fTreeFlattener = new MatTreeFlattener(this.transformer, this.getLevel,
         this.isExpandable, this.getChildren);
      this.fTreeSource = new MatTreeFlatDataSource(this.fTreeControl, this.fTreeFlattener);
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:Schedule Tasks)";

      this.http.get(CHANGE_SHOW_TYPE_URI).subscribe((showTasksAsList) => {
         this.loading = false;
         this.showTasksAsList = <boolean> showTasksAsList;
         this.loadTasks();
      });

      this.http.get(CHECK_ROOT_PERMISSION_URI).subscribe((rootPermission: boolean) => {
         this.noRootPermission = !rootPermission;
      });

      this.route.queryParamMap
         .subscribe((params: ParamMap) => {
            this.path = params.get("path");
            this.path = !this.path ? "/" : this.path;
            this.taskName = params.get("taskName");
         });


   }

   ngAfterViewInit(): void {
      this.loadDistributionChart();
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   loadTasks(refreshTaskAndFolder?: boolean): void {
      if(!this.currentFolder && !this.showTasksAsList) {
         this.setTasks([]);

         return;
      }

      this.loading = true;
      this.http.post<ScheduleTaskList>(GET_SCHEDULED_TASKS_URI, this.currentFolder).subscribe(
         (list) => {
            this.loading = false;
            this.setTasks(list.tasks);
            this.setDisplayColumns(list.showOwners);
            this.timeZone = list.timeZone;
            this.timeZoneId = list.timeZoneId;
            this.dateTimeFormat = list.dateTimeFormat;
            this.updateTaskList();
            this.updateServerTime();
            timer((new Date().getSeconds() % 60) * 1000, 60000)
               .pipe(takeUntil(this.destroy$))
               .subscribe(() => this.updateServerTime());
            this.changeService.onChange
               .pipe(takeUntil(this.destroy$))
               .subscribe(change => this.mergeChange(change));
         },
         (error) => {
            this.dialog.open(MessageDialog, this.setConfigs(`_#(js:Error)`,
               "Failed to load tasks: " + error.error ? error.error.message : "",
               MessageDialogType.ERROR));
         }
      );

      if(refreshTaskAndFolder && !this.showTasksAsList && this.folderTree) {
         this.folderTree.refreshTree();
      }
   }

   newTask(): void {
      // http REST requests use URI and a body object with data
      this.http.post(NEW_TASKS_URI, this.currentFolder).subscribe(
         (model: ScheduleTaskDialogModel) => this.navigateToTaskEditor(model.name, model.taskDefaultTime),
         (error) => {
            let message = error?.error?.type == "SecurityException" ? error.error.message : "Failed to get schedule task model";
            this.dialog.open(MessageDialog, this.setConfigs(`_#(js:Error)`,
               message, MessageDialogType.ERROR));
         });
   }

   removeTasks(): void {
      let body = (this.selection.selected.length == 1) ? `_#(js:em.schedule.delete.confirm)` : `_#(js:em.schedule.deleteMultiple.confirm)`;
      const dialogRef = this.dialog.open(MessageDialog, this.setConfigs(`_#(js:Confirmation)`, body, MessageDialogType.DELETE));
      dialogRef.afterClosed().subscribe(result => {
         if(result) {
            let model = new TaskListModel(this.getTaskNames());
            let selection: ScheduleTaskModel[] = [];

            for(let task of this.selection.selected) {
               let taskClone = Object.assign({}, task);
               taskClone.name = this.getTaskName(taskClone);
               selection.push(taskClone);
            }

            this.http.post(CHECK_TASK_DEPENDENCY, selection).subscribe(
               (dependencies: string[]) => {
                  if(dependencies.length) {
                     // we make a copy so that pointer won"t get messed up
                     let paramCopy = model.taskNames.slice();

                     // filter out all the dependency tasks by keeping those that are not found in dependencies
                     // we delete the filtered list
                     model.taskNames = paramCopy.filter(task => !dependencies.includes(task));

                     // if dependencies exist, we won"t delete them; let user know there are dependencies
                     this.dialog.open(MessageDialog, this.setConfigs(`_#(js:em.schedule.dependenciesFound)`,
                        `Because there are dependents of these tasks, we cannot delete the following: [${dependencies}]`,
                        MessageDialogType.DEPENDENCY
                     ));
                  }

                  // REMOVE TASKS
                  this.http.post<ScheduleTaskList>(REMOVE_TASKS_URI, selection).subscribe(
                     (list) => {
                        if(this.showTasksAsList) {
                           this.setTasks(list.tasks);
                        }

                        this.updateTaskList();
                        this.loadDistributionChart();

                        if(!this.showTasksAsList) {
                           this.loadTasks();
                        }
                     },
                     (error) => {
                        const message = error.error != null && error.error.type == "MessageException" ?
                           error.error.message : "Failed to remove selected tasks";

                        this.dialog.open(MessageDialog, this.setConfigs(`_#(js:Error)`,
                           message, MessageDialogType.ERROR));
                     }
                  );

                  // to clear the selection model after we delete them so the top checkbox would not be checked
                  this.selection.clear();
               },
               () => {
                  this.dialog.open(MessageDialog, this.setConfigs(`_#(js:Error)`,
                     "Failed to check task dependency",
                     MessageDialogType.ERROR));
               }
            );
         }
      });
   }

   public moveTasks(): void {
      const dialogRef = this.dialog.open(MoveTaskFolderDialogComponent, {
         role: "dialog",
         width: "750px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: {}
      });

      let currentSource = this.moveDialogTreeSource;
      currentSource.data = Tool.clone(this.fTreeSource.data);
      dialogRef.componentInstance.treeSource = currentSource;
      dialogRef.componentInstance.treeControl = this.fTreeControl;

      dialogRef.afterClosed().subscribe((res) => {
         if(res) {
            let moveTaskFolderRequest: MoveTaskFolderRequest = {
               target: res.data,
               tasks: this.selection.selected,
               folders: []
            };

            this.http.post(TASKS_MOVE_URI, moveTaskFolderRequest).subscribe(() => {
               this.loadTasks();
            }, (error) => {
               if(error.status == 403) {
                  this.dialog.open(MessageDialog, this.setConfigs(`_#(js:Unauthorized)`,
                     "_#(js:schedule.folder.moveTargetPermissionError)", MessageDialogType.ERROR));
               }
            });
         }
      });
   }

   /**
    * Get the tree dataSource for move task dialog tree, should not have contextMenu.
    */
   private get moveDialogTreeSource() {
      let moveDialogTran = (node: any, level: number) => new RepositoryFlatNode(
         node.label, level, !!node.children, node, false, true,
         null, this.getIcon);
      let moveDialogFlattener = new MatTreeFlattener(moveDialogTran, this.getLevel,
         this.isExpandable, this.getChildren);

      return new MatTreeFlatDataSource(this.fTreeControl, moveDialogFlattener);
   }

   importTasks(): void {
      //@temp Bug #43384
      // In the future, importing and exporting tasks should be handled in the content repository tree
      /*
      The issues with this implementation are as follows:

      1. It ignores the dependencies of the tasks.

      2. It is separate from the asset import/export. The use case for this feature is migration between environments.
      Our current best practice for migrating between environments is using the asset import/export, which can be automated using the DSL.
      The workflow as currently implemented would be: export the assets using the existing asset export,
      export the schedule XML, import the assets, import the schedule XML.
      Usability would greatly improved if there was a single archive containing all assets and tasks and that would be all that would need to be transferred.

      3. The asset import/export back end has a lot of things built into it,
      like handing missing users, auditing, controlling whether to overwrite existing items, excluding items from the import, etc.
       */

      this.dialog.open(ImportTaskDialogComponent, {
         role: "dialog",
         width: "750px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: {}
      });
   }

   exportTasks(): void {
      let selectedTasks: string[] = this.getTaskNames();
      let tasksValue = selectedTasks.join(",");
      const params = new HttpParams()
          .set("tasks", tasksValue);

      this.http.get(GET_EXPORT_DEPENDENT_TASKS_URI, {params}).subscribe((res: TaskDependencyModel[]) => {
         if(res.length < 1) {
            this.downloadService.download(
                GuiTool.appendParams(EXPORT_TASKS_URI, params));
         }
         else {
            let exportDialog = this.dialog.open(ExportTaskDialogComponent, {
               role: "dialog",
               width: "750px",
               maxWidth: "100%",
               maxHeight: "100%",
               disableClose: true,
               data: {}
            });

            exportDialog.componentInstance.model = res;
            exportDialog.afterClosed().subscribe((dialogRes: string[]) => {
               if(dialogRes != null) {
                  if(dialogRes.length > 0) {
                     selectedTasks = selectedTasks.concat(dialogRes);
                  }

                  let tasks = selectedTasks.join(",");
                  const downloadParams = new HttpParams()
                      .set("tasks", tasks);

                  this.downloadService.download(
                      GuiTool.appendParams(EXPORT_TASKS_URI, downloadParams));
               }
            });
         }
      });
   }

   search(filter: string) {
      this.dataSource.filter = filter.trim().toLowerCase();
   }

   private navigateToTaskEditor(name: string, taskDefaultTime: boolean = true): void {
      let path = !!this.selectedNodes && this.selectedNodes.length != 0 ?
         this.selectedNodes[0].data.path : "/";
      this.router.navigate(["/settings/schedule/tasks", name],
         {queryParams: {taskDefaultTime: taskDefaultTime, path: path}});
   }

   getEditorQueryParams(): any {
      let path = !!this.selectedNodes && this.selectedNodes.length != 0 ?
         this.selectedNodes[0].data.path : "/";
      return {path: path};
   }

   runTasks() {
      this.http.post(RUN_SELECTED_TASKS, new TaskListModel(this.getTaskNames()))
         .pipe(catchError(error => this.handleError(error)))
         .subscribe();
   }

   stopTasks() {
      this.http.post(STOP_SELECTED_TASKS, new TaskListModel(this.getTaskNames()))
         .pipe(catchError(error => this.handleError(error)))
         .subscribe();
   }

   public handleError<T>(error: HttpErrorResponse): Observable<T> {
      this.snackBar.open(error.error.message, "_#(js:Close)", {duration: Tool.SNACKBAR_DURATION});
      return throwError(error);
   }

   // returns true if all rows on page are selected
   isAllSelected(): boolean {
      let pageIndex = this.paginator.pageIndex;
      let pageSize = this.paginator.pageSize;
      let pgStart = pageIndex * pageSize;

      // current page items
      let pageItems = this.dataSource.filteredData.slice(pgStart, pgStart + pageSize);
      let all = true;

      // if empty selected list
      if(!this.selection.selected.length) {
         return false;
      }

      // check if entire page is on selection model
      pageItems.forEach(
         row => {
            if(!this.selection.selected.includes(row)) {
               all = false;
            }
         }
      );

      return all;
   }

   masterToggle(): void {
      if(this.isAllSelected()) {
         this.selection.clear();
      }
      else {
         let pageIndex = this.paginator.pageIndex;
         let pageSize = this.paginator.pageSize;
         let pgStart = pageIndex * pageSize;

         let pageItems = this.dataSource.filteredData.slice(pgStart, pgStart + pageSize);
         pageItems.forEach(
            row => this.selection.select(row)
         );
      }
   }

   isToggleTasksEnabledDisabled(): boolean {
      if(this.selection.selected.length === 0) {
         return true;
      }

      return this.selection.selected.some((val, i, arr) =>
         val.enabled !== arr[0].enabled || !val.editable);
   }

   get allSelectedAreDisabled(): boolean {
      if(this.selection.selected.length === 0) {
         return false;
      }

      return this.selection.selected.every(val => !val.enabled);
   }

   toggleTasksEnabled(): void {
      this.http.post<ToggleTaskResponse>(ENABLE_TASK_URI, new TaskListModel(this.getTaskNames()))
         .subscribe(res => {
            if(res.archiveRequiresConfiguration) {
               this.snackBar.open("_#(js:em.schedule.task.archiveRequiresConfiguration)",
                  "_#(js:Link)").onAction()
                  .subscribe(
                     _ => this.router.navigate(["/settings/general"], {fragment: "archive"}));
            }
         });
   }

   removable(): boolean {
      return !this.selection.selected.map(sel => sel.removable).includes(false);
   }

   canDelete(): boolean {
      return !this.selection.selected.map(sel => sel.canDelete).includes(false);
   }

   editable(): boolean {
      return !this.selection.selected.map(sel => sel.editable).includes(false);
   }

   hasSelected(): boolean {
      return this.selection.selected.length > 0;
   }

   internalTask(taskName: string): boolean {
      return ScheduleTaskListComponent.internalTask0(taskName);
   }

   static internalTask0(taskName: string): boolean {
      return taskName === ASSET_FILE_BACKUP || taskName === BALANCE_TASKS
         || taskName === UPDATE_ASSETS_DEPENDENCIES; // extend to other internal tasks with "&&" if needed
   }

   canEditTask(task: ScheduleTaskModel): boolean {
      if(this.internalTask(task.name)) {
         return task.editable;
      }

      return task.canDelete && task.editable;
   }

   // returns params
   private setConfigs(title: string, content: string, type: MessageDialogType): any {
      return {
         width: "350px",
         data: {
            title: title,
            content: content,
            type: type
         }
      };
   }

   private getTaskNames(): string[] {
      return this.selection.selected.map(task => this.getTaskName(task));
   }

   getTaskName(task: ScheduleTaskModel): string {
      return ScheduleTaskListComponent.getTaskName(task);
   }

   public static getTaskName(task: ScheduleTaskModel): string {
      if(ScheduleTaskListComponent.internalTask0(task.name)) {
         return task.name;
      }

      if(!!task.owner && task.owner.name == SYSTEM_USER) {
         return task.owner.name + KEY_DELIMITER + task.owner.organization + "__" + task.name;
      }

     return !!task.owner && !task.name.startsWith(task.owner.name) &&
        !task.name.startsWith("MV Task:") && !task.name.startsWith("MV Task Stage 2:") ?
        task.owner.name + KEY_DELIMITER + task.owner.organization + ":" + task.name : task.name;
   }

   getDistributionDayLabel(): string {
      switch(this.distributionWeekday) {
      case 1:
         return "_#(js:Sunday)";
      case 2:
         return "_#(js:Monday)";
      case 3:
         return "_#(js:Tuesday)";
      case 4:
         return "_#(js:Wednesday)";
      case 5:
         return "_#(js:Thursday)";
      case 6:
         return "_#(js:Friday)";
      case 7:
         return "_#(js:Saturday)";
      default:
         return null;
      }
   }

   getDistributionHourLabel(): string {
      return (this.distributionHour < 10 ? "0" : "") + this.distributionHour + ":00";
   }

   getDistributionValueCoords(value: DistributionChartValue): string {
      return `${value.x},${value.y},${value.x + value.width},${value.y + value.height}`;
   }

   selectDistributionValue(value: DistributionChartValue): void {
      switch(this.distributionType) {
      case DistributionType.WEEK:
         this.distributionType = DistributionType.DAY;
         this.distributionWeekday = value.index + 1;
         this.loadDistributionChart();
         this.updateTaskList();
         break;
      case DistributionType.DAY:
         this.distributionType = DistributionType.HOUR;
         this.distributionHour = value.index;
         this.loadDistributionChart();
         this.updateTaskList();
         break;
      case DistributionType.HOUR:
         this.distributionMinute = value.index;
         this.loadDistributionChart();
         this.updateTaskList();
         break;
      }
   }

   showWeekDistribution(): void {
      this.distributionType = DistributionType.WEEK;
      this.distributionWeekday = 0;
      this.distributionHour = -1;
      this.distributionMinute = -1;
      this.loadDistributionChart();
      this.updateTaskList();
   }

   showDayDistribution(): void {
      this.distributionType = DistributionType.DAY;
      this.distributionHour = -1;
      this.distributionMinute = -1;
      this.loadDistributionChart();
      this.updateTaskList();
   }

   private setTasks(list: ScheduleTaskModel[]): void {
      this.tasks = list;
      this.dataSource.data = list;
      this.dataSource.sortingDataAccessor = (item, property) => {
         switch(property) {
         case "name": return item.label;
         case "user": return this.internalTask(item.name) ? "" : item.owner;
         case "lastRunStatus": return item.status.lastRunStatus;
         case "lastRunStart": return item.status.lastRunStart;
         case "lastRunEnd": return item.status.lastRunEnd;
         case "nextRunStatus": return item.status.nextRunStatus;
         case "nextRunStart": return item.status.nextRunStart;
         default: return item[property];
         }
      };

      this.dataSource.sort = this.sort;
      this.dataSource.paginator = this.paginator;
      this.expandedElement = list.find(task =>
         this.expandedElement && task.name == this.expandedElement.name);

      // if just finished add/edit a task, go to the page that contains the task.
      if(this.taskName) {
         for(let i = 0; i < this.tasks.length; i++) {
            if(this.taskName == this.tasks[i].owner + ":" + this.tasks[i].name) {
               const page = Math.floor(i / this.paginator.pageSize);
               this.paginator.pageIndex = page;
            }
         }
      }
   }

   private mergeChange(change: ScheduleTaskChange): void {
      const list = this.tasks.slice();
      const index = list.findIndex(t => t.name === change.name);

      if(index >= 0) {
         if(change.type === "REMOVED") {
            list.splice(index, 1);
         }
         else {
            list[index] = change.task;
         }
      }
      else if(change.type === "ADDED") {
         if(this.showTasksAsList || (!this.currentFolder || !this.currentFolder.path ||
               this.currentFolder.path == "/") &&
            (!change?.task?.path || change?.task?.path == "/") ||
            this.currentFolder.path == change?.task?.path)
         {
            list.push(change.task);
         }
      }

      this.setTasks(list);
      this.updateTaskList();
   }

   private updateTaskList(): void {
      switch(this.distributionType) {
      case DistributionType.WEEK:
         this.dataSource.data = this.tasks || [];
         break;
      case DistributionType.DAY:
         this.dataSource.data = this.tasks.filter((task) => {
            return !!task.distribution.days.find(
               (group) => group.index === this.distributionWeekday);
         });
         break;
      case DistributionType.HOUR:
         this.dataSource.data = this.tasks.filter((task) => {
            return !!task.distribution.days.find((day) => {
               if(day.index === this.distributionWeekday) {
                  return !!day.children.find((hour) => {
                     if(hour.index === this.distributionHour) {
                        return this.distributionMinute < 0 ||
                           !!hour.children.find(min => min.index === this.distributionMinute);
                     }

                     return false;
                  });
               }

               return false;
            });
         });
         break;
      }

      const selectedIds = this.selection.selected.map((s) => s.name);
      const selected: ScheduleTaskModel[] = this.dataSource.data.filter(
         (row) => selectedIds.indexOf(row.name) !== -1
      );
      this.selection = new SelectionModel<ScheduleTaskModel>(true, selected);
   }

   setDisplayColumns(showOwners: boolean): void {
      if(showOwners) {
         this.displayedColumns = this.includeOwnerColumns;
      }
      else {
         this.displayedColumns = this.excludeOwnerColumns;
      }
   }

   onChartResized(): void {
      if(!this.loadingChart) {
         this.loadDistributionChart();
      }
   }

   private onChartLoaded(): void {
      Promise.resolve(null).then(() => this.loadingChart = false);
   }

   private loadDistributionChart(): void {
      this.loadingChart = true;
      switch(this.distributionType) {
      case DistributionType.WEEK:
         this.loadWeekDistributionChart();
         break;
      case DistributionType.DAY:
         this.loadDayDistributionChart();
         break;
      case DistributionType.HOUR:
         this.loadHourDistributionChart();
         break;
      }
   }

   private loadWeekDistributionChart(): void {
      const {width, height} = GuiTool.getElementRect(this.chartDiv.nativeElement);
      const params = new HttpParams()
         .set("width", `${Math.round(width)}`)
         .set("height", `${Math.round(height)}`);
      this.http.get<DistributionChart>(DISTRIBUTION_CHART_URI, {params})
         .pipe(
            catchError(error => this.handleWeekError(error)),
            tap(chart => this.distributionChart = chart),
            map(chart => this.domSanitizer.bypassSecurityTrustResourceUrl(chart.image)),
            finalize(() => this.onChartLoaded())
         )
         .subscribe(url => this.distributionChartUrl = url);

   }

   private loadDayDistributionChart(): void {
      const uri = `${DISTRIBUTION_CHART_URI}/${this.distributionWeekday}`;
      const {width, height} = GuiTool.getElementRect(this.chartDiv.nativeElement);
      const params = new HttpParams()
         .set("width", `${Math.round(width)}`)
         .set("height", `${Math.round(height)}`);
      this.http.get<DistributionChart>(uri, {params})
         .pipe(
            catchError(error => this.handleDayError(error)),
            tap(chart => this.distributionChart = chart),
            map(chart => this.domSanitizer.bypassSecurityTrustResourceUrl(chart.image)),
            finalize(() => this.onChartLoaded())
         )
         .subscribe(url => this.distributionChartUrl = url);
   }

   private loadHourDistributionChart(): void {
      const uri = `${DISTRIBUTION_CHART_URI}/${this.distributionWeekday}/${this.distributionHour}`;
      const {width, height} = GuiTool.getElementRect(this.chartDiv.nativeElement);
      let params = new HttpParams()
         .set("width", `${Math.round(width)}`)
         .set("height", `${Math.round(height)}`);

      if(this.distributionMinute >= 0) {
         params = params.set("highlight", `${this.distributionMinute}`);
      }

      this.http.get<DistributionChart>(uri, {params})
         .pipe(
            catchError(error => this.handleHourError(error)),
            tap(chart => this.distributionChart = chart),
            map(chart => this.domSanitizer.bypassSecurityTrustResourceUrl(chart.image)),
            finalize(() => this.onChartLoaded())
         )
         .subscribe(url => this.distributionChartUrl = url);
   }

   openDebugDialog(task: ScheduleTaskModel) {
      this.dialog.open(MessageDialog, {
         width: "60%",
         data: {
            title: "_#(js:Execution Failed)",
            content: task.status.errorMessage,
            type: MessageDialogType.ERROR
         }
      });
   }

   private handleWeekError(error: HttpErrorResponse): Observable<DistributionChart> {
      this.snackBar.open("_#(js:em.schedule.distribution.weekError)", "_#(js:Close)", {
         duration: Tool.SNACKBAR_DURATION,
      });
      console.error("Failed to get week distribution: ", error);
      return throwError(error);
   }

   private handleDayError(error: HttpErrorResponse): Observable<DistributionChart> {
      this.snackBar.open("_#(js:em.schedule.distribution.dayError)", "_#(js:Close)", {
         duration: Tool.SNACKBAR_DURATION,
      });
      console.error("Failed to get day distribution: ", error);
      return throwError(error);
   }

   private handleHourError(error: HttpErrorResponse): Observable<DistributionChart> {
      this.snackBar.open("_#(js:em.schedule.distribution.hourError)", "_#(js:Close)", {
         duration: Tool.SNACKBAR_DURATION,
      });
      console.error("Failed to get hour distribution: ", error);
      return throwError(error);
   }

   private timeChronological: (FormGroup) => ValidationErrors | null = (group: UntypedFormGroup) => {
      if(!group) {
         return null;
      }

      const startControl = group.get("startTime");
      const endControl = group.get("endTime");

      if(startControl && endControl) {
         const startTime = DateTypeFormatter.toTimeInstant(
            startControl.value, DateTypeFormatter.ISO_8601_TIME_FORMAT);
         const endTime = DateTypeFormatter.toTimeInstant(
            endControl.value, DateTypeFormatter.ISO_8601_TIME_FORMAT);

         if(startTime && endTime) {
            const startDate =
               new Date(2000, 0, 1, startTime.hours, startTime.minutes, startTime.seconds);
            const endDate = new Date(2000, 0, 1, endTime.hours, endTime.minutes, endTime.seconds);

            if(startDate >= endDate) {
               return {timeChronological: true};
            }
         }
      }

      return null;
   };

   private updateServerTime(): void {
      if(this.timeZoneId) {
         const now = new Date();
         const datetime =  DateTypeFormatter.formatInTimeZone(now, this.timeZoneId,
            DateTypeFormatter.fixFormatToMoment(this.dateTimeFormat));
         this.serverTime = `${datetime}`;
      }
   }

   public nodeSelected(evt: FlatTreeSelectNodeEvent): void {
       this.selectedNodes = [<RepositoryFlatNode>evt.node];
       this.loadTasks();
   }

   get currentFolder(): RepositoryTreeNode {
      if(!this.showTasksAsList && this.selectedNodes.length > 0) {
         return this.selectedNodes[0].data;
      }

      return null;
   }

   public hasSelectedTreeNode(): boolean {
      return this.selectedNodes.length > 0;
   }

   showAllTasks(showAll: boolean): void {
      this.showTasksAsList = showAll;
      let params = new HttpParams().set("showTasksAsList", showAll + "");
      this.http.put(CHANGE_SHOW_TYPE_URI, null, {params}).subscribe(() => {
         this.loadTasks();
      });
   }

   isExportTasksDisabled(): boolean {
      return !this.hasSelected() || !this.removable() || !this.canDelete();
   }

   showExpandElement(row: ScheduleTaskModel) {
      return this.expandedElement = this.expandedElement === row ? null : row;
   }

   private getContextMenu(node: RepositoryTreeNode, level: number): () => FlatTreeNodeMenu | null {
      return () => this.createContextMenu(node, level);
   }

   private createContextMenu(node: RepositoryTreeNode, level: number): FlatTreeNodeMenu | null {
      const items: FlatTreeNodeMenuItem[] = [];

      items.push({
         name: "new-task-folder",
         label: "_#(js:New Folder)",
         disabled: () => node.properties[ScheduleFolderTreeAction.CREATE] != "true"
      });

      if(node.path !== "/") {
         items.push(...[
            {
               name: "edit-task-folder",
               label: "_#(js:Edit Folder)",
               disabled: () => node.properties[ScheduleFolderTreeAction.EDIT] != "true"
            },
            {
               name: "delete-task-folder",
               label: "_#(js:Delete Folder)",
               disabled: () => node.properties[ScheduleFolderTreeAction.DELETE] != "true"
            }
         ]);
      }

      return items.length > 0 ? {items} : null;
   }

   dragTask(event: any, task: ScheduleTaskModel) {
      let moveTasks: ScheduleTaskModel[] = this.selection?.isSelected(task) ?
         this.selection.selected : [task];
      this.dragService.put("tasks", JSON.stringify(moveTasks));
   }

   isDataCycle(task: ScheduleTaskModel) {
      return task.name.startsWith("DataCycle Task:") && !task.owner?.name.startsWith("DataCycle Task:");
   }

   isCreateTaskEnabled(): boolean {
      let node: RepositoryFlatNode = this.selectedNodes?.length > 0 ? this.selectedNodes[0] : null;
      return node == null && !this.noRootPermission ||
         !!node?.data?.properties && node.data.properties[ScheduleFolderTreeAction.READ] == "true";
   }
}
