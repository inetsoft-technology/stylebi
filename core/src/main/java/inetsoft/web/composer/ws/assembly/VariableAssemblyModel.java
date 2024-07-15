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
package inetsoft.web.composer.ws.assembly;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AbstractWSAssembly;
import inetsoft.uql.asset.VariableAssembly;
import inetsoft.uql.asset.internal.*;

public class VariableAssemblyModel extends WSAssemblyModel {
   public VariableAssemblyModel(
      VariableAssembly assembly, RuntimeWorksheet rws) throws Exception
   {
      super((AbstractWSAssembly) assembly, rws);
      WSAssemblyInfo info = assembly.getWSAssemblyInfo();
      this.setInfo(new WSAssemblyInfoModel(info));

      if(info instanceof MirrorAssemblyInfo) {
         MirrorAssemblyImpl impl = ((MirrorAssemblyInfo) info).getImpl();
         this.getInfo().setMirrorInfo(WSMirrorAssemblyInfoModel.builder().from(impl).build());
      }

      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      VariableTable vart = box.getVariableTable();
      this.setVariableInfo(new VariableAssemblyModelInfo(assembly.getVariable(),
                                                 vart.get(assembly.getName())));
   }

   @Override
   public String getClassType() {
      return "VariableAssembly";
   }

   @Override
   public WSAssemblyInfoModel getInfo() {
      return info;
   }

   public void setInfo(WSAssemblyInfoModel info) {
      this.info = info;
   }

   public VariableAssemblyModelInfo getVariableInfo() {
      return variableInfo;
   }

   public void setVariableInfo(VariableAssemblyModelInfo info) {
      this.variableInfo= info;
   }

   private WSAssemblyInfoModel info;
   private VariableAssemblyModelInfo variableInfo;
}