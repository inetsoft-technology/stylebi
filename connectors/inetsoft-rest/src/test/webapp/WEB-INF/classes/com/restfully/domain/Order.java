/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package com.restfully.domain;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "order")
@XmlType(propOrder = {"total", "date", "cancelled", "customer", "lineItems"})
public class Order
{
   protected int id;
   protected boolean cancelled;
   protected List<LineItem> lineItems = new ArrayList<LineItem>();
   protected double total;
   protected String date;
   protected Customer customer;

   public Order() {
   }
   
   public Order(int id, double total, String date) {
      this.id = id;
      this.total = total;
      this.date = date;
   }

   @XmlAttribute
   public int getId()
   {
      return id;
   }

   public void setId(int id)
   {
      this.id = id;
   }

   public boolean isCancelled()
   {
      return cancelled;
   }

   public void setCancelled(boolean cancelled)
   {
      this.cancelled = cancelled;
   }

   @XmlElementWrapper(name = "line-items")
   public List<LineItem> getLineItems()
   {
      return lineItems;
   }

   public void setLineItems(List<LineItem> lineItems)
   {
      this.lineItems = lineItems;
   }

   public String getDate()
   {
      return date;
   }

   public void setDate(String date)
   {
      this.date = date;
   }

   public double getTotal()
   {
      return total;
   }

   public void setTotal(double total)
   {
      this.total = total;
   }

   @XmlElementRef
   public Customer getCustomer()
   {
      return customer;
   }

   public void setCustomer(Customer customer)
   {
      this.customer = customer;
   }

   @Override
   public String toString()
   {
      return "Order{" +
              "id=" + id +
              ", cancelled=" + cancelled +
              ", lineItems=" + lineItems +
              ", total=" + total +
              ", date='" + date + '\'' +
              ", customer=" + customer +
              '}';
   }
}
