import { Injectable } from "@angular/core";
import { Observable, Subject } from "rxjs";
import { WizDashboard } from "../../../data/vs/wizDashboard";

@Injectable({
   providedIn: "root"
})
export class WizService {
   showingWiz: boolean = false;
   private _openVisualization = new Subject<string>();
   private _showVisualization = new Subject<WizDashboard>();
   private _saveVisualization = new Subject<WizDashboard>();
   private _exitVisualization = new Subject<void>();

   constructor() {
   }

   get openVisualization(): Observable<string>  {
      return this._openVisualization.asObservable();
   }

   onOpenVisualization(value?: string) {
      return this._openVisualization.next(value);
   }

   get showVisualization(): Observable<WizDashboard> {
      return this._showVisualization.asObservable();
   }

   onShowVisualization(vs: WizDashboard): void {
      this._showVisualization.next(vs);
   }

   get saveVisualization(): Observable<WizDashboard> {
      return this._saveVisualization.asObservable();
   }

   onSaveVisualization(vs: WizDashboard): void {
      this._saveVisualization.next(vs);
   }

   get exitVisualization(): Observable<void> {
      return this._exitVisualization.asObservable();
   }

   onExitVisualization(): void {
      this._exitVisualization.next();
   }
}
