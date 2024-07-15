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

import inetsoft.uql.XCondition;

public class TopNModel {
   /**
    * Create a default TopNModel.
    */
   public TopNModel() {
   }

   /**
    * Create a TopNModel according to topninfo.
    */
   public TopNModel(inetsoft.report.internal.binding.TopNInfo tinfo) {
      super();

      if(tinfo == null) {
         return;
      }

      setTopn(tinfo.getTopN());

      if(tinfo.getTopN() <= 0) {
         setType(0);
         setTopn(3);
      }
      else if(tinfo.isTopNReverse()) {
         setType(XCondition.BOTTOM_N);
      }
      else {
         setType(XCondition.TOP_N);
      }

      setSumCol(tinfo.getTopNSummaryCol());
      setOthers(tinfo.isOthers());
   }

   /**
    * Get topn type.
    * @return the topn type.
    */
   public int getType() {
      return type;
   }

   /**
    * Set topn type.
    * @param type the topn type.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Get topn value.
    * @return the topn value.
    */
   public int getTopn() {
      return topn;
   }

   /**
    * Set topn value.
    * @param topn the topn value.
    */
   public void setTopn(int topn) {
      this.topn = topn;
   }

   /**
    * Get sum col index.
    * @return the sum col index.
    */
   public int getSumCol() {
      return sumCol;
   }

   /**
    * Set sum col index.
    * @param sum sum col index.
    */
   public void setSumCol(int sum) {
      this.sumCol = sum;
   }

   /**
    * Get sum col name.
    * @return the sum col name.
    */
   public String getSumColValue() {
      return sumColValue;
   }

   /**
    * Set sum col value.
    * @param sum sum col value.
    */
   public void setSumColValue(String sum) {
      this.sumColValue = sum;
   }

   /**
    * Get is group others or not.
    * @return is group others or not.
    */
   public boolean getOthers() {
      return others;
   }

   /**
    * Set is group others.
    * @param other is group others.
    */
   public void setOthers(boolean other) {
      this.others = other;
   }

   /**
    * Set information to topn info.
    * @param topn the topninfo.
    */
   public void setTopNInfo(inetsoft.report.internal.binding.TopNInfo topn) {
      topn.setTopN(getTopn());

      if(getType() == XCondition.NONE) {
         topn.setTopN(0);
      }
      else if(getType() == XCondition.TOP_N) {
         topn.setTopNReverse(false);
      }
      else if(getType() == XCondition.BOTTOM_N) {
         topn.setTopNReverse(true);
      }

      topn.setTopNSummaryCol(getSumCol());
      topn.setOthers(getOthers());
   }

   private int type = 0;
   private int topn = 3;
   private int sumCol = -1;
   private String sumColValue;
   private boolean others = false;
}
