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
import { HttpParams } from "@angular/common/http";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { Observable } from "rxjs";
import { AssemblyAction } from "../../../common/action/assembly-action";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { AggregateRef } from "../../../common/data/aggregate-ref";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { DataRef } from "../../../common/data/data-ref";
import { DataRefType } from "../../../common/data/data-ref-type";
import { ExpressionRef } from "../../../common/data/expression-ref";
import { FormulaType } from "../../../common/data/formula-type";
import { GuiTool } from "../../../common/util/gui-tool";
import { Tool } from "../../../../../../shared/util/tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { Viewsheet } from "../../../composer/data/vs/viewsheet";
import { ContextMenuActions } from "../../../widget/context-menu/context-menu-actions";
import { FormulaEditorDialog } from "../../../widget/formula-editor/formula-editor-dialog.component";
import { ModelService } from "../../../widget/services/model.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { CalculateRef } from "../../data/calculate-ref";
import { ModifyAggregateFieldEvent } from "../../event/modify-aggregate-field-event";
import { ModifyCalculateFieldEvent } from "../../event/modify-calculate-field-event";
import { BindingTreeService } from "./binding-tree.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { ChartRef } from "../../../common/data/chart-ref";
import { SourceInfoType } from "../../data/source-info-type";
import { SourceInfo } from "../../data/source-info";
import { ChartBindingModel } from "../../data/chart/chart-binding-model";
import { ChangeChartRefEvent } from "../../event/change-chart-ref-event";
import { ChartGeoRef } from "../../data/chart/chart-geo-ref";
import { GeographicOptionInfo } from "../../data/chart/geographic-option-info";
import { FeatureMappingInfo } from "../../data/chart/feature-mapping-info";
import { EditGeographicDialog } from "./edit-geographic-dialog.component";
import { GeoProvider } from "../../../common/data/geo-provider";
import { VSGeoProvider } from "../../editor/vs-geo-provider";
import { ChangeGeographicEvent } from "../../event/change-geographic-event";
import { VsWizardEditModes } from "../../../vs-wizard/model/vs-wizard-edit-modes";

const MODIFY_CALC_URI: string = "/events/vs/calculate/modifyCalculateField";
const CHECK_CALC_TRAP: string = "../api/vs/calculate/checkCalcTrap";
const CHECK_IN_USED_ASSEMBLIES: string = "../api/vs/calculate/get-in-use-assemblies/";

/**
 * Base class for chart-specific actions shared by all contexts.
 */
export class VSBindingTreeActions extends ContextMenuActions {
   private CUBE_VS: string = "___inetsoft_cube_";
   protected SET_GEOGRAPHIC: string = "set";
   protected CLEAR_GEOGRAPHIC: string = "clear";
   protected CONVERT_TO_MEASURE: number = 1;
   protected CONVERT_TO_DIMENSION: number = 2;
   protected runtimeId: string;
   protected socket: ViewsheetClientService;

   constructor(viewsheet: Viewsheet,
               protected selectedNode: TreeNodeModel,
               protected selectedNodes: TreeNodeModel[],
               protected dialogService: NgbModal,
               protected treeService: BindingTreeService,
               protected modelService: ModelService,
               protected assemblyName: string,
               private grayedOutFields: DataRef[] = null,
               private isWizard: boolean, private wizardOriginalMode?: VsWizardEditModes)
   {
      super();

      if(viewsheet) {
         this.runtimeId = viewsheet.runtimeId;
         this.socket = viewsheet.socketConnection;
      }
   }

