/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.web.composer.ws;

import inetsoft.analytic.AnalyticAssistant;
import inetsoft.analytic.web.adhoc.AdHocQueryHandler;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.internal.binding.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Catalog;
import inetsoft.util.ItemList;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.BrowseDataController;
import inetsoft.web.composer.model.BrowseDataModel;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Enumeration;

/**
 * Controller containing commonly used worksheet mappings.
 */

@Controller
public class WSUtilControllers extends WorksheetController {

   /**
    * From {@link AdHocQueryHandler} process method.
    *
    * @return the values of entry's attribute in a {values, labels} object
    */
   @RequestMapping(
      value = "/api/composer/ws/asset-entry-attribute-data",
      method = RequestMethod.POST)
   @ResponseBody
   public BrowseDataModel getAttributeData(
      HttpServletRequest req,
      @RequestBody AssetEntry entry,
      @RequestParam("attribute") String attribute,
      Principal principal) throws Exception
   {
      SourceInfo source = new SourceInfo(Integer.parseInt(entry.getProperty("type")),
                                          entry.getProperty("prefix"),
                                          entry.getProperty("source"));
      ColumnSelection columns = AssetEventUtil.getAttributesBySource(
         getWorksheetEngine(), principal, source);
      DataRef ref = columns.getAttribute(attribute);
      Field field = new BaseField(ref);
      field.setDataType(ref.getDataType());

      // get the reportID and elementID
      String elementID = null;
      AnalyticAssistant engine = AnalyticAssistant.getAnalyticAssistant();

      ItemList values = null;

      try {
         // only get list if the field is selected
         if(!source.isEmpty()) {
            // @by billh, the field might be a formula field, which is not
            // supported at present, return an empty item list
            // in this case
            if(field.getAttribute() == null) {
               values = new ItemList("Values");
            }
            else if(source.getType() == SourceAttr.EMBEDDED_DATA) {
               values = engine.getEmbeddedValues(field.getName());
            }
            else {
               VariableTable vars = new VariableTable();
               Enumeration<?> names = req.getAttributeNames();

               // support to get parameters from session
               while(names.hasMoreElements()) {
                  String name = (String) names.nextElement();
                  vars.put(name, req.getAttribute(name));
               }

               // Bug #56579, execute the asset query to get the model's available values
               if(source.getType() == SourceAttr.MODEL && ref instanceof ColumnRef) {
                  Worksheet ws = new Worksheet();
                  BoundTableAssembly table = new BoundTableAssembly(ws, "model_browse_data_table");
                  table.setSourceInfo(source);
                  table.setColumnSelection(columns);
                  ws.addAssembly(table);
                  BrowseDataController browseDataController = new BrowseDataController();
                  browseDataController.setName(table.getName());
                  browseDataController.setColumn((ColumnRef) ref);
                  BrowseDataModel browseModel = browseDataController.process(
                     new AssetQuerySandbox(ws, (XPrincipal) principal, vars));

                  if(browseModel != null) {
                     return browseModel;
                  }
               }

               values = engine.getAvailableValues(source, field, principal, field.getDataType(),
                       vars);
            }
         }
      }
      catch(Exception e) {
         values = new ItemList("Values");
      }

      if(values == null) {
         System.out.println(Catalog.getCatalog().getString(
            "No available values"));
         return null;
      }
      if(values.getSize() != 2) {
         System.out.println("Not 2");
         return null;
      }

      ItemList labels = (ItemList) values.getItem(1);
      return BrowseDataModel.builder()
         .values(labels.toArray())
         .build();
   }

   /**
    * @return the attributes of entry
    */
   @RequestMapping(
      value = "/api/composer/ws/asset-entry-attributes",
      method = RequestMethod.POST)
   @ResponseBody
   public ColumnRefModel[] getAttributes(
      @RequestBody AssetEntry entry, Principal principal) throws Exception
   {
      SourceInfo source = new SourceInfo(Integer.parseInt(entry.getProperty("type")),
                                         entry.getProperty("prefix"),
                                         entry.getProperty("source"));
      ColumnSelection columns = AssetEventUtil.getAttributesBySource(
         getWorksheetEngine(), principal, source);
      ColumnRefModel[] attributes = new ColumnRefModel[columns.getAttributeCount()];

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         attributes[i] = (ColumnRefModel) dataRefModelFactoryService
            .createDataRefModel(columns.getAttribute(i));

         if(attributes != null && StringUtils.isEmpty(attributes[i].getAlias()) &&
            StringUtils.isEmpty(attributes[i].getAttribute()))
         {
            attributes[i].setName("Column[" + i + "]");
            attributes[i].setView("Column[" + i + "]");
         }
      }

      return attributes;
   }

   @Autowired
   public void setDataRefModelFactoryService(
      DataRefModelFactoryService dataRefModelFactoryService)
   {
      this.dataRefModelFactoryService = dataRefModelFactoryService;
   }

   private DataRefModelFactoryService dataRefModelFactoryService;
}
