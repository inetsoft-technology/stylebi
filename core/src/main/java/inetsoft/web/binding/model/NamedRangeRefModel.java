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
package inetsoft.web.binding.model;

import inetsoft.uql.asset.NamedRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.service.DataRefModelFactory;
import org.springframework.stereotype.Component;

public class NamedRangeRefModel extends ExpressionRefModel {
   public NamedRangeRefModel(){}

   public NamedRangeRefModel(NamedRangeRef namedRangeRef) {
      super(namedRangeRef);

      if(namedRangeRef.getDataRef() instanceof AttributeRef) {
         setRef(new AttributeRefModel((AttributeRef) namedRangeRef.getDataRef()));
      }

      setAttr(namedRangeRef.getAttribute());
      setDataType(namedRangeRef.getDataType());
      setBaseDataType(namedRangeRef.getBaseDataType());
      setDbtype(namedRangeRef.getDBType());
      setDbversion(namedRangeRef.getDBVersion());

      if(namedRangeRef != null) {
         setNamedGroupInfoModel(new NamedGroupInfoModel(namedRangeRef.getNamedGroupInfo()));
      }
   }

   @Override
   public DataRef createDataRef() {
      NamedRangeRef ref = new NamedRangeRef(getAttr());
      super.setProperties(ref);
      ref.setDataType(getDataType());
      ref.setBaseDataType(getBaseDataType());
      ref.setDBType(getDbtype());
      ref.setDBVersion(getDbversion());

      if(getNamedGroupInfoModel() != null) {
         ref.setNamedGroupInfo(namedGroupInfoModel.getXNamedGroupInfo());
      }

      if(getRef() != null) {
         ref.setDataRef(getRef().createDataRef());
      }

      return ref;
   }

   public DataRefModel getRef() {
      return ref;
   }

   public void setRef(DataRefModel ref) {
      this.ref = ref;
   }

   public String getAttr() {
      return attr;
   }

   public void setAttr(String attr) {
      this.attr = attr;
   }

   @Override
   public String getDataType() {
      return dataType;
   }

   @Override
   public void setDataType(String dataType) {
      this.dataType = dataType;
   }

   public String getBaseDataType() {
      return baseDataType;
   }

   public void setBaseDataType(String baseDataType) {
      this.baseDataType = baseDataType;
   }

   public NamedGroupInfoModel getNamedGroupInfoModel() {
      return namedGroupInfoModel;
   }

   public void setNamedGroupInfoModel(NamedGroupInfoModel namedGroupInfoModel) {
      this.namedGroupInfoModel = namedGroupInfoModel;
   }

   public String getDbtype() {
      return dbtype;
   }

   public void setDbtype(String dbtype) {
      this.dbtype = dbtype;
   }

   public String getDbversion() {
      return dbversion;
   }

   public void setDbversion(String dbversion) {
      this.dbversion = dbversion;
   }

   private DataRefModel ref;
   private String attr;
   private String dataType;
   private String baseDataType;
   private NamedGroupInfoModel namedGroupInfoModel;
   private String dbtype;
   private String dbversion;

   @Component
   public static final class NamedRangeRefModelFactory
      extends DataRefModelFactory<NamedRangeRef, NamedRangeRefModel>
   {
      @Override
      public Class<NamedRangeRef> getDataRefClass() {
         return NamedRangeRef.class;
      }

      @Override
      public NamedRangeRefModel createDataRefModel(NamedRangeRef dataRef) {
         return new NamedRangeRefModel(dataRef);
      }
   }
}
