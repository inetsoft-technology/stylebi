package inetsoft.util.pojava.datetime;

import java.io.Serializable;

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
 */

/**
 * Duration is a fixed measurement of time.
 *
 * @author John Pile
 */
public class Duration implements Comparable<Duration>, Serializable {

    /**
     * Compulsory serial ID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * A MILLISECOND = one thousandth of a second
     */
    public static final long MILLISECOND = 1L;

    /**
     * A SECOND = One second is the time that elapses during 9,192,631,770 cycles of the
     * radiation produced by the transition between two levels of the cesium 133 atom... rounded
     * to some margin of error by your less accurate system clock.
     */
    public static final long SECOND = 1000L * MILLISECOND;

    /**
     * A MINUTE = 60 seconds
     */
    public static final long MINUTE = 60L * SECOND;

    /**
     * An HOUR = 60 minutes
     */
    public static final long HOUR = 60L * MINUTE;

    /**
     * A DAY = 24 hours (for a variable day, see CalendarUnit)
     */
    public static final long DAY = 24L * HOUR;

    /**
     * A WEEK = a fixed set of seven 24-hour days
     */
    public static final long WEEK = 7L * DAY;

    /**
     * 1 ms = 1 million nanoseconds
     */
    private static final int NANOS_PER_MILLI = 1000000;

    /**
     * Non-leap Milliseconds since an epoch
     */
    protected long millis = 0;

    /**
     * Nanoseconds used by high-resolution time stamps
     */
    protected int nanos = 0;

    /**
     * Constructor for a duration of zero.
     */
    public Duration() {
        // Default is zero duration
    }

    /**
     * Constructor parsing from a string.
     *
     * @param duration String representation of a duration.
     */
    public Duration(String duration) {
        char[] chars = duration.toCharArray();
        double accum = 0.0;
        double tot = 0.0;
        double dec = 1.0;
        int sign = 1;
        // Weeks, days, hours, minutes, seconds, nanoseconds [wdhmsn]
        for (char c : chars) {
            if (c == '.') {
                dec /= 10;
            } else if (c == '-') {
                sign = -1;
            } else if (c >= '0' && c <= '9') {
                if (Math.abs(dec) > 0.5) {
                    accum = accum * 10 + sign * dec * (c - '0');
                } else {
                    accum += sign * dec * (c - '0');
                    dec /= 10;
                }
            } else if (c == 'w' || c == 'W') {
                if (accum != 0) {
                    tot += Duration.WEEK * accum;
                    accum = 0;
                    dec = 1;
                }
            } else if (c == 'd' || c == 'D') {
                if (accum != 0) {
                    tot += Duration.DAY * accum;
                    accum = 0;
                    dec = 1;
                }
            } else if (c == 'h' || c == 'H') {
                if (accum != 0) {
                    tot += Duration.HOUR * accum;
                    accum = 0;
                    dec = 1;
                }
            } else if (c == 'm' || c == 'M' || c == '\'') {
                if (accum != 0) {
                    tot += Duration.MINUTE * accum;
                    accum = 0;
                    dec = 1;
                }
            } else if (c == 's' || c == 'S' || c == '\"') {
                if (accum != 0) {
                    tot += Duration.SECOND * accum;
                    accum = 0;
                    dec = 1;
                }
            } else if (c == 'n' || c == 'N') {
                if (accum != 0) {
                    tot += accum / Duration.NANOS_PER_MILLI;
                    accum = 0;
                    dec = 1;
                }
            }
        }
        // tot is in whole and fractional milliseconds
        if (tot > 0) {
            tot += 0.0000001;
        } else {
            tot -= 0.0000001;
        }
        this.millis = (long) tot;
        tot /= 1000;
        tot -= (long) tot;
        tot *= 1000;
        this.nanos = (int) (tot * Duration.NANOS_PER_MILLI);
    }

    /**
     * Duration specified in milliseconds.
     *
     * @param millis
     */
    public Duration(long millis) {
        this.millis = millis;
        int calcNanos = (int) (millis % SECOND) * NANOS_PER_MILLI;
        if (calcNanos < 0)
            this.nanos = 1000000000 + calcNanos;
        else
            this.nanos = calcNanos;
    }

    /**
     * Seconds + nanos pair will always be adjusted so that nanos is positive.
     * It's a strange arrangement, but useful when representing time as an
     * offset of Epoch, where a negative value usually represents a positive year.
     *
     * @param seconds
     * @param nanos
     */
    public Duration(long seconds, int nanos) {
        while (nanos > 999999999) {
            seconds++;
            nanos -= 1000000000;
        }
        while (nanos < 0) {
            seconds--;
            nanos += 1000000000;
        }
        this.millis = seconds * SECOND + nanos / NANOS_PER_MILLI;
        this.nanos = nanos;
        if (this.millis < 0 && this.nanos > 0)
            this.millis -= 1000;
    }

