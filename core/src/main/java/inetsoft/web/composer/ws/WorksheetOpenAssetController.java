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
package inetsoft.web.composer.ws;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WSModelTrapContext;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.internal.Util;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XQuery;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.OpenAssetEvent;
import inetsoft.web.composer.ws.event.OpenAssetEventValidator;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;

/**
 * Controller which handles opening assets inside of a worksheet.
 */
@Controller
public class WorksheetOpenAssetController extends WorksheetController {
   /**
    * Check for traps before adding the assets to the worksheet.
    */
   @PostMapping("api/composer-worksheet/open-asset/check-trap/{runtimeId}")
   @ResponseBody
   public OpenAssetEventValidator checkTrap(
      @PathVariable("runtimeId") String runtimeId, @RequestBody OpenAssetEvent event,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      AssetEntry[] entries = event.entries();
      Point pos = new Point(event.left(), event.top());

      // not dropping a single worksheet; dropping table or column
      if(entries.length != 1 || !entries[0].isWorksheet()) {
         AbstractTableAssembly table = dropEntries(rws, principal, entries, pos);

         if(table == null) {
            return null;
         }

         WSModelTrapContext context =
            new WSModelTrapContext(table, principal);

         if(context.isCheckTrap() &&
            (context.checkTrap(null, table)).showWarning())
         {
            String msg = context.getTrapCondition();
            return OpenAssetEventValidator.builder()
               .trapMessage(msg)
               .build();
         }
      }

      return null;
   }

