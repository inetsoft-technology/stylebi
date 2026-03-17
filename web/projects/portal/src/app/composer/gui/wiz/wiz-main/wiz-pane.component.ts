import { Component, Input, OnDestroy, OnInit } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { ComponentTool } from "../../../../common/util/component-tool";
import { WizDashboard } from "../../../data/vs/wizDashboard";
import {
   NewVisualizationDialog,
   NewVisualizationDialogModel
} from "../new-visualization-dialog/new-visualization-dialog.component";
import { WizService } from "../services/wiz.service";
import { FontService } from "../../../../widget/services/font.service";
import { TouchAssetEvent } from "../../ws/socket/touch-asset-event";

let wizDashboardCounter = 1;

@Component({
   selector: "wiz-pane",
   templateUrl: "./wiz-pane.component.html",
   styleUrl: "./wiz-pane.component.scss"
})
export class WizPane implements OnInit, OnDestroy {
   private _currentVisualization: WizDashboard;
   private _currentDashboard: WizDashboard;
   private subscriptions = new Subscription();
   private heartbeatSubscription: Subscription = Subscription.EMPTY;

   @Input()
   set currentDashboard(value: WizDashboard) {
      this._currentDashboard = value;
   }

   get currentDashboard(): WizDashboard {
      return this._currentDashboard;
   }

   get currentVisualization(): WizDashboard {
      return this._currentVisualization;
   }

   constructor(private wizService: WizService, private fontService: FontService, private modalService: NgbModal) {
      this.subscriptions.add(wizService.openVisualization.subscribe((value: string) => {
         this.createVisualization(value);
      }));
   }

   ngOnInit(): void {
      this.subscriptions.add(
         this.wizService.exitVisualization.subscribe(() => {
            this._currentVisualization = null;
            this.heartbeatSubscription.unsubscribe();
            this.heartbeatSubscription = Subscription.EMPTY;
         })
      );
   }

   ngOnDestroy(): void {
      this.heartbeatSubscription.unsubscribe();
      this.subscriptions.unsubscribe();
   }

   createVisualization(value: string) {
      if(!this.currentDashboard) {
         return;
      }

      if(this.currentDashboard.socketConnection) {
         this.heartbeatSubscription.unsubscribe();
         this.heartbeatSubscription = this.currentDashboard.socketConnection.onHeartbeat.subscribe(() => {
            this.touchAsset();
         });
      }

      const vs = new WizDashboard(this.fontService);
      vs.label = "";
      vs.wizSheetRuntimeId = this.currentDashboard.runtimeId;

      if(value) {
         // open existing visualization
         vs.id = value;
         vs.newSheet = false;
         vs.localId = wizDashboardCounter++;
         this._currentVisualization = vs;
      }
      else {
         // create new visualization
         ComponentTool.showDialog(this.modalService, NewVisualizationDialog,
            (model: NewVisualizationDialogModel) => {
               vs.baseEntries = model?.baseEntries;
               vs.id = "";
               vs.localId = wizDashboardCounter++;
               vs.newSheet = true;
               vs.visualization = true;
               vs.visualizationSheet = this.currentDashboard?.id;
               this._currentVisualization = vs;
            }, {});
      }
   }

   private touchAsset(): void {
      if(this.currentDashboard?.runtimeId && this.currentDashboard?.socketConnection) {
         const event = new TouchAssetEvent();
         event.setDesign(true);
         event.setChanged(false);
         event.setUpdate(false);
         this.currentDashboard.socketConnection.sendEvent("/events/composer/touch-asset", event);
      }
   }
}
