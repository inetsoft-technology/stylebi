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
package inetsoft.web.binding.model;

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.web.binding.drm.AbstractDataRefModel;
import inetsoft.web.binding.service.DataRefModelFactory;
import org.springframework.stereotype.Component;

public class ExpressionRefModel extends AbstractDataRefModel {

   public ExpressionRefModel(){}

   public ExpressionRefModel(ExpressionRef ref){
      super(ref);
      setDatasource(ref.getDataSource());
      setDType(ref.getDataType());
      setEntity(ref.getEntity());
      setExp(ref.getExpression());
      setName(ref.getName());
      setRefType(ref.getRefType());
   }

   @Override
   public DataRef createDataRef(){
      ExpressionRef ref = new ExpressionRef();
      setProperties(ref);
      return ref;
   }

   protected void setProperties(ExpressionRef ref) {
      ref.setDataSource(datasource);
      ref.setDataType(dtype);
      ref.setEntity(super.getEntity());
      ref.setExpression(exp);
      ref.setName(super.getName());
      ref.setRefType(super.getRefType());
   }

   public String getDatasource() {
      return datasource;
   }

   public void setDatasource(String datasource) {
      this.datasource = datasource;
   }

   @Override
   public boolean isExpression() {
      return true;
   }

   public String getExp() {
      return exp;
   }

   public void setExp(String exp) {
      this.exp = exp;
   }

   public String getDType() {
      return dtype;
   }

   public void setDType(String dtype) {
      this.dtype = dtype;
   }

   private String datasource;
   private String exp = "";
   private String dtype;

   @Component
   public static final class ExpressionRefModelFactory
      extends DataRefModelFactory<ExpressionRef, ExpressionRefModel>
   {
      @Override
      public Class<ExpressionRef> getDataRefClass() {
         return ExpressionRef.class;
      }

      @Override
      public ExpressionRefModel createDataRefModel(ExpressionRef dataRef) {
         return new ExpressionRefModel(dataRef);
      }
   }
}
