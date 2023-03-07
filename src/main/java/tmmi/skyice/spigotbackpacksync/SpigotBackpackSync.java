package tmmi.skyice.spigotbackpacksync;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import tmmi.skyice.spigotbackpacksync.Tools.MysqlUtil;

import java.util.Arrays;

public final class SpigotBackpackSync extends JavaPlugin implements Listener {
    private static SpigotBackpackSync instance;
    public static FileConfiguration config;
    public static String v = "unknown";
    public static String version = "unknown";

    //服务器启动阶段
    @Override
    public void onLoad() {
        instance = this;
        saveDefaultConfig();
        config = getConfig();
        try {
            v = Bukkit.getServer().getClass().getName().split("\\.")[3];
        } catch (Error | Exception ignored) {
        }
        try {
            MysqlUtil.initDatabase();
        } catch (Exception e) {
            getLogger().severe("插件加载时发生错误!数据库初始化失败");
            getLogger().severe("-------------------------------------------------------");
            getLogger().severe("当前服务端版本：" + v);
            getLogger().severe("-------------------------------------------------------");
            getLogger().severe("若有疑问，您可以前往GitHub提交你的问题。");
            getLogger().severe("为了保证数据安全，将在30秒后关闭服务器。");
            try {
                Thread.sleep(30000);
            } catch (InterruptedException ignored) {
            }
            Bukkit.shutdown();
            setEnabled(false);
            e.printStackTrace();
        }
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("-------------------------------------------------------");
        getLogger().info("插件已就绪");
        getLogger().info("作者：SkyIce");
        getLogger().info("-------------------------------------------------------");
        getLogger().info("感谢您的使用");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.loadData();
        String name = player.getName();
        // 使用 name 来处理您需要的逻辑
        Bukkit.getScheduler().runTaskAsynchronously(SpigotBackpackSync.instance, () -> {
            String data = MysqlUtil.selectData(name);
            if (data != null) {
                try {
                    player.getInventory().clear();
                    getLogger().info("清包成功");
                } catch (Exception e) {
                    getLogger().info("清包失败");
                    e.printStackTrace();
                }

                //将data转成obj对象
                JsonObject dataObj = new Gson().fromJson(data, JsonObject.class);
                //获取dataobj对象的inventory属性的String值
                String inventoryNbtStr = dataObj.get("inventory").getAsString();
                if (!"[]".equals(inventoryNbtStr)) {
                    inventoryNbtStr = inventoryNbtStr
                            .replaceAll("Slot:100", "Slot:36")
                            .replaceAll("Slot:101", "Slot:37")
                            .replaceAll("Slot:102", "Slot:38")
                            .replaceAll("Slot:103", "Slot:39")
                            .replaceAll("Slot:-106", "Slot:40");
                    inventoryNbtStr = "{items:" + inventoryNbtStr + ",size:41}";
                    ReadWriteNBT nbt = NBT.parseNBT(inventoryNbtStr);
                    ItemStack[] readitemStacks = NBT.itemStackArrayFromNBT(nbt);
                    //替换玩家背包
                    player.getInventory().setContents(readitemStacks);
                }

                Bukkit.getScheduler().runTask(SpigotBackpackSync.instance, () -> {
                    //替换玩家经验
                    float xp = dataObj.get("xp").getAsFloat();
                    int level = dataObj.get("level").getAsInt();
                    player.setLevel(level);
                    player.setExp(xp);
                });
            }
        });
    }


    //玩家离开服务器
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();
        JsonObject dataObj = new JsonObject();
        //获取玩家当前背包、经验值和等级
        ItemStack[] saveitemStacks = player.getInventory().getContents();
        getLogger().info("saveitemStacks" + Arrays.toString(saveitemStacks));
        String nbt = NBT.itemStackArrayToNBT(saveitemStacks).toString();
        getLogger().info("nbt:" + nbt);
        if ("{size:41}".equals(nbt)) {
            dataObj.addProperty("inventory", "[]");
        } else {
            //截掉size和item
            String nbtfixs = nbt.substring("{items:".length(), nbt.lastIndexOf(",size:41"));
            getLogger().info("nbt2:" + nbt);
            //字符串slot反向
            String inventoryNbtStr = nbtfixs.replaceAll("Slot:36", "Slot:100")
                    .replaceAll("Slot:37", "Slot:101")
                    .replaceAll("Slot:38", "Slot:102")
                    .replaceAll("Slot:39", "Slot:103")
                    .replaceAll("Slot:40", "Slot:-106");

            dataObj.addProperty("inventory", inventoryNbtStr);
        }


        //获取经验值
        double xp = player.getExp();
        int level = player.getLevel();
        // 将背包、经验值和等级保存到数据库中
        dataObj.addProperty("xp", xp);
        dataObj.addProperty("level", level);

        //将保存好的数据添加到数据库
        if (MysqlUtil.updataTable(name, dataObj.toString())) {
            getLogger().info("背包是" + dataObj);
            getLogger().info("更新成功");
        } else if (MysqlUtil.insertTable(name, dataObj.toString())) {
            getLogger().info("保存成功");
        }
    }


    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        // Plugin shutdown logic
    }
}
