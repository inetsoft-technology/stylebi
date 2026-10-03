package inetsoft.util.pojava.datetime;

/*
 Copyright 2010 John Pile

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.

 Modified by InetSoft Technology in 2026: moved from org.pojava.datetime 3.0.0 to
 inetsoft.util.pojava.datetime, and calendar arithmetic uses GregorianCalendar instead of
 Calendar.getInstance() (#77605).
 */

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * <p>
 * DateTime provides an immutable representation of Date and Time to the nearest nanosecond. You can access DateTime properties
 * either in milliseconds or in seconds and nanoseconds. Both the seconds and milliseconds values can be understood as being
 * truncated to their respective precisions. Nanos holds the fractional portion of a second in the range 0-999999999. Note that
 * whether seconds is positive or negative, the internal values will be adjusted if necessary to support a positive value for
 * nanos.
 * </p>
 * <p>
 * You may think of a DateTime object as a fixed offset of time measured from the Unix epoch in non-leap milliseconds or
 * non-leap seconds and nanoseconds. Leap years are calculated according to the Gregorian Calendar, matching the same
 * interpretation as the java.util.Date object (every 4th year is a leap year, except for years divisible by 100 but not divisible
 * by 400). The times are stored according to the UTC (aka GMT) time zone, and a TimeZone object is referenced to translate to a
 * local time zone.
 * </p>
 * <p>
 * DateTime includes a robust parser for interpreting date and time from a String. It parses a date and time using heuristics
 * rather than comparing against preset formats, so it is point-and-shoot simple. The following, for example, are interpreted the
 * same:
 * <ul>
 * <li>3:21pm on January 26, 1969</li>
 * <li>26-Jan-1969 03:21 PM</li>
 * <li>1/26/69 15:21</li>
 * <li>1969.01.26 15.21</li>
 * <li>el 26 de enero de 1969 15.21</li>
 * </ul>
 * <p>
 * Some notes on the date interpretations:
 * </p>
 * <p/>
 * <p>
 * All dates are interpreted in your local time zone, unless a time zone is specified in the String. Time zones are configurable
 * in the DateTimeConfig object, so you can determine for your own application whether CST, for example, would adjust to Central
 * Standard Time or Chinese Standard Time.
 * </p>
 * <p/>
 * <p>
 * A two-digit year will assume up to 80 years in the past and 20 years in the future. It is prudent in many cases to follow this
 * with a check based on whether you know the date to represent a past or future date. If you know you parsed a birthday, you can
 * compare with today's date and subtract 100 yrs if needed (references to birthdays 20 years in the future are rare). Similarly,
 * if you're dealing with an annuity date, you can add 100 years if the parsed date occurred in the past.
 * </p>
 * <p/>
 * <p>
 * If you're parsing European dates expecting DD/MM/YYYY instead of MM/DD/YYYY, then you can alter the global DateTimeConfig
 * setting by first calling, " <code>DateTimeConfig.globalEuropeanDateFormat();</code>".
 * </p>
 *
 * @author John Pile
 */
public class DateTime implements Serializable, Comparable<DateTime> {

    private static final long serialVersionUID = 300L;

    /**
     * These months have less than 31 days
     */
    private static final int FEB = 1;
    private static final int APR = 3;
    private static final int JUN = 5;
    private static final int SEP = 8;
    private static final int NOV = 10;

    /**
     * Config contains info specific to zoning and formatting.
     */
    protected IDateTimeConfig config;

    /**
     * System time is a lazy calculation of milliseconds from Unix epoch 1970-01-01 00:00:00, assuming no leap seconds and a leap
     * year every year evenly divisible by 4, except for years divisible by 100 but not divisible by 400.
     */
    protected Duration systemDur = null;

    private static final Pattern partsPattern = Pattern.compile("[^\\p{L}\\d]+");

    /**
     * Default constructor gives current time to millisecond.
     */
    public DateTime() {
        config();
        this.systemDur = new Duration(config.systemTime());
    }

    /**
     * DateTime with a specified config
     */
    public DateTime(IDateTimeConfig config) {
        this.config = config;
        this.systemDur = new Duration(config.systemTime());
    }

    /**
     * DateTime constructed from time in milliseconds since epoch.
     *
     * @param millis time since epoch
     */
    public DateTime(long millis) {
        config();
        this.systemDur = new Duration(millis);
    }

    /**
     * DateTime constructed from time in milliseconds since epoch.
     *
     * @param millis time since epoch
     * @param config configuration defaults
     */
    public DateTime(long millis, IDateTimeConfig config) {
        this.config = config;
        this.systemDur = new Duration(millis);
    }

    /**
     * DateTime constructed from time in milliseconds since epoch.
     *
     * @param millis Number of milliseconds since epoch
     * @param tz     Override the output Time Zone
     */
    public DateTime(long millis, TimeZone tz) {
        this.config = LocalConfig.instanceOverridingOutputTimeZone(DateTimeConfig.getGlobalDefault(), tz);
        this.systemDur = new Duration(millis);
    }

    /**
     * DateTime constructed from time in milliseconds since epoch.
     *
     * @param millis Number of milliseconds since epoch
     * @param tzId   Override the output time zone
     */
    public DateTime(long millis, String tzId) {
        IDateTimeConfig globalConfig = DateTimeConfig.getGlobalDefault();
        TimeZone tz = globalConfig.lookupTimeZone(tzId);
        this.config = LocalConfig.instanceOverridingOutputTimeZone(globalConfig, tz);
        this.systemDur = new Duration(millis);
    }

