/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.sree.schedule;

import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.*;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;

/**
 * Class that represents a range of time in which a schedule task may be started.
 *
 * @since 13.1
 */
public final class TimeRange implements XMLSerializable, Serializable, Comparable<TimeRange> {
   /**
    * Creates a new instance of {@code TimeRange}.
    */
   public TimeRange() {
   }

   /**
    * Creates a new instance of {@code TimeRange}.
    *
    * @param name         the name of the time range.
    * @param startTime    the start time, inclusive.
    * @param endTime      the end time, exclusive.
    * @param defaultRange flag that indicates if the time range is the default selection.
    */
   public TimeRange(String name, String startTime, String endTime, boolean defaultRange) {
      this.name = name;
      this.startTime = LocalTime.parse(startTime);
      this.endTime = LocalTime.parse(endTime);
      this.defaultRange = defaultRange;
   }

   /**
    * Gets the name of this time range.
    *
    * @return the name.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the name of this time range.
    *
    * @param name the name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Gets the start time, inclusive, of this time range.
    *
    * @return the start time.
    */
   public LocalTime getStartTime() {
      return startTime;
   }

   /**
    * Sets the start time, inclusive, of this time range.
    *
    * @param startTime the start time.
    */
   public void setStartTime(LocalTime startTime) {
      this.startTime = startTime;
   }

   /**
    * Gets the end time, exclusive, of this time range.
    *
    * @return the end time.
    */
   public LocalTime getEndTime() {
      return endTime;
   }

   /**
    * Sets the end time, exclusive, of this time range.
    *
    * @param endTime the end time.
    */
   public void setEndTime(LocalTime endTime) {
      this.endTime = endTime;
   }

   /**
    * Gets the flag that indicates if this is the default time range. The default time range is the
    * first listed in the user interface.
    *
    * @return {@code true} if the default; {@code false} otherwise.
    */
   public boolean isDefault() {
      return defaultRange;
   }

   /**
    * Sets the flag that indicates if this is the default time range. The default time range is the
    * first listed in the user interface.
    *
    * @param defaultRange {@code true} if the default; {@code false} otherwise.
    */
   public void setDefault(boolean defaultRange) {
      this.defaultRange = defaultRange;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.format("<timeRange default=\"%s\">", defaultRange);

      if(name != null) {
         writer.format("<name><![CDATA[%s]]></name>", name);
      }

      if(startTime != null) {
         writer.format("<start>%s</start>", startTime);
      }

      if(endTime != null) {
         writer.format("<end>%s</end>", endTime);
      }

      writer.print("</timeRange>");
   }

   @Override
   public void parseXML(Element tag) {
      defaultRange = "true".equals(Tool.getAttribute(tag, "default"));
      Element element;

      if((element = Tool.getChildNodeByTagName(tag, "name")) != null) {
         name = Tool.getValue(element);
      }
      else {
         name = null;
      }

      if((element = Tool.getChildNodeByTagName(tag, "start")) != null) {
         startTime = LocalTime.parse(Tool.getValue(element));
      }
      else {
         startTime = null;
      }

      if((element = Tool.getChildNodeByTagName(tag, "end")) != null) {
         endTime = LocalTime.parse(Tool.getValue(element));
      }
      else {
         endTime = null;
      }
   }

