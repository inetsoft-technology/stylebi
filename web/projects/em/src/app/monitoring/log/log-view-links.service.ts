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
import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable, of } from "rxjs";
import { tap } from "rxjs/operators";
import { LogViewLinks } from "./log-view-links";

const LOG_LINKS_URL = "../api/em/monitoring/log/links";

/**
 * Fetches the log view links. The route guard fetches them to decide whether the page may open;
 * the page needs the same answer, so the guard's result is handed over once instead of the page
 * requesting the links a second time.
 */
@Injectable({
   providedIn: "root"
})
export class LogViewLinksService {
   private handedOver: LogViewLinks = null;

   constructor(private http: HttpClient) {
   }

   /** Requests the links from the server and keeps them for one {@link #getLinks} call. */
   fetchLinks(): Observable<LogViewLinks> {
      return this.http.get<LogViewLinks>(LOG_LINKS_URL).pipe(
         tap(links => this.handedOver = links));
   }

   /** Returns the links fetched by the guard (once), or requests them if there are none. */
   getLinks(): Observable<LogViewLinks> {
      const links = this.handedOver;
      this.handedOver = null;
      return links ? of(links) : this.http.get<LogViewLinks>(LOG_LINKS_URL);
   }
}