    /**
     * Construct a DateTime from seconds and fractional seconds.
     *
     * @param seconds Number of seconds since epoch (typically 1970-01-01)
     * @param nanos   Nanosecond offset in range +/- 999999999
     */
    public DateTime(long seconds, int nanos) {
        config();
        this.systemDur = new Duration(seconds, nanos);
    }

    /**
     * Construct a DateTime from seconds and fractional seconds.
     *
     * @param seconds Number of seconds since epoch (typically 1970-01-01)
     * @param nanos   Nanosecond offset in range +/- 999999999
     * @param tz      Override the output time zone
     */
    public DateTime(long seconds, int nanos, TimeZone tz) {
        this.config = LocalConfig.instanceOverridingOutputTimeZone(DateTimeConfig.getGlobalDefault(), tz);
        this.systemDur = new Duration(seconds, nanos);
    }

    /**
     * Construct a DateTime from seconds and fractional seconds.
     *
     * @param seconds Number of seconds since epoch (typically 1970-01-01)
     * @param nanos   Nanosecond offset in range +/- 999999999
     * @param tzId    Override the output time zone
     */
    public DateTime(long seconds, int nanos, String tzId) {
        IDateTimeConfig globalConfig = DateTimeConfig.getGlobalDefault();
        TimeZone tz = globalConfig.lookupTimeZone(tzId);
        this.config = LocalConfig.instanceOverridingOutputTimeZone(globalConfig, tz);
        this.systemDur = new Duration(seconds, nanos);
    }

    /**
     * Construct a DateTime from seconds and fractional seconds.
     *
     * @param seconds Number of seconds since epoch (typically 1970-01-01)
     * @param nanos   Nanosecond offset in range +/- 999999999
     * @param config  Provide custom configuration options
     */
    public DateTime(long seconds, int nanos, IDateTimeConfig config) {
        this.config = config;
        this.systemDur = new Duration(seconds, nanos);
    }

    /**
     * DateTime constructed from a string using global defaults.
     *
     * @param str String holding date to parse
     */
    public DateTime(String str) {
        this.config = DateTimeConfig.getGlobalDefault();
        DateTime dt = parse(str, config);
        this.systemDur = dt.systemDur;
    }

    /**
     * DateTime constructed from a string using specified defaults.
     *
     * @param str    String to parse
     * @param config Custom configuration options
     */
    public DateTime(String str, IDateTimeConfig config) {
        this.config = config;
        DateTime dt = parse(str, config);
        this.systemDur = dt.systemDur;
    }

    /**
     * DateTime parsed from a string at a specified time zone
     *
     * @param str Date string to parse
     * @param tz  Time zone used for both parsing input and formatting output
     */
    public DateTime(String str, TimeZone tz) {
        IDateTimeConfig globalConfig = DateTimeConfig.getGlobalDefault();
        this.config = LocalConfig.instanceOverridingTimeZones(globalConfig, tz, tz);
        DateTime dt = parse(str, this.config);
        this.systemDur = dt.systemDur;
    }

    /**
     * DateTime parsed from a string at a specified time zone
     *
     * @param str      Date string to parse
     * @param inputTz  Time Zone of the date being parsed
     * @param outputTz Time Zone under which toString will format dates
     */
    public DateTime(String str, TimeZone inputTz, TimeZone outputTz) {
        IDateTimeConfig globalConfig = DateTimeConfig.getGlobalDefault();
        this.config = LocalConfig.instanceOverridingTimeZones(globalConfig, inputTz, outputTz);
        DateTime dt = parse(str, this.config);
        this.systemDur = dt.systemDur;
    }

    /**
     * DateTime constructed from a Timestamp includes nanos.
     *
     * @param ts Timestamp
     */
    public DateTime(Timestamp ts) {
        config();
        this.systemDur = new Duration(ts.getTime() / 1000, ts.getNanos());
    }

    /**
     * Derive a time zone descriptor from the right side of the date/time string.
     *
     * @param str String to parse date/time
     * @return TimeZone id descriptor extracted from string (null if not found)
     */
    private static String tzParse(String str) {
        char[] chars = str.toCharArray();
        int min = 7; // Any less than 7 characters from left would encroach on the date itself
        int max = str.length() - 1;
        int idx = max;
        char c = '\0';
        // Working right to left, skip past numbers and colons
        while (idx > min) {
            c = chars[idx];
            if (c >= '0' && c <= '9' || c == ':') {
                idx--;
            } else {
                break;
            }
        }
        // Recognize umeric offset such as -0800 or +05:30
        if (idx >= min && (c == '+' || c == '-')) {
            return str.substring(idx);
        }
        // Still here?  Looking for a non-numeric time zone like "EST" or "America/New_York"
        while (idx >= min) {
            c = chars[idx];
            if (c >= 'A' && c <= 'Z' || c == '_' || c == '/' || c >= '0' && c <= '9') {
                // rewind to just before the beginning of a word
                idx--;
            } else {
                // set index to first character of that word
                ++idx;
                // skip past any numbers
                while (idx < max && chars[idx] >= '0' && chars[idx] <= '9') {
                    if (++idx == max) {
                        break;
                    }
                }
                break;
            }
        }
        if (idx < min || idx > max) {
            return null;
        }
        c = chars[idx];
        if (c >= 'A' && c <= 'Z') {
            return str.substring(idx);
        }
        return null;
    }

