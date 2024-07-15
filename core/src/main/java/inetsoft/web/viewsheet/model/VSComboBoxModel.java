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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ComboBoxVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.ComboBoxVSAssemblyInfo;
import inetsoft.util.Catalog;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.text.*;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSComboBoxModel extends ListInputModel<ComboBoxVSAssembly> {
   public VSComboBoxModel(ComboBoxVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      ComboBoxVSAssemblyInfo assemblyInfo = (ComboBoxVSAssemblyInfo) assembly.getVSAssemblyInfo();
      Catalog catalog = Catalog.getCatalog(rvs.getUser(), Catalog.REPORT);
      selectedLabel = catalog.getString(assemblyInfo.getSelectedLabel());
      selectedObject = assemblyInfo.getSelectedObject();
      rowCount = assemblyInfo.getRowCount();
      editable = assemblyInfo.isTextEditable();
      dataType = assemblyInfo.getDataType();
      calendar = assemblyInfo.isCalendar() &&
         (XSchema.DATE.equals(dataType) ||
         XSchema.TIME.equals(dataType) ||
         XSchema.TIME_INSTANT.equals(dataType));
      serverTZ = assemblyInfo.isServerTimeZone() ||
         // date has no time component so it should be treated as server side
         // otherwise the difference in timezone may cause the date to shift
         XSchema.DATE.equals(dataType);
      serverTZID = TimeZone.getDefault().getID();
      minDate = assemblyInfo.getMinDate();
      maxDate = assemblyInfo.getMaxDate();
      selectedObject = assemblyInfo.getSelectedObject();

      VSCompositeFormat fmt = assemblyInfo.getFormat();

      if(selectedLabel == null && selectedObject != null && fmt != null) {
         Format fmt2 = TableFormat.getFormat(fmt.getFormat(),
                                             fmt.getFormatExtent(), Locale.getDefault());
         selectedLabel = fmt2 != null ? fmt2.format(selectedObject) : selectedLabel;
      }

      if(calendar && selectedObject instanceof Date) {
         selectedObject = ((Date) selectedObject).getTime();
      }
      // used entered value, don't convert to full date in serializer
      else if(editable && selectedObject instanceof Date) {
         selectedObject = selectedObject.toString();
      }

      if(fmt.getFormat() != null && "DateFormat".equals(fmt.getFormat())) {
         String extent = fmt.getFormatExtent();

         if(extent == null) {
            dateFormat = "";
         }
         else if("FULL".equals(extent)) {
            SimpleDateFormat fullFormat = (SimpleDateFormat) DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault());
            dateFormat = fullFormat.toPattern();
         }
         else if("LONG".equals(extent)) {
            SimpleDateFormat longFormat = (SimpleDateFormat) DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault());
            dateFormat = longFormat.toPattern();
         }
         else if("MEDIUM".equals(extent)) {
            SimpleDateFormat mediumFormat = (SimpleDateFormat) DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
            dateFormat = mediumFormat.toPattern();
         }
         else if("SHORT".equals(extent)) {
            SimpleDateFormat shortFormat = (SimpleDateFormat) DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault());
            dateFormat = shortFormat.toPattern();
         }
         else {
            dateFormat = fmt.getFormatExtentValue();
         }
      }
      else {
         dateFormat = "";
      }
   }

   public String getSelectedLabel() {
      return selectedLabel;
   }

   public Object getSelectedObject() {
      return selectedObject;
   }

   public int getRowCount() {
      return rowCount;
   }

   public boolean isEditable() {
      return editable;
   }

   public String getDataType() {
      return dataType;
   }

   public boolean isCalendar() {
      return calendar;
   }

   public String getServerTZID() {
      return serverTZID;
   }

   public boolean isServerTZ() {
      return serverTZ;
   }

   public Timestamp getMinDate() {
      return minDate;
   }

   public Timestamp getMaxDate() {
      return maxDate;
   }

   public String getDateFormat() {
      return dateFormat;
   }

   private String selectedLabel;
   private Object selectedObject;
   private int rowCount;
   private boolean editable;
   private String dataType;
   private boolean calendar;
   private boolean serverTZ;
   private String serverTZID;
   private Timestamp minDate;
   private Timestamp maxDate;
   private String dateFormat;

   @Component
   public static final class VSComboBoxModelFactory
      extends VSObjectModelFactory<ComboBoxVSAssembly, VSComboBoxModel>
   {
      public VSComboBoxModelFactory() {
         super(ComboBoxVSAssembly.class);
      }

      @Override
      public VSComboBoxModel createModel(ComboBoxVSAssembly assembly, RuntimeViewsheet rvs) {
         return new VSComboBoxModel(assembly, rvs);
      }
   }
}
