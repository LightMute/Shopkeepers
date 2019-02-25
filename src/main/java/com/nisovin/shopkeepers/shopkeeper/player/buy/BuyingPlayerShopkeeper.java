package com.nisovin.shopkeepers.shopkeeper.player.buy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.api.shopkeeper.ShopkeeperCreateException;
import com.nisovin.shopkeepers.api.shopkeeper.TradingRecipe;
import com.nisovin.shopkeepers.api.shopkeeper.player.PlayerShopCreationData;
import com.nisovin.shopkeepers.api.ui.DefaultUITypes;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopkeeper.SKDefaultShopTypes;
import com.nisovin.shopkeepers.shopkeeper.offers.PriceOffer;
import com.nisovin.shopkeepers.shopkeeper.player.AbstractPlayerShopkeeper;
import com.nisovin.shopkeepers.util.Filter;
import com.nisovin.shopkeepers.util.ItemCount;
import com.nisovin.shopkeepers.util.ItemUtils;

public class BuyingPlayerShopkeeper extends AbstractPlayerShopkeeper {

	private static final Filter<ItemStack> ITEM_FILTER = (ItemStack item) -> {
		if (Settings.isCurrencyItem(item) || Settings.isHighCurrencyItem(item)) return false;
		if (item.getType() == Material.WRITTEN_BOOK) return false;
		if (!item.getEnchantments().isEmpty()) return false; // TODO why don't allow buying of enchanted items?
		return true;
	};

	// contains only one offer for a specific type of item:
	private final List<PriceOffer> offers = new ArrayList<>();
	private final List<PriceOffer> offersView = Collections.unmodifiableList(offers);

	/**
	 * Creates a not yet initialized {@link BuyingPlayerShopkeeper} (for use in sub-classes).
	 * <p>
	 * See {@link AbstractShopkeeper} for details on initialization.
	 * 
	 * @param id
	 *            the shopkeeper id
	 */
	protected BuyingPlayerShopkeeper(int id) {
		super(id);
	}

	protected BuyingPlayerShopkeeper(int id, PlayerShopCreationData shopCreationData) throws ShopkeeperCreateException {
		super(id);
		this.initOnCreation(shopCreationData);
	}

	protected BuyingPlayerShopkeeper(int id, ConfigurationSection configSection) throws ShopkeeperCreateException {
		super(id);
		this.initOnLoad(configSection);
	}

	@Override
	protected void setup() {
		if (this.getUIHandler(DefaultUITypes.EDITOR()) == null) {
			this.registerUIHandler(new BuyingPlayerShopEditorHandler(this));
		}
		if (this.getUIHandler(DefaultUITypes.TRADING()) == null) {
			this.registerUIHandler(new BuyingPlayerShopTradingHandler(this));
		}
		super.setup();
	}

	@Override
	protected void loadFromSaveData(ConfigurationSection configSection) throws ShopkeeperCreateException {
		super.loadFromSaveData(configSection);
		// load offers:
		this._clearOffers();
		this._addOffers(PriceOffer.loadFromConfig(configSection, "offers"));
	}

	@Override
	public void save(ConfigurationSection configSection) {
		super.save(configSection);
		// save offers:
		PriceOffer.saveToConfig(configSection, "offers", this.getOffers());
	}

	@Override
	public BuyingPlayerShopType getType() {
		return SKDefaultShopTypes.PLAYER_BUYING();
	}

	@Override
	public List<TradingRecipe> getTradingRecipes(Player player) {
		List<TradingRecipe> recipes = new ArrayList<>();
		int currencyInChest = this.getCurrencyInChest();
		for (PriceOffer offer : this.getOffers()) {
			ItemStack tradedItem = offer.getItem();
			boolean outOfStock = (currencyInChest < offer.getPrice());
			TradingRecipe recipe = this.createBuyingRecipe(tradedItem, offer.getPrice(), outOfStock);
			if (recipe != null) {
				recipes.add(recipe);
			}
		}
		return Collections.unmodifiableList(recipes);
	}

	protected List<ItemCount> getItemsFromChest() {
		return this.getItemsFromChest(ITEM_FILTER);
	}

	// OFFERS:

	public List<PriceOffer> getOffers() {
		return offersView;
	}

	public PriceOffer getOffer(ItemStack tradedItem) {
		for (PriceOffer offer : this.getOffers()) {
			if (ItemUtils.isSimilar(offer.getItem(), tradedItem)) {
				return offer;
			}
		}
		return null;
	}

	public PriceOffer addOffer(ItemStack tradedItem, int price) {
		// create offer (also handles validation):
		PriceOffer newOffer = new PriceOffer(tradedItem, price);

		// add new offer (replacing any previous offer for the same item):
		this._addOffer(newOffer);
		this.markDirty();
		return newOffer;
	}

	private void _addOffer(PriceOffer offer) {
		assert offer != null;
		// remove previous offer for the same item:
		this.removeOffer(offer.getItem());
		offers.add(offer);
	}

	private void _addOffers(Collection<PriceOffer> offers) {
		assert offers != null;
		for (PriceOffer offer : offers) {
			if (offer == null) continue; // skip invalid entries
			// add new offer (replacing any previous offer for the same item):
			this._addOffer(offer);
		}
	}

	private void _clearOffers() {
		offers.clear();
	}

	public void clearOffers() {
		this._clearOffers();
		this.markDirty();
	}

	public void removeOffer(ItemStack tradedItem) {
		Iterator<PriceOffer> iterator = offers.iterator();
		while (iterator.hasNext()) {
			if (ItemUtils.isSimilar(iterator.next().getItem(), tradedItem)) {
				iterator.remove();
				this.markDirty();
				break;
			}
		}
	}
}
