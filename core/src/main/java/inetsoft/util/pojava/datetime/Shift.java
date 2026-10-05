package inetsoft.util.pojava.datetime;

/*
 From org.pojava.datetime 3.0.0 by John Pile, moved to inetsoft.util.pojava.datetime by
 InetSoft Technology in 2026.

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
 * Shift describes an offset in time in terms of CalendarUnit increments of various proportions.
 * Compare this to the "Duration" class, which describes fixed units of time in a self-contained
 * manner. A Shift, by contrast, may use a 23, 24 or 25 hour day depending on Daylight Saving
 * Time, or a 28, 29, 30, or 31 day month. The amount of time described by a Shift is dependent
 * on a start time and a time zone, both external to the Shift object itself.
 *
 * @author John Pile
 */
public class Shift {
    /**
     * Nanoseconds per second
     */
    private static final long NPS = 1000000000;
    /**
     * Seconds per Minute
     */
    private static final long SPM = 60;
    /**
     * Minutes per Hour
     */
    private static final long MPH = 60;
    private int year = 0;
    private int month = 0;
    private int week = 0;
    private int day = 0;
    private int hour = 0;
    private int minute = 0;
    private int second = 0;
    private long nanosec = 0;

    public Shift() {
        // default constructor
    }

    /**
     * Constructor parsing from a string.
     *
     * @param iso8601 String representation of a duration.
     */
    public Shift(String iso8601) {
        char[] chars = iso8601.toCharArray();
        double accum = 0.0;
        double dec = 1.0;
        int sign = 1;
        int time = 0;
        for (char c : chars) {
            if (c == '.') {
                dec /= 10;
            } else if (c == '-') {
                sign = -1;
            } else if (c >= '0' && c <= '9') {
                if (dec >= 1.0) {
                    accum = accum * 10 + sign * dec * (c - '0');
                } else {
                    accum += sign * dec * (c - '0');
                    dec /= 10;
                }
            } else if (c == 'T') {
                time = 1;
            } else if (c == 'Y') {
                if (accum != 0) {
                    shiftYears(accum);
                    accum = 0;
                    dec = 1.0;
                    sign = 1;
                }
            } else if (c == 'W') {
                if (accum != 0) {
                    shiftWeeks(accum);
                    accum = 0;
                    dec = 1.0;
                    sign = 1;
                }
            } else if (c == 'D') {
                if (accum != 0) {
                    shiftDays(accum);
                    accum = 0;
                    dec = 1.0;
                    sign = 1;
                }
            } else if (c == 'H') {
                if (accum != 0) {
                    shiftHours(accum);
                    accum = 0;
                    dec = 1.0;
                    sign = 1;
                }
            } else if (c == 'M') {
                if (accum != 0) {
                    if (time == 0) {
                        shiftMonths(accum);
                    } else {
                        shiftMinutes(accum);
                    }
                    accum = 0;
                    dec = 1.0;
                    sign = 1;
                }
            } else if (c == 'S') {
                if (accum != 0) {
                    shiftSeconds(accum);
                    accum = 0;
                    dec = 1.0;
                    sign = 1;
                }
            }
        }
    }

    /**
     * Shift by number of years
     *
     * @param accum
     */
    public void shiftYears(double accum) {
        if (accum >= 1.0 || accum <= -1.0) {
            year += (long) accum;
            accum -= (long) accum;
        }
        if (accum != 0) {
            shiftMonths(accum * 12);
        }
    }

    /**
     * Shift by whole number of months
     *
     * @param accum
     */
    public void shiftMonths(double accum) {
        if (accum >= 1.0 || accum <= -1.0) {
            month += (long) accum;
            accum -= (long) accum;
        }
        if (accum != 0) {
            shiftDays(accum * 30);
        }
    }

    /**
     * Shift by whole number of weeks
     *
     * @param accum
     */
    public void shiftWeeks(double accum) {
        if (accum >= 1.0 || accum <= -1.0) {
            week += (long) accum;
            accum -= (long) accum;
        }
        if (accum != 0) {
            shiftDays(accum * 7);
        } else {
            settleContents();
        }
    }