    /**
     * Compare two DateTime objects to determine ordering.
     *
     * @param other DateTime to compare to this
     * @return -1, 0, or 1 based on comparison to another DateTime.
     */
    public int compareTo(DateTime other) {
        if (other == null) {
            throw new NullPointerException("Cannot compare DateTime to null.");
        }
        return this.systemDur.compareTo(other.systemDur);
    }

    /**
     * Get a timestamp useful for JDBC
     *
     * @return This DateTime as a Timestamp object.
     */
    public Timestamp toTimestamp() {
        Timestamp ts = new Timestamp(this.systemDur.toMillis());
        if (this.systemDur.getNanos() > 0) {
            ts.setNanos(this.systemDur.getNanos());
        }
        return ts;
    }

    /**
     * Get Date/Time as a Java Date object.
     *
     * @return this DateTime truncated and converted to a java.util.Date object.
     */
    public Date toDate() {
        return new Date(this.systemDur.toMillis());
    }

    /**
     * Get the TimeZone used for formatted string output
     *
     * @return this TimeZone.
     */
    public TimeZone timeZone() {
        return config().getOutputTimeZone();
    }

    /**
     * By default, the toString method gives a sortable ISO 8601 date and time to nearest second in the same time zone as the
     * system. The default format can be redefined in DateTimeConfig.
     *
     * @return DateTime using the default config options
     */
    @Override
    public String toString() {
        return DateTimeFormat.format(config().getFormat(), this, config.getOutputTimeZone(), config.getLocale());
    }

    /**
     * Return a String according to the provided format.
     *
     * @param format Date format specifier
     * @return A formatted string version of the current DateTime.
     */
    public String toString(String format) {
        return DateTimeFormat.format(format, this, config().getOutputTimeZone(), config().getLocale());
    }

    /**
     * Return a String according to the provided format.
     *
     * @param format Date format specifier
     * @param tz     Show formatted date & time at the given TimeZone
     * @return A formatted string version of the current DateTime.
     */
    public String toString(String format, TimeZone tz) {
        return DateTimeFormat.format(format, this, tz, config().getLocale());
    }

    /**
     * Return a String according to the provided format.
     *
     * @param format Date format specifier
     * @param locale Show formatted date & time at the given TimeZone
     * @return A formatted string version of the current DateTime.
     */
    public String toString(String format, Locale locale) {
        return DateTimeFormat.format(format, this, this.timeZone(), locale);
    }

    /**
     * Return a String according to the provided format.
     *
     * @param tz Show formatted date & time at the given TimeZone
     * @return A formatted string version of the current DateTime.
     */
    public String toString(TimeZone tz) {
        return DateTimeFormat.format(config().getFormat(), this, tz, config().getLocale());
    }

    /**
     * Return a String according to the provided format.
     *
     * @param format Date format specifier
     * @param tz     Show formatted date & time at the given TimeZone
     * @param locale Display date words like month or day of week in a given language.
     * @return A formatted string version of the current DateTime.
     */
    public String toString(String format, TimeZone tz, Locale locale) {
        return DateTimeFormat.format(format, this, tz, locale);
    }

    /**
     * Add a fixed duration of time
     *
     * @param dur Duration
     * @return Newly calculated DateTime object.
     */
    public DateTime add(Duration dur) {
        Duration calcDur = dur.add(this.getSeconds(), this.getNanos());
        return new DateTime(calcDur.getSeconds(), calcDur.getNanos(), config());
    }

    /**
     * Add a fixed duration in milliseconds. The Duration object provides fixed multipliers such as SECOND or HOUR.
     *
     * @param milliseconds Duration in milliseconds
     * @return Newly calculated DateTime object.
     */
    public DateTime add(long milliseconds) {
        Duration dur = this.systemDur.add(milliseconds);
        return new DateTime(dur.getSeconds(), dur.getNanos(), config());
    }

    /**
     * Add +/- a block of time to a date in it's OutputTimeZone.
     *
     * @param calUnit CalendarUnit (MINUTE, DAY, WEEK, MONTH, etc)
     * @param qty     May be positive or negative.
     * @return recalculated DateTime
     */
    public DateTime add(CalendarUnit calUnit, int qty) {
        return shift(calUnit, qty);
    }

    private DateTime shiftUsingRecalculatedOffset(long milliseconds) {
        long beginningOffset = config.getOutputTimeZone().getOffset(toMillis());
        long unadjustedShift = toMillis() + milliseconds;
        long endingOffset = config.getOutputTimeZone().getOffset(unadjustedShift);
        return add(milliseconds - beginningOffset + endingOffset);
    }

