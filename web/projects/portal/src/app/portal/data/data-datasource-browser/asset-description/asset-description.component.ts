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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from "@angular/core";
import { AssetItem } from "../../model/datasources/database/asset-item";
import { LogicalModelBrowserInfo } from "../../model/datasources/database/physical-model/logical-model/logical-model-browser-info";

interface WorksheetRootBlockInfo {
  name: string;
  kind: string;
  fields?: WorksheetRootBlockFieldInfo[];
  sourceInfo?: {
    source?: string;
    prefix?: string;
    dataSourceType?: string;
  };
}

interface WorksheetRootBlockFieldInfo {
  name: string;
  type: string;
}

interface WorksheetRootBlocksSummary {
  id: string;
  path: string;
  scope: number;
  primaryBlock?: WorksheetRootBlockInfo;
  transformationSummary?: string;
  usedBy: WorksheetDependentAssetInfo[];
  rootBlocks: WorksheetRootBlockInfo[];
}

interface WorksheetDependentAssetInfo {
  name: string;
  path: string;
  type: string;
}

@Component({
  selector: "asset-description",
  templateUrl: "./asset-description.component.html",
  styleUrls: ["./asset-description.component.scss"]
})
export class AssetDescriptionComponent implements OnChanges {
  @Input() selectedFile: AssetItem;
  @Input() isWorksheet: boolean = false;
  @Output() onClose: EventEmitter<void> = new EventEmitter();
  primaryBlock: WorksheetRootBlockInfo | null = null;
  transformationSummary = "";
  usedBy: WorksheetDependentAssetInfo[] = [];
  rootBlocks: WorksheetRootBlockInfo[] = [];
  loadingRootBlocks = false;

  constructor(private httpClient: HttpClient) {
  }

  ngOnChanges(changes: SimpleChanges): void {
    if(changes["selectedFile"] || changes["isWorksheet"]) {
      this.loadRootBlocks();
    }
  }

  getBasedView(): string {
    if(this.selectedFile?.type === "logical_model") {
      return (<LogicalModelBrowserInfo> this.selectedFile).physicalModel;
    }

    return "";
  }

  close(): void {
    this.onClose.emit();
  }

  getRootBlockLabel(block: WorksheetRootBlockInfo): string {
    if(block.kind === "connected") {
      const prefix = block.sourceInfo?.prefix;
      const source = block.sourceInfo?.source;

      if(prefix && source) {
        return `${source} (${prefix})`;
      }
      else if(source) {
        return source;
      }
      else if(prefix) {
        return prefix;
      }
    }

    return block.name;
  }

  getRootBlockMeta(block: WorksheetRootBlockInfo): string {
    if(block.kind === "embedded") {
      return "_#(Embedded Data)";
    }

    return block.sourceInfo?.dataSourceType || "_#(Connected Data Source)";
  }

  getRootBlockFieldType(field: WorksheetRootBlockFieldInfo): string {
    return field?.type || "unknown";
  }

  getDependentAssetTypeLabel(asset: WorksheetDependentAssetInfo): string {
    return asset?.type === "dashboard" ? "_#(Dashboard)" : "_#(Viewsheet)";
  }

  private loadRootBlocks(): void {
    this.primaryBlock = null;
    this.transformationSummary = "";
    this.usedBy = [];
    this.rootBlocks = [];

    if(!this.isWorksheet || !this.selectedFile?.path || this.selectedFile?.scope == null) {
      return;
    }

    this.loadingRootBlocks = true;
    const params = new HttpParams()
      .set("path", this.selectedFile.path)
      .set("scope", `${this.selectedFile.scope}`);

    this.httpClient.get<WorksheetRootBlocksSummary>("../api/portal/data/worksheet/root-blocks", {params})
      .subscribe({
        next: (summary) => {
          this.primaryBlock = summary?.primaryBlock || null;
          this.transformationSummary = summary?.transformationSummary || "";
          this.usedBy = summary?.usedBy || [];
          this.rootBlocks = summary?.rootBlocks || [];
          this.loadingRootBlocks = false;
        },
        error: () => {
          this.loadingRootBlocks = false;
        }
      });
  }
}
