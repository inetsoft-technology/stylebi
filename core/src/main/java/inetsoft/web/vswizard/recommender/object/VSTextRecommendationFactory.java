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
package inetsoft.web.vswizard.recommender.object;

import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.web.vswizard.model.VSWizardData;
import inetsoft.web.vswizard.model.recommender.VSTextRecommendation;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class VSTextRecommendationFactory extends
   VSOutputRecommendationFactory<VSTextRecommendation>
{
   /**
    * Get the target assembly supported by this factory.
    */
   @Override
   public Class getVSAssemblyClass() {
      return TextVSAssembly.class;
   }

   /**
    * Return the VSRecommendObject for target selections.
    */
   @Override
   public VSTextRecommendation recommend(VSWizardData wizardData, Principal principal) {
      VSTextRecommendation textRecommendation = new VSTextRecommendation();
      textRecommendation.setDataRef(createDataRef(wizardData.getSelectedEntries()));

      return textRecommendation;
   }
}
