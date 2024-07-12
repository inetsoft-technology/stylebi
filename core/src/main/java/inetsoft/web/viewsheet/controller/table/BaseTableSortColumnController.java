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
package inetsoft.web.viewsheet.controller.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.command.UpdateSortInfoCommand;
import inetsoft.web.viewsheet.event.table.SortColumnEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.sql.Types;
import java.util.*;

/**
 * Controller for sorting columns on tables and crosstabs
 */
@Controller
public class BaseTableSortColumnController extends BaseTableController<SortColumnEvent> {
   @Autowired
   public BaseTableSortColumnController(RuntimeViewsheetRef runtimeViewsheetRef,
                                        PlaceholderService placeholderService,
                                        ViewsheetService viewsheetService,
                                        VSBindingService bindingFactory)
   {
      super(runtimeViewsheetRef, placeholderService, viewsheetService);
      this.bindingFactory = bindingFactory;
      this.viewsheetService = viewsheetService;
   }

   private static boolean isSortableSqlType(int sqltype) {
      switch(sqltype) {
         case Types.BIT:
         case Types.TINYINT:
         case Types.SMALLINT:
         case Types.INTEGER:
         case Types.BIGINT:
         case Types.FLOAT:
         case Types.DOUBLE:
         case Types.REAL:
         case Types.NUMERIC:
         case Types.DECIMAL:
         case Types.CHAR:
         case Types.VARCHAR:
         case Types.NVARCHAR:
         case Types.LONGVARCHAR:
         case Types.DATE:
         case Types.TIME:
         case Types.TIMESTAMP:
            return true;
         default:
            return false;
      }
   }

   @Override
   @Undoable
   @LoadingMask
   @MessageMapping("/table/sort-column")
   public void eventHandler(@Payload SortColumnEvent event, Principal principal,
                            CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeViewsheetRef.getRuntimeId(), principal);
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();

      box.lockRead();

