import { Component, Input, OnDestroy, OnInit } from "@angular/core";
import { WizDashboard } from "../../../data/vs/wizDashboard";
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

   constructor(private wizService: WizService, private fontService: FontService) {
      wizService.openVisualization.subscribe((value: string) => {
         this.createVisualization(value);
      });
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
         vs.id = "";
         vs.newSheet = true;
      }

      vs.visualization = true;
      vs.visualizationSheet = this.currentDashboard?.id;
      this._currentVisualization = vs;
   }
}
