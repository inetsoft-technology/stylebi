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
package inetsoft.web.viewsheet.controller;

import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.web.factory.RemainingPath;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.OutputStream;
import java.security.Principal;

/**
 * Controller that provides endpoints for presenter related actions.
 */
@Controller
public class PresenterController {
   /**
    * Creates a new instance of <tt>PresenterController</tt>.
    */
   @Autowired
   public PresenterController(PresenterServiceProxy presenterServiceProxy)
   {
      this.presenterServiceProxy = presenterServiceProxy;
   }

   /**
    * Get table cell presenter image.
    *
    * @param assembly  the table name
    * @param row       the table row
    * @param column    the table column
    * @param width     the cell width
    * @param height    the cell height
    * @param runtimeId the viewsheet runtime id
    * @param response  the http response
    */
   @GetMapping(value = "/vs/table/presenter/{assembly}/{row}/{col}/{width}/{height}/**")
   public void getPresenterImage(@PathVariable("assembly") String assembly,
                                 @PathVariable("row") int row,
                                 @PathVariable("col") int column,
                                 @PathVariable("width") int width,
                                 @PathVariable("height") int height,
                                 @RemainingPath String runtimeId,
                                 HttpServletResponse response,
                                 Principal principal)
      throws Exception
   {
      BinaryTransfer imageData = presenterServiceProxy
         .getPresenterImage(runtimeId, assembly, row, column, width, height, principal);

      if(imageData.isEmpty()) {
         response.setContentType("image/png");
         OutputStream out = response.getOutputStream();
         imageData.writeData(out);
         out.flush();
      }
   }

   /**
    * Get presenter image for VSText.
    *
    * @param assembly  the text assembly name
    * @param width     the text width
    * @param height    the text height
    * @param runtimeId the viewsheet runtime id
    * @param response  the http response
    */
   @GetMapping(value = "/vs/text/presenter/{assembly}/{width}/{height}/{layout}/{layoutRegion}/**")
   public void getPresenterImage(@PathVariable("assembly") String assembly,
                                 @PathVariable("width") int width,
                                 @PathVariable("height") int height,
                                 @PathVariable("layout") boolean layout,
                                 @PathVariable("layoutRegion") int layoutRegion,
                                 @RemainingPath String runtimeId,
                                 HttpServletResponse response,
                                 Principal principal)
      throws Exception
   {
      BinaryTransfer imageData = presenterServiceProxy
         .getPresenterImage(runtimeId, assembly, width, height,layout, layoutRegion, principal);

      if(imageData.isEmpty()) {
         response.setContentType("image/png");
         OutputStream out = response.getOutputStream();
         imageData.writeData(out);
         out.flush();
      }
   }

   private PresenterServiceProxy presenterServiceProxy;
}
