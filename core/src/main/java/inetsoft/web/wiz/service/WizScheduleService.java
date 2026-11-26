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
package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.RepletRequest;
import inetsoft.sree.schedule.ScheduleCondition;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.schedule.ViewsheetAction;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

/**
 * Creates, lists, updates and deletes the caller's own dashboard schedule tasks for the
 * composer-chat plugin (Redmine #82413), the non-admin counterpart of the admin plugin's schedule
 * task changesets.
 *
 * <p>The portal Schedule endpoints are not reachable with the plugin's token, so this builds the
 * same task the Schedule tab builds (a time condition and one dashboard action that emails the
 * exported dashboard) and stores it through {@link ScheduleManager}.</p>
 *
 * <p><b>Permissions.</b> The permission checks of the portal path are approved for a wiz
 * principal, so they are repeated here on the real user ({@link WizDashboardAccess}): the Schedule
 * tab and scheduler access, a read of the dashboard, and the schedule options
 * ({@code ScheduleTaskService.sanitizeConditions/sanitizeAction}). A task belongs to the caller by
 * construction: its owner is the caller's identity, never a request value, and a task is listed,
 * changed or deleted only when its owner is the caller. A task of another user is reported as not
 * found. Tasks are created in the root task folder.</p>
 */
@Service
public class WizScheduleService {
   public WizScheduleService(WizDashboardAccess access, ScheduleManager scheduleManager,
                             ScheduleTaskService scheduleTaskService,
                             ViewsheetService viewsheetService)
   {
      this.access = access;
      this.scheduleManager = scheduleManager;
      this.scheduleTaskService = scheduleTaskService;
      this.viewsheetService = viewsheetService;
   }

   /** The formats the schedule email action offers and the default among them. */
   public FormatOptions getFormatOptions(Principal user) {
      access.requireScheduleAccess(user);
      List<String> formats = scheduleTaskService.getVSMailFormatNames();
      return new FormatOptions(formats, defaultFormat(formats));
   }

   /** The caller's own dashboard schedule tasks, optionally of one dashboard. */
   public List<TaskView> list(String path, String scope, Principal user) throws Exception {
      access.requireScheduleAccess(user);
      IdentityID caller = WizDashboardAccess.caller(user);
      AssetEntry filter = path == null || path.isBlank() ?
         null : access.requireReadableDashboard(path, scope, user);
      List<TaskView> views = new ArrayList<>();
      Vector<ScheduleTask> tasks = scheduleManager.getScheduleTasks(user);

      for(ScheduleTask task : tasks) {
         ViewsheetAction action = dashboardAction(task);

         if(!caller.equals(task.getOwner()) || action == null) {
            continue;
         }

         TaskView view = toView(task, action);

         if(filter == null ||
            (view.path().equals(filter.getPath()) && view.scope().equals(scopeName(filter))))
         {
            views.add(view);
         }
      }

      return views;
   }

   /** One of the caller's own tasks. */
   public TaskView get(String name, Principal user) {
      access.requireScheduleAccess(user);
      ScheduleTask task = requireOwnTask(name, user);
      return toView(task, requireDashboardAction(task, name));
   }

   /** Creates a task for the caller; an existing task is never overwritten. */
   public TaskView create(TaskRequest request, Principal user, String linkUri) throws Exception {
      if(request == null) {
         throw new IllegalArgumentException("The request body is required.");
      }

      access.requireScheduleAccess(user);
      IdentityID caller = WizDashboardAccess.caller(user);
      AssetEntry entry = access.requireReadableDashboard(request.path(), request.scope(), user);
      String name = requireName(request.name());
      String taskId = ScheduleManager.getTaskId(caller.convertToKey(), name);

      if(scheduleManager.getScheduleTask(taskId) != null) {
         throw new IllegalArgumentException(
            "A schedule task named \"" + name + "\" already exists; choose another name, or " +
            "update it.");
      }

      ScheduleTask task = new ScheduleTask();
      task.setName(name);
      task.setOwner(caller);
      task.setEnabled(request.enabled() == null || request.enabled());
      task.setDeleteIfNoMoreRun(false);
      task.setDescription(request.description());
      task.addCondition(buildCondition(request));
      ViewsheetAction action = new ViewsheetAction();
      fillAction(action, entry, request, caller, user, true);
      task.addAction(action, linkUri);
      sanitize(task, new ScheduleTask(), null, user);
      scheduleManager.setScheduleTask(task.getTaskId(), task, null, user);
      return toView(task, action);
   }

