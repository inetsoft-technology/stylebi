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
import { createApplication } from "@angular/platform-browser";
import { createCustomElement } from "@angular/elements";
import { provideRouter } from "@angular/router";
import { EmbedChartComponent } from "./app/embed/chart/embed-chart.component";
import { embedElementConfig } from "./app/embed/embed-element.config";
import { embedChartRoutes } from "./app/embed/chart/embed-chart.routes";
import "./main-base-element";

createApplication({
   providers: [
      ...embedElementConfig.providers,
      provideRouter(embedChartRoutes)
   ]
}).then(app => {
   const embedChart = createCustomElement(EmbedChartComponent, {injector: app.injector});
   customElements.define("inetsoft-chart", embedChart);
}).catch(err => console.error(err));

/**
 * Check if inetsoft is connected on app load in case there is no need to log in such as when
 * security is disabled or there is an active session
 */
(window as any).checkInetsoftConnection(null, false);
