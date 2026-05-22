import { Injectable } from "@angular/core";
import { Observable, Subject } from "rxjs";

@Injectable({
   providedIn: "root"
})
export class WizService {
   wizComposer: boolean = false;
   wizVizIds: string[] = [];
   private _refreshFilters = new Subject<void>();
   private _refreshTree = new Subject<void>();

   constructor() {
   }

   get refreshFilters(): Observable<void> {
      return this._refreshFilters.asObservable();
   }

   onRefreshFilters(): void {
      this._refreshFilters.next();
   }

   get refreshTree(): Observable<void> {
      return this._refreshTree.asObservable();
   }

   onRefreshTree(): void {
      this._refreshTree.next();
   }
}