    /**
     * Add increments of any calendar time unit from a nanosecond to a century. This is different from a Duration in
     * that it will make adjustments to preserve variables such as daylight saving or day-of-month offsets.
     *
     * @param calUnit CalendarUnit (MINUTE, DAY, WEEK, MONTH, etc)
     * @param qty     May be positive or negative.
     * @return Newly calculated DateTime object.
     */
    public DateTime shift(CalendarUnit calUnit, int qty) {
        /* Fixed durations */
        if (calUnit.compareTo(CalendarUnit.DAY) < 0) {
            if (calUnit == CalendarUnit.HOUR) {
                return shiftUsingRecalculatedOffset(qty * 3600000L);
            }
            if (calUnit == CalendarUnit.MINUTE) {
                return shiftUsingRecalculatedOffset(qty * 60000L);
            }
            if (calUnit == CalendarUnit.SECOND) {
                return shiftUsingRecalculatedOffset(qty * 1000L);
            }
            if (calUnit == CalendarUnit.MILLISECOND) {
                return shiftUsingRecalculatedOffset(qty);
            }
            if (calUnit == CalendarUnit.MICROSECOND) {
                long nanos = this.getNanos() + qty * 1000;
                long seconds = nanos / 1000000000L;
                int remainder = (int) (nanos - seconds * 1000000000L);
                return new DateTime(systemDur.getSeconds() + seconds, remainder, config);
            }
            if (calUnit == CalendarUnit.NANOSECOND) {
                long nanos = this.getNanos() + qty;
                long seconds = nanos / 1000000000L;
                int remainder = (int) (nanos - seconds * 1000000000L);
                return new DateTime(systemDur.getSeconds() + seconds, remainder, config);
            }
        }
        /* Calendar periods (same time, different day) */
        // InetSoft: always Gregorian, see Tm.calcTime() (#77605)
        Calendar cal = new GregorianCalendar(config().getInputTimeZone(), config().getLocale());
        cal.setTimeInMillis(this.systemDur.millis);
        if (calUnit == CalendarUnit.DAY) {
            cal.add(Calendar.DATE, qty);
        } else if (calUnit == CalendarUnit.WEEK) {
            cal.add(Calendar.DATE, qty * 7);
        } else if (calUnit == CalendarUnit.MONTH) {
            cal.add(Calendar.MONTH, qty);
        } else if (calUnit == CalendarUnit.QUARTER) {
            cal.add(Calendar.MONTH, qty * 3);
        } else if (calUnit == CalendarUnit.YEAR) {
            cal.add(Calendar.YEAR, qty);
        } else if (calUnit == CalendarUnit.CENTURY) {
            cal.add(Calendar.YEAR, 100 * qty);
        }
        return new DateTime(cal.getTimeInMillis() / 1000, systemDur.getNanos(), config);
    }

    /**
     * Shift this DateTime +/- a Shift offset.
     *
     * @param shift a pre-defined shift of various calendar time increments.
     * @return a new DateTime offset by the values specified.
     */
    public DateTime shift(Shift shift) {
        if (shift == null) {
            return this;
        }
        // InetSoft: always Gregorian, see Tm.calcTime() (#77605)
        Calendar cal = new GregorianCalendar(config().getOutputTimeZone(), config().getLocale());
        cal.setTimeInMillis(this.systemDur.millis);
        if (shift.getYear() != 0) {
            cal.add(Calendar.YEAR, shift.getYear());
        }
        if (shift.getMonth() != 0) {
            cal.add(Calendar.MONTH, shift.getMonth());
        }
        if (shift.getWeek() != 0) {
            cal.add(Calendar.DATE, shift.getWeek() * 7);
        }
        if (shift.getDay() != 0) {
            cal.add(Calendar.DATE, shift.getDay());
        }
        if (shift.getHour() != 0) {
            cal.add(Calendar.HOUR, shift.getHour());
        }
        if (shift.getMinute() != 0) {
            cal.add(Calendar.MINUTE, shift.getMinute());
        }
        if (shift.getSecond() != 0) {
            cal.add(Calendar.SECOND, shift.getSecond());
        }
        return new DateTime(cal.getTimeInMillis() / 1000, systemDur.getNanos() + shift.getNanosec(), config);
    }

    /**
     * Shift this DateTime +/- a Shift offset specified as an ISO 8601 string.
     *
     * @param iso8601 A string of format "P[#Y][#M][#D][T[#H][#M][#S[.#]]" holding a list of offsets.
     * @return a new DateTime shifted by the specified amounts.
     */
    public DateTime shift(String iso8601) {
        return this.shift(new Shift(iso8601));
    }

    /**
     * Return numeric day of week, usually Sun=1, Mon=2, ... , Sat=7;
     *
     * @return Numeric day of week, usually Sun=1, Mon=2, ... , Sat=7. See DateTimeConfig.
     */
    public int weekday() {
        long leftover;
        // Adding 2000 years in weeks makes all calculations positive.
        // Adding epoch DOW shifts us into phase with start of week.
        long offset = config().getEpochDOW() * Duration.DAY + 52 * Duration.WEEK * 2000;
        leftover = offset + this.toMillis() + config().getOutputTimeZone().getOffset(this.toMillis());
        leftover %= Duration.WEEK;
        leftover /= Duration.DAY;
        // Convert from zero to one based
        leftover++;
        return (int) leftover;
    }

    /**
     * Parse a time reference that fits in a single word. Supports: YYYYMMDD, [+-]D, [0-9]+Y
     *
     * @param str    Date/Time string to be parsed.
     * @param config Configuration parameters governing parsing and presentation.
     * @return New DateTime interpreted from string.
     */
    private static DateTime parseRelativeDate(String str, IDateTimeConfig config) {
        char firstChar = str.charAt(0);
        char lastChar = str.charAt(str.length() - 1);
        DateTime dt = new DateTime(config);
        if ((firstChar == '+' || firstChar == '-') && lastChar >= '0' && lastChar <= '9') {
            if (onlyDigits(str.substring(1))) {
                int offset = new Integer((firstChar == '+') ? str.substring(1) : str);
                return dt.add(CalendarUnit.DAY, offset);
            }
        }
        if ((lastChar == 'D' || lastChar == 'Y' || lastChar == 'M')) {
            CalendarUnit unit;
            if (lastChar == 'D') {
                unit = CalendarUnit.DAY;
            } else if (lastChar == 'Y') {
                unit = CalendarUnit.YEAR;
            } else {
                unit = CalendarUnit.MONTH;
            }
            String inner = str.substring((firstChar >= '0' && firstChar <= '9') ? 0 : 1, str.length() - 1);
            // ^[+-][0-9]+$
            if (firstChar == '+') {
                if (onlyDigits(inner)) {
                    int offset = new Integer(inner);
                    return dt.add(unit, offset);
                }
            }
            if ((firstChar == '-' || firstChar >= '0' && firstChar <= '9') && isInteger(inner)) {
                String offset = firstChar == '-' ? "-" + inner : inner;
                int innerVal = new Integer(offset);
                return dt.add(unit, innerVal);
            }
        }
        throw new IllegalArgumentException("Could not parse date from '" + str + "'");
    }

