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

interface WorksheetRootTableAssemblyInfo {
  name: string;
  kind: string;
  fields?: WorksheetRootTableAssemblyFieldInfo[];
  sourceInfo?: {
    source?: string;
    prefix?: string;
    dataSourceType?: string;
  };
}

interface WorksheetRootTableAssemblyFieldInfo {
  name: string;
  type: string;
}

interface WorksheetRootTableAssembliesSummary {
  id: string;
  path: string;
  scope: number;
  primaryTableAssembly?: WorksheetRootTableAssemblyInfo;
  transformationSummary?: string;
  usedBy: WorksheetDependentAssetInfo[];
  rootTableAssemblies: WorksheetRootTableAssemblyInfo[];
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
  primaryTableAssembly: WorksheetRootTableAssemblyInfo | null = null;
  transformationSummary = "";
  usedBy: WorksheetDependentAssetInfo[] = [];
  rootTableAssemblies: WorksheetRootTableAssemblyInfo[] = [];
  loadingRootTableAssemblies = false;

  constructor(private httpClient: HttpClient) {
  }

  ngOnChanges(changes: SimpleChanges): void {
    if(changes["selectedFile"] || changes["isWorksheet"]) {
      this.loadRootTableAssemblies();
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

  getRootTableAssemblyLabel(tableAssembly: WorksheetRootTableAssemblyInfo): string {
    if(tableAssembly.kind === "connected") {
      const prefix = tableAssembly.sourceInfo?.prefix;
      const source = tableAssembly.sourceInfo?.source;

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

    return tableAssembly.name;
  }

  getRootTableAssemblyMeta(tableAssembly: WorksheetRootTableAssemblyInfo): string {
    if(tableAssembly.kind === "embedded") {
      return "_#(Embedded Data)";
    }

    return tableAssembly.sourceInfo?.dataSourceType || "_#(Connected Data Source)";
  }

  getRootTableAssemblyFieldType(field: WorksheetRootTableAssemblyFieldInfo): string {
    return field?.type || "unknown";
  }

  getDependentAssetTypeLabel(asset: WorksheetDependentAssetInfo): string {
    return asset?.type === "dashboard" ? "_#(Dashboard)" : "_#(Viewsheet)";
  }

  private loadRootTableAssemblies(): void {
    this.primaryTableAssembly = null;
    this.transformationSummary = "";
    this.usedBy = [];
    this.rootTableAssemblies = [];

    if(!this.isWorksheet || !this.selectedFile?.path || this.selectedFile?.scope == null) {
      return;
    }

    this.loadingRootTableAssemblies = true;
    const params = new HttpParams()
      .set("path", this.selectedFile.path)
      .set("scope", `${this.selectedFile.scope}`);

    this.httpClient.get<WorksheetRootTableAssembliesSummary>(
      "../api/portal/data/worksheet/root-table-assemblies", {params})
      .subscribe({
        next: (summary) => {
          this.primaryTableAssembly = summary?.primaryTableAssembly || null;
          this.transformationSummary = summary?.transformationSummary || "";
          this.usedBy = summary?.usedBy || [];
          this.rootTableAssemblies = summary?.rootTableAssemblies || [];
          this.loadingRootTableAssemblies = false;
        },
        error: () => {
          this.loadingRootTableAssemblies = false;
        }
      });
  }
}
