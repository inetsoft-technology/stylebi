import { Injectable } from "@angular/core";
import { Observable, Subject } from "rxjs";

@Injectable({
   providedIn: "root"
})
export class WizService {
   showingWiz: boolean = false;
   private _openVisualization = new Subject<string>();

   constructor() {
   }

   get openVisualization(): Observable<string>  {
      return this._openVisualization.asObservable();
   }

   onOpenVisualization(value?: string) {
      return this._openVisualization.next(value);
   }
}
