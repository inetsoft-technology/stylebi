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

/**
 * ImagePreviewPane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — imageSrc fallback; selectImage current vs leaf; clearSelected
 *   Group 2 [Risk 2] — initCurrentNode dynamic image; getAlpha bounds
 *   Group 3 [Risk 3] — deleteUpload confirm + HTTP delete + tree mutation
 *
 * Direct instantiation — tree/upload DOM stubbed.
 */

import { HttpClient } from "@angular/common/http";
import { ChangeDetectorRef } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of } from "rxjs";
import { ComponentTool } from "../../common/util/component-tool";
import { TreeNodeModel } from "../tree/tree-node-model";
import { TreeComponent } from "../tree/tree.component";
import { ImagePreviewPaneModel } from "./image-preview-pane-model";
import { ImagePreviewPane } from "./image-preview-pane.component";

function createModel(): ImagePreviewPaneModel {
   return {
      selectedImage: null,
      animateGifImage: false,
      scaleImage: false,
      alpha: 100,
      allowNullImage: true,
      imageTree: { children: [] },
      presenter: false,
   } as ImagePreviewPaneModel;
}

function createPane(model = createModel()) {
   const http = {
      post: vi.fn(),
      delete: vi.fn(() => of(true)),
   };
   const comp = new ImagePreviewPane(
      http as unknown as HttpClient,
      { detectChanges: vi.fn() } as unknown as ChangeDetectorRef,
      {} as NgbModal,
   );
   comp.model = model;
   comp.runtimeId = "runtime-1";
   comp.tree = {
      getNodeByData: vi.fn(),
      selectAndExpandToNode: vi.fn(),
      expandToNode: vi.fn(),
      root: { children: [] },
   } as unknown as TreeComponent;
   return { comp, http };
}

describe("ImagePreviewPane — selection and preview URL [Group 1, Risk 3]", () => {

   it("should return emptyimage.gif when no image node is selected", () => {
      const { comp } = createPane();
      comp.selectedImageNode = {} as TreeNodeModel;

      expect(comp.imageSrc).toBe("assets/emptyimage.gif");
   });

   it("should build preview URL from selected node data and runtime id", () => {
      const { comp } = createPane();
      comp.selectedImageNode = {
         data: "logo.png",
         type: "^UPLOADED^",
      } as TreeNodeModel;
      comp.currentTime = 123;

      expect(comp.imageSrc).toContain("logo~_2e_~png");
      expect(comp.imageSrc).toContain("^UPLOADED^");
      expect(comp.imageSrc).toContain("runtime-1");
      expect(comp.imageSrc).toContain("123");
   });

   it("should build preview URL for dataspace server images with ^SERVER^ type", () => {
      const { comp } = createPane();
      comp.selectedImageNode = {
         data: "icons/chart.png",
         type: "^SERVER^",
      } as TreeNodeModel;
      comp.currentTime = 456;

      expect(comp.imageSrc).toContain("icons~_2f_~chart~_2e_~png");
      expect(comp.imageSrc).toContain("^SERVER^");
      expect(comp.imageSrc).not.toContain("^UPLOADED^");
   });

   it("should persist dataspace selection as ^SERVER^-prefixed selectedImage", () => {
      const { comp } = createPane();
      const serverLeaf = {
         leaf: true,
         type: "^SERVER^",
         data: "folder/logo.png",
         label: "logo.png",
      } as TreeNodeModel;

      comp.selectImage(serverLeaf);

      expect(comp.selectedImageNode).toBe(serverLeaf);
      expect(comp.model.selectedImage).toBe("^SERVER^folder/logo.png");
   });

   it("should ignore non-leaf tree nodes in selectImage", () => {
      const { comp } = createPane();
      comp.model.selectedImage = "^SERVER^old.png";
      comp["selectedImageNodeForTree"] = { data: "old" } as TreeNodeModel;

      comp.selectImage({ leaf: false, data: "Images", type: "^SERVER^" } as TreeNodeModel);

      expect(comp.model.selectedImage).toBe("^SERVER^old.png");
   });

   it("should set selectedImage to null when leaf node has no data", () => {
      const { comp } = createPane();
      comp.model.selectedImage = "^UPLOADED^x.png";

      comp.selectImage({ leaf: true, type: "^UPLOADED^", data: null } as TreeNodeModel);

      expect(comp.model.selectedImage).toBeNull();
   });

   it("should keep currentNode when selecting the current-image tree entry", () => {
      const { comp } = createPane();
      comp.currentNode = { data: "cur.png", type: "^UPLOADED^", leaf: true } as TreeNodeModel;

      comp.selectImage({ leaf: true, type: "current", data: "_CURRENT_IMAGE_" } as TreeNodeModel);

      expect(comp.selectedImageNode).toBe(comp.currentNode);
      expect(comp.model.selectedImage).toBe("^UPLOADED^cur.png");
   });

   it("should clear selected image and animateGif flag on clearSelected", () => {
      const { comp } = createPane();
      comp.model.selectedImage = "^UPLOADED^x.png";
      comp.model.animateGifImage = true;

      comp.clearSelected();

      expect(comp.model.selectedImage).toBeNull();
      expect(comp.model.animateGifImage).toBe(false);
      expect(comp.selectedImageNode).toEqual({});
   });
});

