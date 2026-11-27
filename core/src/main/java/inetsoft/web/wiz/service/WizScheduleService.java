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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
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
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.stream.IntStream;

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
   @Autowired
   public WizScheduleService(WizDashboardAccess access, ScheduleManager scheduleManager,
                             ScheduleTaskService scheduleTaskService)
   {
      this(access, scheduleManager, scheduleTaskService, new Environment() {
         @Override
         public VSBookmarkInfo[] bookmarks(AssetEntry entry, IdentityID caller) {
            return VSUtil.getBookmarks(entry, caller);
         }

         @Override
         public String sender(IdentityID caller) {
            return defaultSender(caller);
         }
      });
   }

   WizScheduleService(WizDashboardAccess access, ScheduleManager scheduleManager,
                      ScheduleTaskService scheduleTaskService, Environment environment)
   {
      this.access = access;
      this.scheduleManager = scheduleManager;
      this.scheduleTaskService = scheduleTaskService;
      this.environment = environment;
   }

   /** What the Schedule tab reads from the server when it builds a new dashboard action. */
   interface Environment {
      /** The bookmarks of the dashboard the Schedule tab offers the user, Home first. */
      VSBookmarkInfo[] bookmarks(AssetEntry entry, IdentityID caller);

      /** The sender a new email action gets. */
      String sender(IdentityID caller);
   }

   /**
    * The sender the Schedule tab gives an action whose From field is not set: the user's own
    * address (unless {@code em.mail.defaultEmailFromSelf} is off), else the server's
    * {@code mail.from.address}.
    */
   static String defaultSender(IdentityID caller) {
      String[] emails = new String[0];

      try {
         emails = SUtil.getEmails(caller);
      }
      catch(Exception e) {
         // the server default is used
      }

      boolean useSelf = !"false".equals(SreeEnv.getProperty("em.mail.defaultEmailFromSelf"));
      String userEmail = useSelf && emails.length > 0 ? emails[0] : null;
      return userEmail != null && !userEmail.isEmpty() ?
         userEmail : SreeEnv.getProperty("mail.from.address");
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
    * Changes one of the caller's own tasks. Only the fields the request carries change. The
    * schedule changes when {@code frequency} is given (it then replaces the whole time
    * condition), or when only some of its fields are given ({@code time}, {@code runAt},
    * {@code weekdays}, {@code dayOfMonth}, {@code interval}, {@code weekdayOnly},
    * {@code timeZone}): those fields change in the stored schedule and the others stay. The
    * email action changes when any of its fields is.
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

      ScheduleCondition rebuilt = null;

      if(request.frequency() != null && !request.frequency().isBlank()) {
         rebuilt = buildCondition(request);
      }
      else if(hasScheduleField(request)) {
         rebuilt = buildCondition(withStoredSchedule(name, stored, request));
      }

      if(rebuilt != null) {
         for(int i = candidate.getConditionCount() - 1; i >= 0; i--) {
            candidate.removeCondition(i);
         }

         candidate.addCondition(rebuilt);
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
         applyBookmarks(action, entry, request.bookmarks(), caller);
      }

      if(create) {
         applyScheduleTabDefaults(action, entry, caller);
      }
   }

   /**
    * The defaults the Schedule tab gives a new dashboard email action, which a task that is to
    * run needs and the plugin does not ask for: the tab's save path
    * ({@code ScheduleService.getActionFromModel}) keeps Match Layout on and the layout options
    * off, names the attachment after the dashboard, sends from the user's own address, and the
    * tab's editors turn the HTML message on.
    */
   private void applyScheduleTabDefaults(ViewsheetAction action, AssetEntry entry,
                                         IdentityID caller)
   {
      action.setMatchLayout(true);
      action.setExpandSelections(false);
      action.setOnlyDataComponents(false);
      action.setExportAllTabbedTables(false);
      action.setSaveToServerMatch(true);
      action.setSaveToServerExpandSelections(false);
      action.setSaveToServerOnlyDataComponents(false);
      action.setSaveExportAllTabbedTables(false);
      action.setMessageHtml(true);
      action.setDeliverLink(false);
      action.setCompressFile(false);
      action.setAttachmentName(attachmentName(entry));
      action.setFrom(environment.sender(caller));
   }

   /**
    * The attachment name the Schedule tab derives when none is typed: the dashboard path with
    * "/" and " " replaced by "_" ({@code Examples/Sales Summary} gives
    * {@code Examples_Sales_Summary}). Left empty, the delivered attachment is named just
    * {@code .pdf}.
    */
   static String attachmentName(AssetEntry entry) {
      return entry.getPath().replace("/", "_").replace(" ", "_");
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

   /**
    * The bookmarks of the action, resolved from the list the Schedule tab offers
    * ({@code VSUtil.getBookmarks}) and stored with the type and owner of that list's entry: the
    * run matches stored name, type and owner against the same list
    * ({@code ViewsheetAction#runViewsheetAction}), so a type that differs from the list's (the
    * Home bookmark is all-share, not private) never delivers. The default is the Home bookmark.
    */
   private void applyBookmarks(ViewsheetAction action, AssetEntry entry, List<String> requested,
                               IdentityID caller)
   {
      List<VSBookmarkInfo> offered = new ArrayList<>(
         java.util.Arrays.asList(environment.bookmarks(entry, caller)));
      VSBookmarkInfo home = null;

      for(VSBookmarkInfo info : offered) {
         if(VSBookmark.HOME_BOOKMARK.equals(info.getName())) {
            home = info;
            break;
         }
      }

      if(home == null) {
         home = new VSBookmarkInfo(VSBookmark.HOME_BOOKMARK, VSBookmarkInfo.ALLSHARE, caller,
                                   false, System.currentTimeMillis());
         offered.add(0, home);
      }

      List<VSBookmarkInfo> chosen = new ArrayList<>();

      if(requested == null || requested.isEmpty()) {
         chosen.add(home);
      }
      else {
         for(String name : requested) {
            chosen.add(findBookmark(offered, name));
         }
      }

      String[] names = new String[chosen.size()];
      IdentityID[] users = new IdentityID[chosen.size()];
      int[] types = new int[chosen.size()];

      for(int i = 0; i < names.length; i++) {
         VSBookmarkInfo info = chosen.get(i);
         names[i] = info.getName();
         users[i] = info.getOwner() == null ? caller : info.getOwner();
         types[i] = info.getType();
      }

      action.setBookmarks(names);
      action.setBookmarkUsers(users);
      action.setBookmarkTypes(types);
   }

   /** The offered bookmark a request names, as {@code name} or {@code name(owner)}. */
   private static VSBookmarkInfo findBookmark(List<VSBookmarkInfo> offered, String requested) {
      List<VSBookmarkInfo> matches = new ArrayList<>();

      for(VSBookmarkInfo info : offered) {
         String owner = info.getOwner() == null ? null : info.getOwner().getName();

         if(requested.equals(info.getName() + (owner == null ? "" : "(" + owner + ")"))) {
            return info;
         }

         if(requested.equals(info.getName())) {
            matches.add(info);
         }
      }

      if(matches.size() == 1) {
         return matches.get(0);
      }

      if(matches.size() > 1) {
         throw new IllegalArgumentException(
            "'bookmarks' \"" + requested + "\" is ambiguous (several owners); use one of " +
            bookmarkLabels(matches) + ".");
      }

      throw new IllegalArgumentException(
         "'bookmarks' has \"" + requested + "\", which this dashboard does not offer you; " +
         "available: " + bookmarkLabels(offered) + ".");
   }

   private static String bookmarkLabels(List<VSBookmarkInfo> bookmarks) {
      List<String> labels = new ArrayList<>();

      for(VSBookmarkInfo info : bookmarks) {
         labels.add(info.getOwner() == null ? info.getName() :
                       info.getName() + "(" + info.getOwner().getName() + ")");
      }

      return String.join(", ", labels);
   }

   /** Whether the request carries any field that belongs to the time condition. */
   private static boolean hasScheduleField(TaskRequest request) {
      return !givenFields(request).isEmpty();
   }

   /** The schedule fields the request carries, in the order the tool documents them. */
   private static List<String> givenFields(TaskRequest request) {
      List<String> given = new ArrayList<>();

      if(request.time() != null && !request.time().isBlank()) {
         given.add("time");
      }

      if(request.runAt() != null && !request.runAt().isBlank()) {
         given.add("runAt");
      }

      if(request.weekdays() != null && !request.weekdays().isEmpty()) {
         given.add("weekdays");
      }

      if(request.dayOfMonth() != null) {
         given.add("dayOfMonth");
      }

      if(request.interval() != null) {
         given.add("interval");
      }

      // false is a value too: it turns weekday-only off on a daily schedule
      if(request.weekdayOnly() != null) {
         given.add("weekdayOnly");
      }

      if(request.timeZone() != null && !request.timeZone().isBlank()) {
         given.add("timeZone");
      }

      return given;
   }

   /** The kinds of schedule each field belongs to. */
   private static String appliesTo(String field) {
      return switch(field) {
      case "time" -> "daily, weekly or monthly";
      case "runAt" -> "once";
      case "weekdays" -> "weekly";
      case "dayOfMonth" -> "monthly";
      case "interval" -> "daily or weekly";
      case "weekdayOnly" -> "daily";
      default -> "every";
      };
   }

   private static boolean applies(String field, String frequency) {
      return switch(field) {
      case "time" -> !"once".equals(frequency);
      case "runAt" -> "once".equals(frequency);
      case "weekdays" -> "weekly".equals(frequency);
      case "dayOfMonth" -> "monthly".equals(frequency);
      case "interval" -> "daily".equals(frequency) || "weekly".equals(frequency);
      case "weekdayOnly" -> "daily".equals(frequency);
      default -> true;
      };
   }

   /**
    * Refuses a schedule field that the kind of schedule it is applied to does not use, instead
    * of dropping it and answering as if it had taken effect.
    */
   private static void requireApplicable(TaskRequest request, String frequency, String task) {
      for(String field : givenFields(request)) {
         // false is what "not weekday only" already is on every other kind of schedule
         boolean tolerated = "weekdayOnly".equals(field) &&
            Boolean.FALSE.equals(request.weekdayOnly());

         if(!tolerated && !applies(field, frequency)) {
            throw new IllegalArgumentException(
               "'" + field + "' does not apply to " + (task == null ? "a " + frequency +
                  " schedule" : "the " + frequency + " schedule of \"" + task + "\"") +
               "; it applies to a " + appliesTo(field) + " one. To change the kind of " +
               "schedule, send 'frequency' together with the fields of the new kind.");
         }
      }
   }

   /**
    * Completes a request that carries schedule fields but no {@code frequency}: the stored
    * condition supplies its frequency and every schedule field the request leaves out, so that
    * only the given fields change. When only {@code timeZone} is given a once schedule keeps its
    * wall-clock time (the run moves to that time in the new zone), as a {@code runAt} is read
    * in the given zone.
    */
   private static TaskRequest withStoredSchedule(String name, ScheduleTask stored,
                                                 TaskRequest request)
   {
      if(stored.getConditionCount() != 1 ||
         !(stored.getCondition(0) instanceof TimeCondition current) ||
         !supported(current))
      {
         throw new IllegalArgumentException(
            "The schedule of \"" + name + "\" (" + (stored.getConditionCount() == 1 ?
               describe(stored.getCondition(0)) : stored.getConditionCount() + " conditions") +
            ") cannot be changed field by field here; send 'frequency' with the fields of a " +
            "daily, weekly, monthly or once schedule to replace it, or change it in the " +
            "Schedule tab.");
      }

      String frequency = frequencyOf(current.getType());
      requireApplicable(request, frequency, name);
      String zone = request.timeZone() != null && !request.timeZone().isBlank() ?
         request.timeZone() : current.getTimeZone().getID();
      String time = request.time();
      String runAt = request.runAt();

      if("once".equals(frequency)) {
         if(runAt == null || runAt.isBlank()) {
            Date date = current.getDate();

            runAt = date == null ? null : LocalDateTime.ofInstant(
               date.toInstant(), current.getTimeZone().toZoneId()).toString();
         }
      }
      else if(time == null || time.isBlank()) {
         time = String.format("%02d:%02d", current.getHour(), current.getMinute());
      }

      List<Integer> weekdays = request.weekdays();

      if("weekly".equals(frequency) && (weekdays == null || weekdays.isEmpty()) &&
         current.getDaysOfWeek() != null)
      {
         weekdays = java.util.Arrays.stream(current.getDaysOfWeek()).boxed().toList();
      }

      Integer dayOfMonth = request.dayOfMonth();

      if("monthly".equals(frequency) && dayOfMonth == null) {
         dayOfMonth = current.getDayOfMonth();
      }

      Integer interval = request.interval();

      // a task made elsewhere may not store an interval (-1), which means every time
      if(interval == null && ("daily".equals(frequency) || "weekly".equals(frequency)) &&
         current.getInterval() > 0)
      {
         interval = current.getInterval();
      }

      Boolean weekdayOnly = request.weekdayOnly();

      if(weekdayOnly == null && "daily".equals(frequency)) {
         weekdayOnly = current.isWeekdayOnly();
      }

      return new TaskRequest(request.name(), request.path(), request.scope(),
                             request.description(), request.enabled(), frequency, time, runAt,
                             weekdays, dayOfMonth, interval, weekdayOnly, zone, request.to(),
                             request.cc(), request.bcc(), request.subject(), request.message(),
                             request.format(), request.bookmarks());
   }

   /**
    * The conditions this tool builds, the only ones it can change field by field. An "nth weekday
    * of the month" condition shares the monthly type, so it is told apart by its week.
    */
   private static boolean supported(TimeCondition condition) {
      return switch(condition.getType()) {
      case TimeCondition.AT, TimeCondition.EVERY_DAY, TimeCondition.EVERY_WEEK -> true;
      case TimeCondition.EVERY_MONTH -> condition.getWeekOfMonth() <= 0;
      default -> false;
      };
   }

   private static String frequencyOf(int type) {
      return switch(type) {
      case TimeCondition.AT -> "once";
      case TimeCondition.EVERY_DAY -> "daily";
      case TimeCondition.EVERY_WEEK -> "weekly";
      default -> "monthly";
      };
   }

   /** Builds the one time condition of a request. */
   static ScheduleCondition buildCondition(TaskRequest request) {
      String frequency = request.frequency() == null ? "" :
         request.frequency().trim().toLowerCase(Locale.ROOT);
      TimeCondition condition;

      if(List.of("once", "daily", "weekly", "monthly").contains(frequency)) {
         requireApplicable(request, frequency, null);
      }

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
         // Bug #82496, no selected month means the task is never scheduled; run every month as
         // the Viewer Schedule tab's monthly option does
         condition.setMonthsOfYear(IntStream.range(0, 12).toArray());
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
   private final Environment environment;
}
