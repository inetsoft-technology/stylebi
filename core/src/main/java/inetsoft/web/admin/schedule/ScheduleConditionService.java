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
package inetsoft.web.admin.schedule;

import inetsoft.util.dep.XAsset;
import inetsoft.sree.*;
import inetsoft.sree.schedule.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.composer.model.vs.DynamicValueModel;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeConditionModel;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeRangeModel;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class ScheduleConditionService {
   /**
    * Convert schedule condition to a model.
    */
   public ScheduleConditionModel getConditionModel(ScheduleCondition condition, Principal principal) {
      if(condition instanceof TimeCondition) {
         TimeCondition timeCondition = (TimeCondition) condition;
         TimeRangeModel timeRange = null;
         String timeZoneId = timeCondition.getTimeZone().getID();

         if(timeCondition.getTimeRange() != null) {
            TimeRange range = timeCondition.getTimeRange();
            Catalog catalog = Catalog.getCatalog();
            timeRange = TimeRangeModel.builder().from(range, catalog).build();
         }

         return TimeConditionModel.builder()
            .label(condition.toString())
            .dayOfMonth(timeCondition.getDayOfMonth())
            .dayOfWeek(timeCondition.getDayOfWeek())
            .daysOfWeek(timeCondition.getDaysOfWeek())
            .hour(timeCondition.getHour())
            .hourEnd(timeCondition.getHourEnd())
            .interval(timeCondition.getInterval())
            .hourlyInterval(timeCondition.getHourlyInterval())
            .minute(timeCondition.getMinute())
            .minuteEnd(timeCondition.getMinuteEnd())
            .second(timeCondition.getSecond())
            .secondEnd(timeCondition.getSecondEnd())
            .monthsOfYear(timeCondition.getMonthsOfYear())
            .type(timeCondition.getType())
            .weekdayOnly(timeCondition.isWeekdayOnly())
            .weekOfMonth(timeCondition.getWeekOfMonth())
            .date(timeCondition.getDate() == null ? 0L
                     : timeCondition.getDate().getTime())
            .dateEnd(timeCondition.getDate() == null ? 0L
                        : timeCondition.getDate().getTime())
            .timeZone(timeZoneId)
            .timeZoneOffset(timeCondition.getDate() != null ?
                               TimeZone.getDefault().getOffset(timeCondition.getDate().getTime()) :
                               TimeZone.getDefault().getOffset(System.currentTimeMillis()))
            .conditionType("TimeCondition")
            .monthlyDaySelected(timeCondition.getDayOfMonth() != 0)
            .timeRange(timeRange)
            .build();
      }
      else {
         CompletionCondition completionCondition = (CompletionCondition) condition;
         String taskName = completionCondition == null ? null : completionCondition.getTaskName();
         return CompletionConditionModel.builder()
            .label(condition.toString())
            .taskName(Optional.ofNullable(taskName).orElse(null))
            .conditionType("CompletionCondition")
            .build();
      }
   }

   /**
    * Convert a model to a schedule condition.
    */
   public ScheduleCondition getConditionFromModel(ScheduleConditionModel model)
      throws Exception
   {
      ScheduleCondition condition;

      if(model instanceof TimeConditionModel) {
         TimeConditionModel timeConditionModel = (TimeConditionModel) model;
         TimeCondition timeCondition = null;
         Date date = null;
         TimeRange timeRange = null;

         if(timeConditionModel.date() != null) {
            long timestamp = timeConditionModel.date().longValue();
            int offset = Optional.ofNullable(timeConditionModel.timeZoneOffset()).orElse(0);
            int server = TimeZone.getDefault().getOffset(timestamp);
            timestamp += -server - offset;
            date = new Date(timestamp);
         }

         if(timeConditionModel.timeRange() != null) {
            TimeRangeModel range = timeConditionModel.timeRange();
            timeRange = new TimeRange(
               range.name(), range.startTime(), range.endTime(), range.defaultRange());
         }

         switch(timeConditionModel.type()) {
         case TimeCondition.AT:
            timeCondition = TimeCondition.at(date);
            break;
         case TimeCondition.EVERY_HOUR:
            timeCondition = TimeCondition.atHours(
               timeConditionModel.daysOfWeek(),
               Optional.ofNullable(timeConditionModel.hour()).orElse(0),
               Optional.ofNullable(timeConditionModel.minute()).orElse(0),
                  Optional.ofNullable(timeConditionModel.second()).orElse(0));
            timeCondition.setHourEnd(Optional.ofNullable(timeConditionModel.hourEnd()).orElse(0));
            timeCondition.setMinuteEnd(Optional.ofNullable(timeConditionModel.minuteEnd()).orElse(0));
            timeCondition.setSecondEnd(Optional.ofNullable(timeConditionModel.secondEnd()).orElse(0));
            timeCondition.setHourlyInterval(
               Optional.ofNullable(timeConditionModel.hourlyInterval()).orElse((float) 1));
            timeCondition.setInterval(Optional.ofNullable(timeConditionModel.interval()).orElse(1));
            break;
         case TimeCondition.EVERY_DAY:
            timeCondition = TimeCondition.at(
               Optional.ofNullable(timeConditionModel.hour()).orElse(0),
               Optional.ofNullable(timeConditionModel.minute()).orElse(0),
               Optional.ofNullable(timeConditionModel.second()).orElse(0));
            timeCondition.setInterval(
               Optional.ofNullable(timeConditionModel.interval()).orElse(1));
            timeCondition.setWeekdayOnly(timeConditionModel.weekdayOnly());
            break;
         case TimeCondition.EVERY_WEEK:
            timeCondition = TimeCondition.atDaysOfWeek(
               timeConditionModel.daysOfWeek(),
               Optional.ofNullable(timeConditionModel.hour()).orElse(0),
               Optional.ofNullable(timeConditionModel.minute()).orElse(0),
               Optional.ofNullable(timeConditionModel.second()).orElse(0));
            timeCondition.setInterval(
               Optional.ofNullable(timeConditionModel.interval()).orElse(1));
            break;
         case TimeCondition.EVERY_MONTH:
            if(Optional.ofNullable(timeConditionModel.monthlyDaySelected()).orElse(false)) {
               timeCondition = TimeCondition.atDayOfMonth(
                  Optional.ofNullable(timeConditionModel.dayOfMonth()).orElse(0),
                  Optional.ofNullable(timeConditionModel.hour()).orElse(0),
                  Optional.ofNullable(timeConditionModel.minute()).orElse(0),
                  Optional.ofNullable(timeConditionModel.second()).orElse(0));
            }
            else {
               timeCondition = TimeCondition.atWeekOfMonth(
                  Optional.ofNullable(timeConditionModel.weekOfMonth()).orElse(0),
                  Optional.ofNullable(timeConditionModel.dayOfWeek()).orElse(0),
                  Optional.ofNullable(timeConditionModel.hour()).orElse(0),
                  Optional.ofNullable(timeConditionModel.minute()).orElse(0),
                  Optional.ofNullable(timeConditionModel.second()).orElse(0));
            }

            timeCondition.setMonthsOfYear(timeConditionModel.monthsOfYear());
            break;
         }

         if(timeCondition != null) {
            timeCondition.setTimeRange(timeRange);

            if(timeConditionModel.timeZone() != null) {
               timeCondition.setTimeZone(TimeZone.getTimeZone(timeConditionModel.timeZone()));
            }
         }

         condition = timeCondition;
      }
      else {
         CompletionConditionModel completionConditionModel = (CompletionConditionModel) model;
         CompletionCondition completionCondition = new CompletionCondition();
         completionCondition.setTaskName(Tool.byteDecode(completionConditionModel.taskName()));
         condition = completionCondition;
      }

      return condition;
   }

   /**
    * Gets the list of parameters as dialog models.
    */
   public List<AddParameterDialogModel> getParameterModelList(RepletRequest repRequest) {
      if(repRequest == null) {
         return Collections.emptyList();
      }

      Enumeration<?> paramNames = repRequest.getParameterNames();
      List<AddParameterDialogModel> paramModels = new ArrayList<>();

      while(paramNames.hasMoreElements()) {
         String paramName = (String) paramNames.nextElement();
         Object value = repRequest.getParameter(paramName);
         String type = getParameterType(repRequest, paramName, value);
         value = decodeParameter(value, "array".equals(type));
         boolean array = "array".equals(type);
         Object[] vals = null;

         if(value instanceof DynamicParameterValue) {
            value = ((DynamicParameterValue) value).convertModel();

            if(array) {
               vals = (Object[]) ((DynamicValueModel) value).getValue();
            }

            if(DynamicValueModel.VALUE.equals(((DynamicValueModel) value).getType())) {
               ((DynamicValueModel) value).setValue(
                  formatParameterValue(repRequest, type, ((DynamicValueModel) value).getValue(), paramName));
            }
         }
         else {
            vals = array ? (Object[]) value : null;
            value = formatParameterValue(repRequest, type, value, paramName);
         }

         if(array) {
            type = getParameterType(repRequest, paramName, vals.length > 0 ? vals[0] : null);
         }

         AddParameterDialogModel paramModel = AddParameterDialogModel.builder()
            .name(paramName)
            .type(type)
            .array(array)
            .value(value instanceof DynamicValueModel ? (DynamicValueModel) value
               : new DynamicValueModel(value, DynamicValueModel.VALUE, type))
            .build();

         paramModels.add(paramModel);
      }

      return paramModels;
   }

   private Object formatParameterValue(RepletRequest repRequest, String type, Object value,
                                       String paramName)
   {
      if(XSchema.DATE.equals(type)) {
         SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
         value = formatter.format((Date) value);
      }
      else if(XSchema.TIME.equals(type)) {
         SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
         value = formatter.format((Date) value);
      }
      else if(XSchema.TIME_INSTANT.equals(type)) {
         SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
         value = formatter.format((Date) value);
      }
      else if("array".equals(type)) {
         Object[] vals = (Object[]) value;
         type = getParameterType(repRequest, paramName, vals.length > 0 ? vals[0] : null);
         StringBuilder builder = new StringBuilder();

         for(int i = 0; i < vals.length; i++) {
            Object paramValue = vals[i];

            if(XSchema.DATE.equals(type)) {
               SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
               paramValue = formatter.format((Date) paramValue);
            }
            else if(XSchema.TIME.equals(type)) {
               SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
               paramValue = formatter.format((Date) paramValue);
            }
            else if(XSchema.TIME_INSTANT.equals(type)) {
               SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
               paramValue = formatter.format((Date) paramValue);
            }
            else if(XSchema.STRING.equals(type)) {
               paramValue = Tool.encodeCommas(paramValue.toString());
            }

            builder.append(paramValue);

            if(i != vals.length - 1) {
               builder.append(",");
            }
         }

         value = builder.toString();
      }
      else if("string".equals(type) && value == null) {
         value = XAsset.NULL;
      }

      return value;
   }

   public Object decodeParameter(Object value, boolean array) {
      if(value instanceof Object[]) {
         Object[] vals = (Object[]) value;
         Object[] nvals = new Object[vals.length];

         for(int i = 0; vals != null && i < vals.length; i++) {
            nvals[i] = vals[i];

            if(vals[i] instanceof String) {
               nvals[i] = Tool.decodeParameter((String) vals[i]);
            }
         }

         return nvals;
      }

      return value instanceof String ? Tool.decodeParameter((String) value) : value;
   }

   /**
    * Get the parameter type based on the value.
    */
   public String getParameterType(RepletRequest request, String name, Object value) {
      String type = XSchema.STRING;

      if(value instanceof DynamicParameterValue) {
         DynamicParameterValue parameterValue = (DynamicParameterValue) value;

         if(!DynamicValueModel.VALUE.equals(parameterValue.getType())) {
            return parameterValue.getDataType();
         }

         value = parameterValue.getValue();
      }


      if(value instanceof Boolean) {
         type = XSchema.BOOLEAN;
      }
      else if(value instanceof Integer) {
         type = XSchema.INTEGER;
      }
      else if(value instanceof Double) {
         type = XSchema.DOUBLE;
      }
      else if(value instanceof java.sql.Time) {
         type = XSchema.TIME;
      }
      else if(value instanceof Date) {
         GregorianCalendar cal = new GregorianCalendar();
         cal.setTime((Date) value);

         if(cal.get(Calendar.YEAR) == 1970 && cal.get(Calendar.MONTH) == 0 &&
            cal.get(Calendar.DAY_OF_MONTH) == 1) {
            type = XSchema.TIME;
         }
         else if(request.containsDateTime(name) || cal.get(Calendar.HOUR_OF_DAY) != 0 ||
                 cal.get(Calendar.MINUTE) != 0 || cal.get(Calendar.SECOND) != 0)
         {
            type = XSchema.TIME_INSTANT;
         }
         else {
            type = XSchema.DATE;
         }
      }
      else if(value instanceof Object[]) {
         type = "array";
      }

      return type;
   }

   /**
    * Add parameter to the replet request.
    */
   public void setParameter(AddParameterDialogModel model, RepletRequest repletRequest) {
      DynamicValueModel value = model.value();
      String name = model.name();
      String type = model.type();
      DynamicParameterValue parameterValue = value.convertParameterValue();

      if(model.array()) {
         parameterValue.setValue(getParamValueAsArray(type, value.getValue().toString()));
      }
      else if(DynamicValueModel.VALUE.equals(value.getType())) {
         parameterValue.setValue(getParamValueAsType(type, value));
      }

      repletRequest.setParameter(name, parameterValue);

      if(XSchema.TIME_INSTANT.equals(type)) {
         repletRequest.addDateTime(name);
      }
   }


   /**
    * Get the parameter value as the type provided (most useful for date types).
    */
   public Object getParamValueAsType(String type, DynamicValueModel initialValue) {
      Object value = initialValue.getValue();
      String stringValue = "" + value;

      try {
         if(XSchema.DATE.equals(type)) {
            SimpleDateFormat formatter =
               new SimpleDateFormat("yyyy-MM-dd");
            value = new java.sql.Date(formatter.parse((String) value).getTime());
         }
         else if(XSchema.TIME.equals(type)) {
            SimpleDateFormat formatter =
               new SimpleDateFormat("HH:mm:ss");
            value = formatter.parse((String) value);
         }
         else if(XSchema.TIME_INSTANT.equals(type)) {
            String dateTime = (String) value;
            dateTime = dateTime.replace("T", " ");
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            value = formatter.parse(dateTime);
         }
         else if(XSchema.DOUBLE.equals(type)) {
            value = Double.parseDouble(stringValue);
         }
         else if(XSchema.INTEGER.equals(type)) {
            value = Integer.parseInt(stringValue);
         }
         else if(XSchema.BOOLEAN.equals(type)) {
            value = Boolean.parseBoolean(stringValue);
         }
      }
      catch(Exception e) {
         value = initialValue.getValue();
      }

      return value;
   }

   public Object[] getParamValueAsArray(String type, String initialValue) {
      initialValue = initialValue.replace("\\,", "\n");
      String[] initialArray = initialValue.split(",");
      Object[] valuesArray = new Object[initialArray.length];
      SimpleDateFormat formatter;

      for(int i = 0; i < initialArray.length; i++) {
         try {
            switch(type) {
            case XSchema.BYTE:
               valuesArray[i] = Byte.parseByte(initialArray[i]);
               break;
            case XSchema.SHORT:
               valuesArray[i] = Short.parseShort(initialArray[i]);
               break;
            case XSchema.LONG:
               valuesArray[i] = Long.parseLong(initialArray[i]);
               break;
            case XSchema.INTEGER:
               valuesArray[i] = Integer.parseInt(initialArray[i]);
               break;
            case XSchema.FLOAT:
               valuesArray[i] = Float.parseFloat(initialArray[i]);
               break;
            case XSchema.DOUBLE:
               valuesArray[i] = Double.parseDouble(initialArray[i]);
               break;
            case XSchema.BOOLEAN:
               valuesArray[i] = Boolean.parseBoolean(initialArray[i]);
               break;
            case XSchema.DATE:
               formatter = new SimpleDateFormat("yyyy-MM-dd");
               valuesArray[i] = formatter.parse(initialArray[i]);
               break;
            case XSchema.TIME:
               formatter = new SimpleDateFormat("HH:mm:ss");
               valuesArray[i] = formatter.parse(initialArray[i]);
               break;
            case XSchema.TIME_INSTANT:
               String dateTime = initialArray[i].replace("T", " ");
               formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
               valuesArray[i] = formatter.parse(dateTime);
               break;
            default:
               valuesArray[i] = initialArray[i].replace("\n", ",");
            }
         }
         catch(Exception e) {
            valuesArray[i] = initialArray[i].replace("\n", ",");
         }
      }

      return valuesArray;
   }
}
