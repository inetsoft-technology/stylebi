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

package inetsoft.web.wiz.controller;

import inetsoft.report.internal.graph.MapData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Returns available map types and their supported layers.
 * Used by wiz-services to dynamically build map chart prompts.
 */
@RestController
@RequestMapping("/api/wiz")
public class WizMapDataController {

   private static final Logger LOG = LoggerFactory.getLogger(WizMapDataController.class);

   /**
    * Returns all supported map types and their valid layers.
    * Example response:
    * {
    *   "World":  ["Country", "City"],
    *   "U.S.":   ["State", "City", "Zip"],
    *   "Canada": ["Province", "City"],
    *   ...
    * }
    */
   @GetMapping(value = "/map/types", produces = MediaType.APPLICATION_JSON_VALUE)
   public Map<String, List<String>> getMapTypes() {
      String[] types = MapData.getMapTypes();
      Map<String, List<String>> result = new LinkedHashMap<>();

      for(String type : types) {
         try {
            Map<String, Integer> layers = MapData.getLayers(type);
            result.put(type, new ArrayList<>(layers.keySet()));
         }
         catch(RuntimeException e) {
            LOG.warn("Skipping map type '{}' due to configuration error: {}", type, e.getMessage());
         }
      }

      return result;
   }
}
