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
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

  /**
   *
   * Full Funding	4563.153	380.263	4.938
   * Baseline Funding	3385.593	282.133	3.664
   * Current Funding	2700	225	2.922
   *
   * Transposed
   * Full Funding	Baseline Funding	Current Funding
   * 4563.153	3385.593	2700
   * 380.263	282.133	225
   * 4.938	3.664	2.922
   */
  @Test
  public void testTableTranspose() {
    List<String> header = new ArrayList<String>() {{
      add("Alternative");
      add("Annual");
      add("Monthly");
      add("Per unit and month");
    }};

    List<List<Object>> rowList = new ArrayList<List<Object>>() {{
      add(new ArrayList<Object>(){{
        add("Full Funding");
        add(4563.153);
        add(380.263);
        add(4.938);
      }});
      add(new ArrayList<Object>(){{
        add("Baseline Funding");
        add(3385.593);
        add(282.133);
        add(3.664);
      }});
      add(new ArrayList<Object>(){{
        add("Current Funding");
        add(2700);
        add(225);
        add(2.922);
      }});
    }};

    Table table = new Table(header, rowList);
    Table transposed = table.asTransposed();

    // Transpose again so that it becomes the same as the original so we can compare
    List<List<Object>> transposedColumnList = transposed.getColumnList();
    for (int i = 0; i < table.getRowSize(); i++) {
      List<Object> tableRow = table.getRowList().get(i);
      List<Object> transposedColRow = transposedColumnList.get(i);
      for (int j = 0; j < tableRow.size(); j++) {
        assertEquals(tableRow.get(j), transposedColRow.get(j), "Error in row " + i + ", col " + j);
      }
    }

    List<List<Object>> tableColumnList = table.getColumnList();
    List<List<Object>> transposedRowList = transposed.getRowList();
    for (int i = 0; i < table.getHeaderSize(); i++) {
      List<Object> tableRow = tableColumnList.get(i);
      List<Object> transposedRow = transposedRowList.get(i);
      for (int j = 0; j < tableRow.size(); j++) {
        assertEquals(tableRow.get(j), transposedRow.get(j), "Error in row " + i + ", col " + j);
      }
    }
  }
}
