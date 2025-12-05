package org.kari.kariClaims.managers;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.kari.kariClaims.KariClaims;

/**
 * 经济管理器 - 处理Vault经济API
 */
public class EconomyManager {
    private final KariClaims plugin;
    private Economy economy;
    private boolean enabled;

    public EconomyManager(KariClaims plugin) {
        this.plugin = plugin;
        this.enabled = false;
        
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("未找到Vault插件，经济功能将被禁用");
            return;
        }
        
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager()
            .getRegistration(Economy.class);
        
        if (rsp == null) {
            plugin.getLogger().warning("未找到经济服务提供者，经济功能将被禁用");
            return;
        }
        
        economy = rsp.getProvider();
        enabled = true;
        plugin.getLogger().info("经济系统已启用: " + economy.getName());
    }

    /**
     * 检查经济系统是否可用
     */
    public boolean isEnabled() {
        return enabled && economy != null;
    }

    /**
     * 获取玩家余额
     */
    public double getBalance(OfflinePlayer player) {
        if (!isEnabled()) {
            return 0.0;
        }
        return economy.getBalance(player);
    }

    /**
     * 给玩家添加金钱
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            plugin.getLogger().warning("经济系统未启用，无法存款");
            return false;
        }
        
        if (amount <= 0) {
            return false;
        }
        
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    /**
     * 从玩家扣除金钱
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            plugin.getLogger().warning("经济系统未启用，无法扣款");
            return false;
        }
        
        if (amount <= 0) {
            return false;
        }
        
        if (getBalance(player) < amount) {
            return false;
        }
        
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * 检查玩家是否有足够的金钱
     */
    public boolean hasEnough(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            return false;
        }
        return getBalance(player) >= amount;
    }

    /**
     * 格式化金额
     */
    public String format(double amount) {
        if (!isEnabled()) {
            return String.format("%.2f", amount);
        }
        return economy.format(amount);
    }

    /**
     * 获取货币名称（单数）
     */
    public String getCurrencyName() {
        if (!isEnabled()) {
            return "元";
        }
        return economy.currencyNameSingular();
    }

    /**
     * 获取货币名称（复数）
     */
    public String getCurrencyNamePlural() {
        if (!isEnabled()) {
            return "元";
        }
        return economy.currencyNamePlural();
    }
}

