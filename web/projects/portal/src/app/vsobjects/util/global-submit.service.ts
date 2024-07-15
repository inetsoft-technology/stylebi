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
import { Injectable } from "@angular/core";
import { Observable, Subject } from "rxjs";
import { VSObjectModel } from "../model/vs-object-model";
import { VSTableModel } from "../model/vs-table-model";
import { SelectionStateModel } from "../objects/selection/selection-base-controller";

@Injectable()
export class GlobalSubmitService {
  private globalSubmitStates: Map<string, boolean> = new Map<string, boolean>();
  hasUnapplyData: boolean = false;
  submitAllSelections: Subject<string> = new Subject<string>();
  updateSelectionsValues: Subject<Map<string, SelectionStateModel[]>> =
     new Subject<Map<string, SelectionStateModel[]>>();

  public updateState(name: string, states: any[], setPending = true) {
    if(!states || states.length == 0) {
      this.globalSubmitStates.set(name, false);
    }
    else {
      this.globalSubmitStates.set(name, true);
    }

    this.hasUnapplyData = false;

     if(setPending) {
        let keys = this.globalSubmitStates.keys();

        for(let key of keys) {
           if(this.globalSubmitStates.get(key)) {
              this.hasUnapplyData = true;
              break;
           }
        }
     }
  }

  public globalSubmit(): Observable<string> {
    return this.submitAllSelections.asObservable();
  }

  public submitGlobal(eventSource: string) {
    this.submitAllSelections.next(eventSource);
  }

  public updateSelections(): Observable<Map<string, SelectionStateModel[]>> {
     return this.updateSelectionsValues.asObservable();
  }

  public emitUpdateSelections(changes: Map<string, SelectionStateModel[]>): void {
     return this.updateSelectionsValues.next(changes);
  }
}
