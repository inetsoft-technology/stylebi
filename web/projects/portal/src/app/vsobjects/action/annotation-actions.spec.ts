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
import { TestUtils } from "../../common/test/test-utils";
import { ViewerContextProviderFactory } from "../context-provider.service";
import { AnnotationActions } from "./annotation-actions";
import { VSAnnotationModel } from "../model/annotation/vs-annotation-model";

describe("AnnotationActions", () => {
   const createModel = () => TestUtils.createMockVSAnnotationModel("Annotation1");

   it("check menu actions is created", () => {
      const expectedMenu = (srcModel: VSAnnotationModel) => [
         [
            { id: "annotation edit" + srcModel.absoluteName, visible: true },
            { id: "annotation format" + srcModel.absoluteName, visible: true },
            { id: "annotation remove" + srcModel.absoluteName, visible: true }
         ]
      ];

      //check status in viewer
      const model = createModel();
      const actions1 = new AnnotationActions(model, ViewerContextProviderFactory(false));
      const menuActions1 = actions1.menuActions;

      expect(menuActions1).toMatchSnapshot();

      //check status in preview
      const model2 = createModel();
      const actions2 = new AnnotationActions(model2, ViewerContextProviderFactory(true));
      const menuActions2 = actions2.menuActions;

      expect(menuActions2).toMatchSnapshot();
   });
});