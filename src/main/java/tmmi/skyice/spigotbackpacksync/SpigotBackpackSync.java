package tmmi.skyice.spigotbackpacksync;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import tmmi.skyice.spigotbackpacksync.Tools.MysqlUtil;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        }catch (Error|Exception ignored){}
        try {
            MysqlUtil.initDatabase();
        } catch (Exception e) {
            getLogger().severe("插件加载时发生错误!数据库初始化失败");
            getLogger().severe("-------------------------------------------------------");
            getLogger().severe("当前服务端版本："+v);
            getLogger().severe("-------------------------------------------------------");
            getLogger().severe("若有疑问，您可以前往GitHub提交你的问题。");
            getLogger().severe("为了保证数据安全，将在30秒后关闭服务器。");
            try {Thread.sleep(30000);} catch (InterruptedException ignored) {}
            Bukkit.shutdown();
            setEnabled(false);
            e.printStackTrace();
        }
    }
    @Override
    public void onEnable() {
        // Plugin startup logic
        getServer().getPluginManager().registerEvents(this,this);
        getLogger().info("-------------------------------------------------------");
        getLogger().info("插件已就绪");
        getLogger().info("作者：SkyIce");
        getLogger().info("-------------------------------------------------------");
        getLogger().info("感谢您的使用");
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();
        String data = MysqlUtil.selectData(name);
        JsonElement jsonElement = new JsonParser().parse(data);
        // 使用 name 来处理您需要的逻辑
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
                        String itemEnchant = null;
                        String displayName = null;
                        // 判断物品是否包含数据标签，如果包含则解析标签中的Damage、Enchantments和display
                        if (itemObj.has("tag")) {
                            JsonObject tagObj = itemObj.getAsJsonObject("tag");
                            if (tagObj.has("Damage")) {
                                itemDamage = tagObj.get("Damage").getAsShort();
                            }
                            if (tagObj.has("Enchantments")) {
                                JsonArray enchantmentsArray = tagObj.getAsJsonArray("Enchantments");
                                for (int j = 0; j < enchantmentsArray.size(); j++) {
                                    JsonObject enchantObj = enchantmentsArray.get(j).getAsJsonObject();
                                    String enchantId = enchantObj.get("id").getAsString();
                                    String enchantLevelStr = enchantObj.get("lvl").getAsString().replaceAll("[^0-9]", "");
                                    int enchantLevel = enchantLevelStr.isEmpty() ? 1 : Integer.parseInt(enchantLevelStr);
                                    itemEnchant = enchantId + ":" + enchantLevel;
                                }

                            }
                            if (tagObj.has("display")) {
                                JsonObject displayObj = tagObj.getAsJsonObject("display");
                                if (displayObj.has("Name")) {
                                    displayName = displayObj.get("Name").getAsString();
                                }

                            }
                            // 构造物品堆叠
                            ItemStack itemStack = new ItemStack(Material.matchMaterial(itemId), itemAmount);
                            ItemMeta itemMeta = itemStack.getItemMeta();
                            // 如果物品损坏度不为0则设置损坏度
                            if (itemDamage != 0) {
                                itemStack.setDurability(itemDamage);
                            }
                            getLogger().info("附魔："+itemEnchant);
                            // 如果物品有附魔则解析附魔并添加
                            if (itemEnchant != null) {
                                String[] enchantSplit = itemEnchant.split(":");
                                Enchantment enchant = Enchantment.getByKey(NamespacedKey.minecraft(enchantSplit[1]));
                                getLogger().info("附魔添加前检测"+enchant);
                                if (enchant != null) {
                                    int level = Integer.parseInt(enchantSplit[2]);
                                    itemMeta.addEnchant(enchant,level,true);
                                    itemStack.setItemMeta(itemMeta);

                                }
                            }
                            getLogger().info("成品"+itemStack);
                            // 如果物品有自定义名称则设置名称
                            if (displayName != null) {
                                itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
                            }

                            // 将物品设置到指定的物品栏格子里
                            player.getInventory().setItem(slot, itemStack);
                            }
                        }


                        Bukkit.getScheduler().runTask(SpigotBackpackSync.instance, () -> {
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
        if (MysqlUtil.updataTable(player.getName(), dataObj.toString())) {
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