   /** @inheritDoc */
   protected createMenuActions(actions: AssemblyAction[],
                               groups: AssemblyActionGroup[]): AssemblyActionGroup[]
   {
      return super.createMenuActions(actions, groups.concat([
         new AssemblyActionGroup([
            {
               id: () => "binding tree new-calculated-field",
               label: () => "_#(js:New Calculated Field)",
               icon: () => "fa fa-eye-slash",
               enabled: () => true,
               visible: () => this.isNewCalculatedVisible(),
               action: () => this.createCalculatedField()
            },
            {
               id: () => "binding tree edit",
               label: () => "_#(js:Edit)",
               icon: () => "fa fa-eye-slash",
               enabled: () => true,
               visible: () => this.isEditCalculatedVisible(),
               action: () => this.editCalculatedField()
            },
            {
               id: () => "binding tree remove",
               label: () => "_#(js:Remove)",
               icon: () => "fa fa-eye-slash",
               enabled: () => true,
               visible: () => this.isEditCalculatedVisible(),
               action: () => this.removeCalculatedField()
            },
            {
               id: () => "binding tree convert-to-measure",
               label: () => "_#(js:Convert To Measure)",
               icon: () => "fa fa-eye-slash",
               enabled: () => true,
               visible: () => this.isConvertToMeasureVisible(this.currentEntry),
               action: () => this.convertToMeasure()
            },
            {
               id: () => "binding tree convert-to-dimension",
               label: () => "_#(js:Convert To Dimension)",
               icon: () => "fa fa-eye-slash",
               enabled: () => true,
               visible: () => this.isConvertToDimensionVisible(this.currentEntry),
               action: () => this.convertToDimension()
            }
         ])
      ]));
   }

   protected get currentEntry(): AssetEntry {
      if(!this.selectedNode) {
         return null;
      }

      return this.selectedNode.data;
   }

   /**
    * Return if New Calculated Field action visible.
    */
   private isNewCalculatedVisible(): boolean {
      if(!this.currentEntry || this.isEmbedded()) {
         return false;
      }

      if(this.isAssemblyTreeNode(this.currentEntry)) {
         return false;
      }

      let entry: AssetEntry = this.currentEntry;
      let pentry: AssetEntry = this.treeService.getParent(entry);

      if(AssetEntryHelper.couldCreateCubeMeasure(entry)) {
         return true;
      }

      let folder: AssetEntry = entry;

      if(AssetEntryHelper.isFolder(entry) &&
         (AssetEntryHelper.getEntryName(entry) == "_#(js:Dimensions)" ||
         AssetEntryHelper.getEntryName(entry) == "_#(js:Measures)"))
      {
         folder = pentry;
      }

      return AssetEntryHelper.isTable(folder) &&
         folder.properties["CUBE_TABLE"] != "true" ||
         AssetEntryHelper.isLogicModel(folder) ||
         AssetEntryHelper.isPhysicalTable(folder);
   }

   protected isAssemblyTreeNode(entry): boolean {
      return entry.path.indexOf("/components/__vs_assembly") > -1;
   }

   /**
    * Return if Edit/Remove Calculated Field action visible.
    */
   private isEditCalculatedVisible(): boolean {
      if(!this.currentEntry || this.isEmbedded() ||
         this.currentEntry.properties["isPreparedCalc"] == "true")
      {
         return false;
      }

      if(this.isAssemblyTreeNode(this.currentEntry)) {
         return false;
      }

      return AssetEntryHelper.couldEditCubeMeasure(this.currentEntry) ||
         (AssetEntryHelper.isColumn(this.currentEntry) &&
         !!this.currentEntry.properties["basedOnDetail"]);
   }

   /**
    * Check if convert to measure menu visible.
    * @param entry the current asset entry.
    * @return if convert to measure menu visible.
    */
   protected isConvertToMeasureVisible(entry: AssetEntry): boolean {
      return false;
   }

   /**
    * Check if convert to dimension menu visible.
    * @param entry the current asset entry.
    * @return if convert to dimension menu visible.
    */
   protected isConvertToDimensionVisible(entry: AssetEntry): boolean {
      return false;
   }

   /**
    * Convert chart ref.
    * @param type the convert type.
    * @return Observable.
    */
   protected convertRef(type: number): void {

   }

   protected convertToDimension(): void {
      this.convertRef(this.CONVERT_TO_DIMENSION);
   }

   protected convertToMeasure(): void {
      this.convertRef(this.CONVERT_TO_MEASURE);
   }