   /**
    * Changes one of the caller's own tasks. Only the fields the request carries change; the
    * schedule changes when {@code frequency} is given, the email action when any of its fields
    * is.
    */
   public TaskView update(String name, TaskRequest request, Principal user, String linkUri)
      throws Exception
   {
      if(request == null) {
         throw new IllegalArgumentException("The request body is required.");
      }

      access.requireScheduleAccess(user);
      ScheduleTask stored = requireOwnTask(name, user);
      requireDashboardAction(stored, name);
      IdentityID caller = WizDashboardAccess.caller(user);
      ScheduleTask candidate = stored.clone();
      ViewsheetAction storedAction = (ViewsheetAction) stored.getAction(0);
      ViewsheetAction action = (ViewsheetAction) storedAction.clone();
      AssetEntry entry = AssetEntry.createAssetEntry(action.getViewsheet());

      if(request.path() != null && !request.path().isBlank()) {
         entry = access.requireReadableDashboard(request.path(), request.scope(), user);
      }
      else {
         // the dashboard of the task must still be one the caller can read
         entry = access.requireReadableDashboard(entry.getPath(), scopeName(entry), user);
      }

      if(request.description() != null) {
         candidate.setDescription(request.description());
      }

      if(request.enabled() != null) {
         candidate.setEnabled(request.enabled());
      }

      if(request.frequency() != null && !request.frequency().isBlank()) {
         for(int i = candidate.getConditionCount() - 1; i >= 0; i--) {
            candidate.removeCondition(i);
         }

         candidate.addCondition(buildCondition(request));
      }

      fillAction(action, entry, request, caller, user, false);

      for(int i = candidate.getActionCount() - 1; i >= 0; i--) {
         candidate.removeAction(i);
      }

      candidate.addAction(action, linkUri);
      sanitize(candidate, stored, storedAction, user);
      scheduleManager.setScheduleTask(candidate.getTaskId(), candidate, folderOf(stored), user);
      return toView(candidate, action);
   }

   /** Deletes one of the caller's own tasks. */
   public void delete(String name, Principal user) throws Exception {
      access.requireScheduleAccess(user);
      ScheduleTask task = requireOwnTask(name, user);
      requireDashboardAction(task, name);
      scheduleManager.removeScheduleTask(task.getTaskId(), user, true);
   }

   private ScheduleTask requireOwnTask(String name, Principal user) {
      String clean = requireName(name);
      IdentityID caller = WizDashboardAccess.caller(user);
      // the id always starts with the caller's key, so a name cannot address another user's task
      ScheduleTask task = scheduleManager.getScheduleTask(
         ScheduleManager.getTaskId(caller.convertToKey(), clean));

      // a missing task and one of another user are not told apart
      if(task == null || !caller.equals(task.getOwner())) {
         throw new IllegalArgumentException(
            "No schedule task named \"" + clean + "\" of yours; list_dashboard_schedules " +
            "returns your tasks.");
      }

      return task;
   }

   /** A task this tool can change: exactly one action, which exports a dashboard. */
   private static ViewsheetAction requireDashboardAction(ScheduleTask task, String name) {
      ViewsheetAction action = dashboardAction(task);

      if(action == null || task.getActionCount() != 1) {
         throw new IllegalArgumentException(
            "The schedule task \"" + name + "\" was not made for a single dashboard email; " +
            "change it in the Schedule tab.");
      }

      return action;
   }

   private static ViewsheetAction dashboardAction(ScheduleTask task) {
      for(int i = 0; i < task.getActionCount(); i++) {
         if(task.getAction(i) instanceof ViewsheetAction action) {
            return action;
         }
      }

      return null;
   }

   private static String requireName(String name) {
      if(name == null || name.isBlank()) {
         throw new IllegalArgumentException("'name' is required.");
      }

      String clean = name.trim();

      if(clean.indexOf('^') >= 0 || clean.indexOf('/') >= 0 || clean.indexOf('\\') >= 0) {
         throw new IllegalArgumentException(
            "'name' must not contain '^', '/' or '\\' (got \"" + clean + "\").");
      }

      return clean;
   }

