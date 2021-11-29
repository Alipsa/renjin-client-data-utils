package test.alipsa.renjin.client.datautils;

import org.junit.jupiter.api.Test;
import se.alipsa.renjin.client.datautils.ValueConverter;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ValueConverterTest {

  @Test
  public void testAsInteger() {
    NumberFormat numberFormat = DecimalFormat.getInstance();
    assertEquals(12, ValueConverter.asInteger(Short.valueOf("12"), numberFormat));
    byte byteVal = 8;
    assertEquals(8, ValueConverter.asInteger(byteVal, numberFormat));
    assertEquals(121212, ValueConverter.asInteger(Integer.valueOf("121212"), numberFormat));
  }
}