      try {
         tableSortColumn(rvs, box, event, principal, dispatcher, linkUri);
      }
      catch(Exception e) {
         MessageCommand command = new MessageCommand();
         command.setMessage("Failed to process sort filter");
         command.setType(MessageCommand.Type.ERROR);
         dispatcher.sendCommand(command);
      }
      finally {
         box.unlockRead();
      }
   }

   private void tableSortColumn(RuntimeViewsheet rvs, ViewsheetSandbox box,
                                SortColumnEvent event, Principal principal,
                                CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      String name = event.getAssemblyName();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly(name);
      //detail column contains hidden column.
      ColumnSelection columns = table.getColumnSelection();

      if(!event.detail()) {
         TableVSAssemblyInfo tableInfo  = (TableVSAssemblyInfo) table.getInfo();
         columns = tableInfo.getVisibleColumns();
      }

      DataRef column = columns.getAttribute(event.getCol());

      // sorting is done before alias is applied, so the setting should be on
      // the base data ref
      if(column instanceof ColumnRef) {
         DataRef oref = column;
         column = ((ColumnRef) column).getDataRef();

         // fix bug1375259843588, make sure ref is ColumnRef
         if(!(column instanceof ColumnRef)) {
            column = new ColumnRef(column);
            ((ColumnRef) column).setDataType(oref.getDataType());
         }
      }

      if(!isSortableColumn(column)) {
         return;
      }

      SortInfo sinfo = table.getSortInfo();
      List<SortRef> sorts = Arrays.asList(sinfo.getSorts());
      int order = StyleConstants.SORT_ASC;
      int position = -1;

      if(sinfo != null) {
         SortRef ref = sinfo.getSort(column);
         position = sinfo.containsSort(ref) ? sorts.indexOf(ref) : sinfo.getSortCount();

         if(ref != null) {
            if(ref.getOrder() == StyleConstants.SORT_ASC) {
               order = StyleConstants.SORT_DESC;
            }
            else if(ref.getOrder() == StyleConstants.SORT_DESC) {
               order = StyleConstants.SORT_NONE;
            }
         }
      }

      if(sinfo == null || !event.multi()) {
         sinfo = new SortInfo();
      }

      SortRef ref = new SortRef(column);

      if(order != StyleConstants.SORT_NONE) {
         ref.setOrder(order);
         ref.setPosition(position);
         sinfo.addSort(ref);
      }
      else {
         sinfo.removeSort(ref);
      }

      table.setSortInfo(sinfo);

      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) table.getVSAssemblyInfo();

      if(box.isRuntime() && info instanceof TableVSAssemblyInfo &&
         ((TableVSAssemblyInfo) info).isForm())
      {
         box.sortFormTableLens(name, ref);

         int columnCount = info.getColumnCount();
         int[] sortColumns = new int[columnCount];
         int[] sortPositionColumns = new int[columnCount];
         ColumnSelection selection = ((TableVSAssemblyInfo) info).getColumnSelection();

         for(int i = 0; i < columnCount; i++) {
            DataRef dataRef = selection.getAttribute(i);

            if(dataRef instanceof ColumnRef) {
               DataRef dataRef2 = ((ColumnRef) dataRef).getDataRef();
               SortRef sortRef = sinfo.getSort(dataRef2);
               sortColumns[i] = sortRef == null ? XConstants.SORT_NONE : sortRef.getOrder();
               sortPositionColumns[i] = sortRef == null ? -1 : sorts.indexOf(ref);
            }
            else {
               sortColumns[i] = XConstants.SORT_NONE;
               sortPositionColumns[i] = -1;
            }
         }

         dispatcher.sendCommand(
            name, UpdateSortInfoCommand.builder().sortOrders(sortColumns)
            .sortPositions(sortPositionColumns).build());
         this.placeholderService.loadTableLens(rvs, name, null, dispatcher);
      }
      else {
         this.placeholderService.execute(rvs, name, linkUri, VSAssembly.INPUT_DATA_CHANGED,
            dispatcher);
         this.placeholderService.layoutViewsheet(rvs, rvs.getID(), linkUri, dispatcher);
         BindingModel binding = bindingFactory.createModel(table);
         SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
         dispatcher.sendCommand(bcommand);
      }
   }

   private boolean isSortableColumn(DataRef column) {
      if(column == null) {
         return false;
      }

      DataRef ref = DataRefWrapper.getBaseDataRef(column);

      if(ref instanceof AttributeRef) {
         AttributeRef aref = (AttributeRef) ref;
         return isSortableSqlType(aref.getSqlType());
      }

      return true;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/crosstab/sort-column")
   public void sortColumnAction(@Payload SortColumnEvent event, Principal principal,
                                CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeViewsheetRef.getRuntimeId(), principal);
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();

      box.lockRead();

      try {
         crosstabSortColumn(rvs, box, event, principal, dispatcher, linkUri);
      }
      finally {
         box.unlockRead();
      }
   }

   private void crosstabSortColumn(RuntimeViewsheet rvs, ViewsheetSandbox box,
                                   SortColumnEvent event, Principal principal,
                                   CommandDispatcher dispatcher, String linkUri)
         throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null || box == null) {
         return;
      }

      String name = event.getAssemblyName();
      VSAssembly assembly = (VSAssembly) vs.getAssembly(name);
      CrosstabVSAssemblyInfo cinfo = (CrosstabVSAssemblyInfo) assembly.getInfo().clone();
      VSCrosstabInfo vinfo = cinfo.getVSCrosstabInfo();
      CrosstabVSAssembly crosstab = (CrosstabVSAssembly) assembly;
      CrosstabTree ctree = crosstab.getCrosstabTree();

      if(vinfo != null) {
         DataRef[] rheaders = vinfo.getDesignRowHeaders();
         DataRef[] cheaders = vinfo.getDesignColHeaders();
         boolean appliedDC= DateComparisonUtil.appliedDateComparison(cinfo);

         DataRef dynamicRef = getRuntimeOnlyRef((CrosstabVSAssemblyInfo) assembly.getInfo(),
            event.getColName());

         if(dynamicRef instanceof VSDimensionRef) {
            setOrder(ctree, new DataRef[]{dynamicRef}, event.getColName());
         }
         else if(appliedDC && dynamicRef instanceof VSAggregateRef) {
            DataRef[] rtRheaders = vinfo.getRuntimeRowHeaders();
            DataRef[] rtCheaders = vinfo.getRuntimeColHeaders();
            DataRef[] headers = rtRheaders.length > 0 ? rtRheaders : rtCheaders;
            headers = headers == null ? new DataRef[0] : headers;
            String aggRefName = ((VSAggregateRef) dynamicRef).getFullName();
            aggRefName = VSUtil.getAggregateField(aggRefName, dynamicRef);
            int order = getAggregateOrder(aggRefName, headers);

            if(order == XConstants.SORT_VALUE_ASC) {
               order = XConstants.SORT_VALUE_DESC;
            }
            else if(order == XConstants.SORT_VALUE_DESC) {
               order = XConstants.SORT_NONE;
            }
            else {
               order = XConstants.SORT_VALUE_ASC;
            }

            setDataRefOrder(cinfo, true, order, aggRefName);
         }
         // sort dimension
         else if(event.getRow() < vinfo.getHeaderRowCount() ||
            event.getCol() < vinfo.getHeaderColCountWithPeriod())
         {
            String colName = event.getColName();

            setOrder(ctree, cheaders, colName);
            setOrder(ctree, rheaders, colName);
         }
         // sort aggregate
         else {
            DataRef[] headers = rheaders.length > 0 ? rheaders : cheaders;
            headers = headers == null ? new DataRef[0] : headers;
            DataRef[] aggrs = vinfo.getDesignAggregates();
            int hcols = vinfo.getHeaderColCount();
            int hrows = vinfo.getHeaderRowCount();
            int ai = vinfo.isSummarySideBySide() ?
               (event.getCol() - hcols) % aggrs.length : (event.getRow() - hrows) % aggrs.length;
            VSAggregateRef ref = (VSAggregateRef) aggrs[ai];

            String aggRefName = ref.getFullName();
            int order = getAggregateOrder(aggRefName, headers);

            if(order == XConstants.SORT_VALUE_ASC) {
               order = XConstants.SORT_VALUE_DESC;
            }
            else if(order == XConstants.SORT_VALUE_DESC) {
               order = XConstants.SORT_NONE;
            }
            else {
               order = XConstants.SORT_VALUE_ASC;
            }

            setDataRefOrder(cinfo, false, order, aggRefName);
            setDataRefOrder(cinfo, true, order, aggRefName);
            setDataRefOrder(((CrosstabVSAssembly) assembly).getCrosstabInfo(), true, order, aggRefName);
         }

         int hint = assembly.setVSAssemblyInfo(cinfo);

         box.resetDataMap(name);
         ChangedAssemblyList clist =
            placeholderService.createList(false, dispatcher, rvs, linkUri);
         box.processChange(name, hint, clist);
         placeholderService.execute(rvs, name, linkUri, clist, dispatcher, true);

         BindingModel binding = bindingFactory.createModel(assembly);
         SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
         dispatcher.sendCommand(bcommand);
      }
   }

   private void setDataRefOrder(CrosstabVSAssemblyInfo info, boolean runtime,
                                int order, String aggRefName)
   {
      VSCrosstabInfo cinfo = info.getVSCrosstabInfo();
      DataRef[] rheaders = runtime ? cinfo.getRuntimeRowHeaders() : cinfo.getRowHeaders();
      DataRef[] cheaders = runtime ? cinfo.getRuntimeColHeaders() : cinfo.getColHeaders();
      DataRef[] headers = rheaders != null && rheaders.length > 0 ? rheaders : cheaders;
      headers = headers == null ? new DataRef[0] : headers;

      for(int i = 0; i < headers.length; i++) {
         VSDimensionRef dim = (VSDimensionRef) headers[i];
         VSDimensionRef dcRef = runtime ? getDcRef(cinfo, dim.getFullName()) : null;
         dim.setSortByColValue(aggRefName);

         if(dcRef != null) {
            dcRef.setSortByColValue(aggRefName);
         }

         // for crosstab, if the group values are not sorted, the grouping could
         // be wrong (values in the same gropu could be spread into multiple groups)
         if(order == XConstants.SORT_NONE && i < headers.length - 1) {
            dim.setOrder(XConstants.SORT_ASC);

            if(dcRef != null) {
               dcRef.setOrder(XConstants.SORT_ASC);
            }
         }
         else {
            dim.setOrder(order);

            if(dcRef != null) {
               dcRef.setOrder(order);
            }
         }
      }
   }

   private VSDimensionRef getDcRef(VSCrosstabInfo cinfo, String dimFullName) {
      DataRef[] dcRefs = cinfo.getRuntimeDateComparisonRefs();

      if(dimFullName == null || dcRefs == null || dcRefs.length == 0) {
         return null;
      }

      DataRef ref = Arrays.stream(dcRefs).filter(r -> r instanceof VSDimensionRef)
         .filter(r -> Tool.equals(((VSDimensionRef) r).getFullName(), dimFullName))
         .findFirst().orElse(null);

      return ref == null ? null : (VSDimensionRef) ref;
   }

   /**
    * Get the order of the closest header to a given aggregate
    *
    * @param refName the aggregate to match
    * @param headers the headers to check
    *
    * @return the order the matching header
    */
   private int getAggregateOrder(String refName, DataRef[] headers) {
      for(int i = headers.length - 1; i >= 0; i--) {
         VSDimensionRef dim = (VSDimensionRef) headers[i];
         int order = dim.getOrder();
         String scol = dim.getSortByColValue();

         if(refName.equals(scol)) {
            return order;
         }
      }

      return XConstants.SORT_NONE;
   }

   private DataRef getRuntimeOnlyRef(CrosstabVSAssemblyInfo cinfo, String colName)
   {
      VSCrosstabInfo vinfo = cinfo.getVSCrosstabInfo();
      DataRef[] rDHeaders = vinfo.getDesignRowHeaders();
      DataRef[] cDHeaders = vinfo.getDesignColHeaders();
      DataRef[] rRHeaders = vinfo.getRuntimeRowHeaders();
      DataRef[] cRHeaders = vinfo.getRuntimeColHeaders();

      if((cinfo.getDateComparisonInfo() != null ||
         !Tool.isEmptyString(cinfo.getComparisonShareFrom())) && cinfo.supportDateComparison())
      {
         DataRef ref = cinfo.getDCBIndingRef(colName);

         if(ref != null) {
            return ref;
         }
      }

      for(DataRef ref : rDHeaders) {
         if(ref instanceof VSDimensionRef) {
            VSDimensionRef dim = (VSDimensionRef) ref;

            if(!dim.isDynamic() && dim.getFullName().equals(colName)) {
               return null;
            }
         }
      }

      for(DataRef ref : cDHeaders) {
         if(ref instanceof VSDimensionRef) {
            VSDimensionRef dim = (VSDimensionRef) ref;

            if(!dim.isDynamic() && dim.getFullName().equals(colName)) {
               return null;
            }
         }
      }

      for(DataRef ref : rRHeaders) {
         if(ref instanceof VSDimensionRef) {
            VSDimensionRef dim = (VSDimensionRef) ref;

            if(dim.getFullName().equals(colName)) {
               return ref;
            }
         }
      }

      for(DataRef ref : cRHeaders) {
         if(ref instanceof VSDimensionRef) {
            VSDimensionRef dim = (VSDimensionRef) ref;

            if(dim.getFullName().equals(colName)) {
               return ref;
            }
         }
      }

      return null;
   }

   private void setOrder(CrosstabTree ctree, DataRef[] refs, String colName) {
      for(DataRef ref : refs) {
         if(ref instanceof VSDimensionRef) {
            VSDimensionRef dim = (VSDimensionRef) ref;

            if(dim.getFullName().equals(colName)) {
               if((XCondition.TOP_N + "").equals(dim.getRankingOptionValue())) {
                  dim.setRankingOptionValue(XCondition.BOTTOM_N + "");
               }
               else if((XCondition.BOTTOM_N + "").equals(dim.getRankingOptionValue())) {
                  dim.setRankingOptionValue(XCondition.TOP_N + "");
               }

               dim.setOrder(getNewSort(dim.getOrder()));
               Map<String, VSDimensionRef> childRefs = ctree.getChildRefs();

               for(String parent : childRefs.keySet()) {
                  if(childRefs.get(parent) != null &&
                     colName.equals(childRefs.get(parent).getFullName()))
                  {
                     ctree.updateChildRef(parent, dim);
                     return;
                  }
               }
            }
         }
      }
   }

   /**
    * Get new order by old order.
    * @param currentOrder old order.
    * @return
    */
   private int getNewSort(int currentOrder) {
      final int newOrder;

      switch(currentOrder) {
      case XConstants.SORT_ASC:
         newOrder = XConstants.SORT_DESC;
         break;
      case XConstants.SORT_VALUE_ASC:
         newOrder = XConstants.SORT_VALUE_DESC;
         break;
      case XConstants.SORT_VALUE_DESC:
         newOrder = XConstants.SORT_VALUE_ASC;
         break;
      default:
         newOrder = XConstants.SORT_ASC;
      }

      return newOrder;
   }

   protected final VSBindingService bindingFactory;
   protected final ViewsheetService viewsheetService;
}