    /**
     * Interpret a DateTime from a String using global defaults.
     *
     * @param str Date/Time string to be parsed.
     * @return New DateTime interpreted from string.
     */
    public static DateTime parse(String str) {
        IDateTimeConfig config = DateTimeConfig.getGlobalDefault();
        return parse(str, config);
    }

    private static class HasDatepart {
        boolean year;
        boolean month;
        boolean day;
        boolean hour;
        boolean minute;
        boolean second;
        boolean nanosecond;
    }

    private static class DateState {
        boolean isYearFirst;
        boolean isTwoDigitYear;
        boolean isBC;
        int centuryTurn;
        int thisYear;
        int year;
        int month;
        int day = 1;
        int hour;
        int minute;
        int second;
        int nanosecond;
        String[] parts;
        boolean[] integers;
        boolean[] usedint;
    }

    public static void assignIntegersToRemainingSlots(IDateTimeConfig config, HasDatepart hasDatepart,
                                                      DateState dateState) {
        // Assign integers to remaining slots in order
        for (int i = 0; i < dateState.parts.length; i++) {
            if (dateState.integers[i] && !dateState.usedint[i]) {
                int part = parseIntFragment(dateState.parts[i]);
                if (!hasDatepart.day && part < 32 && config.isDmyOrder()) {
                    /*
                     * If one sets the isDmyOrder to true in DateTimeConfig, then this will properly interpret DD before MM in
                     * DD-MM-yyyy dates. If the first number is a year, then an ISO 8601 date is assumed, in which MM comes before
                     * DD.
                     */
                    if (!dateState.isYearFirst) {
                        dateState.day = part;
                        hasDatepart.day = true;
                        dateState.usedint[i] = true;
                        continue;
                    }
                }
                if (!hasDatepart.month) {
                    if (part < 1 || part > 12) {
                        throw new IllegalArgumentException("Invalid month parsed from [" + part + "].");
                    }
                    dateState.month = part - 1;
                    hasDatepart.month = true;
                    dateState.usedint[i] = true;
                    continue;
                }
                if (!hasDatepart.day) {
                    if (part < 1 || part > 31) {
                        throw new IllegalArgumentException("Invalid day parsed from [" + part + "].");
                    }
                    dateState.day = part;
                    hasDatepart.day = true;
                    dateState.usedint[i] = true;
                    continue;
                }
                if (!hasDatepart.year && part < 1000) {
                    if (part > 99) {
                        dateState.year = 1900 + part;
                    } else {
                        dateState.isTwoDigitYear = true;
                        if (dateState.centuryTurn + part - dateState.thisYear > 20) {
                            dateState.year = dateState.centuryTurn + part - 100;
                        } else {
                            dateState.year = dateState.centuryTurn + part;
                        }
                    }
                    hasDatepart.year = true;
                    dateState.usedint[i] = true;
                    continue;
                }
                if (!hasDatepart.day || !hasDatepart.year) {
                    throw new IllegalArgumentException("Unable to determine valid placement for parsed value [" + part + "].");
                }
                if (!hasDatepart.hour) {
                    if (part >= 24) {
                        throw new IllegalArgumentException("Invalid hour parsed from [" + part + "].");
                    }
                    dateState.hour = part;
                    hasDatepart.hour = true;
                    if (dateState.parts[i].indexOf('H') == -1) {
                        dateState.usedint[i] = true;
                        continue;
                    }
                    dateState.parts[i] = dateState.parts[i].substring(dateState.parts[i].indexOf('H') + 1);
                    part = parseIntFragment(dateState.parts[i]);
                }
                if (!hasDatepart.minute) {
                    if (part >= 60) {
                        throw new IllegalArgumentException("Invalid minute parsed from [" + part + "].");
                    }
                    dateState.minute = part;
                    hasDatepart.minute = true;
                    if (dateState.parts[i].indexOf('M') == -1) {
                        dateState.usedint[i] = true;
                        continue;
                    }
                    dateState.parts[i] = dateState.parts[i].substring(dateState.parts[i].indexOf('M') + 1);
                    part = parseIntFragment(dateState.parts[i]);
                }
                if (!hasDatepart.second) {
                    if (part < 60 || part == 60 && dateState.minute == 59 && dateState.hour == 23 && dateState.day >= 30
                            && (dateState.month == 11 || dateState.month == 5)) {
                        dateState.second = part;
                        hasDatepart.second = true;
                        dateState.usedint[i] = true;
                        continue;
                    } else {
                        throw new IllegalArgumentException("Invalid second parsed from [" + part + "].");
                    }
                }
                if (!hasDatepart.nanosecond) {
                    if (part >= 1000000000) {
                        throw new IllegalArgumentException("Invalid nanosecond parsed from [" + part + "].");
                    }
                    dateState.nanosecond = Integer.parseInt((dateState.parts[i].split("[^0-9]+")[0] + "00000000").substring(0, 9));
                    hasDatepart.nanosecond = true;
                    dateState.usedint[i] = true;
                }
            }
        }
    }

