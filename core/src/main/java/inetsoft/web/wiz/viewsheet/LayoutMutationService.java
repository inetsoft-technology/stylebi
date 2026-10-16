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
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.PopVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TipVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.vslayout.AbstractLayout;
import inetsoft.uql.viewsheet.vslayout.PrintLayout;
import inetsoft.uql.viewsheet.vslayout.VSAssemblyLayout;
import inetsoft.uql.viewsheet.vslayout.VSEditableAssemblyLayout;
import inetsoft.uql.viewsheet.vslayout.ViewsheetLayout;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.composer.vs.event.AddVSLayoutObjectEvent;
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
 * already reachable off a generated {@code @ClusterProxy} bean ({@code
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
 * <h2>The confirmation guard on remove</h2>
 *
 * <p>An unconfirmed remove asks for confirmation only when another assembly on the master uses
 * the object as its data-tip view or pop component. The scan covers every master assembly
 * (embedded viewsheets and the object itself excluded), whether or not the dependent is placed
 * in the layout being edited, and does not depend on the region the object sits in. Flyover
 * views are not treated as a dependency. (Bug 78614: the guard used to call {@code
 * checkAssemblyInLayout}, which only reports whether the object is placed, so every placed
 * object was blocked.)
 */
@Service
public class LayoutMutationService {
   @Autowired
   public LayoutMutationService(LayoutSessionService layoutSessions,
                                 ViewsheetSessionService viewsheetSessions,
                                 VSLayoutService vsLayoutService)
   {
      this.layoutSessions = layoutSessions;
      this.viewsheetSessions = viewsheetSessions;
      this.vsLayoutService = vsLayoutService;
   }

   public static final String OP_ADD = "add";
   public static final String OP_REMOVE = "remove";
   public static final String OP_MOVE_RESIZE = "move_resize";

   /**
    * {@code edit_layout_objects}: {@code op} is one of {@link #OP_ADD}, {@link #OP_REMOVE},
    * {@link #OP_MOVE_RESIZE}. {@code objects} is a list of per-object patches:
    * <ul>
    *   <li>{@code move_resize}: {@code name}, {@code x}, {@code y}, {@code width}, {@code height},
    *   optional {@code text} (only valid for an existing layout-only text object).</li>
    *   <li>{@code add}: {@code name}; {@code type} ({@code "text"}/{@code "image"}/
    *   {@code "pagebreak"}, omitted if {@code name} already names an existing assembly on this
    *   viewsheet); {@code x}, {@code y}; optional {@code text} (only valid when {@code type} is
    *   {@code "text"}).</li>
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

      // Bug 77045 #6: supportTableLayout above checks only the ASSEMBLY's own type -- it never
      // checks which kind of layout layoutName resolves to. VSAssemblyLayout.tableLayout is only
      // ever consumed by VsToReportConverter (whose own layout field is declared PrintLayout-typed
      // and therefore structurally cannot hold a device layout reference), so a write here on a
      // device layout would silently persist and never be read at render time. Refused up front,
      // before mutateLayout opens a mutation seam, matching this method's own established
      // convention for the assembly-type guard above.
      if(requireLayout(master, layoutName) instanceof ViewsheetLayout) {
         throw new IllegalArgumentException(
            "set_layout_table_options: \"" + layoutName + "\" is a device layout -- table layout " +
            "options only apply within the print layout. VsToReportConverter, the only consumer " +
            "of this setting, converts a viewsheet to a printed/exported report through the print " +
            "layout exclusively, so a write on a device layout would be silently ignored at " +
            "render time rather than genuinely effective. Call list_layouts to find the print " +
            "layout's name.");
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
               int resolved = resolveRegion(layout, name, region, layoutName,
                                            "add it first with edit_layout_objects (op: \"add\").");
               VSAssemblyLayout assemblyLayout =
                  requireAssemblyLayout(layout, name, resolved, layoutName);

               // A move-only or resize-only call must leave the other dimension unchanged --
               // merge onto the assembly's current position/size rather than defaulting an
               // omitted field to 0, which would silently shrink/relocate it instead.
               Point currentPosition = assemblyLayout.getPosition();
               Dimension currentSize = assemblyLayout.getSize();
               int x = object.containsKey("x") ? toInt(object.get("x")) :
                  (currentPosition != null ? currentPosition.x : 0);
               int y = object.containsKey("y") ? toInt(object.get("y")) :
                  (currentPosition != null ? currentPosition.y : 0);
               int width = object.containsKey("width") ? toInt(object.get("width")) :
                  (currentSize != null ? currentSize.width : 0);
               int height = object.containsKey("height") ? toInt(object.get("height")) :
                  (currentSize != null ? currentSize.height : 0);
               requireNonNegativePosition(x, y);
               Point position = new Point(x, y);
               Dimension size = new Dimension(width, height);
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

                  if(object.containsKey("text")) {
                     if(!(info instanceof TextVSAssemblyInfo textInfo)) {
                        throw new IllegalArgumentException(
                           "edit_layout_objects move_resize: \"" + name + "\" is not a text " +
                           "object -- \"text\" only applies to a layout-only text object.");
                     }

                     String text = String.valueOf(object.get("text"));
                     textInfo.setValue(text);
                     textInfo.setTextValue(text);
                  }
               }
               else if(object.containsKey("text")) {
                  throw new IllegalArgumentException(
                     "edit_layout_objects move_resize: \"" + name + "\" is not a layout-only " +
                     "text object -- \"text\" only applies there.");
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
               int xOffset = toInt(object.getOrDefault("x", 0));
               int yOffset = toInt(object.getOrDefault("y", 0));
               requireNonNegativePosition(xOffset, yOffset);
               event.setLayoutName(layoutName);
               event.setRegion(region);
               event.setxOffset(xOffset);
               event.setyOffset(yOffset);
               event.setNames(new String[] { name });

               VSAssembly assembly = masterVs.getAssembly(name);
               boolean existAssembly = assembly != null;

               // Bug 77045 #8: a container's child is positioned only through its container's
               // own single layout entry (createAssemblyLayout/getVSTabSize computes size from
               // the container, never from a child's own independent entry) -- adding a child
               // here directly creates an entry the render logic never consults. Neither the
               // interactive Composer's own object tree offers a child as a draggable node
               // (components-pane.component.ts builds it from top-level assemblies only), so this
               // mirrors that same restriction here instead of silently accepting a dead entry.
               if(existAssembly && assembly.getContainer() != null) {
                  throw new IllegalArgumentException(
                     "edit_layout_objects add: \"" + name + "\" is inside container \"" +
                     assembly.getContainer().getAbsoluteName() + "\" -- a container's child is " +
                     "positioned only through its container's own single layout entry, so " +
                     "adding it independently would create an entry the render logic never " +
                     "consults. Add \"" + assembly.getContainer().getAbsoluteName() +
                     "\" itself instead.");
               }

               // Bug 78210: an annotation (root, line or rectangle) is never placed by a layout
               // entry -- AbstractLayout.apply skips it -- and the interactive Composer's own
               // object tree omits it, so an entry here would read back as placed yet do nothing.
               if(existAssembly && AnnotationFamily.isPart(assembly)) {
                  throw new IllegalArgumentException(
                     "edit_layout_objects add: \"" + name + "\" is an annotation -- annotations " +
                     "cannot be placed in a layout.");
               }

               // Bug 78210: a child of an embedded viewsheet resolves by its dotted name, but the
               // interactive Composer's object tree lists only the embedded viewsheet itself.
               // Compare against the master (not isEmbedded(), which is also true for the
               // embedded viewsheet itself, which must stay addable).
               if(existAssembly && assembly.getViewsheet() != masterVs) {
                  String owner = assembly.getViewsheet().getAbsoluteName();
                  throw new IllegalArgumentException(
                     "edit_layout_objects add: \"" + name + "\" is a child of embedded " +
                     "viewsheet \"" + owner + "\" and cannot be placed on its own. Add \"" +
                     owner + "\" itself instead.");
               }

               // Bug 77045 #7: no existing-entry check before layouts.add(...) below (contrast
               // removeObjects, whose own removeIf already dedups by name) -- re-adding an
               // already-placed object, or a brand-new layout-only object under an
               // already-used new name, silently created a second, duplicate entry.
               // Bug 78210: a print layout's header, footer and content share one object namespace
               // (the interactive Composer checks all three), so look in the other two regions as
               // well; "layouts" is the working list for the target region and still catches a
               // duplicate within one batch. A device layout has only its own list.
               String heldIn = layouts.stream().anyMatch(l -> name.equals(l.getName()))
                  ? regionName(region) : null;

               if(heldIn == null && layout instanceof PrintLayout print) {
                  for(int other : new int[] { VSLayoutService.HEADER, VSLayoutService.FOOTER,
                                              VSLayoutService.CONTENT })
                  {
                     if(other != region && vsLayoutService.getVSAssemblyLayouts(print, other)
                        .stream().anyMatch(l -> name.equals(l.getName())))
                     {
                        heldIn = regionName(other);
                        break;
                     }
                  }
               }

               if(heldIn != null) {
                  throw new IllegalArgumentException(
                     "edit_layout_objects add: \"" + name + "\" is already placed in layout \"" +
                     layoutName + "\" (" + heldIn + ") -- re-adding it would create a duplicate " +
                     "entry. Use op \"move_resize\" to reposition it instead, or \"remove\" it " +
                     "first if you mean to place it again.");
               }

               if(!existAssembly) {
                  // Bug 77045 #5: a brand-new layout-only object (text/image/pagebreak) has no
                  // device-layout renderer to consume it, and "pagebreak" in particular crashes
                  // with a raw ClassCastException below (createVSAssembly's own unconditional
                  // (PrintLayout) cast) rather than a named refusal -- refused here, before ever
                  // reaching that cast, uniformly for all three types rather than crashing on one
                  // and silently no-op'ing the others.
                  if(layout instanceof ViewsheetLayout) {
                     throw new IllegalArgumentException(
                        "edit_layout_objects add: \"" + layoutName + "\" is a device layout -- " +
                        "a new text/image/pagebreak layout-only object cannot be created there " +
                        "(no device-layout renderer consumes it, and \"pagebreak\" has no " +
                        "meaning outside a print layout's own pages). Add it to the print " +
                        "layout instead.");
                  }

                  event.setType(parseAssetType(object.get("type"), name));
                  assembly = vsLayoutService.createVSAssembly(event, layout, masterVs, name);

                  if(object.containsKey("text")) {
                     if(!(assembly instanceof TextVSAssembly textAssembly)) {
                        throw new IllegalArgumentException(
                           "edit_layout_objects add: \"" + name + "\" is not a text object -- " +
                           "\"text\" only applies to type \"text\".");
                     }

                     String text = String.valueOf(object.get("text"));
                     textAssembly.setValue(text);
                     textAssembly.setTextValue(text);
                  }
               }
               else if(object.containsKey("text")) {
                  throw new IllegalArgumentException(
                     "edit_layout_objects add: \"" + name + "\" is an existing viewsheet " +
                     "assembly -- \"text\" only applies when creating a new layout-only text " +
                     "object.");
               }

               VSAssemblyLayout assemblyLayout = vsLayoutService
                  .createAssemblyLayout(event, masterVs, name, assembly, existAssembly);
               layouts.add(assemblyLayout);
            }

            vsLayoutService.setVSAssemblyLayouts(layout, layouts, region);
         });
   }

   private static String regionName(int region) {
      return region == VSLayoutService.HEADER ? "region HEADER" :
         region == VSLayoutService.FOOTER ? "region FOOTER" : "region CONTENT";
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

   /**
    * True when some other assembly on the master has {@code name} as its data-tip view or pop
    * component. Reads the design-time values, like {@code VSDataTipService}. Flyover views are
    * deliberately not a dependency: removing one from a layout does not break a popup.
    */
   private static boolean isDataTipOrPopTarget(Viewsheet master, String name) {
      for(Assembly assembly : master.getAssemblies()) {
         if(assembly instanceof Viewsheet || name.equals(assembly.getName())) {
            continue;
         }

         AssemblyInfo info = assembly.getInfo();

         if(info instanceof TipVSAssemblyInfo tip &&
            tip.getTipOptionValue() == TipVSAssemblyInfo.VIEWTIP_OPTION &&
            name.equals(tip.getTipViewValue()))
         {
            return true;
         }

         if(info instanceof PopVSAssemblyInfo pop &&
            pop.getPopOptionValue() == PopVSAssemblyInfo.POP_OPTION &&
            name.equals(pop.getPopComponentValue()))
         {
            return true;
         }
      }

      return false;
   }

   private Map<String, Object> removeObjects(String sessionToken, Principal agent,
                                              String layoutName, int region,
                                              List<Map<String, Object>> objects, boolean confirmed)
      throws Exception
   {
      List<String> names = new ArrayList<>();

      for(Map<String, Object> object : objects) {
         names.add(requireName(object));
      }

      // Bug 78588: a name that is not placed used to be silently ignored (removeIf + ok), unlike
      // move_resize. Checked against the master before anything is removed, so one unknown name
      // fails the whole call. Reads the master directly -- resolveForRead/mutateLayout would mint
      // a preview clone, which the data-tip pre-check below deliberately avoids.
      RuntimeViewsheet placementMaster = viewsheetSessions.resolve(sessionToken, agent);
      AbstractLayout placementLayout = requireLayout(placementMaster, layoutName);

      for(String name : names) {
         resolveRegion(placementLayout, name, region, layoutName,
                       "nothing to remove; call get_layout to see what is placed.");
      }

      if(!confirmed) {
         List<String> dependent = new ArrayList<>();

         for(String name : names) {
            if(isDataTipOrPopTarget(placementMaster.getViewsheet(), name)) {
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
            Map<Integer, List<String>> byRegion = new LinkedHashMap<>();

            for(String name : names) {
               byRegion.computeIfAbsent(
                  resolveRegion(layout, name, region, layoutName,
                                "nothing to remove; call get_layout to see what is placed."),
                  r -> new ArrayList<>()).add(name);
            }

            for(Map.Entry<Integer, List<String>> entry : byRegion.entrySet()) {
               List<VSAssemblyLayout> layouts = new ArrayList<>(
                  vsLayoutService.getVSAssemblyLayouts(layout, entry.getKey()));
               layouts.removeIf(l -> entry.getValue().contains(l.getName()));
               vsLayoutService.setVSAssemblyLayouts(layout, layouts, entry.getKey());
            }
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

   /**
    * Bug 78588: the plugin always passes CONTENT, but a print layout's object may sit in its
    * HEADER or FOOTER (placed by a person; the add refusal of bug 78210 tells the caller to
    * move_resize/remove it). The requested region wins, so a name duplicated across regions
    * still acts on the requested one; only when it is absent there do HEADER/FOOTER get
    * searched. A name in both HEADER and FOOTER is ambiguous and fails loud. A device layout has
    * one object list and keeps the passed region.
    */
   private int resolveRegion(AbstractLayout layout, String name, int region, String layoutName,
                             String notPlacedTail)
   {
      if(vsLayoutService.findAssemblyLayout(layout, name, region).isPresent()) {
         return region;
      }

      if(layout instanceof PrintLayout) {
         boolean inHeader = region != VSLayoutService.HEADER &&
            vsLayoutService.findAssemblyLayout(layout, name, VSLayoutService.HEADER).isPresent();
         boolean inFooter = region != VSLayoutService.FOOTER &&
            vsLayoutService.findAssemblyLayout(layout, name, VSLayoutService.FOOTER).isPresent();

         if(inHeader && inFooter) {
            throw new IllegalArgumentException(
               "\"" + name + "\" is placed in both the HEADER and the FOOTER of layout \"" +
               layoutName + "\" (and not in " + regionName(region) + ") -- it is ambiguous " +
               "which one is meant; remove or rename one of them in the Composer first.");
         }

         if(inHeader) {
            return VSLayoutService.HEADER;
         }

         if(inFooter) {
            return VSLayoutService.FOOTER;
         }
      }

      throw new IllegalArgumentException(
         "\"" + name + "\" is not placed in layout \"" + layoutName + "\" -- " + notPlacedTail);
   }

   private VSAssemblyLayout requireAssemblyLayout(AbstractLayout layout, String objectName,
                                                   int region, String layoutName)
   {
      return vsLayoutService.findAssemblyLayout(layout, objectName, region)
         .orElseThrow(() -> new IllegalArgumentException(
            "\"" + objectName + "\" is not placed in layout \"" + layoutName + "\" -- add it " +
            "first with edit_layout_objects (op: \"add\")."));
   }

   /**
    * VSLayoutControllerService.moveResizeLayoutObjects (the human UI's own controller path)
    * already refuses a negative left/top -- but it does so by silently skipping the update,
    * which is a poor fit for an agent-facing API (a caller has no way to tell "nothing changed
    * because the input was invalid" from "nothing changed for some other reason"). This enforces
    * the same StyleBI-wide invariant (a layout object's position is clamped to the page origin;
    * no on-screen equivalent exists for a negative one) but fails loud instead, matching every
    * other validation in this class.
    */
   private static void requireNonNegativePosition(int x, int y) {
      if(x < 0 || y < 0) {
         throw new IllegalArgumentException(
            "edit_layout_objects: x/y must be non-negative -- got (" + x + ", " + y + "). " +
            "StyleBI clamps a layout object's position to the page origin.");
      }
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
}
