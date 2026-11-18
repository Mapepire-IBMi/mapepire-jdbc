package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.sql.CallableStatement;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.mapepire_ibmi.types.ParameterResult;
import io.github.mapepire_ibmi.types.QueryOptions;
import io.github.mapepire_ibmi.types.QueryResult;

@ExtendWith(MockitoExtension.class)
class MapepireCallableStatementTest {

    private MapepireConnection connection;

    @Mock
    private SqlJob mockJob;

    @Mock
    private Query mockQuery;

    @BeforeEach
    void setUp() {
        connection = new MapepireConnection(mockJob);
    }

    // Built with setters: the SDK's all-arguments constructor drops the value
    private static ParameterResult parameter(String name, String type, int index, Object value) {
        ParameterResult parameter = new ParameterResult();
        parameter.setName(name);
        parameter.setType(type);
        parameter.setindex(index);
        parameter.setValue(value);
        return parameter;
    }

    private void returnOutputParameters(ParameterResult... parameters) throws Exception {
        QueryResult<Object> result = new QueryResult<>();
        result.setOutputParms(Arrays.asList(parameters));
        when(mockJob.query(anyString(), any(QueryOptions.class))).thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt())).thenReturn(CompletableFuture.completedFuture(result));
    }

    @Test
    void executeSendsPlaceholderForOutParameterAndReadsOutValues() throws Exception {
        CallableStatement cs = connection.prepareCall("{call LIB.ADD(?, ?, ?)}");
        cs.setInt(1, 6);
        cs.setInt(2, 4);
        cs.registerOutParameter(2, Types.INTEGER);
        cs.registerOutParameter(3, JDBCType.INTEGER);
        // The server returns every parameter, with a value for OUT and INOUT ones
        returnOutputParameters(
                parameter("P1", "INTEGER", 1, null),
                parameter("P2", "INTEGER", 2, 0),
                parameter("P3", "INTEGER", 3, 10));

        assertFalse(cs.execute());

        // The escape is sent as a plain CALL, and the unset OUT parameter as a placeholder
        ArgumentCaptor<QueryOptions> options = ArgumentCaptor.forClass(QueryOptions.class);
        verify(mockJob).query(eq("call LIB.ADD(?, ?, ?)"), options.capture());
        assertEquals(Arrays.asList(6, 4, ""), options.getValue().getParameters());

        assertEquals(10, cs.getInt(3));
        assertEquals(0, cs.getObject("p2"));
        assertFalse(cs.wasNull());
    }

    @Test
    void outValuesAreOnlyReadableForRegisteredParametersAfterExecution() throws Exception {
        CallableStatement cs = connection.prepareCall("CALL LIB.LOOKUP(?, ?, ?)");
        cs.setString(1, "A");
        cs.registerOutParameter(2, Types.CHAR);
        cs.registerOutParameter(3, Types.BINARY);
        assertThrows(SQLException.class, () -> cs.getString(2));

        returnOutputParameters(
                parameter("KEY", "CHAR", 1, null),
                parameter("NAME", "CHAR", 2, null),
                parameter("DATA", "BINARY", 3, "AAECAw=="));
        cs.execute();

        assertNull(cs.getString(2));
        assertTrue(cs.wasNull());
        assertTrue(assertThrows(SQLException.class, () -> cs.getString(1)).getMessage()
                .contains("not registered"));
        assertThrows(SQLFeatureNotSupportedException.class, () -> cs.getObject(3));
    }

    @Test
    void failedExecutionClearsOutValuesOfThePreviousOne() throws Exception {
        CallableStatement cs = connection.prepareCall("CALL LIB.ADD(?, ?, ?)");
        cs.setInt(1, 6);
        cs.setInt(2, 4);
        cs.registerOutParameter(3, Types.INTEGER);
        returnOutputParameters(
                parameter("P1", "INTEGER", 1, null),
                parameter("P2", "INTEGER", 2, null),
                parameter("P3", "INTEGER", 3, 10));
        cs.execute();
        assertEquals(10, cs.getInt(3));

        doThrow(new RuntimeException("Connection lost")).when(mockJob).query(anyString(), any(QueryOptions.class));
        assertThrows(SQLException.class, () -> cs.execute());
        assertTrue(assertThrows(SQLException.class, () -> cs.getInt(3)).getMessage().contains("not been executed"));
    }

    @Test
    void characterOutValuesAreReadableAsNStringsAndStreams() throws Exception {
        CallableStatement cs = connection.prepareCall("CALL LIB.NAME(?, ?)");
        cs.registerOutParameter(1, Types.NVARCHAR);
        cs.registerOutParameter(2, Types.VARCHAR);
        returnOutputParameters(
                parameter("FIRST", "NVARCHAR", 1, "Zoë"),
                parameter("LAST", "VARCHAR", 2, null));
        cs.execute();

        assertEquals("Zoë", cs.getNString(1));
        assertEquals("Zoë", new BufferedReader(cs.getNCharacterStream(1)).readLine());
        assertEquals("Zoë", new BufferedReader(cs.getCharacterStream("first")).readLine());
        assertNull(cs.getCharacterStream(2));
        assertTrue(cs.wasNull());

        assertEquals("Zoë", cs.getObject(1, Collections.<String, Class<?>>emptyMap()));
        assertThrows(SQLFeatureNotSupportedException.class,
                () -> cs.getObject(1, Collections.<String, Class<?>>singletonMap("T", String.class)));
    }

    @Test
    void unsupportedCallsAreRejected() throws SQLException {
        assertThrows(SQLFeatureNotSupportedException.class, () -> connection.prepareCall("{? = call LIB.F(?)}"));
        assertEquals("call LIB.P(?)", MapepireCallableStatement.translateCallEscape("{call LIB.P(?)};"));
        // The server would prepare SQL that does not start with CALL as a plain statement
        assertThrows(SQLException.class,
                () -> connection.prepareCall("/* tag */ CALL LIB.P(?)").registerOutParameter(1, Types.INTEGER));

        CallableStatement cs = connection.prepareCall("CALL LIB.P(?)");
        assertThrows(SQLFeatureNotSupportedException.class, () -> cs.setInt("P1", 1));
        cs.registerOutParameter(1, Types.INTEGER);
        assertThrows(SQLException.class, () -> cs.addBatch());

        // The server would skip registering a parameter sent as null
        cs.setNull(1, Types.INTEGER);
        assertThrows(SQLFeatureNotSupportedException.class, () -> cs.execute());
    }
}
