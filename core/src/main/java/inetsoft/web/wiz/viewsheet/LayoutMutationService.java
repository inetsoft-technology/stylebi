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
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.vslayout.AbstractLayout;
import inetsoft.uql.viewsheet.vslayout.VSAssemblyLayout;
import inetsoft.uql.viewsheet.vslayout.VSEditableAssemblyLayout;
import inetsoft.web.composer.vs.controller.VSLayoutControllerServiceProxy;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.composer.vs.event.AddVSLayoutObjectEvent;
import inetsoft.web.viewsheet.DataTipInLayoutCheckResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Dimension;
import java.awt.Point;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 3 of the layout implementation plan (2026-08-20-layout-implementation.md), Task 4:
 * {@code edit_layout_objects} (add/remove/move_resize) and {@code set_layout_table_options}.
 *
 * <h2>Why this does not delegate to {@code VSLayoutControllerService}'s own
 * {@code addObject}/{@code removeObject}/{@code moveResizeLayoutObjects}</h2>
 *
 * <p>The plan's own Interfaces note for this task says to "call the underlying logic, not the
 * STOMP controller ... widen visibility or extract the shared body ... check at implementation
 * time rather than assuming." Having checked: those three methods are already {@code public} and
 * already reachable off a generated {@code @ClusterProxy} bean ({@link
 * VSLayoutControllerServiceProxy}) exactly the way {@code WizComposerController}/
 * {@code WizDashboardService} already call sibling {@code @ClusterProxy} services in this
 * codebase -- no visibility widening turned out to be necessary. But calling them as-is with our
 * clone's runtime id is still wrong, for a reason specific to this plugin's calling shape:
 *
 * <ul>
 *   <li>{@code moveResizeLayoutObjects} resolves {@code parentRvs} from the clone's
 *   {@code getOriginalID()} (set to the master's id by {@link LayoutSessionService}), then calls
 *   {@code layoutClone.apply(parentRvs.getViewsheet())} -- {@code apply()} directly on the
 *   <b>master's own, live</b> {@code Viewsheet}. That is exactly the Hazard-1 corruption {@link
 *   LayoutSessionService}'s own class doc describes: {@code apply()}'s shallow clone shares
 *   {@code VSAssemblyInfo}/{@code FormatInfo} instances with its argument, so master's real
 *   assemblies' {@code layoutPosition}/{@code layoutSize}/{@code RScaleFont} get overwritten as a
 *   side effect, even though the *result* is only ever installed onto the clone via
 *   {@code rvs.setViewsheet(...)}. (By contrast, {@code addObject} happens to be safe -- its own
 *   {@code apply()} call runs against {@code rvs.getViewsheet().clone()}, a second, throwaway
 *   clone of the clone -- and {@code removeObject} never calls {@code apply()} at all. But relying
 *   on that today-only accident of one method's plumbing while the sibling method two lines away
 *   in the same class corrupts master would leave the safety of this whole task's exit criterion
 *   resting on an implementation detail nobody signed up to keep stable.)</li>
 *   <li>All three methods also call {@link VSLayoutService#makeUndoable} themselves, taking a
 *   checkpoint. {@link LayoutSessionService#mutateLayout} <em>already</em> takes exactly one
 *   checkpoint after the mutation runs (Global Constraint 4). Delegating to the STOMP-facing
 *   bodies as well would double-checkpoint every edit, corrupting the very undo/redo count Task 5
 *   depends on.</li>
 * </ul>
 *
 * <p>The fix used here: call {@link VSLayoutService}'s already-{@code public}, non-checkpointing,
 * non-{@code apply()}-calling granular methods directly ({@code findViewsheetLayout},
 * {@code getVSAssemblyLayouts}/{@code setVSAssemblyLayouts}, {@code createVSAssembly}/
 * {@code createAssemblyLayout}, {@code findAssemblyLayout}) against the <b>master's</b>
 * {@code LayoutInfo} -- which is exactly right to mutate directly, since a layout's stored object
 * list *is* the definitional data {@code edit_layout_objects} exists to change, and none of these
 * calls ever invokes {@code apply()} on anything. The one thing deliberately NOT reproduced here
 * is the STOMP path's {@code apply()}-based live-preview refresh and {@code
 * fixAssemblyLayoutsPosition} auto-repositioning (print-layout collision avoidance): this plugin
 * has no connected browser to refresh, {@link LayoutReadService} reads positions straight off
 * {@code LayoutInfo} rather than off a rendered preview, and skipping it removes the only
 * remaining path back to an {@code apply()} call on a live runtime. A caller that needs several
 * objects not to overlap in a print layout is responsible for spacing them; this is a known,
 * deliberate scope cut from the interactive Composer's behavior, not an oversight.
 *
 * <h2>The {@code check_assembly_in_layout} guard on remove</h2>
 *
 * <p>{@link VSLayoutControllerServiceProxy#checkAssemblyInLayout} is a pure read (no {@code
 * apply()}, no checkpoint) -- safe to call with the master's own runtime id. Its real-world
 * semantics for "does removing this object break a data-tip relationship" could not be fully
 * pinned down from source alone (it reports whether an assembly is present in a given/{@code
 * "null"}-sentinel runtime layout, which is a narrower question than "is this assembly some other
 * object's data-tip target"); this class treats {@code isAssemblyInLayout() == true} on the
 * layout being edited as "yes, something depends on this," which is the interpretation the unit
 * tests below fix in place via mocking. <b>Flagged for the Phase 5 live-verification pass</b> to
 * confirm this polarity against a real data-tip setup before relying on it in production.
 */
@Service
public class LayoutMutationService {
   @Autowired
   public LayoutMutationService(LayoutSessionService layoutSessions,
                                 ViewsheetSessionService viewsheetSessions,
                                 VSLayoutService vsLayoutService,
                                 VSLayoutControllerServiceProxy vsLayoutControllerService)
   {
      this.layoutSessions = layoutSessions;
      this.viewsheetSessions = viewsheetSessions;
      this.vsLayoutService = vsLayoutService;
      this.vsLayoutControllerService = vsLayoutControllerService;
   }

   public static final String OP_ADD = "add";
   public static final String OP_REMOVE = "remove";
   public static final String OP_MOVE_RESIZE = "move_resize";

   /**
    * {@code edit_layout_objects}: {@code op} is one of {@link #OP_ADD}, {@link #OP_REMOVE},
    * {@link #OP_MOVE_RESIZE}. {@code objects} is a list of per-object patches:
    * <ul>
    *   <li>{@code move_resize}: {@code name}, {@code x}, {@code y}, {@code width}, {@code height}.
    *   </li>
    *   <li>{@code add}: {@code name}; {@code type} ({@code "text"}/{@code "image"}/
    *   {@code "pagebreak"}, omitted if {@code name} already names an existing assembly on this
    *   viewsheet); {@code x}, {@code y}.</li>
    *   <li>{@code remove}: {@code name}.</li>
    * </ul>
    *
    * <p>Returns {@code {"requiresConfirmation": false}} on success, or
    * {@code {"requiresConfirmation": true, "reason": ..., "objectNames": [...]}} when the remove
    * path's data-tip guard blocks the call -- re-call with {@code confirmed: true} to proceed
    * anyway.
    */
   public Map<String, Object> editObjects(String sessionToken, Principal agent, String layoutName,
                                           String op, int region, List<Map<String, Object>> objects,
                                           boolean confirmed)
      throws Exception
   {
      if(objects == null || objects.isEmpty()) {
         throw new IllegalArgumentException(
            "edit_layout_objects needs at least one object in \"objects\".");
      }

      switch(op == null ? "" : op) {
      case OP_MOVE_RESIZE:
         moveResize(sessionToken, agent, layoutName, region, objects);
         return okResult();
      case OP_ADD:
         addObjects(sessionToken, agent, layoutName, region, objects);
         return okResult();
      case OP_REMOVE:
         return removeObjects(sessionToken, agent, layoutName, region, objects, confirmed);
      default:
         throw new IllegalArgumentException(
            "edit_layout_objects: unknown op \"" + op + "\" -- expected \"" + OP_ADD + "\", \"" +
            OP_REMOVE + "\", or \"" + OP_MOVE_RESIZE + "\".");
      }
   }

   /**
    * {@code set_layout_table_options}: wraps the same {@code VSAssemblyLayout.tableLayout} field
    * {@code table-layout-property-dialog} sets, but refuses up front -- before {@link
    * LayoutSessionService#mutateLayout} ever opens a mutation seam or takes a checkpoint -- when
    * {@code objectName} does not report {@code supportsTableLayout} on the master, rather than
    * forwarding a request that would silently no-op.
    */
   public void setTableLayoutOptions(String sessionToken, Principal agent, String layoutName,
                                      String objectName, int region, int tableLayout)
      throws Exception
   {
      RuntimeViewsheet master = viewsheetSessions.resolve(sessionToken, agent);
      VSAssembly assembly = master.getViewsheet().getAssembly(objectName);

      if(!vsLayoutService.supportTableLayout(assembly)) {
         throw new IllegalArgumentException(
            "\"" + objectName + "\" does not support table layout options -- only a table, " +
            "crosstab, or a tab/group container whose content is one of those does. Call " +
            "get_layout to see which objects report supportsTableLayout: true.");
      }

      layoutSessions.mutateLayout(sessionToken, agent, layoutName,
         (clone, mutMaster, cloneRuntimeId, dispatcher) -> {
            AbstractLayout layout = requireLayout(mutMaster, layoutName);
            VSAssemblyLayout assemblyLayout = requireAssemblyLayout(layout, objectName, region,
                                                                     layoutName);
            assemblyLayout.setTableLayout(tableLayout);
         });
   }

   // ── op: move_resize ──────────────────────────────────────────────────────

   private void moveResize(String sessionToken, Principal agent, String layoutName, int region,
                            List<Map<String, Object>> objects) throws Exception
   {
      layoutSessions.mutateLayout(sessionToken, agent, layoutName,
         (clone, master, cloneRuntimeId, dispatcher) -> {
            AbstractLayout layout = requireLayout(master, layoutName);

            for(Map<String, Object> object : objects) {
               String name = requireName(object);
               VSAssemblyLayout assemblyLayout =
                  requireAssemblyLayout(layout, name, region, layoutName);

               Point position = new Point(toInt(object.get("x")), toInt(object.get("y")));
               Dimension size = new Dimension(toInt(object.get("width")),
                                               toInt(object.get("height")));
               assemblyLayout.setPosition(position);
               assemblyLayout.setSize(size);

               // A layout-only ("editable") object's own self-contained VSAssemblyInfo tracks its
               // layout position/size too -- keep both in sync, mirroring
               // VSLayoutControllerService.moveResizeLayoutObjects. This info lives inside the
               // VSAssemblyLayout entry itself, not on any real viewsheet assembly, so updating it
               // is not a Hazard-1 concern.
               if(assemblyLayout instanceof VSEditableAssemblyLayout editable) {
                  VSAssemblyInfo info = editable.getInfo();
                  info.setLayoutPosition(position);
                  info.setLayoutSize(size);
               }
            }
         });
   }

   // ── op: add ───────────────────────────────────────────────────────────────

   private void addObjects(String sessionToken, Principal agent, String layoutName, int region,
                            List<Map<String, Object>> objects) throws Exception
   {
      layoutSessions.mutateLayout(sessionToken, agent, layoutName,
         (clone, master, cloneRuntimeId, dispatcher) -> {
            Viewsheet masterVs = master.getViewsheet();
            AbstractLayout layout = requireLayout(master, layoutName);
            List<VSAssemblyLayout> layouts =
               new ArrayList<>(vsLayoutService.getVSAssemblyLayouts(layout, region));

            for(Map<String, Object> object : objects) {
               String name = requireName(object);
               AddVSLayoutObjectEvent event = new AddVSLayoutObjectEvent();
               event.setLayoutName(layoutName);
               event.setRegion(region);
               event.setxOffset(toInt(object.getOrDefault("x", 0)));
               event.setyOffset(toInt(object.getOrDefault("y", 0)));
               event.setNames(new String[] { name });

               VSAssembly assembly = masterVs.getAssembly(name);
               boolean existAssembly = assembly != null;

               if(!existAssembly) {
                  event.setType(parseAssetType(object.get("type"), name));
                  assembly = vsLayoutService.createVSAssembly(event, layout, masterVs, name);
               }

               VSAssemblyLayout assemblyLayout = vsLayoutService
                  .createAssemblyLayout(event, masterVs, name, assembly, existAssembly);
               layouts.add(assemblyLayout);
            }

            vsLayoutService.setVSAssemblyLayouts(layout, layouts, region);
         });
   }

   private static int parseAssetType(Object type, String name) {
      if(type == null) {
         throw new IllegalArgumentException(
            "edit_layout_objects add: \"" + name + "\" is not an existing assembly on this " +
            "viewsheet, so a \"type\" (\"text\", \"image\", or \"pagebreak\") is required to " +
            "create it as a layout-only object.");
      }

      String normalized = String.valueOf(type).trim().toLowerCase();

      return switch(normalized) {
         case "text" -> AbstractSheet.TEXT_ASSET;
         case "image" -> AbstractSheet.IMAGE_ASSET;
         case "pagebreak", "page_break", "page-break" -> AbstractSheet.PAGEBREAK_ASSET;
         default -> throw new IllegalArgumentException(
            "edit_layout_objects add: unknown type \"" + type + "\" -- expected \"text\", " +
            "\"image\", or \"pagebreak\".");
      };
   }

   // ── op: remove ────────────────────────────────────────────────────────────

   private Map<String, Object> removeObjects(String sessionToken, Principal agent,
                                              String layoutName, int region,
                                              List<Map<String, Object>> objects, boolean confirmed)
      throws Exception
   {
      List<String> names = new ArrayList<>();

      for(Map<String, Object> object : objects) {
         names.add(requireName(object));
      }

      if(!confirmed) {
         RuntimeViewsheet master = viewsheetSessions.resolve(sessionToken, agent);
         List<String> dependent = new ArrayList<>();

         for(String name : names) {
            DataTipInLayoutCheckResult check = vsLayoutControllerService
               .checkAssemblyInLayout(master.getID(), layoutName, name, agent);

            if(check.isAssemblyInLayout()) {
               dependent.add(name);
            }
         }

         if(!dependent.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("requiresConfirmation", true);
            result.put("reason",
               "Removing " + dependent + " from \"" + layoutName + "\" may break a data-tip " +
               "relationship that depends on " + (dependent.size() == 1 ? "it" : "them") +
               " -- call again with confirmed: true to remove anyway.");
            result.put("objectNames", dependent);
            return result;
         }
      }

      layoutSessions.mutateLayout(sessionToken, agent, layoutName,
         (clone, master, cloneRuntimeId, dispatcher) -> {
            AbstractLayout layout = requireLayout(master, layoutName);
            List<VSAssemblyLayout> layouts =
               new ArrayList<>(vsLayoutService.getVSAssemblyLayouts(layout, region));
            layouts.removeIf(l -> names.contains(l.getName()));
            vsLayoutService.setVSAssemblyLayouts(layout, layouts, region);
         });

      return okResult();
   }

   // ── shared helpers ────────────────────────────────────────────────────────

   private AbstractLayout requireLayout(RuntimeViewsheet master, String layoutName) {
      return vsLayoutService.findViewsheetLayout(master.getViewsheet(), layoutName)
         .orElseThrow(() -> new IllegalArgumentException(
            "Unknown layout \"" + layoutName + "\" -- call list_layouts to see the print and " +
            "device layouts defined on this viewsheet."));
   }

   private VSAssemblyLayout requireAssemblyLayout(AbstractLayout layout, String objectName,
                                                   int region, String layoutName)
   {
      return vsLayoutService.findAssemblyLayout(layout, objectName, region)
         .orElseThrow(() -> new IllegalArgumentException(
            "\"" + objectName + "\" is not placed in layout \"" + layoutName + "\" -- add it " +
            "first with edit_layout_objects (op: \"add\")."));
   }

   private static String requireName(Map<String, Object> object) {
      Object name = object.get("name");

      if(name == null || String.valueOf(name).isBlank()) {
         throw new IllegalArgumentException(
            "edit_layout_objects: every entry in \"objects\" needs a \"name\".");
      }

      return String.valueOf(name);
   }

   private static Map<String, Object> okResult() {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("requiresConfirmation", false);
      return result;
   }

   private static int toInt(Object value) {
      if(value instanceof Number number) {
         return number.intValue();
      }

      return Integer.parseInt(String.valueOf(value));
   }

   private final LayoutSessionService layoutSessions;
   private final ViewsheetSessionService viewsheetSessions;
   private final VSLayoutService vsLayoutService;
   private final VSLayoutControllerServiceProxy vsLayoutControllerService;
}
