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
package inetsoft.web.vswizard.recommender.object;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.vswizard.model.VSWizardData;
import inetsoft.web.vswizard.model.recommender.VSTableRecommendation;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Arrays;

@Component
public class VSTableRecommendationFactory implements VSObjectRecommendationFactory {
   @Autowired
   public VSTableRecommendationFactory(RuntimeViewsheetRef runtimeViewsheetRef,
                                       ViewsheetService viewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.viewsheetService = viewsheetService;
   }

   @Override
   public VSTableRecommendation recommend(VSWizardData wizardData, Principal principal) {
      VSTableRecommendation tableRecommendation = new VSTableRecommendation();
      tableRecommendation.setColumns(createColumnSelection(wizardData.getSelectedEntries(),
         principal));

      return tableRecommendation;
   }

   private ColumnSelection createColumnSelection(AssetEntry[] entries, Principal principal) {
      RuntimeViewsheet rvs = getRuntimeViewsheet(principal);

      if(entries == null || entries.length < 1 || rvs == null) {
         return null;
      }

      ColumnSelection columns = new ColumnSelection();

      Arrays.stream(entries).forEach((AssetEntry entry) -> {
         ColumnRef columnRef = WizardRecommenderUtil.createColumnRef(entry);

         if(WizardRecommenderUtil.isDimension(entry) ||
            !WizardRecommenderUtil.isCalcAggregateField(rvs, columnRef))
         {
            columns.addAttribute(columnRef);
         }
      });

      return columns;
   }

   private RuntimeViewsheet getRuntimeViewsheet(Principal principal) {
      try {
         return viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      } catch (Exception ex) {
         LOG.error("Failed to get viewsheet", ex);
         return null;
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
   private static final Logger LOG =
      LoggerFactory.getLogger(VSTableRecommendationFactory.class);

}
