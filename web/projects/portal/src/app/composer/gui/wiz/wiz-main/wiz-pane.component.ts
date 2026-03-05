import { Component } from "@angular/core";
import { WizDashboard } from "../../../data/vs/wizDashboard";
import { WizService } from "../services/wiz.service";

@Component({
   selector: "wiz-pane",
   templateUrl: "./wiz-pane.component.html",
   styleUrl: "./wiz-pane.component.scss"
})
export class WizPane {
   private _currentVisualization: any;
   private _currentDashboard: WizDashboard = new WizDashboard();

   constructor(private wizService: WizService) {
      wizService.openVisualization.subscribe((value: string) => {
         this.createVisualization(value);
      });
   }

   createVisualization(value: string) {
      if(value) {
         this._currentVisualization = {};
         //Todo open visualization
      }
      else {
         this._currentVisualization = {};
         //Todo new visualization
      }
   }

   get currentVisualization(): any {
      return this._currentVisualization;
   }

   get currentDashboard(): WizDashboard {
      return this._currentDashboard;
   }
}
