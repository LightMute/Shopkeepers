package com.nisovin.shopkeepers.villagers;

import java.util.Map;

import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.pluginhandlers.CitizensHandler;
import com.nisovin.shopkeepers.ui.defaults.VillagerEditorHandler;
import com.nisovin.shopkeepers.util.ItemUtils;
import com.nisovin.shopkeepers.util.Log;
import com.nisovin.shopkeepers.util.TextUtils;

/**
 * Handles prevention of trading, hiring and editing of regular villagers (including wandering traders).
 */
public class VillagerInteractionListener implements Listener {

	private final ShopkeepersPlugin plugin;

	public VillagerInteractionListener(ShopkeepersPlugin plugin) {
		this.plugin = plugin;
	}

	// HIGH, since we don't want to handle hiring if another plugin has cancelled the event.
	// But not HIGHEST, so that other plugins can still react to us canceling the event.
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	void onEntityInteract(PlayerInteractEntityEvent event) {
		if (!(event.getRightClicked() instanceof AbstractVillager)) return;
		AbstractVillager villager = (AbstractVillager) event.getRightClicked();
		boolean isVillager = (villager instanceof Villager);
		boolean isWanderingTrader = (!isVillager && villager instanceof WanderingTrader);
		if (!isVillager && !isWanderingTrader) return; // Unknown villager sub-type

		if (plugin.getShopkeeperRegistry().isShopkeeper(villager)) {
			// Shopkeeper interaction is handled elsewhere
			return;
		}
		Log.debug("Interaction with non-shopkeeper villager ..");

		if (CitizensHandler.isNPC(villager)) {
			// Ignore any interaction with Citizens NPCs
			Log.debug("  ignoring (probably Citizens) NPC");
			return;
		}

		if ((isVillager && Settings.disableOtherVillagers) || (isWanderingTrader && Settings.disableWanderingTraders)) {
			// Prevent trading with non-shopkeeper villagers:
			event.setCancelled(true);
			Log.debug("  trading prevented");
		}

		// Only react to main hand events:
		if (event.getHand() != EquipmentSlot.HAND) return;

		Player player = event.getPlayer();
		if (player.isSneaking()
				&& ((isVillager && Settings.editRegularVillagers) || (isWanderingTrader && Settings.editRegularWanderingTraders))) {
			// Open the villager editor:
			// Note: This will check if the player has the permission to edit villagers.
			event.setCancelled(true);
			Log.debug("  requesting villager editor.");
			VillagerEditorHandler villagerEditor = new VillagerEditorHandler(villager);
			SKShopkeepersPlugin.getInstance().getUIRegistry().requestUI(villagerEditor, player);
		} else if ((isVillager && Settings.hireOtherVillagers) || (isWanderingTrader && Settings.hireWanderingTraders)) {
			// Allow hiring of other villagers
			Log.debug("  possible hire ..");
			if (this.handleHireOtherVillager(player, villager)) {
				// Hiring was successful. -> Prevent normal trading.
				Log.debug("    ..success (normal trading prevented)");
				event.setCancelled(true);
			} else {
				// Hiring was not successful. -> No preventing of normal villager trading.
				Log.debug("    ..failed");
			}
		}
	}

	// Returns false, if the player wasn't able to hire this villager.
	private boolean handleHireOtherVillager(Player player, AbstractVillager villager) {
		// Check if the player is allowed to remove (attack) the entity (in case the entity is protected by another
		// plugin).
		Log.debug("    checking villager access ..");
		TestEntityDamageByEntityEvent fakeDamageEvent = new TestEntityDamageByEntityEvent(player, villager);
		plugin.getServer().getPluginManager().callEvent(fakeDamageEvent);
		if (fakeDamageEvent.isCancelled()) {
			Log.debug("    no permission to remove villager");
			return false;
		}
		// Hire him if holding his hiring item.
		PlayerInventory playerInventory = player.getInventory();
		ItemStack itemInMainHand = playerInventory.getItemInMainHand();
		if (!Settings.isHireItem(itemInMainHand)) {
			// TODO Show hire item via hover event?
			TextUtils.sendMessage(player, Settings.msgVillagerForHire,
					"costs", Settings.hireOtherVillagersCosts,
					"hire-item", Settings.hireItem.getType().name()
			); // TODO Also print required hire item name and lore?
			return false;
		} else {
			// Check if the player has enough of those hiring items:
			int costs = Settings.hireOtherVillagersCosts;
			if (costs > 0) {
				ItemStack[] storageContents = playerInventory.getStorageContents();
				if (ItemUtils.containsAtLeast(storageContents, Settings.hireItem, costs)) {
					Log.debug("  Villager hiring: the player has the needed amount of hiring items");
					int inHandAmount = itemInMainHand.getAmount();
					int remaining = inHandAmount - costs;
					Log.debug(() -> "  Villager hiring: in hand=" + inHandAmount + " costs=" + costs + " remaining=" + remaining);
					if (remaining > 0) {
						itemInMainHand.setAmount(remaining);
					} else { // remaining <= 0
						playerInventory.setItemInMainHand(null); // Remove item in hand
						if (remaining < 0) {
							// Remove remaining costs from inventory:
							ItemUtils.removeItems(storageContents, Settings.hireItem, -remaining);
							// Apply the change to the player's inventory:
							ItemUtils.setStorageContents(playerInventory, storageContents);
						}
					}
				} else {
					TextUtils.sendMessage(player, Settings.msgCantHire);
					return false;
				}
			}

			// Give player the shop creation item
			ItemStack shopCreationItem = Settings.createShopCreationItem();
			Map<Integer, ItemStack> remaining = playerInventory.addItem(shopCreationItem);
			if (!remaining.isEmpty()) {
				villager.getWorld().dropItem(villager.getLocation(), shopCreationItem);
			}

			// Remove the entity:
			// Note: The leashed trader llamas for the wandering trader will break and the llamas will remain.
			villager.remove();

			// Update client's inventory:
			player.updateInventory();

			TextUtils.sendMessage(player, Settings.msgHired);
			return true;
		}
	}
}
