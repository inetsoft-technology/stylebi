/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { AfterViewInit, ElementRef, Input, OnDestroy, ViewChild, Directive } from "@angular/core";
import { SafeStyle } from "@angular/platform-browser";
import { fromEvent, Subscription } from "rxjs";
import { GuiTool } from "../../common/util/gui-tool";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { ChartAreaName } from "../model/chart-area-name";
import { ChartObject } from "../model/chart-object";
import { ChartTile } from "../model/chart-tile";
import { ChartRegion } from "../model/chart-region";
import { ChartSelection } from "../model/chart-selection";
import { ChartTool } from "../model/chart-tool";
import { ChartService } from "../services/chart.service";
import { NetTool } from "../../common/util/net-tool";
import { GraphTypes } from "../../common/graph-types";
import { ChartModel } from "../model/chart-model";

/**
 * Defines fields and certain reusuable methods among different types of
 * chart object areas.
 */
@Directive()
export abstract class ChartObjectAreaBase<T extends ChartObject>
   implements AfterViewInit, OnDestroy
{
   @Input() chartSelection: ChartSelection;
   @Input() maxMode: boolean;
   @Input() genTime: number;
   @Input() urlPrefix: string;
   @Input() objectIndex = 0;
   @Input() links: string[];
   @Input() isDataTip: boolean = false;
   @ViewChild("objectCanvas") _objectCanvas: ElementRef;
   public viewsheetScale = 1;
   private subscriptions = new Subscription();
   public mobile = GuiTool.isMobileDevice();
   protected _model: ChartModel;
   private _scrollTop = 0;
   private _scrollLeft = 0;

   @Input() public set scrollLeft(scroll: number) {
      this._scrollLeft = scroll;
   }

   public get scrollLeft(): number {
      return this._scrollLeft;
   }

   @Input() public set scrollTop(scroll: number) {
      this._scrollTop = scroll;
   }

   public get scrollTop(): number {
      return this._scrollTop;
   }

   @Input() public set model(model: ChartModel) {
      this._model = model;
      this.updateRegionTree();
   }

   public get model(): ChartModel {
      return this._model;
   }

   public get regionTree(): any {
      return this._regionTree;
   }

   public get objectCanvas(): ElementRef {
      return this._objectCanvas;
   }

   protected get canvasX(): number {
      return 0;
   }

   protected get canvasY(): number {
      return 0;
   }

   @Input()
   public set chartObject(value: T) {
      this._chartObject = value;

      this.updateRegionTree();
      this.updateChartObject();
      this.drawSelectedRegions();
   }

   public get chartObject(): T {
      return this._chartObject;
   }

   protected updateRegionTree() {
      if(this.model && this._chartObject && this._chartObject.regions &&
         this._chartObject.regions.length > 0)
      {
         this._regionTree = ChartTool.createRegionTree(this.model, this._chartObject.regions);
      }
   }

   constructor(protected chartService: ChartService,
               scaleService: ScaleService)
   {
      this.addSubscription(
         scaleService.getScale().subscribe((scale) => this.viewsheetScale = scale)
      );

      this.addSubscription(fromEvent(window, "resize")
         .subscribe((event) => this.drawSelectedRegions())

      );
   }

   ngAfterViewInit() {
      this.drawSelectedRegions();
   }

   private drawSelectedRegions() {
      if(this.objectCanvas) {
         let regions: ChartRegion[] = ChartTool.getSelectedRegions(
            this.chartSelection, this._chartObject.areaName);
         let chartObject: ChartObject = this._chartObject;

         regions = regions.reduce(function(final, region) {
            let index: number = region.index;
            let updatedRegion: ChartRegion = chartObject.regions[index];

            if(updatedRegion && updatedRegion.valIdx === region.valIdx) {
               final.push(updatedRegion);
            }

            return final;
         }, []);

         // draw the region after convas is updated in case size changed.
         setTimeout(() => ChartTool.drawRegions(this.getContext(), regions, this.canvasX,
                                                this.canvasY, this.viewsheetScale), 0);
      }
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
      this.subscriptions = null;
      this.cleanup();
   }

   // which-polygon data structure
   private _regionTree: any = null;
   private _chartObject: T;

   public getContext(): CanvasRenderingContext2D {
      if(this.objectCanvas) {
         return this.objectCanvas.nativeElement.getContext("2d");
      }

      return null;
   }

   public getAreaName(): ChartAreaName {
      return this.chartObject.areaName;
   }

   public clearCanvas(): void {
      if(!!this.getContext()) {
         this.chartService.clearCanvas(this.getContext());
      }
   }

   public getSrc(tile: ChartTile, container: any = null): SafeStyle {
      // optimization, don't load chart if not necessary
      if(!this.urlPrefix || (<any> this.model).sheetMaxMode && !this.model.maxMode &&
         !this.isDataTip)
      {
         return "";
      }

      const vis = this.isTileVisible(tile);

      // for plot area outside of the visible area, don't show for better performance.
      if(!vis) {
         return "";
      }

      const width: number = tile.bounds.width;
      const height: number = tile.bounds.height;
      const row: number = tile.row;
      const col: number = tile.col;
      let maxWidth = 0;
      let maxHeight = 0;

      if(this.maxMode) {
         let viewSize = GuiTool.getChartMaxModeSize(container);
         maxWidth = viewSize.width;
         maxHeight = viewSize.height;
      }

      return this.urlPrefix +
         "/" + width +
         "/" + height +
         "/" + Math.ceil(maxWidth) +
         "/" + Math.ceil(maxHeight) +
         "/" + this.getAreaName() +
         "/" + this.objectIndex +
         "/" + row +
         "/" + col +
         "/" + this.genTime +
         "/" + true +
         "?" + NetTool.xsrfToken();
   }

   isTileVisible(tile: ChartTile): boolean {
      let scrollLeft = this.scrollLeft;
      let scrollTop = this.scrollTop;

      if(isNaN(scrollLeft)) {
         scrollLeft = 0;
      }

      if(isNaN(scrollTop)) {
         scrollTop = 0;
      }

      const margin = 50;
      const left1 = scrollLeft - margin;
      const top1 = scrollTop - margin;
      const right1 = scrollLeft + this.chartObject.layoutBounds.width + margin;
      const bottom1 = scrollTop + this.chartObject.layoutBounds.height + margin;
      const left2 = tile.bounds.x;
      const top2 = tile.bounds.y;
      const right2 = tile.bounds.x + tile.bounds.width;
      const bottom2 = tile.bounds.y + tile.bounds.height;

      if(right1 < left2 || left1 > right2) {
         return false;
      }

      if(bottom1 < top2 || top1 > bottom2) {
         return false;
      }

      return true;
   }

   protected getTreeRegions(x1: number, y1: number,
                            x2: number = x1, y2: number = y1): ChartRegion[]
   {
      let regions: ChartRegion[] = ChartTool.getTreeRegions(this.regionTree, x1, y1, x2, y2)
         .map((regionIndex) => this.chartObject.regions[regionIndex]);

      if(this.model && regions.length > 1) {
         const xdiff = Math.abs(x1 - x2);
         const ydiff = Math.abs(y1 - y2);

         if(xdiff < 3 && ydiff < 3) {
            // for donut chart, the center label should have priority over the slices on
            // the outside when clicking on middle.
            if(GraphTypes.isPie(this.model.chartType)) {
               // priority: center label (rectangle type 8) > center circle (move-to, curve)
               // > slice (move-to, curve, move-to)
               // the number of segment happens to capture the priority perfectly
               regions = regions.sort((a, b) => a.segTypes[0].length - b.segTypes[0].length);
            }
            else if(this.model.chartType == GraphTypes.CHART_CIRCLE_PACKING) {
               // select the inner-most circle of nested circles.
               regions = regions.sort((a, b) => a.index - b.index);
               regions = [ regions[0] ];
            }
         }
      }

      return regions;
   }

   /**
    * Update chart object property. Implemented by sub class.
    */
   public abstract updateChartObject(): void;

   /**
    * Cleanup method called from ngOnDestroy
    */
   protected abstract cleanup(): void;

   /**
    * Add a subscription teardown logic to be called when the chart area is destroye
    */
   protected addSubscription(...subscriptions: Subscription[]): void {
      subscriptions.forEach((sub) => this.subscriptions.add(sub));
   }

   trackByFn(index, item) {
      return index;
   }
}
