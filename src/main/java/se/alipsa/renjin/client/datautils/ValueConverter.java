package se.alipsa.renjin.client.datautils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.text.NumberFormat;
import java.text.ParseException;

public class ValueConverter {

  private ValueConverter() {
    // Empty
  }

  @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
  public static Boolean asBoolean(Object val) {
    if (val == null) {
      return null;
    }
    if (val instanceof Number) {
      if (val instanceof Double) {
        return Double.isNaN((Double)val) ? null : val.equals(1.0);
      }
      if (val instanceof Float) {
        return Float.isNaN((Float)val) ? null : val.equals(1.0);
      }
      // Long, Integer etc. are handled in the switch statement below
    }
    if (val instanceof Boolean) {
      return (Boolean) val;
    }
    switch (String.valueOf(val).toLowerCase()) {
      case "true":
      case "sant":
      case "1":
      case "on":
      case "yes":
      case "ja":
      case "真":
        return Boolean.TRUE;
      default:
        return Boolean.FALSE;
    }
  }

  public static Double asDouble(Object val, NumberFormat numberFormat) {
    if (val == null) {
      return null;
    }
    if (val instanceof Double) {
      return (Double)val;
    }

    try {
      return numberFormat.parse(String.valueOf(val)).doubleValue();
    } catch (ParseException e) {
      // if we could not parse it, this will also likely fail
      return Double.parseDouble(String.valueOf(val));
    }
  }

  public static Integer asInteger(Object value, NumberFormat numberFormat) {
    if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
      return ((Number) value).intValue();
    }
    Double val = ValueConverter.asDouble(value, numberFormat);
    if (val == null || Double.isNaN(val)) {
      return null;
    }
    return val.intValue();
  }

  public static Long asLong(Object val, NumberFormat numberFormat) {
    if (val instanceof Long) {
      return (Long)val;
    }
    Double dVal = asDouble(val, numberFormat);
    return dVal == null ? null : dVal.longValue();
  }
}