   /** Applies the schedule options the real user may not set, as the Schedule tab does. */
   private void sanitize(ScheduleTask task, ScheduleTask original, ViewsheetAction originalAction,
                         Principal user)
   {
      Principal real = WizDashboardAccess.realUser(user);
      scheduleTaskService.sanitizeConditions(task, original, real);

      for(int i = 0; i < task.getActionCount(); i++) {
         scheduleTaskService.sanitizeAction(task.getAction(i), originalAction, real);
      }

      if(task.getConditionCount() == 0) {
         throw new SecurityException(
            "You do not have permission to use this kind of schedule (a run-once or hourly " +
            "schedule needs the start time option); use a daily, weekly or monthly one.");
      }

      for(int i = 0; i < task.getActionCount(); i++) {
         if(task.getAction(i) instanceof ViewsheetAction action &&
            (action.getEmails() == null || action.getEmails().isEmpty()))
         {
            throw new SecurityException(
               "You do not have permission to deliver a dashboard by email from a schedule " +
               "(the email delivery schedule option).");
         }
      }
   }

   private void fillAction(ViewsheetAction action, AssetEntry entry, TaskRequest request,
                           IdentityID caller, Principal user, boolean create)
      throws Exception
   {
      action.setViewsheet(entry.toIdentifier());

      if(create || action.getViewsheetRequest() == null) {
         action.setViewsheetRequest(new RepletRequest());
      }

      boolean emailGiven = request.to() != null || request.cc() != null || request.bcc() != null;

      if(create || emailGiven) {
         action.setEmails(WizDashboardDeliveryService.joinAddresses(request.to(), "to", true));
         action.setCCAddresses(WizDashboardDeliveryService.joinAddresses(
            request.cc(), "cc", false));
         action.setBCCAddresses(WizDashboardDeliveryService.joinAddresses(
            request.bcc(), "bcc", false));
      }

      if(create || request.subject() != null) {
         action.setSubject(request.subject() == null ? "" : request.subject());
      }

      if(create || request.message() != null) {
         action.setMessage(request.message() == null ? "" : request.message());
      }

      if(create || (request.format() != null && !request.format().isBlank())) {
         action.setFileFormat(resolveFormat(request.format()));
      }

      if(create || request.bookmarks() != null) {
         applyBookmarks(action, entry, request.bookmarks(), caller, user);
      }
   }

   private String resolveFormat(String requested) {
      List<String> formats = scheduleTaskService.getVSMailFormatNames();

      if(requested == null || requested.isBlank()) {
         return defaultFormat(formats);
      }

      for(String format : formats) {
         if(format.equalsIgnoreCase(requested.trim())) {
            return format;
         }
      }

      throw new IllegalArgumentException(
         "'format' \"" + requested + "\" is not offered for a schedule email; use one of " +
         String.join(", ", formats) + ".");
   }

   private static String defaultFormat(List<String> formats) {
      if(formats.isEmpty()) {
         throw new IllegalStateException(
            "The server offers no export format for a schedule email (vsexport.menu.options).");
      }

      return formats.contains(FileFormatInfo.EXPORT_NAME_EXCEL) ?
         FileFormatInfo.EXPORT_NAME_EXCEL : formats.get(0);
   }

