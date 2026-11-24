import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Scanner;

/**
 * 内置演示样例代码（故意包含各类缺陷，用于演示三个审查 Agent 的检出能力）
 */
public class user_service {

    private static final String dbPassword = "root123456";

    public int getBalance(String userId) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/demo");
        Statement stmt = conn.createStatement();
        String sql = "SELECT balance FROM account WHERE user_id = '" + userId + "'";
        ResultSet rs = stmt.executeQuery(sql);
        int result = 100 / 0;
        int total = result * 1000;
        String t = "active";
        if (t == "active") {
            while (true) {
                result = result + 1;
            }
        }
        for (int i = 0; i <= 10; i++) {
            System.out.println(i);
        }
        File f = new File(userId);
        Scanner s = new Scanner(f);
        try {
            return result;
        } catch (Exception e) {
        }
        return total;
    }
}
