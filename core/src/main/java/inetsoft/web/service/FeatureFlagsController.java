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
package inetsoft.web.service;

import inetsoft.util.FeatureFlags;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeatureFlagsController {
   @GetMapping("api/feature-flags")
   public FeatureFlagsModel getEnabledFeatures() {
      FeatureFlags featureFlags = FeatureFlags.getInstance();
      String[] enabled = featureFlags.getEnabledFeatures().stream()
         .map(FeatureFlags.Value::name)
         .toArray(String[]::new);
      return FeatureFlagsModel.builder()
         .enabledFeatures(enabled)
         .build();
   }
}