    private static void scanForTextualMonth(IDateTimeConfig config, HasDatepart hasDatepart,
                                            DateState dateState) {
        // First, scan for text month
        for (int i = 0; i < dateState.parts.length; i++) {
            if (!dateState.integers[i] && dateState.parts[i].length() > 2) {
                Integer monthIndex = config.lookupMonthIndex(dateState.parts[i]);

                if (monthIndex != null) {
                    dateState.month = monthIndex;
                    hasDatepart.month = true;
                    break;
                }
            }
            if (hasDatepart.month) {
                break;
            }
        }

    }

    private static void adjustHourBasedOnAMPM(DateState dateState) {
        /**
         * Adjust 12AM and 1-11PM.
         */
        for (String part : dateState.parts) {
            if (part.endsWith("M")) {
                if (part.endsWith("PM") && dateState.hour > 0 && dateState.hour < 12) {
                    dateState.hour += 12;
                } else if (part.endsWith("AM") && dateState.hour == 12) {
                    dateState.hour = 0;
                }
            }
        }
    }

    private static void scanForYYYYOrYYYYMMDD(IDateTimeConfig config, HasDatepart hasDatepart, DateState dateState) {
        // Scan for 4-digit year or an 8 digit YYYYMMDD
        for (int i = 0; i < dateState.parts.length; i++) {
            if (dateState.integers[i] && !dateState.usedint[i]) {
                if (!hasDatepart.year && (dateState.parts[i].length() == 4 || dateState.parts[i].length() == 5)) {
                    char c = dateState.parts[i].charAt(dateState.parts[i].length() - 1);
                    if (c >= '0' && c <= '9') {
                        dateState.year = parseIntFragment(dateState.parts[i]);
                        hasDatepart.year = true;
                        dateState.usedint[i] = true;
                        dateState.isYearFirst = (i == 0);
                        // If integer is to the immediate left of year, use now.
                        if (config.isDmyOrder()) {
                            if (!hasDatepart.month && i > 0 && dateState.integers[i - 1] && !dateState.usedint[i - 1]) {
                                dateState.month = parseIntFragment(dateState.parts[i - 1]);
                                dateState.month--;
                                hasDatepart.month = true;
                                dateState.usedint[i - 1] = true;
                            }
                        } else {
                            if (!hasDatepart.day && i > 0 && dateState.integers[i - 1] && !dateState.usedint[i - 1]) {
                                dateState.day = parseIntFragment(dateState.parts[i - 1]);
                                hasDatepart.day = true;
                                dateState.usedint[i - 1] = true;
                            }
                        }
                        break;
                    }
                }
                if (!hasDatepart.year && !hasDatepart.month && !hasDatepart.day && dateState.parts[i].length() == 8) {
                    dateState.year = Integer.parseInt(dateState.parts[i].substring(0, 4));
                    dateState.month = Integer.parseInt(dateState.parts[i].substring(4, 6));
                    dateState.month--;
                    dateState.day = Integer.parseInt(dateState.parts[i].substring(6, 8));
                    hasDatepart.year = true;
                    hasDatepart.month = true;
                    hasDatepart.day = true;
                    dateState.usedint[i] = true;
                }
            }
        }
    }

    private static void validateParsedDate(String dateString, HasDatepart hasDatepart, DateState dateState) {
        /**
         * Validate
         */
        if (!hasDatepart.year || !hasDatepart.month) {
            throw new IllegalArgumentException("Could not determine Year, Month, and Day from '" + dateString + "'");
        }
        if (dateState.month == FEB) {
            if (dateState.day > 28 + (dateState.year % 4 == 0 ? 1 : 0)) {
                throw new IllegalArgumentException("February " + dateState.day + " does not exist in " + dateState.year);
            }
        } else if (dateState.month == SEP || dateState.month == APR || dateState.month == JUN || dateState.month == NOV) {
            if (dateState.day > 30) {
                throw new IllegalArgumentException("30 days hath Sep, Apr, Jun, and Nov... not " + dateState.day);
            }
        } else if (dateState.month < 0 || dateState.month > 11) {
            throw new IllegalArgumentException("Could not determine a valid month");
        } else if (dateState.day > 31) {
            throw new IllegalArgumentException("No month has " + dateState.day + " days in it.");
        }

    }