describe("ImagePreviewPane — init and alpha [Group 2, Risk 2]", () => {

   it("should label dynamic image nodes when tree lookup misses", () => {
      const { comp } = createPane();
      comp.model.selectedImage = "$variables['img']";
      vi.mocked(comp.tree.getNodeByData).mockReturnValue(null);

      comp.initCurrentNode();

      expect(comp.currentNode.label).toBe("Dynamic Image");
      expect(comp.currentNode.data).toBe("$variables['img']");
   });

   it("should resolve dataspace image from tree when selectedImage uses ^SERVER^ prefix", () => {
      const { comp } = createPane();
      const serverNode = {
         data: "icons/chart.png",
         type: "^SERVER^",
         label: "chart.png",
         leaf: true,
      } as TreeNodeModel;
      comp.model.selectedImage = "^SERVER^icons/chart.png";
      vi.mocked(comp.tree.getNodeByData)
         .mockImplementation((field, value) => {
            if(field === "data" && value === "icons/chart.png") {
               return serverNode;
            }
            if(field === "data" && value === "_CURRENT_IMAGE_") {
               return null;
            }
            return null;
         });

      comp.initCurrentNode();

      expect(comp.currentNode).toBe(serverNode);
      expect(comp.selectedImageNode).toBe(serverNode);
   });

   it("should label formula dynamic images starting with =", () => {
      const { comp } = createPane();
      comp.model.selectedImage = "=field['image']";
      vi.mocked(comp.tree.getNodeByData).mockReturnValue(null);

      comp.initCurrentNode();

      expect(comp.currentNode.label).toBe("Dynamic Image");
      expect(comp.currentNode.data).toBe("=field['image']");
   });

   it("should clamp getAlpha between 0 and 1", () => {
      const { comp } = createPane();
      comp.model.alpha = 150;
      expect(comp.getAlpha()).toBe(1);

      comp.model.alpha = -10;
      expect(comp.getAlpha()).toBe(0);

      comp.model.alpha = 50;
      expect(comp.getAlpha()).toBe(0.5);
   });
});

describe("ImagePreviewPane — deleteUpload [Group 3, Risk 3]", () => {

   it("should remove uploaded node from tree after confirmed delete", async () => {
      const uploadedLeaf = {
         data: "test.png",
         label: "test.png",
         leaf: true,
         type: "^UPLOADED^",
      } as TreeNodeModel;
      const model = createModel();
      model.imageTree = {
         children: [{
            data: "Uploaded",
            children: [uploadedLeaf],
         }],
      } as TreeNodeModel;
      const { comp, http } = createPane(model);
      comp.selectedImageNode = uploadedLeaf;
      comp.currentNode = uploadedLeaf;
      const currentImageNode = { data: "_CURRENT_IMAGE_", leaf: true, type: "current" } as TreeNodeModel;
      vi.mocked(comp.tree.getNodeByData).mockReturnValue(currentImageNode);
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("yes");

      comp.deleteUpload();
      await Promise.resolve();
      await Promise.resolve();

      expect(http.delete).toHaveBeenCalled();
      expect(model.imageTree.children[0].children).toHaveLength(0);
      expect(comp.selectedImageNode).toEqual({});
      expect(comp.selectedNodes).toEqual([currentImageNode]);
   });

   it("should not call delete when user cancels confirm dialog", async () => {
      const { comp, http } = createPane();
      comp.selectedImageNode = { data: "test.png", type: "^UPLOADED^" } as TreeNodeModel;
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("no");

      comp.deleteUpload();
      await Promise.resolve();

      expect(http.delete).not.toHaveBeenCalled();
   });
});
