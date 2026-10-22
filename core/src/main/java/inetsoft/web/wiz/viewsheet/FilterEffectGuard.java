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
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.ContainerVSAssembly;
import inetsoft.uql.viewsheet.SelectionVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Tells the caller of {@code set_assembly_properties} when a write changed what a filter applies
 * (bugs #79194 and #79198). The write itself reports only "Set N property", so two silent effects
 * were invisible:
 * <ul>
 *   <li>a Calendar min/max write: the same request's selection refresh
 *       ({@code processChange -> processSelections -> CalendarVSAQuery.refreshSelectionValue ->
 *       setRange -> forceInRange}) rewrites the stored selected dates to fit the new bounds;</li>
 *   <li>{@code enabled:false} on a selection assembly, or on a container (Selection Container,
 *       Tab, ...): a disabled selection assembly contributes no condition while keeping its stored
 *       selection, and {@code AbstractVSAssembly.isEnabled()} folds in every container ancestor.</li>
 * </ul>
 * The two detectors are independent: each runs only for its own trigger and neither reads state
 * for any other write. Both are outcome based: a write that leaves the effect unchanged is silent,
 * and a read that cannot be made (a mock, a missing assembly) is silence, never a claim. The guard
 * never throws: the write it follows has already been applied, so a failure here must not turn a
 * successful call into an error.
 *
 * <p>Notes carry {@link #PREFIX}, which the plugin routes as a note rather than a script failure.
 * Only names and plain values are kept across the write: the toggle paths of the calendar dialog
 * replace the assembly's info, so identity is re-resolved by name afterwards.
 */
final class FilterEffectGuard {
   private FilterEffectGuard() {
   }

   /** Marks a server warning as a filter-effect note (the plugin keys on this exact prefix). */
   static final String PREFIX = "Filter note: ";

   private static final String ENABLED_SUFFIX = "generalPropPaneModel.enabled";
   private static final String CALENDAR_MIN = "calendarAdvancedPaneModel.min";
   private static final String CALENDAR_MAX = "calendarAdvancedPaneModel.max";
   private static final String CALENDAR_MODE = "calendarAdvancedPaneModel.viewMode";

   /**
    * What stood before the patch.
    *
    * @param calendarDates the raw stored date tokens of a calendar, or null
    * @param filters       names of the selection assemblies that applied a selection and are (or
    *                      sit under) the assembly whose {@code enabled} the patch writes
    * @param enabledPath   the resolved path of the live {@code enabled} the patch writes, or null
    */
   record Snapshot(String[] calendarDates, List<String> filters, String enabledPath) {
   }

   /**
    * Taken after the patch is resolved and before anything is written.
    *
    * @param resolved the resolved dotted paths the patch will write
    *
    * @return the snapshot, or null when the patch can't have either effect or nothing was readable
    */
   static Snapshot snapshot(RuntimeViewsheet rvs, String type, String assemblyName,
                            Collection<String> resolved)
   {
      try {
         Viewsheet vs = rvs == null ? null : rvs.getViewsheet();

         if(vs == null || assemblyName == null) {
            return null;
         }

         String[] dates = null;

         if("calendar".equals(type) && vs.getAssembly(assemblyName) instanceof CalendarVSAssembly c)
         {
            String[] stored = c.getDates();
            dates = stored == null ? null : stored.clone();
         }

         String enabledPath = path(resolved, ENABLED_SUFFIX);
         List<String> filters = List.of();

         if(enabledPath != null) {
            filters = new ArrayList<>();
            collect(vs, assemblyName, new HashSet<>(), filters);
         }

         return dates == null && filters.isEmpty() ? null :
            new Snapshot(dates, filters, enabledPath);
      }
      catch(Throwable ex) {
         LOG.debug("Filter-effect snapshot of {} skipped", assemblyName, ex);
         return null;
      }
   }

   /**
    * Taken after the write.
    *
    * @param written  the post-patch model that was written (the gate for {@code enabled})
    * @param resolved the resolved dotted paths the patch wrote
    *
    * @return the notes to send, each already carrying {@link #PREFIX}; empty when none
    */
   static List<String> apply(Snapshot before, RuntimeViewsheet rvs, String assemblyName,
                             Object written, Collection<String> resolved)
   {
      List<String> notes = new ArrayList<>();

      if(before == null) {
         return notes;
      }

      try {
         Viewsheet vs = rvs == null ? null : rvs.getViewsheet();

         if(vs != null && before.calendarDates() != null) {
            String note = calendarDatesNote(vs, assemblyName, before.calendarDates(), resolved);

            if(note != null) {
               notes.add(note);
            }
         }
      }
      catch(Throwable ex) {
         LOG.debug("Filter-effect calendar check of {} skipped", assemblyName, ex);
      }

      try {
         Viewsheet vs = rvs == null ? null : rvs.getViewsheet();

         if(vs != null && before.enabledPath() != null && !before.filters().isEmpty() &&
            isLiteralFalse(PropertyPath.get(written, before.enabledPath())))
         {
            List<String> stopped = new ArrayList<>();

            for(String name : before.filters()) {
               if(vs.getAssembly(name) instanceof SelectionVSAssembly s && !s.containsSelection()) {
                  stopped.add(name);
               }
            }

            if(!stopped.isEmpty()) {
               notes.add(enabledNote(assemblyName, stopped,
                                     !(vs.getAssembly(assemblyName) instanceof SelectionVSAssembly)));
            }
         }
      }
      catch(Throwable ex) {
         LOG.debug("Filter-effect enabled check of {} skipped", assemblyName, ex);
      }

      return notes;
   }

   // calendar dates moved (#79194)

   private static String calendarDatesNote(Viewsheet vs, String name, String[] before,
                                           Collection<String> resolved)
   {
      if(!(vs.getAssembly(name) instanceof CalendarVSAssembly c)) {
         return null;
      }

      String[] after = c.getDates();

      if(after == null || Arrays.equals(before, after)) {
         return null;
      }

      return calendarNote(name, before, after,
                          path(resolved, CALENDAR_MIN) != null || path(resolved, CALENDAR_MAX) != null,
                          path(resolved, CALENDAR_MODE) != null, c.isEnabled());
   }

   /**
    * @param boundWritten a min or max is in the patch
    * @param modeWritten  viewMode is in the patch (the dialog clears the dates on a mode change,
    *                     so the change is then not the bounds' doing)
    * @param enabled      whether the calendar applies its dates at all after the write
    */
   static String calendarNote(String name, String[] before, String[] after, boolean boundWritten,
                              boolean modeWritten, boolean enabled)
   {
      String verb = boundWritten && !modeWritten ? "moved to fit its min/max range" : "changed";
      StringBuilder note = new StringBuilder(PREFIX)
         .append("the selected dates of calendar '").append(name).append("' ").append(verb)
         .append(": ").append(describe(before)).append(" became ").append(describe(after))
         .append('.');

      if(enabled) {
         note.append(" The calendar now filters on the new dates; set_calendar_dates reselects ")
            .append("a different range.");
      }

      return note.toString();
   }

   private static String describe(String[] tokens) {
      if(tokens.length == 0) {
         return "(none)";
      }

      StringJoiner joiner = new StringJoiner(", ");

      for(String token : tokens) {
         joiner.add(String.valueOf(CalendarDisplayService.describeDate(token)));
      }

      return joiner.toString();
   }

   // enabled:false stops filtering (#79198)

   /**
    * Collects the selection assemblies at or under {@code name} that apply a selection now.
    * Visited-set walk: a container resolving to itself must not loop.
    */
   private static void collect(Viewsheet vs, String name, Set<String> visited, List<String> out) {
      if(name == null || !visited.add(name)) {
         return;
      }

      Object assembly = vs.getAssembly(name);

      if(assembly instanceof SelectionVSAssembly s && s.containsSelection()) {
         out.add(name);
      }

      if(assembly instanceof ContainerVSAssembly container) {
         String[] children = container.getAssemblies();

         if(children != null) {
            for(String child : children) {
               collect(vs, child, visited, out);
            }
         }
      }
   }

   /** Only a literal false is a claim; {@code $(x)}, a script or anything else is silence. */
   static boolean isLiteralFalse(Object value) {
      return value instanceof Boolean b ? !b :
         value instanceof String s && "false".equalsIgnoreCase(s.trim());
   }

   static String enabledNote(String name, List<String> stopped, boolean container) {
      if(!container) {
         return PREFIX + "'" + name + "' no longer filters: it is disabled, so its selection " +
            "(kept) is not applied to the assemblies it filters. Set enabled back to true to " +
            "apply it again.";
      }

      boolean one = stopped.size() == 1;

      return PREFIX + "'" + name + "' is disabled, so its child filter" + (one ? " " : "s ") +
         String.join(", ", stopped) + " no longer filter: " +
         (one ? "its selection is kept but is" : "their selections are kept but are") +
         " not applied to the assemblies " + (one ? "it" : "they") + " filter" + (one ? "s" : "") +
         ". Set enabled back to true to apply " + (one ? "it" : "them") + " again.";
   }

   private static String path(Collection<String> resolved, String suffix) {
      if(resolved != null) {
         for(String path : resolved) {
            if(path != null && path.endsWith(suffix)) {
               return path;
            }
         }
      }

      return null;
   }

   private static final Logger LOG = LoggerFactory.getLogger(FilterEffectGuard.class);
}