    /**
     * @param accum
     */
    public void shiftDays(double accum) {
        if (accum >= 1.0 || accum <= -1.0) {
            day += (long) accum;
            accum -= (long) accum;
        }
        if (accum != 0) {
            shiftHours(accum * 24);
        } else {
            settleContents();
        }
    }

    public void shiftHours(double accum) {
        if (accum >= 1.0 || accum <= -1.0) {
            hour += (long) accum;
            accum -= (long) accum;
        }
        if (accum != 0) {
            shiftMinutes(accum * MPH);
        } else {
            settleContents();
        }
    }

    public void shiftMinutes(double accum) {
        if (accum >= 1.0 || accum <= -1.0) {
            minute += (long) accum;
            accum -= (long) accum;
        }
        if (accum != 0) {
            shiftSeconds(accum * SPM);
        } else {
            settleContents();
        }
    }

    public void shiftSeconds(double accum) {
        if (accum >= 1.0 || accum <= -1.0) {
            second += (long) accum;
            accum -= (long) accum;
        }
        if (accum != 0) {
            accum += (accum < 0 ? -0.0000000005 : 0.0000000005);
            nanosec += (long) (accum * 1000000000);
        }
        settleContents();
    }

    private void settleContents() {
        if (nanosec >= NPS || nanosec <= -NPS) {
            long calcsec = nanosec / NPS;
            second += calcsec;
            nanosec -= calcsec * NPS;
        }
        if (second >= SPM || second <= -SPM) {
            long calcmin = second / SPM;
            minute += calcmin;
            second -= calcmin * SPM;
        }
        if (minute >= MPH || minute <= -MPH) {
            long calchour = minute / MPH;
            hour += calchour;
            minute -= calchour * MPH;
        }
        while (day < 0 && week > 0) {
            week--;
            day += 7;
        }
        while (minute < 0 && hour > 0) {
            hour--;
            minute += 60;
        }
        while (second < 0 && minute > 0) {
            minute--;
            second += 60;
        }
        while (nanosec < 0 && second > 0) {
            second--;
            nanosec += 1000000000;
        }
    }

    public String toString() {
        // See ISO 8601
        settleContents();
        StringBuilder sb = new StringBuilder();
        sb.append('P');
        if (year != 0) {
            sb.append(year);
            sb.append('Y');
        }
        if (month != 0) {
            sb.append(month);
            sb.append('M');
        }
        if (week != 0) {
            sb.append(week);
            sb.append('W');
        }
        if (day != 0) {
            sb.append(day);
            sb.append('D');
        }
        if (hour != 0 || minute != 0 || second != 0 || nanosec != 0) {
            sb.append('T');
            if (hour != 0) {
                sb.append(hour);
                sb.append('H');
            }
            if (minute != 0) {
                sb.append(minute);
                sb.append('M');
            }
            if (second != 0 || nanosec != 0) {
                if (nanosec == 0) {
                    sb.append(second);
                } else {
                    sb.append((double) (second + nanosec / 1000000000.0));
                }
                sb.append('S');
            }
        }
        return sb.toString();
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getWeek() {
        return week;
    }

    public void setWeek(int week) {
        this.week = week;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public int getHour() {
        return hour;
    }

    public void setHour(int hour) {
        this.hour = hour;
    }

    public int getMinute() {
        return minute;
    }

    public void setMinute(int minute) {
        this.minute = minute;
        settleContents();
    }

    public int getSecond() {
        return second;
    }

    public void setSecond(int second) {
        this.second = second;
        settleContents();
    }

    public int getNanosec() {
        return (int) nanosec;
    }

    public void setNanosec(int nanosec) {
        this.nanosec = nanosec;
        settleContents();
    }

    public void setNanosec(long nanosec) {
        this.nanosec = nanosec;
        settleContents();
    }

}
