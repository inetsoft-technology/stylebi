/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.Catalog;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.wiz.viewsheet.model.DeviceCatalogEntry;
import inetsoft.web.wiz.viewsheet.model.LayoutModel;
import inetsoft.web.wiz.viewsheet.model.LayoutObjectModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Dimension;
import java.awt.Point;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only projection of a viewsheet's print/device layouts and the shared device catalogue --
 * {@code list_layouts}/{@code get_layout}. A projection over {@link VSLayoutService}'s own
 * traversal/computation methods, the same relationship {@code ViewsheetReadService} has to a
 * plain {@code Viewsheet} -- this class does not re-walk {@code LayoutInfo} or re-derive
 * {@code supportTableLayout}/the device catalogue from scratch.
 *
 * <p>Unlike every mutation-side layout service in this package, {@code LayoutReadService} never
 * calls {@code AbstractLayout.apply(Viewsheet)}, so Hazard 1 (spec #11) does not arise here the
 * way it does for {@code LayoutMutationService}/{@code LayoutUndoService}: both coordinate-space
 * readings {@link #get} reports come from data already sitting on the paired session's own master
 * {@code Viewsheet} -- the layout-space reading from the layout's own stored
 * {@code VSAssemblyLayout} entry, the viewsheet-space reading from the same-named assembly's own
 * {@code pixelOffset}/{@code pixelSize} -- neither of which {@code apply()} would touch even if
 * this class did call it (it mutates {@code layoutPosition}/{@code layoutSize}/
 * {@code layoutVisible} instead). {@link LayoutSessionService#resolveForRead} is still consulted
 * first, purely so an unknown {@code layoutName} fails with the exact same message every other
 * layout tool gives (and warms the same clone {@link LayoutMutationService}/
 * {@link LayoutUndoService} would reuse for the same layout) -- its returned runtime is otherwise
 * unused here.
 */
@Service
public class LayoutReadService {
   @Autowired
   public LayoutReadService(ViewsheetSessionService viewsheetSessions,
                             LayoutSessionService layoutSessions,
                             VSLayoutService vsLayoutService,
                             DeviceRegistry deviceRegistry)
   {
      this.viewsheetSessions = viewsheetSessions;
      this.layoutSessions = layoutSessions;
      this.vsLayoutService = vsLayoutService;
      this.deviceRegistry = deviceRegistry;
   }

   /**
    * Every print/device layout defined on the paired viewsheet, by name and type, plus the
    * shared device catalogue. {@code devices}/{@code editDevicesAllowed} come straight from
    * {@link DeviceRegistry} -- Global Constraint 7: this plugin family reads that catalogue, it
    * never re-derives or writes it.
    */
   public Map<String, Object> list(String sessionToken, Principal agent) throws Exception {
      RuntimeViewsheet master = viewsheetSessions.resolve(sessionToken, agent);
      LayoutInfo info = master.getViewsheet().getLayoutInfo();
      List<Map<String, Object>> layouts = new ArrayList<>();

      if(info != null) {
         if(info.getPrintLayout() != null) {
            layouts.add(summarize(printLayoutName(), "print", null, null));
         }

         for(ViewsheetLayout layout : info.getViewsheetLayouts()) {
            layouts.add(summarize(layout.getName(), "device", layout.isMobileOnly(),
                                   Arrays.asList(layout.getDeviceIds())));
         }
      }

      List<DeviceCatalogEntry> devices = Arrays.stream(deviceRegistry.getDevices())
         .map(d -> new DeviceCatalogEntry(d.getId(), d.getName(), d.getMinWidth(), d.getMaxWidth()))
         .collect(Collectors.toList());

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("layouts", layouts);
      out.put("devices", devices);
      out.put("editDevicesAllowed", DeviceRegistry.isOrgAllowedToEditDevices(agent));
      return out;
   }

   /**
    * One layout's full detail: every object it places, in both coordinate spaces (see
    * {@link LayoutObjectModel}), plus whether each one supports a print-layout table layout.
    */
   public LayoutModel get(String sessionToken, Principal agent, String layoutName)
      throws Exception
   {
      RuntimeViewsheet master = viewsheetSessions.resolve(sessionToken, agent);

      // Fails loud on an unknown layoutName with the same message every layout-mutating tool
      // gives (see the class doc) -- the returned clone itself is not used below.
      layoutSessions.resolveForRead(sessionToken, agent, layoutName);

      Viewsheet masterVs = master.getViewsheet();
      LayoutInfo info = masterVs.getLayoutInfo();
      AbstractLayout layout = vsLayoutService.getViewsheetLayout(info, layoutName);
      boolean print = vsLayoutService.isPrintLayout(layoutName);

      List<LayoutObjectModel> objects = vsLayoutService
         .getVSAssemblyLayouts(layout, VSLayoutService.CONTENT)
         .stream()
         .map(assemblyLayout -> toObjectModel(masterVs, assemblyLayout))
         .collect(Collectors.toList());

      Boolean mobileOnly = print ? null : ((ViewsheetLayout) layout).isMobileOnly();
      List<String> selectedDevices = print ? null
         : Arrays.asList(((ViewsheetLayout) layout).getDeviceIds());

      return new LayoutModel(layoutName, print ? "print" : "device", mobileOnly, selectedDevices,
                              objects);
   }

   private LayoutObjectModel toObjectModel(Viewsheet masterVs, VSAssemblyLayout assemblyLayout) {
      VSAssembly assembly = masterVs.getAssembly(assemblyLayout.getName());
      Point layoutPos = assemblyLayout.getPosition();
      Dimension layoutSize = assemblyLayout.getSize();
      // Read off the MASTER, never a layout-preview clone, so this is a genuinely independent
      // reading from the layout-space one above -- see the class doc.
      Point vsPos = assembly == null ? null : assembly.getPixelOffset();
      Dimension vsSize = assembly == null ? null : assembly.getPixelSize();

      return new LayoutObjectModel(
         assemblyLayout.getName(),
         layoutPos.x, layoutPos.y, layoutSize.width, layoutSize.height,
         vsPos == null ? 0 : vsPos.x,
         vsPos == null ? 0 : vsPos.y,
         vsSize == null ? 0 : vsSize.width,
         vsSize == null ? 0 : vsSize.height,
         vsLayoutService.supportTableLayout(assembly));
   }

   private Map<String, Object> summarize(String name, String type, Boolean mobileOnly,
                                         List<String> selectedDevices)
   {
      Map<String, Object> summary = new LinkedHashMap<>();
      summary.put("name", name);
      summary.put("type", type);

      if(mobileOnly != null) {
         summary.put("mobileOnly", mobileOnly);
      }

      if(selectedDevices != null) {
         summary.put("selectedDevices", selectedDevices);
      }

      return summary;
   }

   /**
    * Not cached statically -- {@code VSLayoutService.isPrintLayout} itself re-resolves this from
    * {@link Catalog} on every call rather than caching it once, since the catalog is locale-
    * dependent per request; this mirrors that rather than risking a stale translation baked in
    * at class-load time.
    */
   private String printLayoutName() {
      return Catalog.getCatalog().getString("Print Layout");
   }

   private final ViewsheetSessionService viewsheetSessions;
   private final LayoutSessionService layoutSessions;
   private final VSLayoutService vsLayoutService;
   private final DeviceRegistry deviceRegistry;
}
