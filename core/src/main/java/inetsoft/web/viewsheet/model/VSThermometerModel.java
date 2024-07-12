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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.ThermometerVSAssembly;
import org.springframework.stereotype.Component;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSThermometerModel extends VSOutputModel<ThermometerVSAssembly> {
   public VSThermometerModel(ThermometerVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
   }

   @Component
   public static final class VSThermometerModelFactory
      extends VSObjectModelFactory<ThermometerVSAssembly, VSThermometerModel>
   {
      public VSThermometerModelFactory() {
         super(ThermometerVSAssembly.class);
      }

      @Override
      public VSThermometerModel createModel(ThermometerVSAssembly assembly, RuntimeViewsheet rvs) {
         return new VSThermometerModel(assembly, rvs);
      }
   }
}
