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

import inetsoft.uql.XCube;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import org.springframework.stereotype.Component;

@Component
public class SyncCrosstabHandler extends SyncAssemblyHandler{
   public SyncCrosstabHandler() { }

   public void syncCrosstab(CrosstabVSAssembly fromAssembly, CrosstabVSAssembly targetAssembly) {
      syncProperties(fromAssembly, targetAssembly);
      syncCondition(fromAssembly, targetAssembly);
      syncFormat(fromAssembly, targetAssembly);
      syncHighlight(fromAssembly, targetAssembly);
      syncHyperlink(fromAssembly, targetAssembly);
   }

   private void syncProperties(CrosstabVSAssembly source, CrosstabVSAssembly target) {
      CrosstabVSAssemblyInfo sourceInfo = (CrosstabVSAssemblyInfo) source.getInfo();
      CrosstabVSAssemblyInfo targetInfo = (CrosstabVSAssemblyInfo) target.getInfo();

      // general
      targetInfo.setEnabledValue(sourceInfo.getEnabledValue());
      targetInfo.setPrimary(sourceInfo.isPrimary());
      targetInfo.setVisibleValue(sourceInfo.getVisibleValue());
      targetInfo.setTableStyleValue(sourceInfo.getTableStyle());

      // advanced
      VSCrosstabInfo sourceCrosstabInfo = sourceInfo.getVSCrosstabInfo();
      VSCrosstabInfo targetCrosstabInfo = targetInfo.getVSCrosstabInfo();

      if(sourceCrosstabInfo != null && targetCrosstabInfo != null) {
         targetCrosstabInfo.setFillBlankWithZeroValue(sourceCrosstabInfo.isFillBlankWithZero());
         targetCrosstabInfo.setSummarySideBySideValue(sourceCrosstabInfo.isSummarySideBySide());
         targetCrosstabInfo.setMergeSpanValue(sourceCrosstabInfo.isMergeSpan());
         targetCrosstabInfo.setSortOthersLastValue(sourceCrosstabInfo.isSortOthersLast());
      }

      targetInfo.setDrillEnabledValue(sourceInfo.isDrillEnabled() + "");
      targetInfo.setEnableAdhocValue(sourceInfo.isEnableAdhoc());
      targetInfo.setShrinkValue(sourceInfo.isShrink());
      targetInfo.setTipOptionValue(sourceInfo.getTipOptionValue());
      targetInfo.setTipViewValue(sourceInfo.getTipViewValue());
      targetInfo.setAlphaValue(sourceInfo.getAlphaValue());
      targetInfo.setFlyOnClickValue(sourceInfo.getFlyOnClickValue());
      targetInfo.setFlyoverViewsValue(sourceInfo.getFlyoverViewsValue());

      // hierarchy
      XCube cube = sourceInfo.getXCube();

      if(cube == null || !(cube instanceof VSCube) || ((VSCube) cube).isEmpty()) {
         return;
      }

      targetInfo.setXCube((VSCube) ((VSCube) cube).clone());
   }

   private void syncHighlight(CrosstabVSAssembly source, CrosstabVSAssembly target) {
      CrosstabVSAssemblyInfo sourceInfo = (CrosstabVSAssemblyInfo) source.getInfo();
      CrosstabVSAssemblyInfo targetInfo = (CrosstabVSAssemblyInfo) target.getInfo();
      targetInfo.setHighlightAttr(sourceInfo.getHighlightAttr());
   }

   private void syncHyperlink(CrosstabVSAssembly source, CrosstabVSAssembly target) {
      CrosstabVSAssemblyInfo sourceInfo = (CrosstabVSAssemblyInfo) source.getInfo();
      CrosstabVSAssemblyInfo targetInfo = (CrosstabVSAssemblyInfo) target.getInfo();
      targetInfo.setHyperlinkAttr(sourceInfo.getHyperlinkAttr());
   }
}
