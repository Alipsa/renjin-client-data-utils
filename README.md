# renjin-client-data-utils
Utilities to facilitate working with R data from Renjin in Java

To use it add the following dependency to your pom.xml:
```xml
<dependency>
    <groupId>se.alipsa</groupId>
    <artifactId>renjin-client-data-utils</artifactId>
    <version>1.1</version>
</dependency>
```

## se.alipsa.renjin.client.datautils.Table
The Table class makes it easy to interact with R data.frame and matrix data.
Data frames in R are "column based" (variable based) which is very convenient for analysis but Java is
Object / Observation based, so a Table which essentially is just a List of rows (observations), makes is much easier
to work with the data in Java.

### Example:

Given the following R code:
```r
employee <- c('John Doe','Peter Smith','Jane Doe')
salary <- c(21000, 23400, 26800)
startdate <- as.Date(c('2013-11-1','2018-3-25','2017-3-14')) 
endDate <- as.POSIXct(c('2020-01-10 00:00:00', '2020-04-12 12:10:13', '2020-10-06 10:00:05'), tz='UTC' ) 
data.frame(employee, salary, startdate, endDate)
```
... you run it in the RenjinScriptEngine like this:

```java
import org.renjin.script.RenjinScriptEngine;
import org.renjin.sexp.SEXP;
import se.alipsa.renjin.client.datautils.Table;

public class Example {

  RenjinScriptEngine engine;

  public Example() {
    RenjinScriptEngineFactory factory = new RenjinScriptEngineFactory();
    Session session = new SessionBuilder().withDefaultPackages().build();
    engine = factory.getScriptEngine(session);
  }

  public Table getData(String code) {
    SEXP sexp = (SEXP) engine.eval(code);
    return Table.createTable(sexp);
  }
}
```
... the following assertions would be valid for the Table returned by the getData() method:
```javascript
assertThat(table.getValue(0, 0), equalTo("John Doe"));
assertThat(table.getValue(1, 1), equalTo(23400.0));
assertThat(table.getValueAsLocalDate(2, 2), equalTo(LocalDate.of(2017, 3, 14)));
assertThat(table.getValueAsLocalDateTime(2, 3), equalTo(LocalDateTime.of(2020, 10, 6, 10, 0, 5)));
```
See [TableTest](src/test/java/test/alipsa/renjin/client/datautils/TableTest.java) for more examples. 

## se.alipsa.renjin.client.datautils.RDataTransformer

This is a utility class that transforms various R data types into Java.
In many ways it is similar to the Table class but allows for more atomic operations
useful when you only want the result in a particular way once.

See [DataTransformationTest](src/test/java/test/alipsa/renjin/client/datautils/DataTransformationTest.java) for examples.

# Version history

### 1.2-SNAPSHOT
- Add docs
- cleanup and expand tests

### 1.1
- Add some utility methods, so we can remove the Ride version of the same class.
- Add docs

### 1.0 
- Initial version