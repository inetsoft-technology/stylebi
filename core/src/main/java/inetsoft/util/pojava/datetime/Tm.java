package inetsoft.util.pojava.datetime;

/*
 Copyright 2008-10 John Pile

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

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * This class converts a DateTime into year, month, day, hour, minute, second, millisecond,
 * nanosecond. It is similar to the tm struct in C.
 *
 * @author John Pile
 */
public class Tm {

    /**
     * The following constants are represented in milliseconds
     */
    private static final int HOUR = 3600000;
    private static final long DAY = HOUR * 24;
    private static final long YEAR = DAY * 365;
    private static final long QUADYEAR = DAY * (365 * 4 + 1);
    private static final long CENT = QUADYEAR * 25 - DAY;
    private static final long QUADCENT = 4 * CENT + DAY;
    // Our year starts March 1
    private static final long[] MONTH = {0, 31 * DAY, 61 * DAY, 92 * DAY, 122 * DAY,
            153 * DAY, 184 * DAY, 214 * DAY, 245 * DAY, 275 * DAY, 306 * DAY, 337 * DAY,
            365 * DAY};
    /**
     * The true Gregorian Calendar was initiated in October 1582, but this start date is easier
     * for calculations, so I use it as an epoch. The year starts on March 1 so that a leap day
     * is always at the end of a year.
     */
    private static final long GREG_EPOCH_UTC = -11670912000000L;
    private static final long START_OF_AD = -62135740800000L;
    /**
     * These are the results we're looking to populate.
     */
    private int year;
    private int month;
    private int day;
    private int hour;
    private int minute;
    private int second;
    private int nanosecond;
    private int weekday;
    private TimeZone tz;

    /**
     * Populate year, month, day, hour, min, sec, nano from a DateTime
     *
     * @param dt DateTime object
     */
    public Tm(DateTime dt) {
        init(dt, dt.timeZone());
    }

    /**
     * Constructor
     *
     * @param dt Populate year, month, day, hour, min, sec, nano from a DateTime
     * @param tz Assert a time zone under which those values are represented.
     */
    public Tm(DateTime dt, TimeZone tz) {
        init(dt, tz);
    }

    /**
     * Populate year, month, day, hour, min, sec, nano
     *
     * @param millis Date/Time in UTC assuming the default time zone.
     */
    public Tm(long millis) {
        IDateTimeConfig config = DateTimeConfig.getGlobalDefault();
        init(new DateTime(millis, config), config.getOutputTimeZone());
    }

    /**
     * Constructor
     *
     * @param millis Millis since epoch
     * @param tz     TimeZone represented by the Tm
     */
    public Tm(long millis, TimeZone tz) {
        IDateTimeConfig globalDefault = DateTimeConfig.getGlobalDefault();
        if (tz == null) tz = globalDefault.getOutputTimeZone();
        LocalConfig config = LocalConfig.instanceOverridingOutputTimeZone(globalDefault, tz);
        init(new DateTime(millis, config), tz);
    }

    /**
     * We'll direct the pre-GREG_EPOCH times here for now. Most folks don't use them, so
     * optimizing is not my highest priority.
     *
     * @param millis Number of milliseconds since epoch (typically negative)
     */
    private void initYeOlde(long millis) {
        // Taking the easy way out...
        Calendar cal = new GregorianCalendar(this.tz);
        cal.setTimeInMillis(millis);
        this.year = cal.get(Calendar.YEAR);
        this.month = 1 + cal.get(Calendar.MONTH);
        this.day = cal.get(Calendar.DATE);
        this.hour = cal.get(Calendar.HOUR_OF_DAY);
        this.minute = cal.get(Calendar.MINUTE);
        this.second = cal.get(Calendar.SECOND);
        this.weekday = cal.get(Calendar.DAY_OF_WEEK);
        if (millis < START_OF_AD) {
            year = -year;
        }
    }

    /**
     * Calculate date parts.
     *
     * @param dt       DateTime
     * @param timeZone TimeZone under which Tm will be represented
     */
    private void init(DateTime dt, TimeZone timeZone) {
        this.tz = timeZone != null ? timeZone : dt.config().getOutputTimeZone();
        long millis = dt.toMillis();
        // Compensate for difference between the system time zone and the recorded time zone
        long duration = millis - GREG_EPOCH_UTC + timeZone.getOffset(dt.toMillis());
        this.nanosecond = dt.getNanos();
        this.weekday = calcWeekday(millis, timeZone);
        if (millis < GREG_EPOCH_UTC - this.tz.getRawOffset()) {
            initYeOlde(millis);
            return;
        }
        // Remove 400yr blocks, then 100yr, then 4, then 1.
        long quadCents = duration / QUADCENT;
        duration -= quadCents * QUADCENT;
        long cents = Math.min(3, duration / CENT);
        duration -= cents * CENT;
        long quadYears = duration / QUADYEAR;
        duration -= quadYears * QUADYEAR;
        year = Math.min(3, (int) (duration / YEAR));
        duration -= year * YEAR;
        // Calculate year based on those blocks
        year += 1600 + quadCents * 400 + cents * 100 + quadYears * 4;
        month = (int) (duration / (30 * DAY));
        if (MONTH[month] <= duration) {
            month++;
        }
        // Same strategy as above, removing largest chunks first.
        if (month > 0) {
            duration -= MONTH[month - 1];
        }
        day = (int) (duration / DAY);
        duration -= day * DAY;
        hour = (int) duration / HOUR;
        duration -= 1L * hour * HOUR;
        minute = (int) duration / 60000;
        duration -= minute * 60000L;
        second = (int) duration / 1000;
        day++;
        // Shift from March calendar start, to January calendar start.
        month += 2;
        if (month > 12) {
            year++;
            if (month == 15) {
                day = 29;
                month = 2;
            } else {
                month -= 12;
            }
        }

    }

