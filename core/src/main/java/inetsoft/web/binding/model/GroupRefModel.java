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

import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.binding.drm.AbstractDataRefModel;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.model.table.OrderModel;
import inetsoft.web.binding.service.DataRefModelFactory;
import org.springframework.stereotype.Component;

public class GroupRefModel extends AbstractDataRefModel {
   public GroupRefModel() {
   }

   public GroupRefModel(GroupRef ref) {
      super(ref);
      setAssemblyName(ref.getNamedGroupAssembly());

      if(ref.getDataRef() != null && ref.getDataRef() instanceof ColumnRef) {
         setRef(new ColumnRefModel((ColumnRef) ref.getDataRef()));
      }

      setDgroup(ref.getDateGroup());
      setTimeSeries(ref.isTimeSeries());
      setOrder(new OrderModel(ref.getOrderInfo()));
   }

   /**
    * Create a data ref.
    */
   @Override
   public DataRef createDataRef() {
      GroupRef ref = new GroupRef();
      ref.setNamedGroupAssembly(assemblyName);
      ref.setDataRef(this.ref.createDataRef());
      ref.setDateGroup(getDgroup());
      ref.setTimeSeries(isTimeSeries());

      if(order != null) {
         ref.setOrderInfo(order.createOrderInfo(null));
      }
      else {
         ref.setOrderInfo(new OrderInfo(OrderInfo.SORT_ASC));
      }

      return ref;
   }

   public String getAssemblyName() {
      return assemblyName;
   }

   public void setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;
   }

   public ColumnRefModel getRef() {
      return ref;
   }

   public void setRef(ColumnRefModel ref) {
      this.ref = ref;
   }

   public int getDgroup() {
      return dgroup;
   }

   public void setDgroup(int dgroup) {
      this.dgroup = dgroup;
   }

   public OrderModel getOrder() {
      return order;
   }

   public void setOrder(OrderModel order) {
      this.order = order;
   }

   public boolean isTimeSeries() {
      return this.timeSeries;
   }

   public void setTimeSeries(boolean timeSeries) {
      this.timeSeries = timeSeries;
   }

   private String assemblyName;
   private ColumnRefModel ref;
   private int dgroup;
   private OrderModel order;
   private boolean timeSeries;

   @Component
   public static final class GroupRefModelFactory
      extends DataRefModelFactory<GroupRef, GroupRefModel>
   {
      @Override
      public Class<GroupRef> getDataRefClass() {
         return GroupRef.class;
      }

      @Override
      public GroupRefModel createDataRefModel(GroupRef dataRef) {
         return new GroupRefModel(dataRef);
      }
   }
}
