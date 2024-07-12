/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.web.composer.vs.controller;

import inetsoft.report.Presenter;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Catalog;
import inetsoft.web.composer.model.TreeNodeModel;
import org.apache.commons.collections.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Controller that provides endpoints for composer presenter related actions.
 */
@Controller
public class ComposerPresenterController {
   /**
    * Gets the presenter tree.
    *
    * @return the root of the presenter tree.
    */
   @RequestMapping(
      value = "/composer/vs/presenter",
      method = RequestMethod.GET
   )
   @ResponseBody
   public TreeNodeModel getPresenterTree() throws Exception {
      Catalog catalog = Catalog.getCatalog();
      String[][] presenters = new String[][] {
         {"Presentation", "HTMLPresenter"},
         {"Presentation", "BooleanPresenter"},
         {"Presentation", "IconCounterPresenter"},
         {"Presentation", "Bar2Presenter"},
         {"Presentation", "BarPresenter"},
         {"Presentation", "BulletGraphPresenter"},
         {"Presentation", "TrafficLightPresenter"},
         {"Presentation", "ImagePresenter"},
         {"Barcode", "Barcode2of7Presenter"},
         {"Barcode", "Barcode3of9Presenter"},
         {"Barcode", "BarcodeCodabarPresenter"},
         {"Barcode", "BarcodeCode128APresenter"},
         {"Barcode", "BarcodeCode128BPresenter"},
         {"Barcode", "BarcodeCode128CPresenter"},
         {"Barcode", "BarcodeCode128Presenter"},
         {"Barcode", "BarcodeCode39Presenter"},
         {"Barcode", "BarcodeEAN128Presenter"},
         {"Barcode", "BarcodeGTINPresenter"},
         {"Barcode", "BarcodeMonarchPresenter"},
         {"Barcode", "BarcodeNW7Presenter"},
         {"Barcode", "BarcodePDF417Presenter"},
         {"Barcode", "BarcodeSCC14Presenter"},
         {"Barcode", "BarcodeSINPresenter"},
         {"Barcode", "BarcodeSSCC18Presenter"},
         {"Barcode", "BarcodeUCC128Presenter"},
         {"Barcode", "BarcodeUSD3Presenter"},
         {"Barcode", "BarcodeUSD4Presenter"},
         {"Barcode", "BarcodeUSPSPresenter"},
         {"2D Code", "QRCodePresenter"},
         {"Rotation", "CCWRotatePresenter"},
         {"Rotation", "CWRotatePresenter"},
         {"Decorative", "HeaderPresenter"},
         {"Decorative", "ButtonPresenter"},
         {"Decorative", "ShadowPresenter"}};
      TreeNodeModel.Builder root = TreeNodeModel.builder().label("Presenters");
      String parent = presenters[0][0];
      List<TreeNodeModel> children = new ArrayList<>();
      Map<String, Object> data = new HashedMap();
      data.put("class", null);
      data.put("hasDescriptors", false);

      TreeNodeModel none = TreeNodeModel.builder()
         .label(catalog.getString("(none)"))
         .leaf(true)
         .data(data)
         .build();

      root.addChildren(none);

      // add built-in presenters
      for(int i = 0; i < presenters.length; i++) {
         try {
            Presenter pobj = PresenterRef.getPresenter(presenters[i][1]);

            if(pobj != null) {
               if(!parent.equals(presenters[i][0])) {
                  TreeNodeModel group = TreeNodeModel.builder()
                     .label(catalog.getString(parent))
                     .children(children)
                     .build();

                  parent = presenters[i][0];
                  children = new ArrayList<>();

                  root.addChildren(group);
               }

               String path = catalog.getString(presenters[i][0]) + "^" + pobj.getDisplayName();

               PresenterRef ref = new PresenterRef(presenters[i][1]);
               boolean hasDescriptors = ref.getPropertyDescriptors() != null &&
                  ref.getPropertyDescriptors().length > 0;

               data = new HashedMap();
               data.put("path", path);
               data.put("class", pobj.getClass().getName());
               data.put("hasDescriptors", hasDescriptors);

               TreeNodeModel node = TreeNodeModel.builder()
                  .label(pobj.getDisplayName())
                  .data(data)
                  .leaf(true)
                  .build();

               children.add(node);
            }
         }
         catch(Exception ex) {
            LOG.warn(
                    "Failed to load presenter class: " + presenters[i][0], ex);
         }
      }

      //Bug17815: add the last children node to root in the end.
      TreeNodeModel lastNode = TreeNodeModel.builder()
         .label(catalog.getString(parent))
         .children(children)
         .build();
      root.addChildren(lastNode);

      // add user defined presenters
      String custom = SreeEnv.getProperty("user.presenters", "");
      StringTokenizer tokens = new StringTokenizer(custom, "|", false);
      TreeNodeModel.Builder userNode = TreeNodeModel.builder()
         .label(catalog.getString("common.userDefinedParam"));

      while(tokens.hasMoreTokens()) {
         String name = tokens.nextToken();

         try {
            Presenter pobj = PresenterRef.getPresenter(name);
            String path = catalog.getString("common.userDefinedParam") + "^" + pobj.getDisplayName();

            TreeNodeModel node = TreeNodeModel.builder()
               .label(name)
               .data(path)
               .leaf(true)
               .build();

            userNode.addChildren(node);
         }
         catch(Exception ex) {
            LOG.warn(
                    "Failed to load presenter class: " + name, ex);
         }
      }

      root.addChildren(userNode.build());

      return root.build();
   }

   private static final Logger LOG = LoggerFactory.getLogger(ComposerPresenterController.class);
}
