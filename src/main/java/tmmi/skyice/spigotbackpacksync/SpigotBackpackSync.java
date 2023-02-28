package tmmi.skyice.spigotbackpacksync;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import tmmi.skyice.spigotbackpacksync.Tools.MysqlUtil;

import java.io.File;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SpigotBackpackSync extends JavaPlugin implements Listener {
    private static SpigotBackpackSync instance;
    public static FileConfiguration config;
    //服务器启动阶段
    @Override
    public void onLoad() {
        instance = this;
        saveDefaultConfig();
        config = getConfig();
        MysqlUtil.initDatabase();
    }
    @Override
    public void onEnable() {
        // Plugin startup logic
        getServer().getPluginManager().registerEvents(this,this);
        getLogger().info("readly");
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String data = MysqlUtil.selectData(uuid);
        JsonElement jsonElement = new JsonParser().parse(data);
        // 使用 uuid 来处理您需要的逻辑
        Bukkit.getScheduler().runTaskAsynchronously(SpigotBackpackSync.instance,()->{
            try {
                Gson gson = new Gson();
                JsonObject jsonObject = gson.fromJson(data,JsonObject.class);

                //替换玩家背包
                //处理字符'b'
                String inventoryJson = jsonElement.getAsJsonObject().get("inventory").getAsString();
                Pattern pattern = Pattern.compile("(?<=:)[0-9]+(?=b)");
                Matcher matcher = pattern.matcher(inventoryJson);
                while (matcher.find()){
                    String numStr = matcher.group();
                    inventoryJson = inventoryJson.replace(numStr + "b",numStr);
                }
                JsonArray inventoryArray = JsonParser.parseString(inventoryJson).getAsJsonArray();
                for (int i = 0; i < inventoryArray.size(); i++) {
                    JsonObject itemObj = inventoryArray.get(i).getAsJsonObject();
                    int slot = itemObj.get("Slot").getAsInt();
                    String itemId = itemObj.get("id").getAsString();
                    int itemAmount = itemObj.get("Count").getAsInt();
                    Short itemDamage = 0;
                    //判断物品是否包含数据标签，如果包含啧解析标签中的Damage
                    if (itemObj.has("tag")) {
                        JsonObject tagObj = itemObj.getAsJsonObject("tag");
                        if (tagObj.has("Damage")) {
                            itemDamage = tagObj.get("Damage").getAsShort();
                        }
                    }
                    ItemStack itemStack = new ItemStack(Material.matchMaterial(itemId),itemAmount,itemDamage);
                    player.getInventory().setItem(slot,itemStack);
                }
                Bukkit.getScheduler().runTask(SpigotBackpackSync.instance,()-> {
                    //替换玩家经验值和等级
                    float xp = jsonObject.get("xp").getAsFloat();
                    int level = jsonObject.get("level").getAsInt();
                    player.setLevel(level);
                    player.setExp(xp);
                });
            } catch (JsonSyntaxException e) {
                getLogger().warning("玩家背包替换失败");
                e.printStackTrace();
            }

        });

    }

    //玩家离开服务器
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        //获取玩家当前背包、经验值和等级
        ItemStack[] inventory = player.getInventory().getContents();
        JsonObject dataObj = new JsonObject();

        // 创建JsonArray来保存背包内容
        JsonArray inventoryArray = new JsonArray();
        for (int i = 0; i < inventory.length; i++){
            ItemStack itemStack = inventory[i];
            if (itemStack == null || itemStack.getType() == Material.AIR){
                continue;
            }
            // 将每个物品都保存到JsonArray中
            JsonObject itemObj = new JsonObject();
            itemObj.addProperty("id",itemStack.getType().getKey().toString());
            itemObj.addProperty("Count",itemStack.getAmount());
            itemObj.addProperty("Slot",i);
            // 如果物品有附魔或者其他特殊属性，则保存到itemObject的tag属性中
            if (itemStack.hasItemMeta()) {
                JsonObject tagObject = new JsonObject();
                ItemMeta meta = itemStack.getItemMeta();
                if (meta.hasDisplayName()) {
                    tagObject.addProperty("display_name", meta.getDisplayName());
                }
                if (meta.hasLore()) {
                    JsonArray loreArray = new JsonArray();
                    for (String lore : meta.getLore()) {
                        loreArray.add(new JsonPrimitive(lore));
                    }
                    tagObject.add("lore", loreArray);
                }
                itemObj.add("tag", tagObject);
            }
            inventoryArray.add(itemObj);
        }
        dataObj.addProperty("inventory", inventoryArray.toString());

        double xp = player.getExp();
        int level = player.getLevel();
        // 将背包、经验值和等级保存到数据库中
        dataObj.addProperty("xp",xp);
        dataObj.addProperty("level",level);

        //将保存好的数据添加到数据库
        if (MysqlUtil.updataTable(uuid.toString(), dataObj.toString())) {
            getLogger().info("更新成功");
        }else if ( MysqlUtil.insertTable(uuid.toString(), dataObj.toString())){
            getLogger().info("保存成功");
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        // Plugin shutdown logic
    }
}
