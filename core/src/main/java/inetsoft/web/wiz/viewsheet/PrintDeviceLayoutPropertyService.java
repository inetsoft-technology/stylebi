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
import inetsoft.uql.viewsheet.vslayout.DeviceInfo;
import inetsoft.uql.viewsheet.vslayout.DeviceRegistry;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.dialog.ViewsheetPropertyDialogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * {@code set_print_layout}/{@code manage_device_layout} -- patches {@code ScreensPaneModel}
 * inside the existing {@code ViewsheetPropertyDialogModel} through {@link
 * ViewsheetPropertyDialogService#getViewsheetInfo}/{@link
 * ViewsheetPropertyDialogService#setViewsheetInfo}, the <b>same</b> method {@link
 * SheetPropertyService} already wraps for {@code set_viewsheet_properties}. A sibling of that
 * class, not a new dialog controller (Global Constraint 6).
 *
 * <p>{@code screensPane} is a whole-viewsheet property, not layout-object geometry, so this task
 * operates on the paired session's master runtime directly via {@link ViewsheetSessionService} --
 * Hazard 1 (spec #11) and {@link LayoutSessionService} do not apply here.
 *
 * <p>Two hazards this class exists to guard against:
 * <ul>
 *   <li><b>Hazard 3 ({@code scaleFont}).</b> {@link VSPrintLayoutDialogModel#getScaleFont()} is a
 *   bare {@code float}, defaulting to Java's {@code 0.0f}. {@code VSCompositeFormat.getFont()}
 *   multiplies every cell's font size by this value, so a print layout whose {@code scaleFont}
 *   reaches the write as {@code 0} renders every table/crosstab cell's text at font size zero --
 *   blank, not merely small. {@link #setPrintLayout} refuses an explicit {@code 0} before writing
 *   anything, and seeds a brand-new print layout's {@code scaleFont} at {@code 1.0f} before
 *   applying the caller's patch, so an omitted key on a first-ever {@code set_print_layout} call
 *   never falls through to Java's bare-field default.</li>
 *   <li><b>Risk 2 (device catalogue vs. device layout).</b> {@link DeviceRegistry}/{@link
 *   ScreenSizeDialogModel} are global, org-wide infrastructure with their own admin-gated REST
 *   surface ({@code DeviceController}), entirely outside this plugin's session model (Global
 *   Constraint 7). {@link #manageDeviceLayout} only ever creates/updates/deletes a named {@link
 *   VSDeviceLayoutDialogModel} -- a device LAYOUT on <em>this</em> viewsheet, referencing existing
 *   catalogue ids by {@code selectedDevices} -- and refuses a request shaped like a catalogue
 *   write (a {@code ScreenSizeDialogModel}-shaped payload with no device-layout {@code name})
 *   rather than silently forwarding it anywhere near {@code DeviceController}.</li>
 * </ul>
 */
@Service
public class PrintDeviceLayoutPropertyService {
   @Autowired
   public PrintDeviceLayoutPropertyService(ViewsheetSessionService sessions,
                                            ViewsheetPropertyDialogService dialogService,
                                            DeviceRegistry deviceRegistry)
   {
      this.sessions = sessions;
      this.dialogService = dialogService;
      this.deviceRegistry = deviceRegistry;
   }

   /**
    * Patches {@code screensPane.printLayout}'s fields (paper size, margins, orientation,
    * header/footer-from-edge, numbering start, custom size, units, {@code scaleFont}) --
    * read-merge-write, exactly like {@link SheetPropertyService#set}: every field not named in
    * {@code patch} survives exactly as read.
    */
   public void setPrintLayout(String sessionToken, Principal user, Map<String, Object> patch,
                               String linkUri) throws Exception
   {
      if(patch == null || patch.isEmpty()) {
         throw new IllegalArgumentException(
            "set_print_layout needs at least one print-layout property to set.");
      }

      // Validated BEFORE the mutation seam opens at all -- a refusal here must never reach
      // setViewsheetInfo and must never open an undo checkpoint for a write that never happened.
      if(patch.containsKey("scaleFont") && toFloat(patch.get("scaleFont")) == 0f) {
         throw new IllegalArgumentException(
            "set_print_layout: scaleFont cannot be 0 -- table and crosstab cell text renders " +
            "at font size 0 (i.e. blank) when a print layout's scale factor is zero. Omit " +
            "scaleFont to keep the default (1.0) or pass a positive value.");
      }

      Map<String, Object> resolvedPatch = new LinkedHashMap<>(patch);

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ViewsheetPropertyDialogModel model = dialogService.getViewsheetInfo(runtimeId, user);
         ScreensPaneModel screensPane = model.screensPane();
         VSPrintLayoutDialogModel printLayout = screensPane.getPrintLayout();

         if(printLayout == null) {
            // No print layout configured on this viewsheet yet: seed scaleFont at the safe
            // default BEFORE applying the patch, so an omitted "scaleFont" key lands on 1.0f
            // rather than the bare-field 0.0f a freshly-constructed model would otherwise carry
            // through to the write untouched (the Hazard-3 regression this class exists to
            // close).
            printLayout = new VSPrintLayoutDialogModel();
            printLayout.setScaleFont(1.0f);
         }

         applyPrintLayoutPatch(printLayout, resolvedPatch);
         screensPane.setPrintLayout(printLayout);
         requireUsableUnits(screensPane);
         dialogService.setViewsheetInfo(runtimeId, model, user, dispatcher, linkUri, null);
      });
   }

   /**
    * Creates, updates, or deletes a device LAYOUT (a named {@link VSDeviceLayoutDialogModel}) on
    * this viewsheet. {@code selectedDevices} picks ids from the existing, read-only device
    * catalogue ({@code list_layouts}) -- every id is validated against {@link
    * DeviceRegistry#getDevices()} before anything is written, and a request shaped like a
    * catalogue write (no device-layout {@code name}) is refused outright (Risk 2).
    *
    * @param action one of {@code create}, {@code update}, {@code delete} (case-insensitive).
    */
   public void manageDeviceLayout(String sessionToken, Principal user, String action,
                                   Map<String, Object> patch, String linkUri) throws Exception
   {
      if(action == null || action.isBlank()) {
         throw new IllegalArgumentException(
            "manage_device_layout needs an action: create, update, or delete.");
      }

      String normalizedAction = action.trim().toLowerCase();

      if(!VALID_ACTIONS.contains(normalizedAction)) {
         throw new IllegalArgumentException(
            "manage_device_layout: unknown action \"" + action + "\" -- expected one of " +
            VALID_ACTIONS + ".");
      }

      Map<String, Object> safePatch = patch == null ? Map.of() : patch;

      // Risk 2: refuse anything shaped like a device CATALOGUE write (a ScreenSizeDialogModel --
      // label/description/minWidth/maxWidth) rather than a device LAYOUT write (a
      // VSDeviceLayoutDialogModel -- name/mobileOnly/selectedDevices). Every device-layout
      // request carries "name"; a catalogue-entry request never does, since catalogue entries are
      // identified by "id"/"label", not "name". This check happens before any DeviceRegistry
      // lookup or write, since this tool has no business forwarding such a request anywhere near
      // DeviceController at all.
      boolean looksLikeCatalogueWrite = !safePatch.containsKey("name") &&
         (safePatch.containsKey("label") || safePatch.containsKey("minWidth") ||
          safePatch.containsKey("maxWidth") || safePatch.containsKey("description"));

      if(looksLikeCatalogueWrite) {
         throw new IllegalArgumentException(
            "manage_device_layout manages device LAYOUTS on this viewsheet, not the device " +
            "catalogue itself. Creating or editing a device size (id/label/min/max width) is " +
            "an org-admin operation outside this tool's scope -- that has to go through " +
            "Composer's Device admin screen. Pick an existing device id from list_layouts's " +
            "catalogue for selectedDevices instead.");
      }

      String name = asString(safePatch.get("name"));

      if(name == null || name.isBlank()) {
         throw new IllegalArgumentException(
            "manage_device_layout needs a \"name\" identifying the device layout.");
      }

      List<String> selectedDevices = asStringList(safePatch.get("selectedDevices"));

      if(selectedDevices != null) {
         Set<String> knownIds = Arrays.stream(deviceRegistry.getDevices())
            .map(DeviceInfo::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

         for(String id : selectedDevices) {
            if(!knownIds.contains(id)) {
               throw new IllegalArgumentException(
                  "manage_device_layout: \"" + id + "\" is not a known device id -- valid ids " +
                  "are " + knownIds + " (see list_layouts's device catalogue).");
            }
         }
      }

      Boolean mobileOnly = safePatch.containsKey("mobileOnly")
         ? Boolean.TRUE.equals(safePatch.get("mobileOnly")) : null;

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ViewsheetPropertyDialogModel model = dialogService.getViewsheetInfo(runtimeId, user);
         ScreensPaneModel screensPane = model.screensPane();
         List<VSDeviceLayoutDialogModel> layouts = screensPane.getDeviceLayouts();
         VSDeviceLayoutDialogModel existing = layouts.stream()
            .filter(l -> name.equals(l.getName()))
            .findFirst()
            .orElse(null);

         switch(normalizedAction) {
            case "create": {
               if(existing != null) {
                  throw new IllegalArgumentException(
                     "manage_device_layout: a device layout named \"" + name +
                     "\" already exists -- use action \"update\" instead.");
               }

               VSDeviceLayoutDialogModel created = new VSDeviceLayoutDialogModel();
               created.setName(name);
               created.setMobileOnly(Boolean.TRUE.equals(mobileOnly));
               created.setSelectedDevices(
                  selectedDevices != null ? selectedDevices : new ArrayList<>());
               layouts.add(created);
               break;
            }
            case "update": {
               if(existing == null) {
                  throw new IllegalArgumentException(
                     "manage_device_layout: no device layout named \"" + name + "\" -- call " +
                     "list_layouts to see what's defined on this viewsheet.");
               }

               if(mobileOnly != null) {
                  existing.setMobileOnly(mobileOnly);
               }

               if(selectedDevices != null) {
                  existing.setSelectedDevices(selectedDevices);
               }

               break;
            }
            case "delete": {
               if(existing == null) {
                  throw new IllegalArgumentException(
                     "manage_device_layout: no device layout named \"" + name + "\" -- call " +
                     "list_layouts to see what's defined on this viewsheet.");
               }

               layouts.remove(existing);
               break;
            }
            default:
               // Unreachable: normalizedAction was already validated against VALID_ACTIONS above.
               throw new IllegalStateException("Unknown action: " + normalizedAction);
         }

         screensPane.setDeviceLayouts(layouts);
         // Not incidental: this method never touches the print layout, but setViewsheetInfo
         // computes the page size from it unconditionally, so a viewsheet carrying a persisted
         // null unit takes manage_device_layout down with it. Guarding only setPrintLayout above
         // would leave that viewsheet permanently unusable with no caller-side repair.
         requireUsableUnits(screensPane);
         dialogService.setViewsheetInfo(runtimeId, model, user, dispatcher, linkUri, null);
      });
   }

   /** Applies every key present in {@code patch} onto {@code printLayout}; leaves the rest. */
   private void applyPrintLayoutPatch(VSPrintLayoutDialogModel printLayout,
                                       Map<String, Object> patch)
   {
      for(Map.Entry<String, Object> entry : patch.entrySet()) {
         Object value = entry.getValue();

         switch(entry.getKey()) {
            case "paperSize":
               printLayout.setPaperSize(asString(value));
               break;
            case "marginTop":
               printLayout.setMarginTop(toDouble(value));
               break;
            case "marginLeft":
               printLayout.setMarginLeft(toDouble(value));
               break;
            case "marginBottom":
               printLayout.setMarginBottom(toDouble(value));
               break;
            case "marginRight":
               printLayout.setMarginRight(toDouble(value));
               break;
            case "footerFromEdge":
               printLayout.setFooterFromEdge(toFloat(value));
               break;
            case "headerFromEdge":
               printLayout.setHeaderFromEdge(toFloat(value));
               break;
            case "landscape":
               printLayout.setLandscape(Boolean.TRUE.equals(value));
               break;
            case "scaleFont":
               printLayout.setScaleFont(toFloat(value));
               break;
            case "numberingStart":
               printLayout.setNumberingStart(toInt(value));
               break;
            case "customWidth":
               printLayout.setCustomWidth(toDouble(value));
               break;
            case "customHeight":
               printLayout.setCustomHeight(toDouble(value));
               break;
            case "units":
               printLayout.setUnits(asString(value));
               break;
            default:
               throw new IllegalArgumentException(
                  "set_print_layout: unknown print-layout property \"" + entry.getKey() + "\".");
         }
      }
   }

   /**
    * Guarantees the print layout carries a usable {@code units}, after the patch has been applied
    * and before anything writes it.
    *
    * <p>A null here is fatal rather than merely wrong: it reaches
    * {@code ViewsheetPropertyDialogService}'s {@code printInfo.setUnit(...)} and then
    * {@code VSLayoutService.getPLayoutSize}'s {@code switch(unit)} -- which recognises only
    * {@code "inches"} and {@code "mm"} -- where it throws NPE. That throw lands <b>after</b> the
    * patch has already mutated the live model, so the failed call persists a half-written layout,
    * and every later write on the viewsheet then fails while re-reading it, taking
    * {@code manage_device_layout} down with it since both share {@code setViewsheetInfo}.
    *
    * <p>Called on <b>every</b> write path in this class -- {@code setPrintLayout} and
    * {@code manageDeviceLayout} both -- rather than only where a brand-new layout is built,
    * because there are three ways to reach the write with a null and only one of them involves
    * creating a layout: an omitted {@code "units"} key on a new layout; a caller passing
    * {@code "units": null} explicitly, which {@code applyPrintLayoutPatch} would otherwise write
    * straight over any default; and a layout persisted before this guard existed, which
    * {@code manageDeviceLayout} would otherwise carry into the write untouched while never
    * looking at the print layout at all.
    *
    * <p>{@code "inches"} matches what {@code WizPrintLayoutBuilder} already hardcodes for the
    * same purpose. An unrecognised non-null value is left alone: {@code getPLayoutSize} falls
    * through its {@code default} for those, which is a wrong scale but not a crash, and silently
    * rewriting a caller's explicit value would be its own surprise.
    */
   private static void requireUsableUnits(ScreensPaneModel screensPane) {
      VSPrintLayoutDialogModel printLayout = screensPane.getPrintLayout();

      if(printLayout == null) {
         // No print layout at all is the safe state, not a broken one: getPrintPageSize has
         // nothing to compute from and never reaches the switch.
         return;
      }

      String units = printLayout.getUnits();

      if(units == null || units.isBlank()) {
         printLayout.setUnits("inches");
      }
   }

   private static String asString(Object value) {
      return value == null ? null : String.valueOf(value);
   }

   @SuppressWarnings("unchecked")
   private static List<String> asStringList(Object value) {
      if(value == null) {
         return null;
      }

      if(value instanceof List<?> list) {
         return list.stream().map(String::valueOf).collect(Collectors.toList());
      }

      throw new IllegalArgumentException(
         "manage_device_layout: selectedDevices must be a list of device ids.");
   }

   private static float toFloat(Object value) {
      if(value instanceof Number number) {
         return number.floatValue();
      }

      return Float.parseFloat(String.valueOf(value));
   }

   private static double toDouble(Object value) {
      if(value instanceof Number number) {
         return number.doubleValue();
      }

      return Double.parseDouble(String.valueOf(value));
   }

   private static int toInt(Object value) {
      if(value instanceof Number number) {
         return number.intValue();
      }

      return Integer.parseInt(String.valueOf(value));
   }

   private static final Set<String> VALID_ACTIONS = Set.of("create", "update", "delete");

   private final ViewsheetSessionService sessions;
   private final ViewsheetPropertyDialogService dialogService;
   private final DeviceRegistry deviceRegistry;
}
