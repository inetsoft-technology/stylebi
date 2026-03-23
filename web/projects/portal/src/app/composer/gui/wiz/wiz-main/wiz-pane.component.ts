import { Component, Input, OnDestroy } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { ComponentTool } from "../../../../common/util/component-tool";
import { FontService } from "../../../../widget/services/font.service";
import { WizDashboard } from "../../../data/vs/wizDashboard";
import {
   NewVisualizationDialog,
   NewVisualizationDialogModel
} from "../new-visualization-dialog/new-visualization-dialog.component";
import { WizService } from "../services/wiz.service";

let wizDashboardCounter = 1;

@Component({
   selector: "wiz-pane",
   templateUrl: "./wiz-pane.component.html",
   styleUrl: "./wiz-pane.component.scss"
})
export class WizPane implements OnDestroy {
   @Input() active: boolean;
   private _currentDashboard: WizDashboard;
   private subscriptions = new Subscription();

   @Input()
   set currentDashboard(value: WizDashboard) {
      this._currentDashboard = value;
   }

   get currentDashboard(): WizDashboard {
      return this._currentDashboard;
   }

   constructor(private wizService: WizService, private fontService: FontService,
               private modalService: NgbModal)
   {
      this.subscriptions.add(wizService.openVisualization.subscribe(({value, standaloneVisualization}) => {
         if(this.active) {
            this.createVisualization(value, standaloneVisualization);
         }
      }));
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }

   createVisualization(value: string, standaloneVisualization?: boolean) {
      if(!this.currentDashboard) {
         return;
      }

      const vs = new WizDashboard(this.fontService);
      vs.label = "";

      if(value) {
         // open existing visualization
         vs.wizSheetRuntimeId = this.currentDashboard.runtimeId;
         vs.id = value;
         vs.newSheet = false;
         vs.localId = wizDashboardCounter++;
         vs.standaloneVisualization = standaloneVisualization;
         this.wizService.onShowVisualization(vs);
      }
      else {
         // create new visualization — runtimeId assigned in callback after server responds
         ComponentTool.showDialog(this.modalService, NewVisualizationDialog,
            (model: NewVisualizationDialogModel) => {
               vs.wizSheetRuntimeId = this.currentDashboard?.runtimeId;
               vs.baseEntries = model?.baseEntries;
               vs.id = "";
               vs.localId = wizDashboardCounter++;
               vs.newSheet = true;
               vs.visualization = true;
               vs.standaloneVisualization = standaloneVisualization;
               vs.visualizationSheet = this.currentDashboard?.id;
               this.wizService.onShowVisualization(vs);
            }, {});
      }
   }
}
