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
package inetsoft.sree.internal;

import inetsoft.report.composition.execution.ReportWorksheetProcessor;
import inetsoft.report.internal.binding.*;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.graph.GeoRef;
import inetsoft.util.*;
import inetsoft.web.composer.model.BrowseDataModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * SreeAssistant provides all non-presentation logic to support the
 * services.
 *
 * @version 6.0 10/23/2003
 * @author InetSoft Technology Corp
 */
public class SreeAssistant {
   /**
    * Create an analytic assistant.
    */
   protected SreeAssistant() {
      try {
         engine = SUtil.getRepletRepository();
      }
      catch(Throwable e) {
         LOG.error("REpository initialization error, using local engine", e);
         engine = new AnalyticEngine();
         ((AnalyticEngine) engine).init();
      }
   }

   /**
    * Return an handle to AnalyticRepository.
    */
   public AnalyticRepository getAnalyticRepository() {
      return engine;
   }

   public ItemList getAvailableValues(final XSourceInfo source, Field field,
                                      Principal principal, String type,
                                      VariableTable vars)
      throws Exception
   {
      return getAvailableValues(source, field, principal, type, vars, false);
   }

   /**
    * Get all available values of specfied field.
    */
   public ItemList getAvailableValues(final XSourceInfo source, Field field,
                                      Principal principal, String type,
                                      VariableTable vars, boolean isGeo)
      throws Exception
   {
      return getAvailableValues(source, field, principal, type, vars, isGeo, true);
   }

   /**
    * Get all available values of specfied field.
    */
   public ItemList getAvailableValues(final XSourceInfo source, Field field,
                                      Principal principal, String type,
                                      VariableTable vars, boolean isGeo, boolean ignoreEmpty)
      throws Exception
   {
      Format fmt = null;

      if(type.equals(XSchema.TIME)) {
         fmt = timeFmt;
      }
      else if(type.equals(XSchema.DATE)) {
         fmt = dateFmt;
      }
      else if(type.equals(XSchema.TIME_INSTANT)) {
         fmt = timeInstantFmt;
      }

      return getAvailableValues(source, field, principal, type, vars, isGeo, ignoreEmpty,
                                fmt, fmt);
   }

   /**
    * Get all available values of specfied field.
    */
   public ItemList getAvailableValues(final XSourceInfo source, Field field,
                                      Principal principal, String type,
                                      VariableTable vars, boolean isGeo, boolean ignoreEmpty,
                                      Format valueFmt, Format labelFmt)
      throws Exception {

      ColumnCache cache = ColumnCache.getColumnCache();
      BrowseDataModel columnData = null;
      SourceAttr sattr = new SourceAttr(source.getType(), source.getPrefix(),
         source.getSource());

      if(field instanceof BaseField) {
         BaseField bfield = (BaseField) field;

         if(bfield.getSource() != null) {
            sattr = new SourceAttr(bfield.getSourceType(),
               bfield.getSourcePrefix(), bfield.getSource());
         }
      }

      if(sattr.getType() == SourceAttr.MODEL) {
         String query = sattr.getSource() + "::" + sattr.getPrefix();
         String column = field.getEntity() + "::" + field.getAttribute();
         columnData = cache.getColumnData((XQueryRepository) null, query, column, principal, vars);
      }
      else if(sattr.getType() == SourceAttr.ASSET && engine instanceof AnalyticEngine) {
         WorksheetProcessor wsp = new ReportWorksheetProcessor();
         AssetEntry entry = AssetEntry.createAssetEntry(sattr.getSource());
         entry.setProperty(XQuery.HINT_MAX_ROWS, "5000");
         String colName = field.getName();

         //fix Geo column
         if(isGeo) {
            colName = GeoRef.getBaseName(colName);
         }

         columnData = cache.getColumnData(wsp, entry, colName, principal, vars, ignoreEmpty);
      }
      else {
         columnData = cache.getColumnData((XQueryRepository) null,
            sattr.getSource(), field.getName(), principal, vars);
      }

      if(columnData != null) {
         final Object[] dataValues;
         final Object[] labelValues;

         // value - description
         if(columnData.existLabels()) {
            dataValues = columnData.values();
            labelValues = columnData.labels();
         }
         else {
            dataValues = columnData.values();
            labelValues = dataValues;
         }

         ItemList values = new ItemList("Values");
         ItemList labels = new ItemList("Labels");

         if(field instanceof GroupField &&
            // date range or date part.
            (XSchema.isDateType(type) || XSchema.INTEGER.equals(type)))
         {
            OrderInfo info = ((GroupField) field).getOrderInfo();

            // apply date grouping
            if(info.getOption() != 0) {
               List vals = new ArrayList();

               for(Object dataValue : dataValues) {
                  if(dataValue instanceof Date) {
                     vals.add(DateRangeRef.getData(info.getOption(), (Date) dataValue));
                  }
                  else {
                     vals.add(dataValue);
                  }
               }

               try {
                  Collections.sort(vals, new inetsoft.report.filter.DefaultComparer());
               }
               catch(Exception e) {
                  return new ItemList("Values");
               }

               for(Object val : vals) {
                  values.addItem(
                     valueFmt != null && val instanceof Date ? valueFmt.format(val) : val);
                  labels.addItem(
                     labelFmt != null && val instanceof Date ? labelFmt.format(val) : val);
               }
            }
         }

         if(values.getSize() == 0) {
            for(int i = 0; i < dataValues.length; i++) {
               values.addItem(formatValue(valueFmt, dataValues[i]));
               labels.addItem(labelFmt != null && (labelValues[i] instanceof Date) ?
                  labelFmt.format(labelValues[i]) : labelValues[i]);
            }
         }

         ItemList result = new ItemList("Values");
         result.addItem(values);
         result.addItem(labels);

         return result;
      }

      return new ItemList("Values");
   }

   private Object formatValue(Format fmt, Object value) {
      if(fmt == null) {
         return value;
      }

      try {
         return fmt.format(value);
      }
      catch(Exception e) {
         return value;
      }
   }

   /**
    * Get roles of a principal.
    *
    * @param principal the specified principal
    * @return the roles of the principal
    */
   protected IdentityID[] getRoles(SRPrincipal principal) {
      IdentityID[] roles = null;

      if(!SreeEnv.getProperty("security.provider").equals("")) {
         if(principal != null) {
            roles = XUtil.getUserRoles(principal);
         }
      }

      return roles;
   }

   protected static final SimpleDateFormat dateFmt =
      Tool.createDateFormat("{'d' ''yyyy-MM-dd''}");
   protected static final SimpleDateFormat timeFmt =
      Tool.createDateFormat("{'t' ''HH:mm:ss''}");
   protected static final SimpleDateFormat timeInstantFmt =
      Tool.createDateFormat("{'ts' ''yyyy-MM-dd HH:mm:ss''}");
   protected AnalyticRepository engine;

   private static final Logger LOG =
      LoggerFactory.getLogger(SreeAssistant.class);
}
