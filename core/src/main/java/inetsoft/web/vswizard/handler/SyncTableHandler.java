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
package inetsoft.web.vswizard.handler;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.internal.EmbeddedTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class SyncTableHandler extends SyncAssemblyHandler{
   public SyncTableHandler() { }

   public void syncTable(TableVSAssembly fromAssembly, TableVSAssembly targetAssembly) {
      TableVSAssemblyInfo sourceInfo = (TableVSAssemblyInfo) fromAssembly.getInfo();
      TableVSAssemblyInfo targetInfo = (TableVSAssemblyInfo) targetAssembly.getInfo();

      targetInfo.copyViewInfo(sourceInfo, true);
      syncProperties(sourceInfo, targetInfo);
      syncCondition(fromAssembly, targetAssembly);
      syncFormat(fromAssembly, targetAssembly);
      syncHighlight(sourceInfo, targetInfo);
      syncHyperlink(sourceInfo, targetInfo);
      syncSort(fromAssembly, targetAssembly);
      syncAlias(sourceInfo, targetInfo);
   }

   private void syncProperties(TableVSAssemblyInfo sourceInfo,
                               TableVSAssemblyInfo targetInfo)
   {
      // general
      targetInfo.setMaxRows(sourceInfo.getMaxRows());
      targetInfo.setEnabledValue(sourceInfo.getEnabledValue());
      targetInfo.setPrimary(sourceInfo.isPrimary());
      targetInfo.setVisibleValue(sourceInfo.getVisibleValue());
      targetInfo.setTableStyleValue(sourceInfo.getTableStyleValue());
      targetInfo.setDataRowHeight(sourceInfo.getDataRowHeight());
      targetInfo.setTitleHeightValue(sourceInfo.getTitleHeightValue());

      // advanced
      targetInfo.setEmbeddedTable(sourceInfo instanceof EmbeddedTableVSAssemblyInfo ||
         sourceInfo.isEmbeddedTable());
      targetInfo.setFormValue(sourceInfo.getFormValue());
      targetInfo.setInsertValue(sourceInfo.getInsertValue());
      targetInfo.setDelValue(sourceInfo.getDelValue());
      targetInfo.setEditValue(sourceInfo.getEditValue());
      targetInfo.setEnableAdhocValue(sourceInfo.getEnableAdhocValue());
      targetInfo.setShrinkValue(sourceInfo.getShrinkValue());

      targetInfo.setTipOptionValue(sourceInfo.getTipOptionValue());
      targetInfo.setTipViewValue(sourceInfo.getTipViewValue());
      targetInfo.setAlphaValue(sourceInfo.getAlphaValue());
      targetInfo.setFlyOnClickValue(sourceInfo.getFlyOnClickValue());
      targetInfo.setFlyoverViewsValue(sourceInfo.getFlyoverViewsValue());
   }

   private void syncHighlight(TableVSAssemblyInfo sourceInfo,
                              TableVSAssemblyInfo targetInfo)
   {
      targetInfo.setHighlightAttr(sourceInfo.getHighlightAttr());
   }

   private void syncHyperlink(TableVSAssemblyInfo sourceInfo,
                              TableVSAssemblyInfo targetInfo)
   {
      targetInfo.setHyperlinkAttr(sourceInfo.getHyperlinkAttr());
   }

   private void syncSort(TableVSAssembly source, TableVSAssembly target) {
      target.setSortInfo(source.getSortInfo());
   }

   private void syncAlias(TableVSAssemblyInfo sourceInfo, TableVSAssemblyInfo targetInfo) {
      ColumnSelection source = sourceInfo.getColumnSelection();
      ColumnSelection target = targetInfo.getColumnSelection();

      target.stream().forEach(col -> {
         final ColumnRef tcol = (ColumnRef) col;
         ColumnRef scol = (ColumnRef) source.stream()
            .filter(c -> Objects.equals(tcol.getDataRef(), ((ColumnRef) c).getDataRef()))
            .findFirst().orElse(null);

         if(scol != null && scol.getAlias() != null && tcol.getAlias() == null) {
            tcol.setAlias(scol.getAlias());
         }
      });
   }
}
