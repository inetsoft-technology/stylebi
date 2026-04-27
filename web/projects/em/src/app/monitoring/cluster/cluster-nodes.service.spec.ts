/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { ClusterNodesService } from "./cluster-nodes.service";

describe("ClusterNodesService", () => {
   let service: ClusterNodesService;
   let http: HttpTestingController;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [ClusterNodesService]
      });
      service = TestBed.inject(ClusterNodesService);
      http = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      http.verify();
   });

   // ── getClusterNodesModel ───────────────────────────────────────────────────

   it("getClusterNodesModel fetches from the correct endpoint", () => {
      service.getClusterNodesModel().subscribe();
      const req = http.expectOne("../api/em/cluster/get-cluster-nodes");
      expect(req.request.method).toBe("GET");
      req.flush({ clusterNodes: ["node1", "node2"] });
   });

   it("getClusterNodesModel returns the full model from the server", () => {
      let result: any;
      service.getClusterNodesModel().subscribe(m => { result = m; });
      http.expectOne("../api/em/cluster/get-cluster-nodes")
         .flush({ clusterNodes: ["node1", "node2"] });
      expect(result).toEqual({ clusterNodes: ["node1", "node2"] });
   });

   // ── getClusterNodes ────────────────────────────────────────────────────────

   it("getClusterNodes returns the clusterNodes array from the model", () => {
      let nodes: string[] | undefined;
      service.getClusterNodes().subscribe(n => { nodes = n; });
      http.expectOne("../api/em/cluster/get-cluster-nodes")
         .flush({ clusterNodes: ["node-a", "node-b", "node-c"] });
      expect(nodes).toEqual(["node-a", "node-b", "node-c"]);
   });

   it("getClusterNodes returns an empty array when the model is null", () => {
      let nodes: string[] | undefined;
      service.getClusterNodes().subscribe(n => { nodes = n; });
      http.expectOne("../api/em/cluster/get-cluster-nodes").flush(null);
      expect(nodes).toEqual([]);
   });

   it("getClusterNodes returns an empty array when clusterNodes is empty", () => {
      let nodes: string[] | undefined;
      service.getClusterNodes().subscribe(n => { nodes = n; });
      http.expectOne("../api/em/cluster/get-cluster-nodes")
         .flush({ clusterNodes: [] });
      expect(nodes).toEqual([]);
   });
});
