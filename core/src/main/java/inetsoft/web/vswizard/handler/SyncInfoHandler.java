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
package inetsoft.web.vswizard.handler;

import inetsoft.uql.ConditionList;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * Sync information of target assembly to source assembly.
 */
@Component
public class SyncInfoHandler {
   public SyncInfoHandler(SyncChartHandler chartHandler,
                          SyncTableHandler tableHandler,
                          SyncCrosstabHandler crostabHandler)
   {
      this.chartHandler = chartHandler;
      this.tableHandler = tableHandler;
      this.crostabHandler = crostabHandler;
   }

   /**
    * sync the information from source to target.
    * @param tempInfo    the temporary information for wizard.
    * @param source      the source assembly to sync information.
    * @param target      the target assembly to sync information.
    */
   public void syncInfo(VSTemporaryInfo tempInfo, VSAssembly source, VSAssembly target) {
      if(source instanceof DrillFilterVSAssembly && target instanceof DrillFilterVSAssembly) {
         syncDrillFilter(((DrillFilterVSAssembly) source), (DrillFilterVSAssembly) target);
      }

      if(!shouldSyncInfo(source, target)) {
         return;
      }

      syncScript(source, target);

      if(source instanceof ChartVSAssembly) {
         chartHandler.syncChart(tempInfo, (ChartVSAssembly) source, (ChartVSAssembly) target,
                                true, true);
      }
      else if(source instanceof TableVSAssembly) {
         tableHandler.syncTable((TableVSAssembly) source, (TableVSAssembly) target);
      }
      else if(source instanceof CrosstabVSAssembly) {
         crostabHandler.syncCrosstab((CrosstabVSAssembly) source, (CrosstabVSAssembly) target);
      }
      else if(source instanceof OutputVSAssembly) {
         VSAssemblyInfo info = source.getVSAssemblyInfo();
         FormatInfo formatInfo = info.getFormatInfo();
         target.setFormatInfo(formatInfo.clone());
      }
   }

   public boolean shouldSyncInfo(VSAssembly source, VSAssembly target) {
      return source != null && target != null &&
         Tool.equals(source.getTableName(), target.getTableName()) &&
         Tool.equals(source.getClass().getName(), target.getClass().getName());
   }

   private void syncScript(VSAssembly source, VSAssembly target) {
      String script = ((AbstractVSAssembly) target).getScript();

      if(!StringUtils.isEmpty(script)) {
         ((AbstractVSAssembly) target).setScript(SyncAssemblyHandler.updateScript(
            script, source.getAbsoluteName(), target.getAbsoluteName()));
      }
   }

   private void syncDrillFilter(DrillFilterVSAssembly sourceAssembly,
                                DrillFilterVSAssembly targetAssembly)
   {
      for(String field : sourceAssembly.getDrillFilterInfo().getFields()) {
         ConditionList drillFilterConditionList = sourceAssembly.getDrillFilterConditionList(field);
         ConditionList newCond = drillFilterConditionList.clone();
         DataRef[] oldRefs = sourceAssembly.getDrillFilterAvailableRefs();
         DataRef[] refs = targetAssembly.getDrillFilterAvailableRefs();

         Arrays.stream(oldRefs).filter(ref -> ref instanceof VSDimensionRef)
            .map(ref -> ((VSDimensionRef) ref))
            .forEach(ref -> {
               if(newCond.getConditionItem(ref.getDataRef()) == null ||
                  !ArrayUtils.contains(refs, ref))
               {
                  newCond.remove(ref);
               }
            });

         targetAssembly.setDrillFilterConditionList(field, newCond);
      }
   }

   private final SyncChartHandler chartHandler;
   private final SyncTableHandler tableHandler;
   private final SyncCrosstabHandler crostabHandler;

   private static final Logger LOGGER = LoggerFactory.getLogger(SyncInfoHandler.class);
}
