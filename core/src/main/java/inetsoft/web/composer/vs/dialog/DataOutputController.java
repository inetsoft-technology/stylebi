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
package inetsoft.web.composer.vs.dialog;

import inetsoft.web.composer.model.vs.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@Controller
class DataOutputController {

   @Autowired
   public DataOutputController(DataOutputServiceProxy dataOutputServiceProxy) {
      this.dataOutputServiceProxy = dataOutputServiceProxy;
   }

   /**
    *  Gets the Columns of the specified table for data output pane
    * @param table      Table name
    * @param runtimeId  Runtime viewsheet id
    * @param principal  the principal user
    * @return the columns of the table
    * @throws Exception if could not get the columns
    */
   @RequestMapping(
      value = "/vs/dataOutput/table/columns",
      method = RequestMethod.GET
   )
   @ResponseBody
   public OutputColumnModel[] getOutputTableColumns(@RequestParam("table") String table,
                                                    @RequestParam("runtimeId") String runtimeId,
                                                    Principal principal)
      throws Exception
   {
      return dataOutputServiceProxy.getOutputTableColumns(runtimeId, table, principal);
   }

   /**
    *  Gets the columns of the specified cube
    * @param table      The cube name
    * @param runtimeId  the runtime viewsheet id
    * @param principal  the principal user
    * @return the columns of the cube
    * @throws Exception if could not get the columns
    */
   @RequestMapping(
      value = "/vs/dataOutput/cube/columns",
      method = RequestMethod.GET
   )
   @ResponseBody
   public OutputCubeModel[] getOutputCubeColumns(@RequestParam("table") String table,
                                                 @RequestParam("runtimeId") String runtimeId,
                                                 @RequestParam("columnType") String columnType,
                                                 Principal principal)
      throws Exception
   {
      return dataOutputServiceProxy.getOutputCubeColumns(runtimeId, table, columnType, principal);
   }

   /**
    * Gets the measures columns of the specified table for selection data pane
    *
    * @param tables    table names
    * @param runtimeId Runtime viewsheet id
    * @param principal the principal user
    * @return the columns of the table
    * @throws Exception if could not get the columns
    */
   @RequestMapping(
      value = "/vs/dataOutput/selection/columns",
      method = RequestMethod.GET
   )
   @ResponseBody
   public OutputColumnRefModel[] getOutputSelectionColumns(
      @RequestParam("table") List<String> tables,
      @RequestParam("runtimeId") String runtimeId,
      Principal principal) throws Exception
   {
      return dataOutputServiceProxy.getOutputSelectionColumns(runtimeId, tables, principal);
   }

   private final DataOutputServiceProxy dataOutputServiceProxy;
}