   /** The default is the Home bookmark, like a new Schedule tab action. */
   private void applyBookmarks(ViewsheetAction action, AssetEntry entry, List<String> requested,
                               IdentityID caller, Principal user)
      throws Exception
   {
      if(requested == null || requested.isEmpty()) {
         action.setBookmarks(new String[] { VSBookmark.HOME_BOOKMARK });
         action.setBookmarkUsers(new IdentityID[] { caller });
         action.setBookmarkTypes(new int[] { VSBookmarkInfo.PRIVATE });
         return;
      }

      String runtimeId = viewsheetService.openViewsheet(entry, user, true);

      try {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, user);
         List<VSBookmarkInfo> visible = rvs.getBookmarks();
         String[] names = new String[requested.size()];
         IdentityID[] users = new IdentityID[requested.size()];
         int[] types = new int[requested.size()];

         for(int i = 0; i < names.length; i++) {
            VSBookmarkInfo info = findBookmark(visible, requested.get(i));

            if(info == null) {
               throw new IllegalArgumentException(
                  "'bookmarks' has \"" + requested.get(i) + "\", which this dashboard does " +
                  "not offer you; available: " + bookmarkNames(visible) + ".");
            }

            names[i] = info.getName();
            users[i] = info.getOwner() == null ? caller : info.getOwner();
            types[i] = info.getType();
         }

         action.setBookmarks(names);
         action.setBookmarkUsers(users);
         action.setBookmarkTypes(types);
      }
      finally {
         try {
            viewsheetService.closeViewsheet(runtimeId, user);
         }
         catch(Exception e) {
            LOG.warn("Failed to close runtime [{}] after a schedule call", runtimeId, e);
         }
      }
   }

   private static VSBookmarkInfo findBookmark(List<VSBookmarkInfo> visible, String requested) {
      for(VSBookmarkInfo info : visible) {
         String owner = info.getOwner() == null ? null : info.getOwner().getName();

         if(requested.equals(info.getName()) ||
            owner != null && requested.equals(info.getName() + "(" + owner + ")"))
         {
            return info;
         }
      }

      return null;
   }

   private static String bookmarkNames(List<VSBookmarkInfo> visible) {
      List<String> names = new ArrayList<>();

      for(VSBookmarkInfo info : visible) {
         names.add(info.getName());
      }

      return String.join(", ", names);
   }

   /** Builds the one time condition of a request. */
   static ScheduleCondition buildCondition(TaskRequest request) {
      String frequency = request.frequency() == null ? "" :
         request.frequency().trim().toLowerCase(Locale.ROOT);
      TimeCondition condition;

      switch(frequency) {
      case "once" -> {
         condition = TimeCondition.at(runAt(request));
      }
      case "daily" -> {
         LocalTime time = timeOfDay(request);
         condition = TimeCondition.at(time.getHour(), time.getMinute(), 0);
         condition.setInterval(positive(request.interval(), "interval"));
         condition.setWeekdayOnly(Boolean.TRUE.equals(request.weekdayOnly()));
      }
      case "weekly" -> {
         LocalTime time = timeOfDay(request);
         List<Integer> days = request.weekdays();

         if(days == null || days.isEmpty()) {
            throw new IllegalArgumentException(
               "'weekdays' is required for a weekly schedule: day numbers 1 (Sunday) to 7 " +
               "(Saturday).");
         }

         int[] numbers = new int[days.size()];

         for(int i = 0; i < numbers.length; i++) {
            if(days.get(i) == null || days.get(i) < 1 || days.get(i) > 7) {
               throw new IllegalArgumentException(
                  "'weekdays' entries must be 1 (Sunday) to 7 (Saturday); got " + days.get(i) +
                  ".");
            }

            numbers[i] = days.get(i);
         }

         condition = TimeCondition.atDaysOfWeek(numbers, time.getHour(), time.getMinute(), 0);
         condition.setInterval(positive(request.interval(), "interval"));
      }
      case "monthly" -> {
         LocalTime time = timeOfDay(request);
         Integer day = request.dayOfMonth();

         if(day == null || (day != TimeCondition.LAST_DAY_OF_MONTH && (day < 1 || day > 31))) {
            throw new IllegalArgumentException(
               "'dayOfMonth' is required for a monthly schedule: 1 to 31, or -1 for the last " +
               "day of the month.");
         }

         condition = TimeCondition.atDayOfMonth(day, time.getHour(), time.getMinute(), 0);
      }
      default -> throw new IllegalArgumentException(
         "'frequency' must be one of once, daily, weekly, monthly (got \"" +
         request.frequency() + "\").");
      }

      if(request.timeZone() != null && !request.timeZone().isBlank()) {
         condition.setTimeZone(resolveZone(request.timeZone()));
      }

      condition.validateRanges();
      return condition;
   }

   private static TimeZone resolveZone(String id) {
      String trimmed = id.trim();
      TimeZone zone = TimeZone.getTimeZone(trimmed);

      // an id the JVM cannot resolve comes back as GMT, which would store a typo as GMT
      if("GMT".equals(zone.getID()) && !"GMT".equalsIgnoreCase(trimmed) &&
         !"UTC".equalsIgnoreCase(trimmed))
      {
         throw new IllegalArgumentException(
            "'timeZone' \"" + id + "\" is not a recognized time zone id (use an IANA id such " +
            "as \"America/New_York\").");
      }

      return zone;
   }

   private static LocalTime timeOfDay(TaskRequest request) {
      if(request.time() == null || request.time().isBlank()) {
         throw new IllegalArgumentException(
            "'time' is required: the time of day as HH:mm (24 hour), e.g. \"08:30\".");
      }

      try {
         return LocalTime.parse(request.time().trim());
      }
      catch(RuntimeException e) {
         throw new IllegalArgumentException(
            "'time' must be HH:mm (24 hour), e.g. \"08:30\"; got \"" + request.time() + "\".", e);
      }
   }

   private static Date runAt(TaskRequest request) {
      if(request.runAt() == null || request.runAt().isBlank()) {
         throw new IllegalArgumentException(
            "'runAt' is required for a once schedule: local date and time as " +
            "yyyy-MM-ddTHH:mm, e.g. \"2026-12-01T08:30\".");
      }

      LocalDateTime local;

      try {
         String text = request.runAt().trim();
         local = text.length() <= 10 ? LocalDate.parse(text).atStartOfDay() :
            LocalDateTime.parse(text);
      }
      catch(RuntimeException e) {
         throw new IllegalArgumentException(
            "'runAt' must be yyyy-MM-ddTHH:mm, e.g. \"2026-12-01T08:30\"; got \"" +
            request.runAt() + "\".", e);
      }

      ZoneId zone = request.timeZone() == null || request.timeZone().isBlank() ?
         ZoneId.systemDefault() : resolveZone(request.timeZone()).toZoneId();
      return Date.from(local.atZone(zone).toInstant());
   }

   private static int positive(Integer value, String field) {
      if(value == null) {
         return 1;
      }

      if(value < 1) {
         throw new IllegalArgumentException("'" + field + "' must be 1 or more (got " + value + ").");
      }

      return value;
   }

   private static AssetEntry folderOf(ScheduleTask task) {
      String path = task.getPath();

      if(path == null || path.isEmpty() || "/".equals(path)) {
         return null;
      }

      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null);
   }

   private static String scopeName(AssetEntry entry) {
      return entry.getScope() == AssetRepository.USER_SCOPE ? "user" : "global";
   }

   private static TaskView toView(ScheduleTask task, ViewsheetAction action) {
      AssetEntry entry = AssetEntry.createAssetEntry(action.getViewsheet());
      List<String> schedule = new ArrayList<>();

      for(int i = 0; i < task.getConditionCount(); i++) {
         schedule.add(describe(task.getCondition(i)));
      }

      List<String> bookmarks = new ArrayList<>();

      if(action.getBookmarks() != null) {
         for(String bookmark : action.getBookmarks()) {
            if(bookmark != null) {
               bookmarks.add(bookmark);
            }
         }
      }

      return new TaskView(ScheduleManager.getTaskMetaData(task.getTaskId()).getTaskName(),
                          entry.getPath(), scopeName(entry), task.isEnabled(),
                          task.getDescription(), schedule, split(action.getEmails()),
                          split(action.getCCAddresses()), split(action.getBCCAddresses()),
                          action.getSubject(), action.getMessage(), action.getFileFormat(),
                          bookmarks);
   }

   private static String describe(ScheduleCondition condition) {
      if(!(condition instanceof TimeCondition time)) {
         return condition == null ? "" : condition.getClass().getSimpleName();
      }

      String at = String.format("%02d:%02d", time.getHour(), time.getMinute());

      return switch(time.getType()) {
      case TimeCondition.AT -> "once at " + time.getDate();
      case TimeCondition.EVERY_DAY -> "every " + (time.getInterval() > 1 ? time.getInterval() + " " : "") +
         (time.isWeekdayOnly() ? "weekday" : "day") + " at " + at;
      case TimeCondition.EVERY_WEEK -> "weekly on days " + java.util.Arrays.toString(
         time.getDaysOfWeek()) + " at " + at;
      case TimeCondition.EVERY_MONTH -> "monthly on day " + time.getDayOfMonth() + " at " + at;
      default -> "time condition type " + time.getType() + " at " + at;
      };
   }

   private static List<String> split(String addresses) {
      List<String> list = new ArrayList<>();

      if(addresses != null) {
         for(String address : addresses.split("[,;]")) {
            if(!address.isBlank()) {
               list.add(address.trim());
            }
         }
      }

      return list;
   }

   public record FormatOptions(List<String> formats, String defaultFormat) {
   }

   /**
    * A create or update request. On create, {@code path}, {@code name}, {@code frequency} and
    * {@code to} are required; on update only the fields present change.
    */
   public record TaskRequest(String name, String path, String scope, String description,
                             Boolean enabled, String frequency, String time, String runAt,
                             List<Integer> weekdays, Integer dayOfMonth, Integer interval,
                             Boolean weekdayOnly, String timeZone, List<String> to,
                             List<String> cc, List<String> bcc, String subject, String message,
                             String format, List<String> bookmarks)
   {
   }

   public record TaskView(String name, String path, String scope, boolean enabled,
                          String description, List<String> schedule, List<String> to,
                          List<String> cc, List<String> bcc, String subject, String message,
                          String format, List<String> bookmarks)
   {
   }

   private final WizDashboardAccess access;
   private final ScheduleManager scheduleManager;
   private final ScheduleTaskService scheduleTaskService;
   private final ViewsheetService viewsheetService;
   private static final Logger LOG = LoggerFactory.getLogger(WizScheduleService.class);
}
