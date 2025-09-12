package sample.Config;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConfig {

    public static Connection getConnection() {
        Properties prop = new Properties();
        try(FileInputStream inputStream = new FileInputStream(".env")) {
            prop.load(inputStream);
        } catch(IOException e) {
            e.printStackTrace();
        }

        String url = prop.getProperty("db.url");
        String user = prop.getProperty("db.user");
        String password = prop.getProperty("db.password");

        Connection conn = null;

        try {
            conn = DriverManager.getConnection(url, user, password);
            System.out.println("Connected til databasen");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return conn;
    }
}
