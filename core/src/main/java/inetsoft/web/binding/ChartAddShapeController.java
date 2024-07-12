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
package inetsoft.web.binding;

import inetsoft.uql.viewsheet.graph.aesthetic.ImageShapes;
import inetsoft.util.DataSpace;
import inetsoft.util.Tool;
import inetsoft.web.admin.content.dataspace.DataSpaceContentSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
/**
 * Controller that upload shapes for chart.
 */
@RestController
public class ChartAddShapeController {
   @Autowired
   public ChartAddShapeController(DataSpaceContentSettingsService dataSpaceContentSettingsService) {
      this.dataSpaceContentSettingsService = dataSpaceContentSettingsService;
   }

   @PostMapping("/api/chart/shape/upload")
   public boolean uploadShape(@RequestParam("file") MultipartFile[] files) throws Exception {
      String folder = ImageShapes.getShapesDirectory();
      DataSpace space = DataSpace.getDataSpace();

      for(MultipartFile file : files) {
         String fileName = file.getOriginalFilename();
         byte[] fileData = file.getBytes();

         try(DataSpace.Transaction tx = space.beginTransaction();
             OutputStream out = tx.newStream(folder, fileName))
         {
            Tool.fileCopy(new ByteArrayInputStream(fileData), out);

            tx.commit();
            dataSpaceContentSettingsService.updateFolder(folder);
         }
         catch(Throwable e) {
            LOG.error("Failed to write shape: " + fileName, e);
            throw e;
         }
      }

      return true;
   }

   private final DataSpaceContentSettingsService dataSpaceContentSettingsService;
   private static final Logger LOG = LoggerFactory.getLogger(ChartAddShapeController.class);
}
