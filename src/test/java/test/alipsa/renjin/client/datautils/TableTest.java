package test.alipsa.renjin.client.datautils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.renjin.eval.Session;
import org.renjin.eval.SessionBuilder;
import org.renjin.script.RenjinScriptEngine;
import org.renjin.script.RenjinScriptEngineFactory;
import org.renjin.sexp.SEXP;
import se.alipsa.renjin.client.datautils.DataType;
import se.alipsa.renjin.client.datautils.Table;

import javax.script.ScriptException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class TableTest {

  private static RenjinScriptEngine engine;

  @BeforeAll
  public static void init() {
    RenjinScriptEngineFactory factory = new RenjinScriptEngineFactory();
    Session session = new SessionBuilder().withDefaultPackages().build();
    engine = factory.getScriptEngine(session);
  }

  @Test
  public void testTableCreationFromSexpMatrix() throws ScriptException {

    SEXP sexp = (SEXP) engine.eval("m <- matrix(nrow = 3, ncol = 3)\n" +
        "m[1,] <- c(1.0, 2.0, 3.0)\n" +
        "m[2,] <- c(4,5,6)\n" +
        "m[3,] <- c(7,8,9) \n" +
        "m");
    Table table = Table.createTable(sexp);
    table.getColumnTypes().forEach(type -> assertThat(type, equalTo(DataType.DOUBLE)));
    assertThat(table.getValueAsInteger(0, 0), equalTo(1));
    assertThat(table.getValueAsInteger(1, 0), equalTo(4));
    assertThat(table.getValueAsInteger(2, 2), equalTo(9));

    sexp = (SEXP) engine.eval("matrix(1:9, nrow = 3, ncol = 3)");
    table = Table.createTable(sexp);
    table.getColumnTypes().forEach(type -> assertThat(type, equalTo(DataType.INTEGER)));
    assertThat(table.getValueAsInteger(0, 0), equalTo(1));
    assertThat(table.getValueAsInteger(1, 0), equalTo(2));
    assertThat(table.getValueAsInteger(2, 2), equalTo(9));
  }

  @Test
  public void testTableCreationFromSexpVector() throws ScriptException {
    SEXP sexp = (SEXP) engine.eval("c(TRUE, FALSE, TRUE, NA)");
    Table table = Table.createTable(sexp);
    table.getColumnTypes().forEach(type -> assertThat(type, equalTo(DataType.BOOLEAN)));
    assertThat(table.getValueAsBoolean(0, 0), equalTo(true));
    assertThat(table.getValue(1, 0), equalTo(false));
    assertThat(table.getValueAsBoolean(3, 0), is(nullValue()));
  }

  @Test
  public void testTableCreationFromSexpListVector() throws ScriptException {
    String code =
    "employee <- c('John Doe','Peter Smith','Jane Doe') \n " +
        "salary <- c(21000, 23400, 26800) \n" +
        "startdate <- as.Date(c('2013-11-1','2018-3-25','2017-3-14')) \n" +
        "endDate <- as.POSIXct(c('2020-01-10 00:00:00', '2020-04-12 12:10:13', '2020-10-06 10:00:05'), tz='UTC' ) \n" +
        "data.frame(employee, salary, startdate, endDate)";
    SEXP sexp = (SEXP) engine.eval(code);

    Table table = Table.createTable(sexp);
    assertThat(table.getValue(0, 0), equalTo("John Doe"));
    assertThat(table.getValue(1, 1), equalTo(23400.0));
    assertThat(table.getValueAsLocalDate(2, 2), equalTo(LocalDate.of(2017, 3, 14)));
    LocalDateTime expected = LocalDateTime.of(2020, 10, 6, 10, 0, 5);
    assertThat(table.getValueAsLocalDateTime(2, 3), equalTo(expected));
    assertThat(table.getValueAsLong(2, 3), equalTo(expected.toEpochSecond(ZoneOffset.UTC)));
  }
}
