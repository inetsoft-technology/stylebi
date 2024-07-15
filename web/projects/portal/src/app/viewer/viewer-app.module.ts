/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { NgModule } from "@angular/core";
import { DownloadModule } from "../../../../shared/download/download.module";
import { UIContextService } from "../common/services/ui-context.service";
import { HideNavService } from "../portal/services/hide-nav.service";
import { VSTrapService } from "../vsobjects/util/vs-trap.service";
import { ViewerAppRoutingModule } from "./app-routing.module";
import { ViewerRootComponent } from "./viewer-root.component";
import { ViewerViewModule } from "./viewer-view/viewer-view.module";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { CodemirrorService } from "../../../../shared/util/codemirror/codemirror.service";
import {
   DefaultCodemirrorService
} from "../../../../shared/util/codemirror/default-codemirror.service";
import { WidgetParameterModule } from "../widget/parameter/widget-parameter.module";

@NgModule({
   imports: [
      DownloadModule,
      ViewerViewModule,
      ViewerAppRoutingModule,
      WidgetParameterModule
   ],
   providers: [
      VSTrapService,
      UIContextService,
      HideNavService,
      NgbModal,
      {
         provide: CodemirrorService,
         useClass: DefaultCodemirrorService
      },
   ],
   declarations: [ ViewerRootComponent ],
   bootstrap: [ ViewerRootComponent ]
})
export class ViewerAppModule {
}