   private createCalculatedField(): void {
      if(this.isCube(this.currentEntry)) {
         this.openFormulaEditorDialog(true, true);
         return;
      }

      this.openCalculationDialog();
   }

   private editCalculatedField(): void {
      if(this.isCube(this.currentEntry)) {
         this.openFormulaEditorDialog(true, false);
         return;
      }

      this.openFormulaEditorDialog();
   }

   private openCalculationDialog(): void {
      let tableName: string = this.getTableName(this.currentEntry);

      this.getFormulaFields(null, tableName).subscribe((fieldsInfo) => {
         const options: NgbModalOptions = {windowClass: "formula-dialog", backdrop: "static"};
         let calcDialog: FormulaEditorDialog = ComponentTool.showDialog(this.dialogService,
            FormulaEditorDialog, (result: any) => {
               this.editFormulaOver(result, fieldsInfo);
            }, options);
         calcDialog.formulaName = this.getNextCalculateFieldName(<string[]> fieldsInfo.calcFieldsGroup);
         calcDialog.isCalc = true;
         calcDialog.calcType = "detail";
         calcDialog.createCalcField = true;
         calcDialog.formulaType = FormulaType.SCRIPT;
         calcDialog.expression = "";
         calcDialog.vsId = this.runtimeId;
         calcDialog.availableFields = <DataRef[]> fieldsInfo.aggregateFields;
         calcDialog.columns = <DataRef[]> fieldsInfo.columnFields;
         calcDialog.aggregates = <DataRef[]> fieldsInfo.aggregateFields;
         calcDialog.calcFieldsGroup = <string[]> fieldsInfo.calcFieldsGroup;
         calcDialog.grayedOutFields = <DataRef[]> fieldsInfo.grayedOutFields;
         calcDialog.userAggNames = <string[]> fieldsInfo.userAggNames;
         calcDialog.dataType = "string";
         calcDialog.aggregateModify.subscribe((result: any) => {
            this.modifyAggregateField(tableName, result.nref, result.oref, calcDialog, tableName);
         });
         calcDialog.aggregateDelete.subscribe((result: any) => {
            this.deleteAggregateField(tableName, result.nref, result.oref, calcDialog, tableName);
         });
         calcDialog.sqlMergeable = fieldsInfo.sqlMergeable == true;
      });
   }

