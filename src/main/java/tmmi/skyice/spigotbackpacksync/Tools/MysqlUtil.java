package tmmi.skyice.spigotbackpacksync.Tools;

import tmmi.skyice.spigotbackpacksync.SpigotBackpackSync;

import java.sql.*;

import static org.bukkit.Bukkit.getLogger;

public class MysqlUtil{
    static {

        String mysqlDriver = "com.mysql.cj.jdbc.Driver";
        try {
            //加载MySql驱动
            Class.forName(mysqlDriver);

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    //初始化数据库
    public static void initDatabase() {

        String ip = (String) SpigotBackpackSync.config.get("mysqlData.ip");
        int port = (int) SpigotBackpackSync.config.get("mysqlData.port");
        //数据库地址
        String url = "jdbc:mysql://"+ ip +":"+ port +"/";
        //数据库账号
        String username = (String) SpigotBackpackSync.config.get("mysqlData.username");
        String password = (String) SpigotBackpackSync.config.get("mysqlData.password");
        //数据库名字
        String sqlName = (String) SpigotBackpackSync.config.get("mysql.database");


        //连接mysql服务
        try (Connection serverConnection = DriverManager.getConnection(url, username, password);
             Statement stmt = serverConnection.createStatement()) {
            //建库语句
            String createSql = "create database if not exists " + sqlName;
            //尝试创建数据库
            try {
                if (stmt.executeUpdate(createSql) == 1){
                    getLogger().info("数据库连接成功");
                }
            } catch (SQLException e) {
                getLogger().warning("创建数据库失败");
                e.printStackTrace();
            }

        } catch (SQLException e) {
            getLogger().warning("MySQL服务器连接失败");
            e.printStackTrace();
            return;
        }
        //连接数据库
        try (Connection dbConnection = DriverManager.getConnection(url+sqlName,username,password);
             Statement dbstmt = dbConnection.createStatement()) {
            //尝试建表
            String createtable = "CREATE TABLE IF NOT EXISTS `backpacksync_player_data` (`id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',`name` varchar(36) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,`nbt` text CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,PRIMARY KEY (`id`) USING BTREE,UNIQUE INDEX `uk_name`(`name` ASC) USING BTREE)";

            try {
                if (dbstmt.executeUpdate(createtable) == 1){
                    getLogger().info("创建数据表成功");
                }
            }catch (SQLException e) {
                getLogger().warning("创建数据表失败:");
                e.printStackTrace();
            }
        }catch (SQLException e) {
            getLogger().warning("数据库连接失败:");
            e.printStackTrace();
            return;
        }

    }

    public  static Connection getConnection() throws SQLException {
        String sqlName = (String) SpigotBackpackSync.config.get("mysql.database");
        String ip = (String) SpigotBackpackSync.config.get("mysqlData.ip");
        int port = (int) SpigotBackpackSync.config.get("mysqlData.port");
        String username = (String) SpigotBackpackSync.config.get("mysqlData.username");
        String password = (String) SpigotBackpackSync.config.get("mysqlData.password");
        //数据库地址
        String url = "jdbc:mysql://"+ ip +":"+ port +"/";
        return DriverManager.getConnection(url+sqlName,username,password);
    }

    public static boolean insertTable (String name, String inventory){
        //插入命令
        String inserttable = "insert into `backpacksync_player_data` (name, nbt) values (?,?)";
        //连接数据库
        try (Connection conn = getConnection();PreparedStatement dbstmt = conn.prepareStatement(inserttable)){
            dbstmt.setString(1, name);
            dbstmt.setString(2, inventory);
            try {
                if (dbstmt.executeUpdate() == 1){
                    getLogger().info("数据插入成功");
                    return true;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                getLogger().warning("数据插入失败");
            }
        } catch (SQLException e) {
            getLogger().warning("数据库连接失败:");
            e.printStackTrace();
        }
        return false;
    }

    public static boolean updataTable (String name, String inventory){
        //插入命令
        String updatatable = "update backpacksync_player_data set nbt = ? where name = ?";
        //连接数据库
        try (Connection conn = getConnection(); PreparedStatement dbstmt = conn.prepareStatement(updatatable)){
            dbstmt.setString(1, inventory);
            dbstmt.setString(2, name);

            try {
                if (dbstmt.executeUpdate() == 1){
                    getLogger().info("数据更新成功");
                    return true;
                }
            } catch (SQLException e) {
                getLogger().warning("数据更新失败");
                e.printStackTrace();
            }
        } catch (SQLException e) {
            getLogger().warning("数据库连接失败:");
            e.printStackTrace();
        }
        return false;
    }

    public static String selectData (String name) {

        //连接数据库
        try (Connection conn = getConnection();Statement dbstmt = conn.createStatement()){
            //查询命令
            String selectdata = "select nbt from backpacksync_player_data where name = '"+name+"'";
            try {
                ResultSet selected = dbstmt.executeQuery(selectdata);
                if (selected.next()){
                    String data = selected.getString("nbt");
                    getLogger().info("查询到数据");
                    return data;
                }
            } catch (SQLException e) {
                getLogger().warning("查询结果为空");
            }

        } catch (SQLException e) {
            getLogger().warning("数据库连接失败:");
            e.printStackTrace();
        }

        return null;
    }

}

