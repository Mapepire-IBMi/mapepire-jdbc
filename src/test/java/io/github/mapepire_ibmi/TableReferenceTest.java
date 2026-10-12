package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TableReferenceTest {

    private static String tableOf(String sql) {
        TableReference table = TableReference.fromQuery(sql);
        return table == null ? null : table.toSql();
    }

    @Test
    void findsTheSingleTableOfASelect() {
        assertEquals("\"EMP\"", tableOf("SELECT * FROM emp"));
        assertEquals("\"MYLIB\".\"EMP\"", tableOf("select e.id from mylib.emp e where e.id = 1 for update"));
        assertEquals("\"MYLIB\"/\"EMP\"", tableOf("SELECT * FROM MYLIB/EMP AS E ORDER BY 1 FETCH FIRST 5 ROWS ONLY"));
        assertEquals("\"My Lib\".\"A\"\"B\"", tableOf("SELECT * FROM \"My Lib\".\"A\"\"B\""));
    }

    @Test
    void ignoresFromInsideParenthesesLiteralsAndComments() {
        assertEquals("\"EMP\"", tableOf("SELECT TRIM(BOTH ' ' FROM NAME), (SELECT COUNT(*) FROM DEPT),"
                + " 'FROM X' /* FROM Y */ FROM -- FROM Z\n EMP WHERE DEPT IN (SELECT 1 FROM A UNION SELECT 2 FROM B)"));
    }

    @Test
    void rejectsQueriesThatAreNotOverASingleTable() {
        assertNull(tableOf("SELECT * FROM EMP E, DEPT D"));
        assertNull(tableOf("SELECT * FROM EMP E LEFT OUTER JOIN DEPT D ON E.D = D.ID"));
        assertNull(tableOf("SELECT * FROM EMP UNION SELECT * FROM EMP2"));
        assertNull(tableOf("SELECT DEPT, COUNT(*) FROM EMP GROUP BY DEPT"));
        assertNull(tableOf("SELECT * FROM (SELECT * FROM EMP) X"));
        assertNull(tableOf("SELECT * FROM TABLE(F()) X"));
        assertNull(tableOf("WITH X AS (SELECT * FROM EMP) SELECT * FROM X"));
        assertNull(tableOf("SELECT 1 FROM"));
        assertNull(tableOf(null));
    }
}