   private removeCalculatedField(): void {
      let msg: string = "_#(js:common.tree.deleteCalField)";
      ComponentTool.showConfirmDialog(this.dialogService, "_#(js:Confirm)", msg,
         {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
         .then((result: string) => {
            if("yes" == result) {
               let tableName: string = this.getTableName(this.currentEntry);
               let refName: string = this.currentEntry.properties["attribute"];
               this.modifyCalculateField(tableName, null, true, false, refName, null, false, true);
            }
         });
   }

   private openFormulaEditorDialog(isCube?: boolean, cubeCreate?: boolean): void {
      let tableName: string = this.getTableName(this.currentEntry);
      const options: NgbModalOptions = {windowClass: "formula-dialog", backdrop: "static"};
      this.getFormulaFields(null, tableName).subscribe((fieldsInfo) => {
         let dialog: FormulaEditorDialog = ComponentTool.showDialog(this.dialogService,
            FormulaEditorDialog, (result: any) => {
               this.editFormulaOver(result, fieldsInfo, isCube);
            }, options);
         dialog.isCalc = true;
         dialog.calcType = "true" != this.currentEntry.properties["basedOnDetail"] ? "aggregate" : "detail";
         dialog.expression = this.currentEntry.properties["script"];
         dialog.formulaType = isCube ? FormulaType.SCRIPT
            : "true" == this.currentEntry.properties["isSQL"] ?
            FormulaType.SQL : FormulaType.SCRIPT;
         dialog.formulaName = cubeCreate ? "" : this.getNameFromPath(this.currentEntry.path);
         dialog.dataType = !!this.currentEntry.properties["dtype"] ?
            this.currentEntry.properties["dtype"] : "double";
         dialog.createCalcField = true;
         dialog.vsId = this.runtimeId;
         dialog.availableFields = <DataRef[]> fieldsInfo.aggregateFields;
         dialog.userAggNames = <string[]> fieldsInfo.userAggNames;
         dialog.columns = <DataRef[]> fieldsInfo.columnFields;
         dialog.aggregates = <DataRef[]> fieldsInfo.aggregateFields;
         dialog.selfVisible = false;
         dialog.sqlMergeable = fieldsInfo.sqlMergeable == true;
         dialog.grayedOutFields = this.grayedOutFields;
         dialog.aggregateModify.subscribe((result: any) => {
            this.modifyAggregateField(tableName, result.nref, result.oref, dialog, tableName);
         });
         dialog.aggregateDelete.subscribe((result: any) => {
            this.deleteAggregateField(tableName, result.nref, result.oref, dialog, tableName);
         });

         if(isCube) {
            dialog.isCube = true;
            let currentPath = this.currentEntry.path;
            let columnTreeRoot: TreeNodeModel = Tool.clone(cubeCreate ? this.selectedNode.children[1] :
               GuiTool.getNodeByPath(currentPath.substring(0, currentPath.indexOf("/Measure")),
                  this.treeService.bindingTreeModel).children[1]);
            columnTreeRoot.label = "_#(js:Measures)";
            columnTreeRoot.expanded = true;
            dialog.columnTreeRoot = {
               label: "_#(js:root)",
               children: [ columnTreeRoot ]
            };
         }
      });
   }

   private editFormulaOver(obj: any, fieldsInfo: any, isCube?: boolean): void {
      let refName: string = this.currentEntry == null ?
         null : this.getNameFromPath(this.currentEntry.path);
      let needConfirm: boolean = false;

      if(refName != obj.formulaName && !this.isValidName(obj.formulaName, fieldsInfo.allcolumns)) {
         ComponentTool.showMessageDialog(this.dialogService, "_#(js:Error)", "_#(js:Duplicate Name)!");
         return;
      }

      let oCalcType = "true" != this.currentEntry.properties["basedOnDetail"] ?
         "aggregate" : "detail";

      if(oCalcType != obj.calcType) {
         needConfirm = true;
      }

      let eref: ExpressionRef = new ExpressionRef();
      eref.classType = "ExpressionRef";
      eref.exp = obj.expression;
      eref.name = obj.formulaName;

      if(isCube) {
         eref.refType = <number> DataRefType.CUBE_MEASURE;
      }

      let calc: CalculateRef = new CalculateRef();
      calc.classType = "CalculateRef";
      calc.dataRefModel = eref;
      calc.sql = obj.formulaType == FormulaType.SQL;
      calc.dataType = obj.dataType;
      let isAggregate: boolean = obj.calcType != undefined ? obj.calcType == "aggregate" :
         this.currentEntry && "true" != this.currentEntry.properties["basedOnDetail"];
      calc.baseOnDetail = !isAggregate;

      let tableName: string = this.getTableName(this.currentEntry);

      this.modifyCalculateField(tableName, calc, false, !this.isEditCalculatedVisible(),
         refName, this.getDimensionType(), needConfirm);
   }

   private getDimensionType(): string {
      if(!this.currentEntry || !AssetEntryHelper.isFolder(this.currentEntry)) {
         return null;
      }

      if(AssetEntryHelper.getEntryName(this.currentEntry) == "_#(js:Dimensions)") {
         return "true";
      }
      else if(AssetEntryHelper.getEntryName(this.currentEntry) == "_#(js:Measures)") {
         return "false";
      }

      return null;
   }

   /**
    * Check if the group name is suitable.
    */
   private isValidName(name: string, allcolumns: DataRef[]): boolean {
      if(allcolumns == null || allcolumns.length == 0) {
         return true;
      }

      for(let val of allcolumns) {
         if(val && val.name.toUpperCase() == name.toUpperCase()) {
            return false;
         }
      }

      return true;
   }

   /**
    * Return a new calculate field name.
    */
   private getNextCalculateFieldName(calcFieldsGroup: string[]): string {
      let calculateName: string = "";

      for(let i = 1; true; i++) {
         let name: string = "CalcField" + i;

         if(calcFieldsGroup.indexOf(name) == -1) {
            calculateName = name;
            break;
         }
      }

      return calculateName;
   }

   /**
    * Check if the calc field based on cube or not.
    */
   private isCube(entry: AssetEntry) {
      return AssetEntryHelper.couldCreateCubeMeasure(entry) ||
         AssetEntryHelper.couldEditCubeMeasure(entry);
   }

   /**
    * Get table name for the given item.
    */
   private getTableName(entry: AssetEntry): string {
      if(AssetEntryHelper.isFolder(entry) &&
         !AssetEntryHelper.isTable(entry) &&
         !AssetEntryHelper.isPhysicalTable(entry) &&
         !AssetEntryHelper.isLogicModel(entry))
      {
         return this.treeService.getTableName(this.treeService.getParent(entry));
      }
      else if(AssetEntryHelper.isLogicModel(entry)) {
         return this.getNameFromPath(entry.path);
      }

      return this.treeService.getTableName(entry);
   }

   /**
    * Get table name from path.
    */
   private getNameFromPath(str: string): string {
      let tname: string = str;

      if(str != null) {
         if(tname != "") {
            let idx: number = tname.lastIndexOf("/");

            return idx >= 0 ? tname.substr(idx + 1) : tname;
         }
      }

      return tname;
   }

   protected isEmbedded(): boolean {
      return !!this.assemblyName && this.assemblyName.indexOf(".") != -1;
   }

   protected isMeasureColumn(entry: AssetEntry): boolean {
      let cubeColumnType: number = this.treeService.getCubeColumnType(entry);
      return cubeColumnType != null && (cubeColumnType & 1) == 1;
   }

   protected isDimensionColumn(entry: AssetEntry): boolean {
      let cubeColumnType: number = this.treeService.getCubeColumnType(entry);
      return cubeColumnType != null && (cubeColumnType & 1) == 0;
   }

   private getFormulaFields(oname?: string, tableName?: string): Observable<any> {
      let params: string = "?vsId=" + Tool.byteEncode(this.runtimeId) + "&assemblyName=null";
      params = tableName ? params + "&tableName=" + encodeURIComponent(tableName) : params;

      return this.modelService.getModel("../api/composer/vsformula/fields" + params);
   }

   private modifyCalculateField(tname: string, cref: CalculateRef, remove: boolean, create: boolean,
                                refname: string, dimtype: string, needConfirm: boolean = false,
                                confirmed: boolean = false): void
   {
      const params = new HttpParams()
         .set("tname", tname)
         .set("refname", refname);

      if(!needConfirm || confirmed) {
         const name: string = this.assemblyName;
         const event: ModifyCalculateFieldEvent = new ModifyCalculateFieldEvent(name,
            cref, tname, remove, create, refname, dimtype, false, true,
            this.isWizard, this.wizardOriginalMode);

         this.checkTrap(cref, params, event, remove);
         return;
      }

      let url: string = CHECK_IN_USED_ASSEMBLIES + Tool.encodeURIComponentExceptSlash(this.runtimeId);

      this.modelService.getModel<string>(url, params).subscribe(names => {
         if(!!names) {
            let msg =  Tool.formatCatalogString("_#(js:common.tree.editCalcField)", [names]);

            ComponentTool.showConfirmDialog(this.dialogService, "_#(js:Confirm)", msg,
               {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
               .then((result: string) => {
                  if("yes" == result) {
                     this.modifyCalculateField(tname, cref, remove, create, refname, dimtype, false, true);
                  }
               });
         }
         else {
            this.modifyCalculateField(tname, cref, remove, create, refname, dimtype, false, true);
         }
      });
   }

   private checkTrap(calc: CalculateRef, params: HttpParams, event: ModifyCalculateFieldEvent,
                     remove: boolean = false): void
   {
      const param = params.set("create", event.create + "")
         .set("runtimeId", this.runtimeId);

      this.modelService.putModel<string>(CHECK_CALC_TRAP, calc, param).subscribe(
         (res0) => {
            let trap = res0.body;

            let promise = Promise.resolve(null);

            if(trap == "true") {
               promise = ComponentTool.showTrapAlert(this.dialogService).then((result: string) => {
                  if(result != "undo") {
                     this.socket.sendEvent(MODIFY_CALC_URI, event);
                  }
               });
            }
            else {
               promise = promise.then(() => {
                  this.socket.sendEvent(MODIFY_CALC_URI, event);
               });
            }
      });
   }

   private modifyAggregateField(tname: string, nref: AggregateRef, oref: AggregateRef,
                                calcDialog: FormulaEditorDialog, tableName: string): void {
      let name: string = this.assemblyName;
      let event: ModifyAggregateFieldEvent = new ModifyAggregateFieldEvent(name,
         tname, nref, oref, false);
      this.modelService.sendModel("../api/vs/calculate/modifyAggregateField?vsId=" +
         Tool.byteEncode(this.runtimeId), event)
         .subscribe(
            () => {
               this.getFormulaFields(null, tableName).subscribe((fieldsInfo) => {
                  calcDialog.aggregates = fieldsInfo.aggregateFields;
               });
            });
   }

   private deleteAggregateField(tname: string, nref: AggregateRef, oref: AggregateRef,
                                calcDialog: FormulaEditorDialog, tableName: string): void
   {
      let name: string = this.assemblyName;
      let event: ModifyAggregateFieldEvent = new ModifyAggregateFieldEvent(name,
         tname, nref, oref, false);
      this.modelService.sendModel("../api/vs/calculate/removeAggregateField?vsId=" +
         Tool.byteEncode(this.runtimeId), event)
         .subscribe(
            () => {
               this.getFormulaFields(null, tableName).subscribe((fieldsInfo) => {
                  calcDialog.aggregates = fieldsInfo.aggregateFields;
               });
            });
   }

   protected getRefNamesForConversion(convertType: number): string[] {
      let table: string = this.treeService.getTableName(this.currentEntry);
      let refNames: string[] = [];

      for(let node of this.selectedNodes) {
         let entry: AssetEntry = node.data;

         if(AssetEntryHelper.isColumn(entry) && this.treeService.getTableName(entry) === table &&
            ((convertType === this.CONVERT_TO_DIMENSION && this.isConvertToDimensionVisible(entry))
               || (convertType === this.CONVERT_TO_MEASURE && this.isConvertToMeasureVisible(entry))))
         {
            refNames.push(this.treeService.getColumnValue(entry));
         }
      }

      return refNames;
   }

   protected getGeoByName(refs: ChartRef[], name: string): ChartRef {
      if(!refs) {
         return null;
      }

      return refs.find(r => r.fullName === name || r.name === name || r.columnValue == name);
   }

   protected changeRef(ref: ChartRef, bindingInfo: any): void {
      let table: string = this.treeService.getTableName(this.currentEntry);
      let name: string = bindingInfo.assemblyName;
      let binding: ChartBindingModel = bindingInfo.bindingModel;
      let event: ChangeChartRefEvent = new ChangeChartRefEvent(name,
         ref.original, binding);

      this.sendEvent("vs/chart/changeChartRef", event, table);
   }

   protected editGeographic(bindingInfo: any): void {
      const chartGeoInfo: ChartGeoRef = this.getChartGeoInfo(this.currentEntry, bindingInfo);

      if(chartGeoInfo != null && chartGeoInfo.option == null) {
         let geoOptionInfo: GeographicOptionInfo = {
            layerValue: "",
            mapping: <FeatureMappingInfo> {}
         };

         chartGeoInfo.option = geoOptionInfo;
      }

      let dialog: EditGeographicDialog = ComponentTool.showDialog(this.dialogService,
         EditGeographicDialog, () => {
            this.changeRef(chartGeoInfo, bindingInfo);
         });

      let refName: string = chartGeoInfo.fullName || chartGeoInfo.name;

      let geoProvider: GeoProvider = new VSGeoProvider(
         bindingInfo.bindingModel, bindingInfo.urlParams,
         chartGeoInfo, this.modelService, refName);
      dialog.refName = refName;
      dialog.provider = geoProvider;
   }

   private getChartGeoInfo(entry: AssetEntry, bindingInfo: any): ChartGeoRef {
      if(!entry) {
         return null;
      }

      let refName: string = this.treeService.getColumnValue(entry);
      return <ChartGeoRef> this.findGeoColByName(refName, bindingInfo);
   }

   /**
    * Find the contained geo ref in geo column slection.
    * @param name the specified attribute's name.
    * @return the contained attribute equals to the attribute, <tt>null</tt>
    * not found.
    */
   protected findGeoColByName(name: string, bindingInfo: any): ChartRef {
      if(!bindingInfo || !bindingInfo.bindingModel) {
         return null;
      }

      let refs: ChartRef[] = bindingInfo.bindingModel.geoCols;
      return this.getGeoByName(refs, name);
   }

   protected changeGeographic(type: string, bindingInfo: any): void {
      let entry: AssetEntry = this.currentEntry;
      let name: string = bindingInfo.assemblyName;
      let refName: string = this.treeService.getColumnValue(entry);
      let ctype: number = parseInt(entry.properties[AssetEntryHelper.CUBE_COL_TYPE], 10);
      let isDim: boolean = (ctype & AssetEntryHelper.MEASURES) == 0; // eslint-disable-line no-bitwise
      let binding: ChartBindingModel = bindingInfo.bindingModel;
      let table: string = this.treeService.getTableName(entry);

      let event: ChangeGeographicEvent = new ChangeGeographicEvent(
         name, refName, isDim, type, binding, table, false);

      this.sendEvent("vs/chart/changeGeographic", event, table);
   }

   protected isGeographicVisible(edit: boolean, bindingInfo: any): boolean {
      if(!this.currentEntry || this.isEmbedded()) {
         return false;
      }

      let properties: any = this.currentEntry.properties;
      let refName: string = this.treeService.getColumnValue(this.currentEntry);
      let type: number = parseInt(properties[AssetEntryHelper.CUBE_COL_TYPE], 10);
      let rtype = properties["refType"];
      let refType: number = rtype ? parseInt(rtype, 10) : DataRefType.NONE;

      if(this.conditionalOnGeoVisible(refType, type)) {
         let isNotDate: boolean = (type & AssetEntryHelper.DATE_DIMENSIONS) == 0 && // eslint-disable-line no-bitwise
            refType != DataRefType.CUBE_MODEL_TIME_DIMENSION &&
            refType != DataRefType.CUBE_TIME_DIMENSION;

         if(isNotDate) {
            let isGeoCol: boolean = this.findGeoColByName(refName, bindingInfo) != null;
            let tname: string = properties["assembly"];
            let sinfo: SourceInfo =
               !!bindingInfo.bindingModel ? bindingInfo.bindingModel.source : null;

            if(!edit && (!isGeoCol || sinfo == null || tname != sinfo.source)) {
               return true;
            }

            if(edit && isGeoCol && sinfo != null && tname == sinfo.source) {
               return true;
            }
         }
      }

      return false;
   }

   protected conditionalOnGeoVisible(refType: number, colType: number): boolean {
      return !(this.currentEntry == null || !AssetEntryHelper.isColumn(this.currentEntry) ||
         this.currentEntry.type == AssetType.PHYSICAL_COLUMN ||
         (refType & DataRefType.DIMENSION) != 0 &&  (refType & DataRefType.MEASURE) != 0);
   }

   protected sendEvent(url: string, event: any, table: string): void {
      let binding: any = event.binding ? event.binding : event.model;

      if(!binding.source || !binding.source.source) {
         const sourceType: any = !!this.currentEntry.properties["type"] ?
            this.currentEntry.properties["type"] : SourceInfoType.ASSET;

         binding.source = {
            source: table,
            prefix: "",
            type: sourceType,
            dataSourceType: "",
            supportFullOutJoin: false,
            joinSources: new Array<SourceInfo>()
         };
      }

      this.socket.sendEvent("/events/" + url, event);
   }
}
