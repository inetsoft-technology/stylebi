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
import { Dimension } from "../../common/data/dimension";
import { GuiTool } from "../../common/util/gui-tool";
import { ViewsheetEvent } from "../../common/viewsheet-client/index";
import { VSChartModel } from "../model/vs-chart-model";
import { SortInfo } from "../objects/table/sort-info";
import { FormatInfoModel } from "../../common/data/format-info-model";
import { DetailDndInfo } from "../objects/table/detail-dnd-info";

/**
 * Event for common parameters for chart user actions.
 */
export class VSChartEvent implements ViewsheetEvent {
   private maxSize: Dimension;
   private chartName: string;
   /**
    * Creates a new instance of <tt>VSChartEvent</tt>.
    */
   constructor(model: VSChartModel, maxMode: boolean = model.maxMode, container: any = null,
               maxSize: Dimension = null, private viewportWidth: number = 0,
               private viewportHeight: number = 0)
   {
      this.chartName = model.absoluteName;

      if(maxMode) {
         if(maxSize) {
            this.maxSize = maxSize;
         }
         else {
            this.maxSize = GuiTool.getChartMaxModeSize(container);
         }

         //subtract the height of the status bar
         this.maxSize.height -= 38;
      }
   }
}

/**
 * Event which sends the parameters for resizing a chart axis.
 */
export class VSChartAxisResizeEvent extends VSChartEvent {
   constructor(model: VSChartModel, private axisType: string,
               private axisField: string, private axisSize: number)
   {
      super(model);
   }
}

/**
 * Event which sends the parameters for resizing a plot y boundary spacing.
 */
export class VSChartPlotResizeEvent extends VSChartEvent {
   constructor(model: VSChartModel,
               private reset: boolean,
               private sizeRatio?: number,
               private heightResized?: boolean)
   {
      super(model);
   }
}

/**
 * Event which sends the parameters for relocating a legend to another
 *  position (north, south, east, west, center).
 */
export class VSChartLegendRelocateEvent extends VSChartEvent {
   constructor(model: VSChartModel, private legendPosition: number,
               private legendX: number, private legendY: number, private legendType: string,
               private field: string, private targetFields: string[],
               private nodeAesthetic: boolean)
   {
      super(model);
   }
}

/**
 * Event which sends the parameters for resizing the legend(s).
 */
export class VSChartLegendResizeEvent extends VSChartEvent {
   constructor(model: VSChartModel, private legendWidth: number,
               private legendHeight: number, private legendType: string,
               private field: string, private targetFields: string[],
               private nodeAesthetic: boolean)
   {
      super(model, model.maxMode, null, new Dimension(model.objectFormat.width,
                                                      model.objectFormat.height));
   }
}

/**
 * Event which sends the parameters for sorting the axis.
 */
export class VSChartSortAxisEvent extends VSChartEvent {
   constructor(model: VSChartModel, private sortOp: string, private sortField: string) {
      super(model);
   }
}

/**
 * Event which sends the parameters for brushing.
 */
export class VSChartBrushEvent extends VSChartEvent {
   constructor(model: VSChartModel, private selected: string,
      private rangeSelection: boolean)
   {
      super(model);
   }
}

/**
 * Event which sends the parameters for zooming.
 */
export class VSChartZoomEvent extends VSChartEvent {
   constructor(model: VSChartModel, private selected: string,
      private rangeSelection: boolean, private exclude: boolean)
   {
      super(model);
   }
}


/**
 * Event which sends the parameters for map zoom.
 */
export class VSMapZoomEvent extends VSChartEvent {
   constructor(model: VSChartModel, private increment: number) {
      super(model);
   }
}

/**
 * Event which sends the parameters for map panning.
 */
export class VSMapPanEvent extends VSChartEvent {
   constructor(model: VSChartModel, private panX: number, private panY: number) {
      super(model);
   }
}

/**
 * Event which sends the parameters for "show data".
 */
export class VSChartShowDataEvent extends VSChartEvent {
   constructor(model: VSChartModel, private sortInfo: SortInfo,
               private format: FormatInfoModel, private columns: number[],
               private worksheetId: string, private detailStyle: string,
               private dndInfo: DetailDndInfo, private newColName: string,
               private toggleHide: boolean = false)
   {
      super(model);
   }
}

/**
 * Event which sends the parameters for "show details".
 */
export class VSChartShowDetailsEvent extends VSChartEvent {
   constructor(model: VSChartModel,
               private selected: string,
               private rangeSelection: boolean,
               private sortInfo: SortInfo,
               private format: FormatInfoModel,
               private columns: number[],
               private worksheetId: string,
               private detailStyle: string,
               private dndInfo: DetailDndInfo,
               private newColName: string,
               private toggleHide: boolean)
   {
      super(model);
   }
}

/**
 * Event which sends the parameters for "show details".
 */
export class VSChartDrillEvent extends VSChartEvent {
   constructor(model: VSChartModel, private axisType: string,
               private field: string, private drillUp: boolean,
               viewportWidth: number, viewportHeight: number)
   {
      super(model, model.maxMode, null, null, viewportWidth, viewportHeight);
   }
}

export class VSChartDrillActionEvent extends VSChartEvent {
   constructor(model: VSChartModel, private selected: string,
               private rangeSelection: boolean, private drillUp: boolean,
               viewportWidth: number, viewportHeight: number)
   {
      super(model, model.maxMode, null, null, viewportWidth, viewportHeight);
   }
}

export class VSChartFlyoverEvent extends VSChartEvent {
   constructor(model: VSChartModel, private conditions: string) {
      super(model);
   }
}

/**
 * Event which sends the parameters for handling title visibility.
 */
export class VSChartTitlesVisibilityEvent extends VSChartEvent {
   constructor(model: VSChartModel, private hide: boolean, private titleType: string) {
      super(model);
   }
}

/**
 * Event which sends the parameters for handling axes visibility.
 */
export class VSChartAxesVisibilityEvent extends VSChartEvent {
   constructor(model: VSChartModel, private hide: boolean,
               private columnName: string, private secondary: boolean)
   {
      super(model);
   }
}

/**
 * Event which sends the parameters for handling legends visibility.
 */
export class VSChartLegendsVisibilityEvent extends VSChartEvent {
   constructor(model: VSChartModel, private hide: boolean, private field: string,
               private targetFields: string[], private aestheticType: string,
               private wizard?: boolean, private colorMerged: boolean = true, private nodeAesthetic: boolean = false)
   {
      super(model);
   }
}

/**
 * Event which sends the parameters for handling on going query.
 */
export class CancelEvent extends VSChartEvent {
   constructor(model: VSChartModel)
   {
      super(model);
   }
}

/**
 * Event which sends the parameters for handling refresh chart.
 */
export class VSChartRefreshEvent extends VSChartEvent {
   constructor(model: VSChartModel)
   {
      super(model);
   }
}
