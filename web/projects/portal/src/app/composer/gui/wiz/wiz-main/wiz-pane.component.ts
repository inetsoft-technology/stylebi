import { Component, Input, OnDestroy, OnInit } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../../common/util/component-tool";
import { WizDashboard } from "../../../data/vs/wizDashboard";
import {
   NewVisualizationDialog,
   NewVisualizationDialogModel
} from "../new-visualization-dialog/new-visualization-dialog.component";
import { WizService } from "../services/wiz.service";
import { FontService } from "../../../../widget/services/font.service";
import { Subscription } from "rxjs";

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
         })
      );
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }

   createVisualization(value: string) {
      if(!this.currentDashboard) {
         return;
      }

      const vs = new WizDashboard(this.fontService);
      vs.localId = wizDashboardCounter++;
      vs.label = "";

      if(value) {
         // open existing visualization
         vs.id = value;
         vs.newSheet = false;
      }
      else {
         // create new visualization
         ComponentTool.showDialog(this.modalService, NewVisualizationDialog,
            (model: NewVisualizationDialogModel) => {
               vs.baseEntries = model?.baseEntries;
               vs.id = "";
               vs.newSheet = true;
               vs.visualization = true;
               vs.visualizationSheet = this.currentDashboard?.id;
               this._currentVisualization = vs;
            }, {});
      }
   }
}
