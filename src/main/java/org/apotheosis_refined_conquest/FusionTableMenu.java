package org.apotheosis_refined_conquest;

import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemItem;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;

import java.util.HashSet;
import java.util.Set;

/**
 * Fusion table: combine two affixed items.
 *
 * Supported operations (all rarity-downgrade-safe via {@link AffixOps}):
 *  - transfer selected affixes from the sacrifice onto the target
 *  - remove selected affixes from the target
 *  - twin upgrade: same item + same rarity -> next rarity, affixes kept & scaled
 *  - gem upgrade: sacrifice a gem of strictly higher rarity -> target raised to gem rarity
 */
public class FusionTableMenu extends AbstractContainerMenu {

    public static final int TARGET_SLOT = 0;
    public static final int SACRIFICE_SLOT = 1;

    private final ContainerLevelAccess access;
    private final Player player;

    private final SimpleContainer inputSlots = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            FusionTableMenu.this.createResult();
        }
    };
    private final ResultContainer resultSlots = new ResultContainer();

    // Client-authoritative selection state, synced to the server via FusionSelectionPacket.
    private final Set<ResourceLocation> transfers = new HashSet<>();
    private final Set<ResourceLocation> removals = new HashSet<>();
    private boolean upgrade = false;

    private final DataSlot cost = DataSlot.standalone();
    private boolean sacrificeUsed = false;

    public FusionTableMenu(int windowId, Inventory playerInv) {
        this(windowId, playerInv, ContainerLevelAccess.NULL);
    }

    public FusionTableMenu(int windowId, Inventory playerInv, ContainerLevelAccess access) {
        super(ModRegistry.FUSION_MENU.get(), windowId);
        this.access = access;
        this.player = playerInv.player;

        // Input slots are HIDDEN (isActive=false): the armor stand panels display
        // the items, and insertion/extraction runs through panelClicked().
        // Shift-click insertion still works because moveItemStackTo ignores isActive.
        this.addSlot(new Slot(this.inputSlots, TARGET_SLOT, 31, 75) {
            @Override
            public boolean isActive() {
                return false;
            }
        });
        this.addSlot(new Slot(this.inputSlots, SACRIFICE_SLOT, 97, 75) {
            @Override
            public boolean isActive() {
                return false;
            }
        });
        this.addSlot(new Slot(this.resultSlots, 0, 181, 45) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player p) {
                return p.getAbilities().instabuild || p.experienceLevel >= FusionTableMenu.this.cost.get();
            }

            @Override
            public void onTake(Player p, ItemStack stack) {
                FusionTableMenu.this.onResultTaken(p);
                super.onTake(p, stack);
            }
        });

        // Player inventory (3 rows + hotbar), centered for imageWidth 232 / imageHeight 250.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 35 + col * 18, 168 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 35 + col * 18, 226));
        }

        this.addDataSlot(this.cost);
    }

    // === panel interaction ===================================================

    /**
     * Click on an armor stand panel: swap the carried stack with the panel's slot.
     * Runs on both sides — the client mirrors for responsiveness, the server is
     * authoritative and re-syncs carried + slots via broadcastChanges().
     */
    public void panelClicked(int slotIndex) {
        if (slotIndex != TARGET_SLOT && slotIndex != SACRIFICE_SLOT) return;
        ItemStack carried = this.getCarried();
        ItemStack inSlot = this.inputSlots.getItem(slotIndex);

        if (carried.isEmpty()) {
            if (!inSlot.isEmpty()) {
                this.setCarried(inSlot);
                this.inputSlots.setItem(slotIndex, ItemStack.EMPTY);
            }
        } else {
            this.inputSlots.setItem(slotIndex, carried);
            this.setCarried(inSlot);
        }
        this.broadcastChanges();
    }

    // === selection sync ======================================================

    /** Applied on the server when the client sends its selection. */
    public void updateSelection(Set<ResourceLocation> newTransfers, Set<ResourceLocation> newRemovals, boolean newUpgrade) {
        // Copy first: callers may pass this menu's own sets (client-side mirror).
        Set<ResourceLocation> t = new HashSet<>(newTransfers);
        Set<ResourceLocation> r = new HashSet<>(newRemovals);
        this.transfers.clear();
        this.transfers.addAll(t);
        this.removals.clear();
        this.removals.addAll(r);
        this.upgrade = newUpgrade;
        createResult();
    }

    public Set<ResourceLocation> getTransfers() {
        return this.transfers;
    }

    public Set<ResourceLocation> getRemovals() {
        return this.removals;
    }

    public boolean isUpgrade() {
        return this.upgrade;
    }

    public int getCost() {
        return this.cost.get();
    }

    public ItemStack getTarget() {
        return this.inputSlots.getItem(TARGET_SLOT);
    }

    public ItemStack getSacrifice() {
        return this.inputSlots.getItem(SACRIFICE_SLOT);
    }

    /** Twin upgrade requires: same equipment TYPE, both affixed, same rarity, and a next tier. */
    public boolean isTwinUpgradeEligible() {
        ItemStack target = getTarget();
        ItemStack sacrifice = getSacrifice();
        if (target.isEmpty() || sacrifice.isEmpty()) return false;
        // Raising rarity requires the very same item — iron sword + iron sword.
        // Affix transfer is looser and only cares about the type (see canTransfer).
        if (!ItemStack.isSameItem(target, sacrifice)) return false;
        LootRarity a = AffixOps.getRarity(target);
        LootRarity b = AffixOps.getRarity(sacrifice);
        return a != null && a == b && a.next() != a;
    }

    // === result computation ==================================================

    private void createResult() {
        int xp = 0;
        int changes = 0;
        this.sacrificeUsed = false;

        ItemStack target = getTarget();
        ItemStack sacrifice = getSacrifice();
        LootRarity targetRarity = AffixOps.getRarity(target);

        if (target.isEmpty() || targetRarity == null) {
            if (!target.isEmpty() && !this.player.level().isClientSide) {
                TrifectaAffixes.LOGGER.info("[Fusion] target {} has no affix data / unbound rarity — no result possible", target.getItem());
            }
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            this.cost.set(0);
            this.broadcastChanges();
            return;
        }

        ItemStack work = target.copy();
        work.setCount(1);
        int costPer = TrifectaConfig.FUSION_XP_COST_PER_AFFIX.get();

        // 1) removals on the target
        if (!this.removals.isEmpty()) {
            int n = AffixOps.removeAffixes(work, this.removals);
            changes += n;
            xp += n * costPer;
        }

        // 2) sacrifice-driven operations
        if (!sacrifice.isEmpty()) {
            if (sacrifice.getItem() instanceof GemItem) {
                // gem upgrade: gem rarity must be STRICTLY higher (downgrade guard included)
                DynamicHolder<LootRarity> gemHolder = AffixHelper.getRarity(sacrifice);
                if (gemHolder.isBound()) {
                    LootRarity gemRarity = gemHolder.get();
                    if (gemRarity.isAtLeast(targetRarity) && gemRarity != targetRarity
                        && AffixOps.upgradeRarity(work, gemRarity)) {
                        changes++;
                        this.sacrificeUsed = true;
                        xp += costPer * 2;
                    }
                }
            } else if (this.upgrade && isTwinUpgradeEligible()) {
                LootRarity next = targetRarity.next();
                if (AffixOps.upgradeRarity(work, next)) {
                    changes++;
                    this.sacrificeUsed = true;
                    xp += costPer * 3;
                }
            } else if (!this.transfers.isEmpty() && AffixOps.canTransfer(target, sacrifice)) {
                int n = AffixOps.transferAffixes(work, sacrifice, this.transfers);
                if (n > 0) {
                    changes += n;
                    this.sacrificeUsed = true;
                    xp += n * costPer;
                }
            }
        }

        if (changes == 0) {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            this.cost.set(0);
        } else {
            this.resultSlots.setItem(0, work);
            this.cost.set(xp);
        }
        this.broadcastChanges();
    }

    private void onResultTaken(Player p) {
        if (!p.getAbilities().instabuild) {
            p.giveExperienceLevels(-this.cost.get());
        }

        // NOTE: read the sacrifice BEFORE touching any slot — setItem fires
        // setChanged -> createResult(), which resets transient state.
        ItemStack sacrifice = this.inputSlots.getItem(SACRIFICE_SLOT).copy();

        this.inputSlots.setItem(TARGET_SLOT, ItemStack.EMPTY);
        // The sacrifice is always consumed by a fusion.
        if (!sacrifice.isEmpty()) {
            sacrifice.shrink(1);
            this.inputSlots.setItem(SACRIFICE_SLOT, sacrifice.isEmpty() ? ItemStack.EMPTY : sacrifice);
        }
        if (!p.level().isClientSide) {
            TrifectaAffixes.LOGGER.info("[Fusion] result taken by {} — sacrifice consumed, {} xp levels charged",
                p.getName().getString(), this.cost.get());
        }

        this.transfers.clear();
        this.removals.clear();
        this.upgrade = false;

        this.access.execute((level, pos) ->
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7F, 1.2F));

        createResult();
    }

    // === plumbing ============================================================

    @Override
    public boolean stillValid(Player p) {
        return stillValid(this.access, p, ModRegistry.FUSION_TABLE.get());
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        this.access.execute((level, pos) -> this.clearContainer(p, this.inputSlots));
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack current = slot.getItem();
            moved = current.copy();
            if (index == 2) {
                // result -> player inventory (slot.onTake below fires onResultTaken)
                if (!slot.mayPickup(p)) return ItemStack.EMPTY;
                if (!this.moveItemStackTo(current, 3, 39, true)) return ItemStack.EMPTY;
                slot.onQuickCraft(current, moved);
            } else if (index <= 1) {
                // inputs -> player inventory
                if (!this.moveItemStackTo(current, 3, 39, true)) return ItemStack.EMPTY;
            } else {
                // player inventory -> inputs (target first, then sacrifice)
                if (!this.moveItemStackTo(current, 0, 2, false)) return ItemStack.EMPTY;
            }
            if (current.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (current.getCount() == moved.getCount()) return ItemStack.EMPTY;
            slot.onTake(p, current);
        }
        return moved;
    }

    /** Client helper for the screen: container holding both inputs. */
    public Container getInputs() {
        return this.inputSlots;
    }
}