    /**
     * Add a duration, producing a new duration.
     *
     * @param dur
     * @return A newly calculated Duration.
     */
    public Duration add(Duration dur) {
        return new Duration(this.getSeconds() + dur.getSeconds(), this.nanos + dur.nanos);
    }

    /**
     * Add fixed number of (+/-) milliseconds to a Duration, producing a new Duration.
     *
     * @param milliseconds
     * @return A newly calculated Duration.
     */
    public Duration add(long milliseconds) {
        Duration d = new Duration(this.toMillis() + milliseconds);
        d.nanos += this.nanos % 1000000;
        return d;
    }

    /**
     * Add seconds and nanoseconds to a Duration, producing a new Duration.
     *
     * @param seconds
     * @param nanos
     * @return A newly calculated Duration.
     */
    public Duration add(long seconds, int nanos) {
        // Adjust to safe range to prevent overflow.
        if (nanos > 999999999) {
            seconds++;
            nanos -= 1000000000;
        }
        return new Duration(this.getSeconds() + seconds, this.nanos + nanos);
    }

    /**
     * Return relative comparison between two Durations.
     *
     * @param other Duration to compare to
     * @return -1, 0, or 1 of left compared to right.
     */
    public int compareTo(Duration other) {
        if (other == null) {
            throw new NullPointerException("Cannot compare Duration to null.");
        }
        if (this.millis == other.millis) {
            return nanos < other.nanos ? -1 : nanos == other.nanos ? 0 : 1;
        }
        return this.millis < other.millis ? -1 : 1;
    }

    /**
     * Two durations are equal if internal values are identical.
     *
     * @param other is a Duration or derived object
     * @return True if durations match.
     */
    public boolean equals(Object other) {
        return other instanceof Duration && compareTo((Duration) other) == 0;
    }

    @Override
    public int hashCode() {
        return (int) ((this.millis & 0xFFFF) ^ this.nanos);
    }

    /**
     * Return fractional seconds in nanoseconds<br/>
     * Sign of value will match whole time value.
     *
     * @return Sub-second portion of Duration specified in nanoseconds.
     */
    public int getNanos() {
        return nanos;
    }

    /**
     * Return time truncated to milliseconds
     *
     * @return Number of whole milliseconds in Duration.
     */
    public long toMillis() {
        return millis;
    }

    /**
     * Return duration truncated seconds.
     *
     * @return Number of whole seconds in Duration.
     */
    public long getSeconds() {
        return millis / 1000 - (nanos < 0 ? 1 : 0);
    }

    /**
     * Return a duration parsed from a string.
     * Expected string is of regex format "(-?[0-9]*\.?[0-9]+ *['"wdhmsnWDHMSN][^-0-9.]*)+"
     *
     * @param str of format similar to "1h15m12s" or "8 weeks, 3.5 days" or [5'13"]
     * @return a new Duration object
     */
    public static Duration parse(String str) {
        return new Duration(str);
    }

    /**
     * Helper to build out a duration string
     *
     * @param ms       milliseconds remaining
     * @param interval number of milliseconds per discrete chunk
     * @param label    character representing chunk size (w,d,h,m,s)
     * @param sb       current duration on which to append
     * @return ms remaining
     */
    private long extract(long ms, long interval, String label, StringBuilder sb) {
        long unit = 0;
        unit = ms / interval;
        sb.append(unit);
        sb.append(label);
        ms -= unit * interval;
        return ms;
    }

    /**
     * Output duration as a string.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        long ms = this.millis;
        int ns = this.nanos;
        if (ms < 0 || (ms == 0 && ns < 0)) {
            sb.append("-");
            ms *= -1;
            ns *= -1;
        }
        if (ms > Duration.DAY) {
            ms = extract(ms, Duration.DAY, "d", sb);
        }
        if (ms > Duration.HOUR) {
            ms = extract(ms, Duration.HOUR, "h", sb);
        }
        if (ms > Duration.MINUTE) {
            ms = extract(ms, Duration.MINUTE, "m", sb);
        }
        if (ms > Duration.SECOND) {
            // Findbugs complains of "dead store" if assigned to ms here
            extract(ms, Duration.SECOND, "s", sb);
        }
        if (ns > 0) {
            sb.append(ns);
            sb.append("n");
        }
        return sb.toString();
    }

}