   @Override
   public int compareTo(TimeRange o) {
      Objects.requireNonNull(o);

      if(this.isDefault()) {
         return -1;
      }

      if(o.isDefault()) {
         return 1;
      }

      return Objects.compare(
         this, o, Comparator.comparing(TimeRange::getStartTime)
            .thenComparing(TimeRange::getEndTime).thenComparing(TimeRange::getName));
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      TimeRange timeRange = (TimeRange) o;
      return Objects.equals(name, timeRange.name) &&
         Objects.equals(startTime, timeRange.startTime) &&
         Objects.equals(endTime, timeRange.endTime);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, startTime, endTime);
   }

   /**
    * Gets the defined time ranges.
    *
    * @return the time ranges.
    */
   public static List<TimeRange> getTimeRanges() {
      String property = SreeEnv.getProperty("schedule.time.ranges");

      if(property == null || property.trim().isEmpty()) {
         return new ArrayList<>();
      }

      return Arrays.stream(property.split(";"))
         .map(TimeRange::parse)
         .filter(Objects::nonNull)
         .collect(Collectors.toList());
   }

   /**
    * Sets the defined time ranges.
    *
    * @param ranges the time ranges.
    *
    * @throws IOException if an I/O error occurs.
    */
   public static void setTimeRanges(Collection<TimeRange> ranges) throws IOException {
      if(ranges == null || ranges.isEmpty()) {
         SreeEnv.setProperty("schedule.time.ranges", null);
      }
      else {
         Set<String> names = ranges.stream().map(TimeRange::getName).collect(Collectors.toSet());

         if(names.size() < ranges.size()) {
            throw new IllegalArgumentException("Duplicate time range names");
         }

         if(ranges.stream().filter(TimeRange::isDefault).count() > 1L) {
            throw new IllegalArgumentException("Multiple default time ranges");
         }

         String property = ranges.stream()
            .map(TimeRange::format)
            .collect(Collectors.joining(";"));
         SreeEnv.setProperty("schedule.time.ranges", property);
      }

      SreeEnv.save();
   }

   /**
    * Finds the defined time range that is closest to the specified time range.
    *
    * @param range  the range to match.
    * @param ranges the defined time ranges, should be obtained from {@link #getTimeRanges()}.
    *
    * @return the matching time range.
    */
   public static TimeRange getMatchingTimeRange(TimeRange range, Collection<TimeRange> ranges) {
      Objects.requireNonNull(range);
      ranges.forEach(Objects::requireNonNull);
      Optional<TimeRange> matched = ranges.stream()
         .filter(r -> Objects.equals(r.getName(), range.getName()))
         .findFirst();

      if(matched.isPresent()) {
         return matched.get();
      }

      matched = ranges.stream()
         .filter(r -> Objects.equals(r.getStartTime(), range.getStartTime()))
         .filter(r -> Objects.equals(r.getEndTime(), range.getEndTime()))
         .findFirst();

      if(matched.isPresent()) {
         return matched.get();
      }

      matched = ranges.stream()
         .min((o1, o2) -> (int) (getDistance(range, o1) - getDistance(range, o2)));

      // this will only be null if ranges is null
      return matched.orElse(range);
   }

   /**
    * Parses a single time range definition. The property may be set by hand or through the
    * generic property editor, neither of which validates it, so a malformed definition is
    * ignored instead of being propagated to the callers of {@link #getTimeRanges()}.
    *
    * @param text the time range definition.
    *
    * @return the time range or {@code null} if the definition is malformed.
    */
   private static TimeRange parse(String text) {
      String[] values = text.split(",");

      if(values.length < 4) {
         logInvalidRange(text, "expected 4 comma-separated fields, found " + values.length);
         return null;
      }

      String name = values[0];
      String def = values[1];
      LocalTime startTime;
      LocalTime endTime;

      try {
         startTime = LocalTime.parse(values[2]);
         endTime = LocalTime.parse(values[3]);
      }
      catch(DateTimeParseException e) {
         logInvalidRange(text, "invalid time \"" + e.getParsedString() + "\"");
         return null;
      }

      TimeRange range = new TimeRange();
      range.setName(name);
      range.setDefault("1".equals(def));
      range.setStartTime(startTime);
      range.setEndTime(endTime);
      return range;
   }

   /**
    * Logs a malformed time range definition. Each distinct definition is only logged once,
    * because the time ranges are read on every scheduler tick.
    *
    * @param text   the malformed definition.
    * @param reason the reason that it could not be parsed.
    */
   private static void logInvalidRange(String text, String reason) {
      if(loggedInvalidRanges.size() < 100 && loggedInvalidRanges.add(text)) {
         LOG.warn(
            "Ignoring malformed time range \"{}\" in the schedule.time.ranges property: {}. " +
            "Each time range must be in the form \"name,default,startTime,endTime\", for " +
            "example \"Morning,0,06:00,12:00\".", text, reason);
      }
   }

   private static String format(TimeRange range) {
      return String.format(
         "%s,%d,%s,%s", range.name, range.defaultRange ? 1 : 0,
         range.startTime.format(ISO_LOCAL_TIME), range.endTime.format(ISO_LOCAL_TIME));
   }

   private static long getDistance(TimeRange from, TimeRange to) {
      Duration start = Duration.between(from.getStartTime(), to.getStartTime());
      Duration end = Duration.between(from.getEndTime(), to.getEndTime());
      return Math.abs(start.toMinutes()) + Math.abs(end.toMinutes());
   }

   private String name;
   private LocalTime startTime;
   private LocalTime endTime;
   private boolean defaultRange;

   private static final Set<String> loggedInvalidRanges = ConcurrentHashMap.newKeySet();
   private static final Logger LOG = LoggerFactory.getLogger(TimeRange.class);
}
