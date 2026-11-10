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
package inetsoft.uql.elasticrest;

import inetsoft.uql.elasticrest.ElasticCatalog.MappedField;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.BaseJsonTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Types a search result's columns by the index's {@code _mapping}, so a table built from
 * {@link ElasticCatalog#describeDataset}'s {@code params} has the column types that description
 * reported. Used by {@link ElasticRestRuntime#runQuery} only when the query names a
 * {@link ElasticRestQuery#getMappingIndex() mapping index}.
 *
 * <p>Without it, {@code JsonTable} infers each column's type from the values, and that disagrees
 * with the mapping in kind for exactly two families of field, which are the two this class acts on:
 *
 * <ul>
 * <li><b>Date fields</b> ({@code date}, {@code date_nanos}). Elasticsearch stores {@code _source}
 *     as the document was sent, and the default date format
 *     {@code strict_date_optional_time||epoch_millis} accepts both an ISO string and an epoch-millis
 *     number. A column mixing the two widens to a string (the number then reads back as a bare
 *     "1700000000000"), and a column of numbers only reads as a long. Each value is therefore
 *     converted here, before the table loads it, by the field's own format list, in order, the way
 *     Elasticsearch itself tries them: {@code epoch_millis} and {@code epoch_second} as instants
 *     (seconds scaled, never read as millis), ISO-style built-in names as ISO dates, and a custom
 *     pattern with that pattern. A string without a zone is UTC, as Elasticsearch reads it.</li>
 * <li><b>String fields</b> ({@code text}, {@code keyword}, ... — every column {@link ElasticCatalog}
 *     reports as {@link XSchema#STRING}). The table is told the column is a string, which stops its
 *     date detection from turning date-looking text into a date column.</li>
 * </ul>
 *
 * <p><b>No value is ever lost.</b> A date column is converted all or nothing: if any value in it is
 * not in one of the field's formats (an array, a format this class cannot read, a value
 * Elasticsearch kept only because of {@code ignore_malformed}), the whole column is left exactly as
 * before — raw values, value inference — and a warning names the column and the value. Converting
 * the rest would leave a mixed column, and handing the table a date type for it would turn the odd
 * value into a null. Numeric and boolean fields are not touched for the same reason: their kind
 * already agrees with the mapping, and forcing the mapped type would turn a stray non-numeric value
 * into a null.
 */
final class ElasticMappingTypes {
   private ElasticMappingTypes() {
   }

   /**
    * Converts the date columns of {@code rows} in place and sets the column types on {@code table}.
    * Call before {@code table.load(rows)}.
    *
    * @param rows   the JSON the query's {@code jsonPath} selected: a list of records, or one record.
    *               Records are the mutable maps the runtime's JSON provider builds.
    * @param fields {@link ElasticCatalog#mappedFields}, keyed by the dotted column name
    *               {@code JsonTable.walkRecord} gives a leaf.
    */
   static void apply(BaseJsonTable table, Object rows, Map<String, MappedField> fields) {
      Map<String, List<Conversion>> conversions = new LinkedHashMap<>();
      Map<String, Object> rejected = new LinkedHashMap<>();
      List<?> records = rows instanceof List<?> list ? list : Collections.singletonList(rows);

      for(Object record : records) {
         if(record instanceof Map<?, ?> map) {
            collect(map, null, fields, conversions, rejected);
         }
      }

      for(Map.Entry<String, MappedField> entry : fields.entrySet()) {
         String name = entry.getKey();
         MappedField field = entry.getValue();

         if(XSchema.STRING.equals(field.type())) {
            table.setColumnType(name, XSchema.STRING);
         }
         else if(XSchema.TIME_INSTANT.equals(field.type())) {
            if(rejected.containsKey(name)) {
               LOG.warn("Elasticsearch date field '{}' is not typed by its mapping (format '{}'): " +
                        "the value {} is not in that format, so the column keeps its raw values " +
                        "and an inferred type.", name, formatsOf(field), rejected.get(name));
               continue;
            }

            for(Conversion conversion : conversions.getOrDefault(name, List.of())) {
               conversion.apply();
            }

            table.setColumnType(name, XSchema.TIME_INSTANT);
         }
      }
   }

   private static void collect(Map<?, ?> record, String prefix, Map<String, MappedField> fields,
                               Map<String, List<Conversion>> conversions,
                               Map<String, Object> rejected)
   {
      for(Map.Entry<?, ?> entry : record.entrySet()) {
         String name = prefix == null ? String.valueOf(entry.getKey())
            : prefix + "." + entry.getKey();
         Object value = entry.getValue();

         // The same rule JsonTable.walkRecord names its columns by: recurse into a map.
         if(value instanceof Map<?, ?> map) {
            collect(map, name, fields, conversions, rejected);
            continue;
         }

         MappedField field = fields.get(name);

         if(value == null || field == null || !XSchema.TIME_INSTANT.equals(field.type()) ||
            rejected.containsKey(name))
         {
            continue;
         }

         Timestamp converted = toTimestamp(value, field);

         if(converted == null) {
            rejected.put(name, value);
         }
         else {
            @SuppressWarnings("unchecked")
            Map<Object, Object> owner = (Map<Object, Object>) record;
            conversions.computeIfAbsent(name, k -> new ArrayList<>())
               .add(new Conversion(owner, entry.getKey(), converted));
         }
      }
   }

   /**
    * The instant {@code value} denotes under the field's format list, or null if no format in it
    * accepts the value.
    */
   static Timestamp toTimestamp(Object value, MappedField field) {
      for(String format : formatsOf(field).split("\\|\\|")) {
         Instant instant;

         try {
            instant = parse(value, format.trim());
         }
         catch(RuntimeException ex) {
            // DateTimeException for a value the format rejects, IllegalArgumentException for a
            // pattern java.time cannot compile, ArithmeticException for an out-of-range epoch.
            instant = null;
         }

         if(instant != null) {
            return Timestamp.from(instant);
         }
      }

      return null;
   }

   private static String formatsOf(MappedField field) {
      if(field.format() != null && !field.format().isBlank()) {
         return field.format();
      }

      return "date_nanos".equals(field.esType()) ? DEFAULT_DATE_NANOS_FORMAT : DEFAULT_DATE_FORMAT;
   }

   private static Instant parse(Object value, String format) {
      if(EPOCH_MILLIS.equals(format) || EPOCH_SECOND.equals(format)) {
         BigDecimal number = toNumber(value);

         if(number == null) {
            return null;
         }

         BigDecimal seconds = EPOCH_MILLIS.equals(format) ? number.movePointLeft(3) : number;
         BigDecimal whole = seconds.setScale(0, RoundingMode.FLOOR);
         long nanos = seconds.subtract(whole).movePointRight(9)
            .setScale(0, RoundingMode.HALF_UP).longValueExact();
         return Instant.ofEpochSecond(whole.longValueExact(), nanos);
      }

      if(!(value instanceof String text) || text.isEmpty()) {
         return null;
      }

      if(ISO_FORMATS.contains(format)) {
         return parseIso(text);
      }

      // Any other lowercase, underscored name is one of Elasticsearch's built-in formats this
      // class does not read (basic_date, week_date, ordinal_date, time, ...). It is not a pattern:
      // compiling it as one would "parse" with the wrong meaning or throw.
      if(BUILT_IN_NAME.matcher(format).matches()) {
         return null;
      }

      return parsePattern(text, format);
   }

   private static BigDecimal toNumber(Object value) {
      if(value instanceof Number number) {
         return new BigDecimal(number.toString());
      }

      if(value instanceof String text && NUMERIC.matcher(text).matches()) {
         return new BigDecimal(text);
      }

      return null;
   }

   /**
    * The ISO-8601 forms Elasticsearch's {@code strict_date_optional_time} family accepts: a date
    * with an optional time and an optional offset or zone, or just a year-month or a year. A
    * string without an offset is UTC, as Elasticsearch reads it.
    */
   private static Instant parseIso(String text) {
      TemporalAccessor parsed;

      if(text.indexOf('T') > 0) {
         parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(
            text, ZonedDateTime::from, LocalDateTime::from);
      }
      else if(YEAR_MONTH.matcher(text).matches()) {
         return YearMonth.parse(text).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
      }
      else if(YEAR.matcher(text).matches()) {
         return Year.parse(text).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
      }
      else {
         parsed = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
      }

      return toInstant(parsed);
   }

   private static Instant parsePattern(String text, String pattern) {
      DateTimeFormatter formatter = new DateTimeFormatterBuilder()
         .appendPattern(pattern)
         .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
         .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
         .toFormatter(Locale.ROOT);
      return toInstant(formatter.parseBest(text, ZonedDateTime::from, LocalDateTime::from,
                                           LocalDate::from));
   }

   private static Instant toInstant(TemporalAccessor parsed) {
      if(parsed instanceof ZonedDateTime zoned) {
         return zoned.toInstant();
      }

      if(parsed instanceof LocalDateTime local) {
         return local.toInstant(ZoneOffset.UTC);
      }

      return ((LocalDate) parsed).atStartOfDay(ZoneOffset.UTC).toInstant();
   }

   /** One pending replacement of a raw date value by its instant. */
   private record Conversion(Map<Object, Object> owner, Object key, Timestamp value) {
      void apply() {
         owner.put(key, value);
      }
   }

   /** The format a {@code date} field has when its mapping states none. */
   static final String DEFAULT_DATE_FORMAT = "strict_date_optional_time||epoch_millis";
   /** The format a {@code date_nanos} field has when its mapping states none. */
   static final String DEFAULT_DATE_NANOS_FORMAT =
      "strict_date_optional_time_nanos||epoch_millis";

   private static final String EPOCH_MILLIS = "epoch_millis";
   private static final String EPOCH_SECOND = "epoch_second";

   /**
    * The built-in format names whose accepted strings are ISO-8601 dates with optional parts — all
    * readable by {@link #parseIso}. The {@code strict_} variants differ from the lenient ones only
    * in requiring zero-padding, which a value Elasticsearch already accepted always satisfies.
    */
   private static final Set<String> ISO_FORMATS = Set.of(
      "strict_date_optional_time", "date_optional_time", "strict_date_optional_time_nanos",
      "strict_date_time", "date_time", "strict_date_time_no_millis", "date_time_no_millis",
      "strict_date", "date", "strict_year_month_day", "year_month_day",
      "strict_date_hour", "date_hour", "strict_date_hour_minute", "date_hour_minute",
      "strict_date_hour_minute_second", "date_hour_minute_second",
      "strict_date_hour_minute_second_millis", "date_hour_minute_second_millis",
      "strict_date_hour_minute_second_fraction", "date_hour_minute_second_fraction",
      "strict_year_month", "year_month", "strict_year", "year");

   private static final Pattern BUILT_IN_NAME = Pattern.compile("[a-z]+(_[a-z]+)+|time");
   private static final Pattern NUMERIC = Pattern.compile("-?\\d+(\\.\\d+)?");
   private static final Pattern YEAR_MONTH = Pattern.compile("\\d{4}-\\d{2}");
   private static final Pattern YEAR = Pattern.compile("\\d{4}");
   private static final Logger LOG = LoggerFactory.getLogger(ElasticMappingTypes.class.getName());
}
