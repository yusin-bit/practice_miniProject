package main.java.com.rental.common.util;

import java.io.FileInputStream;

/**
 * JDBC를 위한 로드, 연결, 닫기
 */

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DBManager {
	private static Properties proFile = new Properties();

	static {
		try {
			
			proFile.load(new FileInputStream("resources/dbmanager.properties"));
			Class.forName(proFile.getProperty("driverName"));
			

		} catch (Exception e) {
			// e.printStackTrace();
		}
	}

	
	public static Properties getProFile() {
		return proFile;
	}

	public static Connection getConnection() throws SQLException {
		return DriverManager.getConnection(
				proFile.getProperty("url"),
				proFile.getProperty("userName"),
				proFile.getProperty("userPass"));
	}
	

	public static void close(Connection con, Statement st, ResultSet rs) {
		try {
			if(rs != null) rs.close();
			close(con, st);

		}
		catch(Exception e) {
			// e.printStackTrace();
		}
	}
	
	public static void close(Connection con, Statement st) {
		try {
			if(st != null) st.close();
			if(con != null) con.close();
		}
		catch(Exception e) {
			// e.printStackTrace();
		}
	}

}
