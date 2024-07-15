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
package inetsoft.web.binding.model.table;

import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.uql.XConstants;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.web.binding.model.NamedGroupInfoModel;

import java.util.ArrayList;
import java.util.List;

public class OrderModel {
   /**
    * Create a default OrderModel.
    */
   public OrderModel() {
   }

   /**
    * Create a OrderModel according to orderinfo.
    */
   public OrderModel(OrderInfo info) {
      super();

      if(info == null) {
         return;
      }

      setType(info.getOrder());
      setSortCol(info.getSortByCol());
      setOption(info.getOption());
      setInterval((int)info.getInterval());
      setSortValue(info.getSortByColValue());
      setOthers(info.getOthers() == 1);
      XNamedGroupInfo ong = info.getNamedGroupInfo();
      NamedGroupInfoModel ng = new NamedGroupInfoModel(ong);
      setInfo(ng);
      manualOrder = new ArrayList<>();
   }

   /**
    * Get order type.
    * @return the order type.
    */
   public int getType() {
      return type;
   }

   /**
    * Set order type.
    * @param type the order type.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Get sort by col index.
    * @return the sort by col index.
    */
   public int getSortCol() {
      return sortByCol;
   }

   /**
    * Set sort by col index.
    * @param col the sort by col index.
    */
   public void setSortCol(int col) {
      this.sortByCol = col;
   }

   /**
    * Get sort by col name.
    * @return the sort by col name.
    */
   public String getSortValue() {
      return sortByValue;
   }

   /**
    * Set sort by col name.
    * @param name the sort by col name.
    */
   public void setSortValue(String name) {
      this.sortByValue = name;
   }

   /**
    * Get named group info.
    * @return the named group info.
    */
   public NamedGroupInfoModel getInfo() {
      return info;
   }

   /**
    * Set named group info.
    * @param info the named group info.
    */
   public void setInfo(NamedGroupInfoModel info) {
      this.info = info;
   }

   /**
    * Set date option.
    * @param option the date option.
    */
   public void setOption(int option) {
      this.option = option;
   }

   /**
    * Get date option.
    * @return the date option.
    */
   public int getOption() {
      return option;
   }

   /**
    * Set date interval.
    * @param interval the date interval.
    */
   public void setInterval(int interval) {
      this.interval = interval;
   }

   /**
    * Get date interval.
    * @return the date interval.
    */
   public int getInterval() {
      return interval;
   }

   /**
    * Set group others or not.
    * @param other is group others or not.
    */
   public void setOthers(boolean other) {
      this.others = other;
   }

   /**
    * Get is group others or not.
    * @return is others or not.
    */
   public boolean isOthers() {
      return others;
   }

   /**
    * List of manual order values
    */
   public List<String> getManualOrder() {
      return manualOrder;
   }

   public void setManualOrder(List<String> manualOrder) {
      this.manualOrder = manualOrder;
   }

   /**
    * Set order information to orderinfo.
    * @param order the orderinfo to be set.
    */
   public OrderInfo createOrderInfo(OrderInfo order) {
      if(order == null) {
         order = new OrderInfo();
      }

      order.setOrder(type);
      order.setSortByCol(sortByCol);
      order.setSortByColValue(sortByValue);
      order.setInterval(interval, option);
      order.setOthers(others ? 1 : 2);
      order.setNamedGroupInfo(info.getXNamedGroupInfo());

      return order;
   }

   private int type = XConstants.SORT_ASC;
   private int sortByCol;
   private int option = XConstants.YEAR_DATE_GROUP;
   private int interval = 1;
   private String sortByValue;
   private NamedGroupInfoModel info = new NamedGroupInfoModel();
   private boolean others = true;
   private List<String> manualOrder;
}
