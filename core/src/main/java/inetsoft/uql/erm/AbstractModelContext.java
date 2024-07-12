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
package inetsoft.uql.erm;

import inetsoft.uql.asset.internal.CompositeColumnHelper;

import java.util.*;

/**
 * A skeletal implementation of an model trap context.
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public abstract class AbstractModelContext {
   /**
    * Get the name of all database tables referenced by this data attributes.
    */
   protected String[] getTables(DataRef[] attributes) {
      HashSet<String> tables = new HashSet<>();

      for(DataRef ref : attributes) {
         for(String table : getTables(ref)) {
            tables.add(table);
         }
      }

      String[] res = new String[tables.size()];
      return tables.toArray(res);
   }

   /**
    * Get the name of all database tables referenced by this attribute.
    */
   protected String[] getTables(DataRef attribute) {
      DataRef ref = DataRefWrapper.getBaseDataRef(attribute);

      if(ref instanceof ExpressionRef) {
         ExpressionRef eref = (ExpressionRef) ref;
         Enumeration attrs = eref.getAttributes();
         List<DataRef> attrList = new ArrayList<>();

         while(attrs.hasMoreElements()) {
            attrList.add((DataRef) attrs.nextElement());
            return getTables(attrList.toArray(new DataRef[attrList.size()]));
         }
      }

      XAttribute xattribute = getAttribute(attribute);

      if(xattribute != null) {
         return xattribute.getTables();
      }

      return new String[0];
   }

   /**
    * Get entity.
    */
   private XEntity getEntity(DataRef ref) {
      if(ref == null || lm == null) {
         return null;
      }

      String entity = ref.getEntity();
      String attribute = ref.getAttribute();
      int idx = attribute.indexOf(":");

      if(idx >= 0) {
         entity = attribute.substring(0, idx);
      }

      return lm.getEntity(entity);
   }

   /**
    * Get attribute.
    */
   protected XAttribute getAttribute(DataRef ref) {
      XEntity entity = getEntity(ref);

      if(entity == null) {
         return null;
      }

      String attribute = ref.getAttribute();
      int idx = attribute.indexOf(":");

      if(idx >= 0) {
         attribute = attribute.substring(idx + 1, attribute.length());
      }

      return entity.getAttribute(attribute);
   }

   /**
    * Add attributes.
    */
   protected void addAttributes(HashSet<DataRef> set, DataRef ref) {
      if(ref == null) {
         return;
      }

      if(helper != null) {
         for(DataRef attribute : helper.getAttributes(ref)) {
            set.add(attribute);
         }
      }
      else {
         set.add(ref);
      }
   }

   /**
    * Find expression attribute which is aggregate expression.
    */
   protected void fixAggregates(HashSet<DataRef> all, HashSet<DataRef> aggs) {
      for(DataRef ref : all) {
         if(aggs.contains(ref)) {
            continue;
         }

         XAttribute xattribute = getAttribute(ref);

         if(xattribute instanceof ExpressionAttribute &&
            ((ExpressionAttribute) xattribute).isAggregateExpression())
         {
            aggs.add(ref);
         }
      }
   }

   protected XLogicalModel lm;
   protected CompositeColumnHelper helper;
}
