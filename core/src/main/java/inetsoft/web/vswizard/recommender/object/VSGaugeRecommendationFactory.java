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
package inetsoft.web.vswizard.recommender.object;

import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.web.vswizard.model.VSWizardData;
import inetsoft.web.vswizard.model.recommender.VSGaugeRecommendation;
import inetsoft.web.vswizard.model.recommender.VSSubType;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

@Component
public final class VSGaugeRecommendationFactory
   extends VSOutputRecommendationFactory<VSGaugeRecommendation>
{
   /**
    * Get the target assembly supported by this factory.
    */
   @Override
   public Class getVSAssemblyClass() {
      return GaugeVSAssembly.class;
   }

   /**
    * Return the VSRecommendObject for target selections.
    */
   @Override
   public VSGaugeRecommendation recommend(VSWizardData wizardData, Principal principal) {
      VSGaugeRecommendation gaugeRecommendation = new VSGaugeRecommendation();
      gaugeRecommendation.setDataRef(createDataRef(wizardData.getSelectedEntries()));
      gaugeRecommendation.setSubTypes(GAUGE_FACES);
      gaugeRecommendation.setSelectedIndex(0);

      return gaugeRecommendation;
   }

   public static final List<VSSubType> GAUGE_FACES = Arrays.asList(
      new VSSubType("90820"), new VSSubType("10920"), new VSSubType("10120"),
      new VSSubType("10910"), new VSSubType("10220"), new VSSubType("13000"));
}