   /**
    * Event handler for adding an asset to the worksheet via drag and drop.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/open-asset")
   public void openAsset(
      @Payload OpenAssetEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      AssetEntry[] entries = event.entries();
      Point pos = new Point(event.left(), event.top());

      // drop a worksheet using the primary assembly
      if(entries.length == 1 && entries[0].isWorksheet()) {
         dropWorksheet(principal, entries, pos, commandDispatcher);
      }
      // drop worksheet columns.
      else if(entries.length > 0 &&
         VSEventUtil.BASE_WORKSHEET.equals(entries[0].getProperty("source")))
      {
         dropWorksheetColumn(principal, entries, pos, commandDispatcher);
      }
      // dropping table or column(s)
      else {
         dropDBData(principal, entries, pos, commandDispatcher);
      }
   }

   private void dropWorksheetColumn(Principal principal, AssetEntry[] entries, Point pos,
      CommandDispatcher commandDispatcher) throws Exception
   {
      List<AssetEntry> list = getValidEntries(entries);

      if(list.size() == 0) {
         return;
      }

      String ws = entries[0].getProperty("ws");

      if(ws == null) {
         return;
      }

      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      String target = rws.getEntry().toIdentifier();

      // If drag current ws to current ws, do nothing.
      if(Tool.equals(ws, target)) {
         return;
      }

      AssetEntry wsEntry = AssetEntry.createAssetEntry(ws);
      AssetEntry[] wsEntries = {wsEntry};
      MirrorAssembly mirror = createNewMirror(principal, wsEntries, pos, commandDispatcher);
      hideColumns(mirror, list);
      refreshWSData(principal, mirror, commandDispatcher);
   }

   private void hideColumns(MirrorAssembly mirror, List<AssetEntry> list) {
      if(!(mirror instanceof TableAssembly)) {
         return;
      }

      TableAssembly table = (TableAssembly) mirror;
      TableAssemblyInfo tinfo = (TableAssemblyInfo) table.getInfo();
      // The column index in drag is from private column selection.
      ColumnSelection icolumns = tinfo.getPrivateColumnSelection();
      // hide some columns not dragged in public column selection.
      ColumnSelection ocolumns = tinfo.getPrivateColumnSelection();

      for(int i = 0; i < ocolumns.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) ocolumns.getAttribute(i);
         col.setVisible(false);
      }

      for(AssetEntry asset: list) {
         String attr = asset.getProperty("attribute");
         ColumnRef column = (ColumnRef) ocolumns.getAttribute(attr);

         if(column == null) {
            continue;
         }

         column.setVisible(true);
      }
   }

   private List<AssetEntry> getValidEntries(AssetEntry[] entries) {
      AssetEntry entry = entries[0];
      String ws = entry.getProperty("ws");
      String source = entry.getProperty("source");
      List<AssetEntry> list = new ArrayList<>();

      for(AssetEntry e2 : entries) {
         String ws2 = e2.getProperty("ws");
         String source2 = e2.getProperty("source");

         if(e2.isColumn() && ws2 != null && source2 != null &&
            Tool.equals(ws2, ws) &&
            Tool.equals(source2, source))
         {
            list.add(e2);
         }
      }

      return list;
   }

   private MirrorAssembly createNewMirror(Principal principal, AssetEntry[] entries, Point pos,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      AssetRepository engine = super.getWorksheetEngine().getAssetRepository();
      Worksheet ws = rws.getWorksheet();
      AssetEntry entry = entries[0];
      Worksheet src = (Worksheet) engine.getSheet(entry, rws.getUser(), true,
                                                  AssetContent.ALL);
      List errors = (List) AssetRepository.ASSET_ERRORS.get();

      if(errors != null && errors.size() > 0) {
         StringBuilder sb = new StringBuilder();

         for(int i = 0; i < errors.size(); i++) {
            if(i > 0) {
               sb.append(", ");
            }

            sb.append(errors.get(i));
         }

         sb.append("(").append(entry.getDescription()).append(")");
         errors.clear();
         String msg = Catalog.getCatalog().getString(
            "common.mirrorAssemblies.updateFailed",
            sb.toString());
         throw new Exception(msg);
      }

      Assembly primary = src.getPrimaryAssembly();
      String bname = primary == null ? entry.getName() : primary.getName();
      MirrorAssembly nassembly = null;
      String name = null;
      Assembly[] assemblies = ws.getAssemblies();
      WSAssembly[] created = new WSAssembly[0];

      if(primary instanceof DefaultVariableAssembly) {
         name = bname;
         created = AssetUtil.copyOuterAssemblies(engine, entry, principal,
                                                 ws, new Point(0, 1));
         WSAssembly assembly = created[created.length - 1];

         nassembly = (MirrorAssembly)
            AssetEventUtil.createMirrorAssembly(rws, assembly, name, entry);
         nassembly.setLastModified(src.getLastModified());
      }
      else {
         for(Assembly assembly1 : assemblies) {
            if(!assembly1.isVisible()) {
               continue;
            }

            if((assembly1 instanceof MirrorAssembly) &&
               entry.equals(((MirrorAssembly) assembly1).getEntry()))
            {
               name = AssetUtil.getNextName(ws, bname);
               nassembly = (MirrorAssembly)
                  ((WSAssembly) assembly1).copyAssembly(name);

               if(nassembly instanceof TableAssembly) {
                  String mname = nassembly.getAssemblyName();
                  AssetEntry mentry = nassembly.getEntry();
                  WSAssembly massembly = (WSAssembly) ws.getAssembly(mname);
                  nassembly = (MirrorAssembly) AssetEventUtil.
                     createMirrorAssembly(rws, massembly, name, mentry);
               }
               else {
                  nassembly.setAutoUpdate(true);
               }

               break;
            }
         }

         if(nassembly == null) {
            created = AssetUtil.copyOuterAssemblies(engine, entry, principal,
                                                    ws, new Point(0, 1));
            WSAssembly assembly = created[created.length - 1];
            name = AssetUtil.getNextName(ws, bname);
            nassembly = (MirrorAssembly)
               AssetEventUtil.createMirrorAssembly(rws, assembly, name, entry);
            nassembly.setLastModified(src.getLastModified());
         }
      }

      ((WSAssembly) nassembly).setPixelOffset(pos);
      ws.addAssembly((WSAssembly) nassembly);

      // sort assemblies according to dependencies
      Arrays.sort(created, new DependencyComparator(ws, true));

      for(WSAssembly aCreated : created) {
         String tname = aCreated.getName();
         WorksheetEventUtil.createAssembly(rws, aCreated, commandDispatcher, principal);
         WorksheetEventUtil.loadTableData(rws, tname, false, false);
         WorksheetEventUtil.refreshAssembly(rws, tname, false, commandDispatcher, principal);
      }

      return nassembly;
   }

   private void dropWorksheet(Principal principal, AssetEntry[] entries, Point pos,
      CommandDispatcher commandDispatcher) throws Exception
   {
      MirrorAssembly mirror = createNewMirror(principal, entries, pos, commandDispatcher);
      refreshWSData(principal, mirror, commandDispatcher);
   }

   private void refreshWSData(Principal principal, MirrorAssembly nassembly,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      WorksheetEventUtil.createAssembly(
         rws, (WSAssembly) nassembly, commandDispatcher, principal);

      if(nassembly instanceof DateRangeAssembly) {
         WorksheetEventUtil.refreshDateRange(ws);
      }

      String name = nassembly.getAssemblyName();
      WorksheetEventUtil.loadTableData(rws, name, false, false);
      WorksheetEventUtil.focusAssembly(name, commandDispatcher);
      WorksheetEventUtil.refreshAssembly(rws, name, false, commandDispatcher, principal);
      WorksheetEventUtil.layout(rws, commandDispatcher);
   }

   private void dropDBData(Principal principal, AssetEntry[] entries, Point pos,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      AbstractTableAssembly table = dropEntries(rws, principal, entries, pos);

      if(table == null) {
         return;
      }

      ws.addAssembly(table);
      WorksheetEventUtil.createAssembly(rws, table, commandDispatcher, principal);
      WorksheetEventUtil.layout(rws, commandDispatcher);
      WorksheetEventUtil.focusAssembly(table.getName(), commandDispatcher);

      // here we should check query/data source variable
      if(WorksheetEventUtil.refreshVariables(
         rws, super.getWorksheetEngine(), table.getName(), commandDispatcher))
      {
         return;
      }

      WorksheetEventUtil.loadTableData(rws, table.getName(), true, true);
      WorksheetEventUtil.refreshAssembly(
         rws, table.getName(), true, commandDispatcher, principal);

   }

   /**
    * Open entries in the worksheet.
    *
    * @param rws       the runtime worksheet
    * @param principal the user principal
    * @param entries   the entries to open
    * @param pos       the position to place the table assembly at
    *
    * @return the new table assembly if it exists, null otherwise
    */
   private AbstractTableAssembly dropEntries(
      RuntimeWorksheet rws, Principal principal, AssetEntry[] entries, Point pos) throws Exception
   {
      AssetRepository engine = super.getWorksheetEngine().getAssetRepository();
      Worksheet ws = rws.getWorksheet();

      if(entries.length == 1 && entries[0].isFolder()) {
         entries = getSubEntries(entries[0], engine, principal);
      }

      if(entries.length == 0) {
         return null;
      }

      AssetEntry entry = entries[0];
      String prefix = entry.getProperty("prefix");
      String source = entry.getProperty("source");
      String type = entry.getProperty("type");
      String folderDesc = entry.getProperty("folder_description");
      String levelNumber = entry.getProperty("level_number");
      List<String> attributes = new ArrayList<>();
      List<AssetEntry> list = new ArrayList<>();
      int max = Util.getOrganizationMaxColumn();

      for(AssetEntry e2 : entries) {
         String prefix2 = e2.getProperty("prefix");
         String source2 = e2.getProperty("source");

         if(e2.isColumn() && prefix2 != null && source2 != null &&
            Tool.equals(prefix2, prefix) &&
            Tool.equals(source2, source) &&
            Tool.equals(e2.getProperty("type"), type))
         {
            if(list.size() >= max) {
               Tool.addUserMessage(Util.getColumnLimitMessage());
               break;
            }

            list.add(e2);
         }
      }

      if(type == null || list.size() == 0) {
         return null;
      }

      entry = list.get(0);
      SourceInfo sinfo = new SourceInfo(
         Integer.parseInt(type), prefix, source);
      sinfo.setProperty(SourceInfo.QUERY_FOLDER, folderDesc);
      String name = source;

      if(sinfo.getType() == SourceInfo.PHYSICAL_TABLE) {
         sinfo.setProperty(SourceInfo.SCHEMA, entry.getProperty(SourceInfo.SCHEMA));
         sinfo.setProperty(SourceInfo.CATALOG, entry.getProperty(SourceInfo.CATALOG));
         sinfo.setProperty(SourceInfo.TABLE_TYPE, entry.getProperty(SourceInfo.TABLE_TYPE));
      }

      if(AssetEventUtil.isCubeType(sinfo.getType()) && levelNumber != null) {
         sinfo.setProperty("level_number", levelNumber);
      }

      ColumnSelection columns = new ColumnSelection();
      XLogicalModel lmodel = null;
      XQuery query = null;

      if(sinfo.getType() == XSourceInfo.MODEL) {
         lmodel = XUtil.getLogicModel(sinfo, rws.getUser() == null ?
               principal : rws.getUser());
      }

      for(AssetEntry temp : list) {
         String entity = temp.getProperty("entity");
         String attr = temp.getProperty("attribute");
         attr = AssetUtil.trimEntity(attr, entity);

         // apply vpm
         if(lmodel != null && principal != null) {
            XAttribute[] attrs = XUtil.getAttributes(prefix, source, entity,
                                                     principal, true, true);
            attributes.clear();

            for(XAttribute xattr : attrs) {
               attributes.add(xattr.getName());
            }

            if(!attributes.contains(attr)) {
               continue;
            }
         }

         AttributeRef attributeRef = new AttributeRef(entity, attr);

         if(entry.getProperty("caption") != null) {
            attributeRef.setCaption(temp.getProperty("caption"));
         }

         if(entry.getProperty("refType") != null) {
            attributeRef.setRefType(
               Integer.parseInt(temp.getProperty("refType")));
         }

         ColumnRef ref = new ColumnRef(attributeRef);
         ref.setDataType(temp.getProperty("dtype"));

         String sqltypeStr = temp.getProperty("sqltype");

         if(!StringUtils.isEmpty(sqltypeStr)) {
            attributeRef.setSqlType(Integer.parseInt(sqltypeStr));
         }

         columns.addAttribute(ref);

         if(entity != null && entity.length() > 0 &&
            !AssetEventUtil.isCubeType(Integer.parseInt(type)))
         {
            name = entity;
         }

         // if logic model, keep ref type
         if(lmodel != null) {
            XEntity xentity = lmodel.getEntity(entity);
            XAttribute xattr = xentity.getAttribute(attr);
            attributeRef.setRefType(xattr.getRefType());
            attributeRef.setDefaultFormula(xattr.getDefaultFormula());
            ref.setDescription(xattr.getDescription());
         }

         if(query != null) {
            XSelection selection = query.getSelection();

            if(selection != null) {
               String path = selection.getAliasColumn(ref.getAttribute());
               String desc = selection.getDescription(path);
               ref.setDescription(desc);
            }
         }

         String tname = temp.getProperty("table");

         if(tname == null) {
            tname = temp.getParent().getName();
         }

         if(tname != null) {
            name = tname;
         }
      }

      name = AssetUtil.normalizeTable(name);
      name = AssetUtil.getNextName(ws, name);
      AbstractTableAssembly table;

      if(sinfo.getType() == SourceInfo.MODEL) {
         table = new BoundTableAssembly(ws, name);
      }
      else if(sinfo.getType() == SourceInfo.PHYSICAL_TABLE) {
         table = new PhysicalBoundTableAssembly(ws, name);
      }
      else if(AssetEventUtil.isCubeType(sinfo.getType())) {
         table = new CubeTableAssembly(ws, name);
      }
      else {
         table = new QueryBoundTableAssembly(ws, name);
      }

      table.setColumnSelection(columns);
      table.setSourceInfo(sinfo);
      table.setPixelOffset(pos);
      return table;
   }

   /**
    * Get the sub entries of an asset table/query.
    *
    * @param entry the specified asset table/query.
    *
    * @return the sub entries of the asset table/entry.
    */
   private AssetEntry[] getSubEntries(
      AssetEntry entry, AssetRepository engine, Principal principal) throws Exception
   {
      AssetEntry.Type type = entry.getType();

      if(type != AssetEntry.Type.TABLE && type != AssetEntry.Type.QUERY &&
         type != AssetEntry.Type.PHYSICAL_TABLE &&
         !"true".equals(entry.getProperty("DIMENSION_FOLDER")))
      {
         return new AssetEntry[0];
      }

      return engine.getEntries(entry, principal, null);
   }
}