    /**
     * Interpret a DateTime from a String.
     *
     * @param str    Date/Time string to be parsed.
     * @param config Configuration parameters governing parsing and presentation.
     * @return New DateTime interpreted from string according to alternate rules.
     */
    public static DateTime parse(String str, IDateTimeConfig config) {

        HasDatepart hasDatepart = new HasDatepart();
        DateState dateState = new DateState();

        if (config == null) {
            config = DateTimeConfig.getGlobalDefault();
        }
        if (str == null) {
            return new DateTime(config.systemTime(), config);
        }
        // Normalize the string a bit
        str = str.trim().toUpperCase(config.getLocale());
        if (str.length() == 0) {
            throw new IllegalArgumentException("Cannot parse DateTime from empty string.");
        }
        if (str.indexOf('T') > 0) {
            // Replace a T separator with a space separator.
            str = str.replaceFirst("([0-9])T([0-9])", "$1 $2");
        }
        if (str.charAt(0) == '+' || str.charAt(0) == '-') {
            return parseRelativeDate(str, config);
        }
        if (str.matches(".*([0-9][A-Z]|[A-Z][0-9]).*")) {
            // Expand dates that use number-to-alpha as implied separator
            // Chars DMY are reserved for day, month, year relative dates
            str = str.replaceAll("([0-9])(Z|[A-Y]{2})", "$1 $2");
            str = str.replaceAll("([A-Z]{3})([0-9])", "$1 $2");
        }
        String tzString = tzParse(str);
        if ("AM".equals(tzString) || "PM".equals(tzString)) {
            tzString = null;
        }
        if ("BC".equals(tzString) || "BCE".equals(tzString)) {
            tzString = null;
            dateState.isBC = true;
        }
        if (tzString != null && tzString.endsWith("0")) {
            if (tzString.matches("[+-][0-9]{2}:[0-9]{2}")) {
                tzString = "GMT" + tzString;
                str = str.substring(0, str.length() - 6);
            } else if (tzString.matches("[+-][0-9]{4}")) {
                tzString = "GMT" + tzString.substring(0, 3) + ":" + tzString.substring(3);
                str = str.substring(0, str.length() - 5);
            }
        }
        TimeZone tz = tzString == null ? config.getInputTimeZone() : config.lookupTimeZone(tzString);
        Tm tm = new Tm(config.systemTime(), tz);
        dateState.parts = partsPattern.split(str);
        dateState.thisYear = tm.getYear();
        dateState.centuryTurn = dateState.thisYear - (dateState.thisYear % 100);
        // Build a table describing which fields are integers.
        dateState.integers = new boolean[dateState.parts.length];
        dateState.usedint = new boolean[dateState.parts.length];
        for (int i = 0; i < dateState.parts.length; i++) {
            if (startsWithDigit(dateState.parts[i])) {
                dateState.integers[i] = true;
            }
        }
        scanForTextualMonth(config, hasDatepart, dateState);

        scanForYYYYOrYYYYMMDD(config, hasDatepart, dateState);

        if (hasDatepart.year && dateState.year == 0) {
            throw new IllegalArgumentException("Invalid zero year parsed.");
        }
        // One more scan for Date.toString() style
        if (!hasDatepart.year && str.endsWith("T " + dateState.parts[dateState.parts.length - 1])) {
            dateState.year = Integer.parseInt(dateState.parts[dateState.parts.length - 1]);
            hasDatepart.year = true;
            dateState.usedint[dateState.usedint.length - 1] = true;
        }
        if (!hasDatepart.year) {
            /* Remove time and alpha */
            String masked = str.replaceAll("[0-9]+:[0-9:]+|[a-zA-Z]+", "");
            if (masked.matches("^\\s*[0-9]+\\s*$")) {
                /* No year given. We'll use this year and test for Dec/Jan at end. */
                dateState.year = dateState.thisYear;
                hasDatepart.year = true;
            }
        }
        assignIntegersToRemainingSlots(config, hasDatepart, dateState);
        adjustHourBasedOnAMPM(dateState);
        validateParsedDate(str, hasDatepart, dateState);

        if (dateState.isBC && dateState.year >= 0) {
            dateState.year = -dateState.year + 1;
        }
        DateTime returnDt = new DateTime(Tm.calcTime(dateState.year, 1 + dateState.month, dateState.day, dateState.hour, dateState.minute, dateState.second, dateState.nanosecond / 1000000, tz),
                config);

        if (dateState.isTwoDigitYear && config.isUnspecifiedCenturyAlwaysInPast()) {
            if (returnDt.getSeconds() * 1000 > config.systemTime()) {
                returnDt = returnDt.shift(CalendarUnit.CENTURY, -1);
            }
        }


        returnDt.systemDur.nanos = dateState.nanosecond;
        return returnDt;
    }

