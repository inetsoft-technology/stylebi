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
import { BehaviorSubject, Observable } from "rxjs";
import { AssetItem } from "../model/datasources/database/asset-item";

export interface WorksheetSelectionRequest {
   path: string;
   scope: string;
}

@Injectable()
export class DataDetailsPaneService {
   private readonly selectedFileSubject = new BehaviorSubject<AssetItem>(null);
   private readonly worksheetSelectionRequestSubject =
      new BehaviorSubject<WorksheetSelectionRequest>(null);

   get selectedFile$(): Observable<AssetItem> {
      return this.selectedFileSubject.asObservable();
   }

   setSelectedFile(selectedFile: AssetItem): void {
      this.selectedFileSubject.next(selectedFile);
   }

   get worksheetSelectionRequest$(): Observable<WorksheetSelectionRequest> {
      return this.worksheetSelectionRequestSubject.asObservable();
   }

   get worksheetSelectionRequest(): WorksheetSelectionRequest {
      return this.worksheetSelectionRequestSubject.value;
   }

   requestWorksheetSelection(request: WorksheetSelectionRequest): void {
      this.worksheetSelectionRequestSubject.next(request);
   }

   clearWorksheetSelectionRequest(): void {
      this.worksheetSelectionRequestSubject.next(null);
   }

   clear(): void {
      this.selectedFileSubject.next(null);
      this.worksheetSelectionRequestSubject.next(null);
   }
}
