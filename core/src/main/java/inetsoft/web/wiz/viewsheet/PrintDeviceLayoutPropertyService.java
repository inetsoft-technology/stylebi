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
import inetsoft.report.internal.PaperSize;
import inetsoft.util.Catalog;
import inetsoft.uql.viewsheet.vslayout.DeviceInfo;
import inetsoft.uql.viewsheet.vslayout.DeviceRegistry;
import inetsoft.uql.viewsheet.vslayout.PrintInfo;
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
            // No print layout configured on this viewsheet yet: seed every field at the same
            // default viewsheet-print-layout-dialog.component.ts's own ngOnInit() uses for a
            // brand-new model, BEFORE applying the caller's patch, so an omitted key lands on
            // that default rather than the bare Java field default (0.0/0.0f/null) a
            // freshly-constructed model would otherwise carry through to the write untouched.
            // scaleFont was the first field this class closed this gap for (the Hazard-3
            // regression); paperSize/margins/header-footer-from-edge are the same shape of bug --
            // an omitted paperSize in particular resolves to PaperSize.getSize(null) => null,
            // which persists a degenerate 0x0 page.
            printLayout = new VSPrintLayoutDialogModel();
            // Matches viewsheet-print-layout-dialog.component.ts's ngOnInit() default verbatim,
            // not PaperSize.getSizeStrs()[0] -- the dialog's default is a product choice, not
            // "whichever size happens to be first in this enum", and the two should not be
            // allowed to drift apart just because the array order changed.
            printLayout.setPaperSize("Letter [8.5x11 in]");
            printLayout.setMarginTop(1);
            printLayout.setMarginBottom(1);
            printLayout.setMarginLeft(1);
            printLayout.setMarginRight(1);
            printLayout.setHeaderFromEdge(0.5f);
            printLayout.setFooterFromEdge(0.75f);
            printLayout.setScaleFont(1.0f);
            // The values above are inch values; label them so, so the unit conversion below
            // has a "from" unit for a freshly seeded layout too.
            printLayout.setUnits("inches");
         }

         if(resolvedPatch.containsKey("units")) {
            // Bug 77045 #4: a units-only (or units-plus-something-else) patch must PRESERVE the
            // physical size of every dimension field already on this print layout, not silently
            // relabel the same raw number under a new unit -- matching
            // viewsheet-print-layout-dialog.component.ts's own unitChanged(), which rescales every
            // dimension field by the ratio between the old and new unit whenever the unit picker
            // changes. A field the SAME patch also sets explicitly is left untouched here: that
            // value is already expressed in the NEW unit by the caller, and applyPrintLayoutPatch
            // (which runs right after this) overwrites it anyway -- rescaling it first would
            // double-convert.
            convertUnitsIfChanged(printLayout, resolvedPatch);
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
      // VSDeviceLayoutDialogModel -- name/mobileOnly/selectedDevices). Bug 77045 #2: this used to
      // also require "name" to be ABSENT before firing -- but layoutTools.ts's manage_device_layout
      // tool always injects "name" into every wire request (it identifies the device layout being
      // create/update/delete'd), for every action, so that precondition can never be true for a
      // call coming through this plugin's own tool surface, making this whole check permanently
      // dead code from that caller. No legitimate device-layout patch ever needs
      // label/minWidth/maxWidth/description regardless of whether "name" also happens to be
      // present, so the check now fires on the catalogue-only fields alone.
      boolean looksLikeCatalogueWrite = safePatch.containsKey("label") ||
         safePatch.containsKey("minWidth") || safePatch.containsKey("maxWidth") ||
         safePatch.containsKey("description");

      if(looksLikeCatalogueWrite) {
         throw new IllegalArgumentException(
            "manage_device_layout manages device LAYOUTS on this viewsheet, not the device " +
            "catalogue itself. Creating or editing a device size (id/label/min/max width) is " +
            "an org-admin operation outside this tool's scope -- that has to go through " +
            "Composer's Device admin screen. Pick an existing device id from list_layouts's " +
            "catalogue for selectedDevices instead.");
      }

      // Bug 77045 #2 (Java default:throw parity with applyPrintLayoutPatch): manageDeviceLayout
      // only ever reads "name"/"selectedDevices"/"mobileOnly" off safePatch by exact key name --
      // anything else (a typo like "selectedDevice", or an inapplicable print-layout field like
      // "scaleFont"/"description" not already caught above) used to be silently never read, with
      // ok:true returned regardless.
      for(String key : safePatch.keySet()) {
         if(!DEVICE_LAYOUT_PATCH_KEYS.contains(key)) {
            throw new IllegalArgumentException(
               "manage_device_layout: unknown device-layout property \"" + key + "\" -- expected " +
               "one of " + DEVICE_LAYOUT_PATCH_KEYS + ".");
         }
      }

      String name = asString(safePatch.get("name"));

      if(name == null || name.isBlank()) {
         throw new IllegalArgumentException(
            "manage_device_layout needs a \"name\" identifying the device layout.");
      }

      // viewsheet-device-layout-dialog.component.ts's own duplicateName() and
      // form-validators.ts's validLayoutName refuse these client-side, but neither backend path
      // re-checks them, so a non-Angular caller could otherwise write either. Create-only,
      // matching the dialog: this tool cannot rename an existing layout (name IS its identity),
      // so update/delete never introduce a new name value.
      if("create".equals(normalizedAction)) {
         if("Master".equals(name)) {
            throw new IllegalArgumentException(
               "manage_device_layout: \"name\" cannot be \"Master\" -- that name is reserved " +
               "for the viewsheet's own Master view.");
         }

         // The dialog's reservedName() blocks a second reserved literal too: the print layout's
         // own display name, "_#(js:Print Layout)" in Angular -- resolved through the same
         // Catalog mechanism server-side, so this stays correct if a translation for the key is
         // ever added (today it falls back to the literal "Print Layout", matching what the
         // dialog currently shows in every locale this community edition ships).
         String reservedPrintLayoutName = Catalog.getCatalog(user).getString("Print Layout");

         if(reservedPrintLayoutName.equals(name)) {
            throw new IllegalArgumentException(
               "manage_device_layout: \"name\" cannot be \"" + name + "\" -- that name is " +
               "reserved for the viewsheet's print layout.");
         }

         if(DEVICE_LAYOUT_INVALID_NAME_CHARS.matcher(name).find()) {
            throw new IllegalArgumentException(
               "manage_device_layout: \"name\" contains a character StyleBI does not allow in " +
               "a layout name (/ \\ % ^ ~ < > * | ? \" ,) -- got \"" + name + "\".");
         }
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

      // Bug 77045 #3: viewsheet-device-layout-dialog.component.ts's own deviceSelected() disables
      // the OK button until at least one device is checked, but neither backend path re-enforces
      // it -- a device layout scoped to zero devices persisted silently. The two actions need
      // DIFFERENT rules, not one shared check: "create" has no existing device list to fall back
      // on, so an omitted OR explicitly-empty selectedDevices is equally a zero-device layout and
      // both must be refused; "update" must still allow OMITTING selectedDevices entirely to mean
      // "leave the existing devices untouched" (ordinary partial-update semantics) -- only an
      // EXPLICIT empty list on update is refused.
      if("create".equals(normalizedAction) &&
         (selectedDevices == null || selectedDevices.isEmpty()))
      {
         throw new IllegalArgumentException(
            "manage_device_layout: \"selectedDevices\" must include at least one device id on " +
            "create -- StyleBI's own dialog disables its OK button until at least one device is " +
            "checked. Call list_layouts for the available device ids.");
      }

      if("update".equals(normalizedAction) && selectedDevices != null && selectedDevices.isEmpty())
      {
         throw new IllegalArgumentException(
            "manage_device_layout: \"selectedDevices\" cannot be updated to an empty list -- a " +
            "device layout must stay scoped to at least one device. Omit \"selectedDevices\" " +
            "entirely to leave the existing devices untouched.");
      }

      // Bug 77045 #1: a JSON string "true"/"false" reaching Boolean.TRUE.equals(...) resolves to
      // false regardless of which string was sent -- the same shape of bug this class's own
      // Hazard-3/L11-#13 precedent already guards other fields against, just missed here.
      Boolean mobileOnly = safePatch.containsKey("mobileOnly")
         ? toNullableBoolean("mobileOnly", safePatch.get("mobileOnly")) : null;

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
               // NEW-1: every OTHER path that ever constructs a VSDeviceLayoutDialogModel
               // assigns an id before it is ever posted -- the Angular dialog does so
               // client-side (viewsheet-device-layout-dialog.component.ts:87), and
               // ViewsheetPropertyDialogService.setViewsheetInfo never generates one itself,
               // only ever copying whatever the incoming DTO already carries (setID(layout
               // .getId())). This was the one path that never assigned one, so the persisted
               // ViewsheetLayout's own id stayed null -- and every LATER write in the same
               // session then NPE'd matching a null id against oldLayouts (setViewsheetInfo:482).
               // A random UUID, prefixed to match the Angular dialog's own naming convention for
               // a human reading the persisted XML, is sufficient: nothing anywhere parses or
               // derives structure from this id, it is only ever compared with .equals().
               created.setId("ViewsheetLayout-" + java.util.UUID.randomUUID());
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

   /**
    * Rescales {@code printLayout}'s margins, header/footer-from-edge, and custom width/height by
    * the ratio between its OLD units and the patch's NEW units -- the server-side mirror of
    * {@code viewsheet-print-layout-dialog.component.ts}'s {@code unitChanged()}. Only touches a
    * field the patch does NOT also set explicitly; {@code applyPrintLayoutPatch} (called right
    * after this) applies the caller's own explicit fields on top, already expressed in the new
    * unit. A no-op when {@code printLayout} has no usable old units yet (nothing to convert from)
    * or the new units are the same as the old (case-insensitively) -- not a real unit change.
    */
   private static void convertUnitsIfChanged(VSPrintLayoutDialogModel printLayout,
                                              Map<String, Object> patch)
   {
      String oldUnits = printLayout.getUnits();
      String newUnits = asString(patch.get("units"));

      // A layout persisted before units were recorded carries inch values with no unit label;
      // treat blank as "inches", matching requireUsableUnits and the dialog's own default.
      if(oldUnits == null || oldUnits.isBlank()) {
         oldUnits = "inches";
      }

      if(newUnits == null || newUnits.isBlank() || oldUnits.trim().equalsIgnoreCase(newUnits.trim())) {
         return;
      }

      double ratio = PrintInfo.getUnitRatio(oldUnits.trim().toLowerCase()) /
         PrintInfo.getUnitRatio(newUnits.trim().toLowerCase());

      if(!patch.containsKey("marginTop")) {
         printLayout.setMarginTop(printLayout.getMarginTop() * ratio);
      }

      if(!patch.containsKey("marginBottom")) {
         printLayout.setMarginBottom(printLayout.getMarginBottom() * ratio);
      }

      if(!patch.containsKey("marginLeft")) {
         printLayout.setMarginLeft(printLayout.getMarginLeft() * ratio);
      }

      if(!patch.containsKey("marginRight")) {
         printLayout.setMarginRight(printLayout.getMarginRight() * ratio);
      }

      if(!patch.containsKey("headerFromEdge")) {
         printLayout.setHeaderFromEdge((float) (printLayout.getHeaderFromEdge() * ratio));
      }

      if(!patch.containsKey("footerFromEdge")) {
         printLayout.setFooterFromEdge((float) (printLayout.getFooterFromEdge() * ratio));
      }

      // Rescaled unconditionally, even when paperSize is a named preset (where
      // ViewsheetPropertyDialogService re-derives the persisted size from PaperSize.getSize(...)
      // on every write regardless): a preset makes this dead work, harmlessly overwritten
      // downstream, but a "(Custom Size)" paperSize makes it load-bearing -- customWidth/
      // customHeight share the exact same units-reinterpretation defect margins do, and are not
      // protected by any self-correction the way a named preset's own size is.
      if(!patch.containsKey("customWidth")) {
         printLayout.setCustomWidth(printLayout.getCustomWidth() * ratio);
      }

      if(!patch.containsKey("customHeight")) {
         printLayout.setCustomHeight(printLayout.getCustomHeight() * ratio);
      }
   }

   /** Applies every key present in {@code patch} onto {@code printLayout}; leaves the rest. */
   private void applyPrintLayoutPatch(VSPrintLayoutDialogModel printLayout,
                                       Map<String, Object> patch)
   {
      for(Map.Entry<String, Object> entry : patch.entrySet()) {
         Object value = entry.getValue();

         switch(entry.getKey()) {
            case "paperSize":
               printLayout.setPaperSize(PaperSize.canonicalize(asString(value)));
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

   /**
    * Accepts a real {@code Boolean}, or the strings {@code "true"}/{@code "false"} (any case) --
    * refuses anything else by name rather than letting {@code Boolean.TRUE.equals(...)} silently
    * resolve a stringified value to {@code false} (bug 77045 #1).
    */
   private static Boolean toNullableBoolean(String field, Object value) {
      if(value == null) {
         return null;
      }

      if(value instanceof Boolean bool) {
         return bool;
      }

      if(value instanceof String str) {
         String normalized = str.trim().toLowerCase();

         if("true".equals(normalized)) {
            return Boolean.TRUE;
         }

         if("false".equals(normalized)) {
            return Boolean.FALSE;
         }
      }

      throw new IllegalArgumentException(
         "manage_device_layout: \"" + field + "\" must be a boolean -- got " + value + ".");
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

   // The only three keys manageDeviceLayout itself ever reads off a patch -- everything else is
   // either a device-CATALOGUE field (caught earlier, with a more specific message) or unknown.
   private static final Set<String> DEVICE_LAYOUT_PATCH_KEYS =
      Set.of("name", "selectedDevices", "mobileOnly");

   // Mirrors form-validators.ts's validLayoutName exactly.
   private static final java.util.regex.Pattern DEVICE_LAYOUT_INVALID_NAME_CHARS =
      java.util.regex.Pattern.compile("[/\\\\%^~<>*|?\",]");

   private final ViewsheetSessionService sessions;
   private final ViewsheetPropertyDialogService dialogService;
   private final DeviceRegistry deviceRegistry;
}