    /**
     * Truncate DateTime down to its nearest time unit as a time. CalendarUnit.(WEEK|DAY|HOUR|MINUTE|SECOND|MILLISECOND)
     *
     * @param unit Unit of time to which new DateTime will be truncated.
     * @return A newly calculated DateTime.
     */
    public DateTime truncate(CalendarUnit unit) {
        long trim;
        if (unit.compareTo(CalendarUnit.HOUR) < 0) {
            if (unit == CalendarUnit.MINUTE) {
                trim = this.systemDur.millis % Duration.MINUTE;
                if (trim < 0) {
                    trim += Duration.MINUTE;
                }
                return new DateTime(this.systemDur.millis - trim, config());
            }
            if (unit == CalendarUnit.SECOND) {
                trim = this.systemDur.millis % Duration.SECOND;
                if (trim < 0) {
                    trim += Duration.SECOND;
                }
                return new DateTime(this.systemDur.millis - trim, config.getOutputTimeZone());
            }
            if (unit == CalendarUnit.MILLISECOND) {
                return new DateTime(this.systemDur.millis, config.getOutputTimeZone());
            }
            if (unit == CalendarUnit.MICROSECOND) {
                int nanotrim = this.systemDur.nanos % 1000000;
                if (nanotrim < 0) {
                    nanotrim += 1000000;
                }
                return new DateTime(this.getSeconds(), this.systemDur.nanos - nanotrim, config);
            }
            return new DateTime(this.systemDur.millis, config);
        }
        // Shift to same time of day at Rose line
        long calcTime = this.systemDur.millis + config().getOutputTimeZone().getOffset(this.systemDur.millis);
        // Truncate and shift back to local time
        if (unit == CalendarUnit.HOUR) {
            trim = calcTime % Duration.HOUR;
            if (trim < 0) {
                trim += Duration.HOUR;
            }
            calcTime -= trim;
            calcTime -= config().getOutputTimeZone().getOffset(calcTime);
            return new DateTime(calcTime, config());
        }
        if (unit == CalendarUnit.DAY) {
            trim = calcTime % Duration.DAY;
            if (trim < 0) {
                trim += Duration.DAY;
            }
            calcTime -= trim;
            calcTime -= config().getOutputTimeZone().getOffset(calcTime);
            return new DateTime(calcTime, config());
        }
        if (unit == CalendarUnit.WEEK) {
            long dow = ((calcTime / Duration.DAY) + config().getEpochDOW()) % 7;
            calcTime -= (calcTime % Duration.DAY + Duration.DAY * dow);
            calcTime -= config().getOutputTimeZone().getOffset(calcTime);
            return new DateTime(calcTime, config());
        }
        Tm tm = new Tm(this.systemDur.millis, config().getOutputTimeZone());
        if (unit == CalendarUnit.MONTH) {
            return new DateTime(Tm.calcTime(tm.getYear(), tm.getMonth(), 1, 0, 0, 0, 0, config.getOutputTimeZone()), config);
        }
        if (unit == CalendarUnit.QUARTER) {
            int monthOffset = (tm.getMonth() - 1) % 3;
            return new DateTime(
                    Tm.calcTime(tm.getYear(), tm.getMonth() - monthOffset, 1, 0, 0, 0, 0, config.getOutputTimeZone()), config);
        }
        if (unit == CalendarUnit.YEAR) {
            return new DateTime(Tm.calcTime(tm.getYear(), 1, 1, 0, 0, 0, 0, config.getOutputTimeZone()), config);
        }
        if (unit == CalendarUnit.CENTURY) {
            return new DateTime(Tm.calcTime(tm.getYear() - tm.getYear() % 100, 1, 1, 0, 0, 0, 0, config.getOutputTimeZone()),
                    config);
        }
        throw new IllegalArgumentException("That precision is still unsupported.  Sorry, my bad.");
    }

    /**
     * Whole seconds offset from epoch.
     *
     * @return Whole seconds offset from epoch (1970-01-01 00:00:00).
     */
    public long getSeconds() {
        return systemDur.getSeconds();
    }

    /**
     * Whole milliseconds offset from epoch.
     *
     * @return Milliseconds offset from epoch (1970-01-01 00:00:00).
     */
    public long toMillis() {
        return systemDur.toMillis();
    }

    /**
     * Positive nanosecond offset from Seconds.
     *
     * @return Fractional second in nanoseconds for the given time.
     */
    public int getNanos() {
        int nanos = systemDur.getNanos();
        return nanos >= 0 ? nanos : 1000000000 + nanos;
    }

    /**
     * This compares a DateTime with another DateTime.
     *
     * @param dateTime DateTime to which this DateTime will be compared.
     * @return True if DateTime values represent the same point in time.
     */
    @Override
    public boolean equals(Object dateTime) {
        if (dateTime == null) {
            return false;
        }
        if (dateTime.getClass() == this.getClass()) {
            DateTime dt = (DateTime) dateTime;
            return systemDur.toMillis() == dt.toMillis() && systemDur.getNanos() == dt.getNanos();
        }
        return false;
    }

    @Override
    /*
     * * Reasonably unique hashCode, since we're providing an equals method.
     * 
     * @return a hashCode varying by the most significant fields, millis and nanos.
     */
    public int hashCode() {
        return systemDur.hashCode();
    }

    /**
     * Return the global configuration used by DateTime.
     *
     * @return the global DateTimeConfig object used by DateTime.
     */
    public IDateTimeConfig config() {
        if (this.config == null) {
            this.config = DateTimeConfig.getGlobalDefault();
        }
        return this.config;
    }


    // ==========================================
    // Copied from POJava to eliminate dependency
    // ==========================================

    /**
     * Parse an integer from left-to-right until non-digit reached
     *
     * @param str String to parse date/time
     * @return first integer greedily matched from a string
     */
    private static int parseIntFragment(String str) {
        if (str == null) {
            return 0;
        }
        int parsed = 0;
        boolean isNeg = false;
        char[] strip = str.toCharArray();
        char c = strip[0];
        if (c == '-') {
            isNeg = true;
        } else if (c >= '0' && c <= '9') {
            parsed = c - '0';
        } else {
            return 0;
        }
        for (int i = 1; i < strip.length; i++) {
            c = strip[i];
            if (c >= '0' && c <= '9') {
                parsed = 10 * parsed + c - '0';
            } else {
                break;
            }
        }
        return isNeg ? -parsed : parsed;
    }

    /**
     * True if a string starts with a digit.
     *
     * @param s string
     * @return true if string starts with a digit.
     */
    private static boolean startsWithDigit(String s) {
        if (s == null || s.length() == 0) {
            return false;
        }
        char c = s.charAt(0);
        return (c >= '0' && c <= '9');
    }

    /**
     * True if a string has only digits in it.
     *
     * @param s String to verify
     * @return true if string is composed of only digits.
     */
    private static boolean onlyDigits(String s) {
        if (s == null || s.length() == 0) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * True if a string matches /^[-]?[0-9]+$/
     *
     * @param s String to verify
     * @return true if string is numeric
     */
    private static boolean isInteger(String s) {
        if (s == null || s.length() == 0) {
            return false;
        }
        char c = s.charAt(0);
        if (s.length() == 1) {
            return c >= '0' && c <= '9';
        }
        return (c == '-' || c >= '0' && c <= '9') && onlyDigits(s.substring(1));
    }

}