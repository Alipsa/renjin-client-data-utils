# renjin-client-data-utils
Utilities to facilitate working with R data from Renjin in Java. In R, there are various basic 
data structures such as matrix and data.frame and are faithfully backed by Java classes such as 
ListVector for data.frame that while very easy to work with in R, can be a bit cumbersome to work with
directly in Java. This library aims to simplify working with data derived from Renjin R from Java code. 

To use it add the following dependency to your pom.xml:
```xml
<dependency>
    <groupId>se.alipsa</groupId>
    <artifactId>renjin-client-data-utils</artifactId>
    <version>1.4.2</version>
</dependency>
```

## se.alipsa.renjin.client.datautils.Table
The Table class makes it easy to interact with R data.frame and matrix data.
Data frames in R are "column based" (variable based) which is very convenient for analysis but Java is
Object / Observation based, so a Table which essentially is just a List of rows (observations), makes is much easier
to work with the data in Java. The data in a Table is immutable once created. If you need mutable data,
consider using RDataTRansformer instead (see below).

### Example:

Given the following R code:
```r
employee <- c('John Doe','Peter Smith','Jane Doe')
salary <- c(21000, 23400, 26800)
startdate <- as.Date(c('2013-11-1','2018-3-25','2017-3-14')) 
endDate <- as.POSIXct(c('2020-01-10 00:00:00', '2020-04-12 12:10:13', '2020-10-06 10:00:05'), tz='UTC' ) 
data.frame(employee, salary, startdate, endDate)
```

I.e a two dimensional table looking like this:

| employee    | salary | startdate | enddate             |
| --------    | -----: | --------- | -------             |
| John Doe    |  21000 | 2013-11-1 | 2020-01-10 00:00:00 |
| Peter Smith |  23400 | 2018-3-25 | 2020-04-12 12:10:13 |
| Jane Doe    |  26800 | 2017-3-14 | 2020-10-06 10:00:05 |

... you create it in the RenjinScriptEngine like this:

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
useful when you only want the result in a particular way once, or you need mutable data.

See [DataTransformationTest](src/test/java/test/alipsa/renjin/client/datautils/DataTransformationTest.java) for examples.

# Version history

### 1.4.2
- If no info on column types are given when creating a table then treat them as Strings
- Add customizable decimal format awareness for getAsFloat and getAsDouble convenience methods.
- Bump dependency versions for spotbugs and require maven 3.6.3 or higher for cleaner output 

### 1.4.1
Handle transformations of empty tables (used to get an ArrayIndexOutOfBoundsException) - now returning empty List instead

### 1.4
Fix handling of factorized columns with NA values
Change Row naming to use an IntSequence instead of the no longer existing RowNamesVector(numberOfRows)
Bump dependency versions

### 1.3
add Table column convenience methods (getColumnForName, getColumnIndex, getColumn)
Make Table data immutable, this allows for some performance optimizations and makes usage much clearer
(Table to process data from Renjin, RDataTransformer to handle mutable data to and from Renjin)

### 1.2
- Add docs
- cleanup and expand tests
- add @SafeVarargs to Table constructor to silence compiler warning which does not apply
- add getRowForName providing the ability to find a row based on a cell value

### 1.1
- Add some utility methods, so we can remove the Ride version of the same class.
- Add docs

### 1.0 
- Initial version