    /**
     * Return numeric day of week, usually Sun=1, Mon=2, ... , Sat=7;
     *
     * @param millis   milliseconds since epoch
     * @param timeZone TimeZone under which Tm will be represented
     * @return Numeric day of week, usually Sun=1, Mon=2, ... , Sat=7. See DateTimeConfig.
     */
    public static int calcWeekday(long millis, TimeZone timeZone) {
        long leftover = 0;
        // Adding 2000 years in weeks makes all calculations positive.
        // Adding epoch DOW shifts us into phase with start of week.
        long offset = DateTimeConfig.getGlobalDefault().getEpochDOW() * Duration.DAY + 52
                * Duration.WEEK * 2000;
        leftover = offset + millis + timeZone.getOffset(millis);
        leftover %= Duration.WEEK;
        leftover /= Duration.DAY;
        // Convert from zero to one based
        leftover++;
        return (int) leftover;
    }

    /**
     * Determine "time" in milliseconds since epoch, UTC, as of the entered local time.
     *
     * @param year  calendar year
     * @param month calendar month
     * @param day   calendar day
     * @return time in milliseconds since epoch, UTC.
     */
    public static long calcTime(int year, int month, int day) {
        IDateTimeConfig config = DateTimeConfig.getGlobalDefault();
        return calcTime(year, month, day, 0, 0, 0, 0, config.getOutputTimeZone());
    }

    /**
     * Determine "time" in milliseconds since epoch, UTC, as of the entered local time.
     * It is acceptable to use numbers outside of a normal range, such as a 15th month, or 64th minute.
     *
     * @param year  calendar year
     * @param month calendar month
     * @param day   calendar day
     * @param hour  hour of day
     * @param min   minute of hour
     * @param sec   seconds
     * @param milli milliseconds
     * @return time in milliseconds since epoch, UTC.
     */
    public static long calcTime(int year, int month, int day, int hour, int min, int sec,
                                int milli) {
        IDateTimeConfig config = DateTimeConfig.getGlobalDefault();
        return calcTime(year, month, day, hour, min, sec, milli, config.getOutputTimeZone());
    }

    /**
     * Determine "time" in milliseconds since epoch, UTC, as of the given time zone provided.
     *
     * @param year  calendar year
     * @param month calendar month
     * @param day   calendar day
     * @param hour  hour of day
     * @param min   minute of hour
     * @param sec   seconds
     * @param milli millisecons
     * @param tz    time zone in which the date parts are given
     * @return number of milliseconds since Epoch
     */
    public static long calcTime(int year, int month, int day, int hour, int min, int sec,
                                int milli, TimeZone tz) {
        // InetSoft: always Gregorian. Calendar.getInstance(tz) takes the calendar of the JVM
        // default locale, which reads the year as Buddhist (th_TH) or in the current Japanese
        // era (ja_JP_JP), e.g. 1994 became 1451 or 4012 (#77605)
        Calendar cal = new GregorianCalendar(tz);
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month - 1);
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, min);
        cal.set(Calendar.SECOND, sec);
        cal.set(Calendar.MILLISECOND, milli);
        return cal.getTimeInMillis();
    }

    /**
     * @return Year as YYYY
     */
    public int getYear() {
        return year;
    }

    /**
     * Returns month between 1 and 12. Differs from C version of tm, but you can always subtract
     * 1 if you want zero-based.
     *
     * @return Month as Jan=1, Feb=2, ..., Dec=12
     */
    public int getMonth() {
        return month;
    }

    /**
     * Returns day of month between 1 and 31.
     *
     * @return Day of month.
     */
    public int getDay() {
        return day;
    }

    /**
     * Returns hour of day between 0 and 23.
     *
     * @return Hour of day.
     */
    public int getHour() {
        return hour;
    }

    /**
     * Returns minute of hour between 0 and 59.
     *
     * @return Minute of hour.
     */
    public int getMinute() {
        return minute;
    }

    /**
     * Returns second of minute between 0 and 59.
     *
     * @return Second of minute.
     */
    public int getSecond() {
        return second;
    }

    /**
     * Returns millisecond fraction of second between 0 and 999999.
     *
     * @return Millisecond fraction of second.
     */
    public int getMillisecond() {
        return nanosecond / 1000000;
    }

    /**
     * Returns nanosecond fraction of second between 0 and 999999999.
     *
     * @return Nanosecond fraction of second.
     */
    public int getNanosecond() {
        return nanosecond;
    }

    /**
     * Returns weekday between 1 and 7
     *
     * @return Typically (although configurable) Sun=1 .. Sat=7
     */
    public int getWeekday() {
        return weekday;
    }

}
