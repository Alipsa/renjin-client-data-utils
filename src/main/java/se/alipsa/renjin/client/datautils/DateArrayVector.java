package se.alipsa.renjin.client.datautils;

import org.renjin.sexp.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Convenience class to build column wise vectors of dates
 * Will be converted and stored as a long (epoch day)
 * The DataType will be double (just as LongArrayVectors are)
 */
public class DateArrayVector extends DoubleVector {
  private static final int MILLIS_IN_DAY = 24 * 60 * 60 * 1000;
  private long[] values;

  public DateArrayVector(AttributeMap attributes) {
    super(attributes);
  }

  public DateArrayVector(double[] values, AttributeMap attributes) {
    this(values, values.length, attributes);
  }

  public DateArrayVector(long[] values, AttributeMap attributes) {
    this(attributes);
    this.values = Arrays.copyOf(values, values.length);
  }

  public DateArrayVector(Object... values) {
    this(AttributeMap.EMPTY);
    List<Long> vals = new ArrayList<>();

    for (Object val : values) {
      if (val == null) {
        vals.add(Double.valueOf(NA).longValue());
      } else if (val instanceof LocalDate) {
        LocalDate date = (LocalDate) val;
        vals.add(date.toEpochDay());
      } else if (val instanceof Date) {
        Date date = (Date) val;
        vals.add(date.getTime() / MILLIS_IN_DAY);
      }
    }
    this.values = vals.stream().mapToLong(l -> l).toArray();
  }

  @Override
  protected SEXP cloneWithNewAttributes(AttributeMap attributes) {
    DateArrayVector clone = new DateArrayVector(attributes);
    clone.values = this.values;
    return clone;
  }

  @Override
  public double getElementAsDouble(int index) {
    return this.values[index];
  }

  @Override
  public int length() {
    return this.values.length;
  }

  @Override
  public boolean isConstantAccessTime() {
    return true;
  }

  public int getElementAsInt(int i) {
    long value = this.values[i];
    return value <= 2147483647L && value >= -2147483648L ? (int)value : -2147483648;
  }

  public String getElementAsString(int index) {
    return Long.toString(this.values[index]);
  }

  public long getElementAsLong(int index) {
    return this.values[index];
  }

  public LocalDate getElementAsLocalDate(int index) {
    return LocalDate.ofEpochDay(this.values[index]);
  }

  public Date getElementAsDate(int index) {
    return new Date(values[index] * MILLIS_IN_DAY);
  }
}
