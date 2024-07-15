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
package inetsoft.report.composition.execution;

import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tuple;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class BoundTableHelper {
   public BoundTableHelper(BoundTableAssembly table, Principal user) {
      this.table = table;
      SourceInfo source = table.getSourceInfo();
      XDataModel model = null;

      try {
         XRepository repository = XFactory.getRepository();
         model = repository.getDataModel(source.getPrefix());
      }
      catch(RemoteException ex) {
         if(model == null) {
            throw new RuntimeException(Catalog.getCatalog().getString(
               "common.dataModelNotFound") + ": "
               + source.getPrefix());
         }
      }

      this.lmodel = model.getLogicalModel(source.getSource(), user);

      if(this.lmodel == null) {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "common.logicalModelNotFound",
            source.getSource(), source.getPrefix()));
      }
   }

   /**
    * Get the contained attributes from an attribute.
    * @param attr the specified attribute.
    * @param vonly <tt>true</tt> if only valid attributes should be returned,
    * <tt>false</tt> otherwise.
    * @return the contained attributes.
    */
   protected List<AttributeRef> getContainedAttributes(DataRef attr, boolean vonly) {
      ColumnRef column = attr instanceof ColumnRef ? (ColumnRef) attr : null;
      attr = getBaseAttribute(attr);
      List<AttributeRef> list = null;

      if(attr.isExpression()) {
         String expr = attr instanceof ExpressionRef ? ((ExpressionRef) attr).getExpression() : null;

         if(expr != null) {
            list = expr2Attrs.get(expr);
         }

         if(list == null) {
            list = getExprAttributes(attr, vonly, column);
         }

         if(expr != null) {
            expr2Attrs.put(expr, list);
         }
      }
      else {
         list = new ArrayList<>();
         list.add((AttributeRef) attr);
      }

      return list;
   }

   /**
    * Get the base attibute of an attribute.
    * @param attr the specified attribute.
    * @return the base attibute of the attibute.
    */
   protected DataRef getBaseAttribute(DataRef attr) {
      return AssetUtil.getBaseAttribute(attr);
   }

   private List<AttributeRef> getExprAttributes(DataRef attr, boolean vonly, ColumnRef column) {
      List<AttributeRef> list = new ArrayList<>();
      Enumeration iter = column != null ? column.getExpAttributes() : attr.getAttributes();

      while(iter.hasMoreElements()) {
         DataRef ref = (DataRef) iter.nextElement();

         if(ref instanceof ExpressionRef) {
            list.addAll(getContainedAttributes(ref, vonly));
            continue;
         }

         AttributeRef attr2 = (AttributeRef) ref;

         if(attr2 == null) {
            continue;
         }

         ref = convertAttributeInExpression(attr2, attr);

         if(ref instanceof ExpressionRef && ref != attr) {
            list.addAll(getContainedAttributes(ref, vonly));
            continue;
         }

         if(!(ref instanceof AttributeRef) && vonly) {
            continue;
         }

         attr2 = ref instanceof AttributeRef ? (AttributeRef) ref : null;

         if(attr2 != null) {
            list.add(attr2);
         }
      }

      return list;
   }

   /**
    * Convert a string reference to a column to the DataRef. The string could be
    * partial name or alias.
    * @param attr the field referenced in the expression.
    * @param src the expression data ref to be converted.
    * @return the converted attribute.
    */
   protected DataRef convertAttributeInExpression(DataRef attr, DataRef src) {
      Tuple key = Tuple.createIdentityTuple(attr, src);
      Object rc = exprAttrs.get(key);

      // optimization
      if(rc != null) {
         return rc instanceof DataRef ? (DataRef) rc : null;
      }

      DataRef ref = convertAttributeInExpression0(attr, src);
      exprAttrs.put(key, ref != null ? ref : "");
      return ref;
   }

   private DataRef convertAttributeInExpression0(DataRef attr, DataRef src) {
      ColumnSelection columns = getTable().getColumnSelection();

      // @by larryl, the following match sequence seems a little reversed from
      // the logical order. The idea is that the logic matches what a user sees
      // on the GUI as much as possible. If an alias is defined, the alias is
      // used as the column header and it is the most likely (logical) name to
      // be used by a user. If alias is not defined, the attribute part is used
      // and will be seen by users as column header. Fully qualified name is
      // nevery displayed as column header, though it's most precise. For
      // example, if two columns Query1.quantity and Query2.quantity both
      // exists in a join table, the column headers will be quantity and
      // quantity_1. If they are used in expression, we find quantity_1 first
      // base on alias. Finding quantity is a little trickier. If we check
      // for fully qualified name, it will match either Query1 or Query2,
      // depending on the order (random). The middle loop eliminates that
      // problem.

      // check for alias first since the aliases are guaranteed to be
      // unique while the column ref may not be
      int size = columns.getAttributeCount();
      List<DataRef> refs = columns.stream().collect(Collectors.toList());

      for(DataRef ref : refs) {
         ColumnRef column = (ColumnRef) ref;

         if(attr.getName().equals(column.getAlias())) {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
      }

      // second we check for column header match where alias is not defined
      for(DataRef ref : refs) {
         ColumnRef column = (ColumnRef) ref;

         if(attr.getEntity() == null && column.getAlias() == null &&
            attr.getAttribute().equals(column.getAttribute()))
         {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
      }

      // next we check for fully qualified name
      for(DataRef ref : refs) {
         ColumnRef column = (ColumnRef) ref;

         if(column.equals(attr)) {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
         else if(attr.getEntity() == null &&
            attr.getAttribute().equals(column.getAttribute()))
         {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
         // a.b -> null."a.b"
         else if(column.getEntity() == null &&
            column.getName().equals(attr.getName()))
         {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
         // a.b -> table_O.a.b
         else if(attr.getName().equals(column.getAttribute())) {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
      }

      // check alias by ignoring case
      for(int i = 0; i < size; i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(attr.getName().equalsIgnoreCase(column.getAlias())) {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
      }

      // check attribute by ignoring case
      for(DataRef ref : refs) {
         ColumnRef column = (ColumnRef) ref;

         if(attr.getEntity() == null && column.getAlias() == null &&
            attr.getAttribute().equalsIgnoreCase(column.getAttribute()))
         {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
      }

      // check attribute by ignoring case and ignoring outer entity. Embedded
      // ws's table assembly is renamed so the entity would not match
      for(DataRef ref : refs) {
         ColumnRef column = (ColumnRef) ref;
         String entity = column.getEntity();

         if(entity != null && entity.startsWith("OUTER") &&
            attr.getAttribute().equalsIgnoreCase(column.getAttribute()))
         {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
      }

      return null;
   }

   public TableAssembly getTable() {
      return table;
   }

   private BoundTableAssembly table;
   private Map<Tuple, Object> exprAttrs = new Object2ObjectOpenHashMap<>();
   private Map<String, List<AttributeRef>> expr2Attrs = new Object2ObjectOpenHashMap<>();
   protected XLogicalModel lmodel; // logical model
   public static String LOGICAL_BOUND_COPY = "logical_bound_copy";
   private static final Logger LOG = LoggerFactory.getLogger(BoundTableHelper.class);
}
