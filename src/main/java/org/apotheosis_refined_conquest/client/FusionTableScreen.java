package org.apotheosis_refined_conquest.client;

import org.apotheosis_refined_conquest.FusionPanelClickPacket;
import org.apotheosis_refined_conquest.FusionSelectionPacket;
import org.apotheosis_refined_conquest.FusionTableMenu;
import org.apotheosis_refined_conquest.TrifectaNetwork;
import dev.shadowsoffire.apotheosis.adventure.affix.Affix;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fusion table GUI v4:
 *  - two portrait panels with slowly rotating armor stands (Tooltip-Overhaul style)
 *  - chips show the actual stat lines (attribute modifiers with values)
 *  - transferring a stat moves its chip from the sacrifice column to the target column
 *  - drag a chip off the target column (or click it) to mark removal
 */
public class FusionTableScreen extends AbstractContainerScreen<FusionTableMenu> {

    // Panels (armor stand preview), portrait orientation
    private static final int PANEL_Y = 16;
    private static final int PANEL_W = 44;
    private static final int PANEL_H = 76;
    private static final int TARGET_PANEL_X = 16;
    private static final int SACRIFICE_PANEL_X = 76;

    // Stat chips
    private static final int LIST_Y = 98;
    private static final int CHIP_H = 11;
    private static final int CHIP_STEP = 13;
    private static final int MAX_ROWS = 5;
    private static final int TARGET_LIST_X = 12;
    private static final int SACRIFICE_LIST_X = 122;
    private static final int LIST_W = 106;

    private Button upgradeButton;

    @Nullable private ArmorStand targetStand;
    @Nullable private ArmorStand sacrificeStand;

    @Nullable private DragState drag;

    // Scroll offsets per chip column
    private int targetScroll = 0;
    private int sacrificeScroll = 0;

    private record DragState(boolean fromTarget, ResourceLocation id, String label, double startX, double startY) {}

    /**
     * A chip in one of the two columns.
     * transferred = chip originates from the sacrifice but is shown in the target column.
     */
    private record Row(ResourceLocation id, String label, boolean selected, boolean transferred) {}

    public FusionTableScreen(FusionTableMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 232;
        this.imageHeight = 250;
        this.inventoryLabelY = -1000; // hidden — chips need the space
    }

    @Override
    protected void init() {
        super.init();
        this.upgradeButton = Button.builder(Component.translatable("gui.apotheosis_refined_conquest.upgrade"), b -> {
                this.getMenu().getTransfers().clear();
                this.getMenu().getRemovals().clear();
                boolean newState = !this.getMenu().isUpgrade();
                this.getMenu().updateSelection(this.getMenu().getTransfers(), this.getMenu().getRemovals(), newState);
                sendSelection();
            })
            .pos(this.leftPos + 142, this.topPos + 16)
            .size(84, 16)
            .build();
        this.addRenderableWidget(this.upgradeButton);
    }

    private void sendSelection() {
        TrifectaNetwork.CHANNEL.sendToServer(new FusionSelectionPacket(
            this.getMenu().getTransfers(), this.getMenu().getRemovals(), this.getMenu().isUpgrade()));
    }

    // === rendering ===========================================================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        this.upgradeButton.visible = this.getMenu().isTwinUpgradeEligible();
        super.render(graphics, mouseX, mouseY, partialTick);

        if (this.drag != null) {
            drawChip(graphics, mouseX + 4, mouseY - 6,
                this.font.width(this.drag.label()) + 8, this.drag.label(), 0xFFFFFFFF, 0xFF6A9BFF);
        } else {
            renderChipTooltip(graphics, mouseX, mouseY);
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Frame + canvas
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF3B3B3B);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, 0xFFC6C6C6);

        // Entity panels (portrait)
        drawPanel(graphics, x + TARGET_PANEL_X, y + PANEL_Y);
        drawPanel(graphics, x + SACRIFICE_PANEL_X, y + PANEL_Y);
        renderPreview(graphics, this.getMenu().getTarget(), true, x + TARGET_PANEL_X, y + PANEL_Y);
        renderPreview(graphics, this.getMenu().getSacrifice(), false, x + SACRIFICE_PANEL_X, y + PANEL_Y);

        // Result slot + arrow
        drawSlot(graphics, x + 180, y + 44);
        graphics.drawString(this.font, "→", x + 160, y + 49, 0xFF404040, false);

        // Cost
        int cost = this.getMenu().getCost();
        if (cost > 0) {
            boolean affordable = this.minecraft.player.getAbilities().instabuild
                || this.minecraft.player.experienceLevel >= cost;
            graphics.drawString(this.font,
                Component.translatable("gui.apotheosis_refined_conquest.cost", cost),
                x + 142, y + 70, affordable ? 0xFF208020 : 0xFFB02020, false);
        }

        // Player inventory slot frames
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(graphics, x + 34 + col * 18, y + 167 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(graphics, x + 34 + col * 18, y + 225);
        }

        // Stat chips (scrollable)
        List<Row> tRows = targetRows();
        List<Row> sRows = sacrificeRows();
        this.targetScroll = clampScroll(this.targetScroll, tRows.size());
        this.sacrificeScroll = clampScroll(this.sacrificeScroll, sRows.size());
        renderChipColumn(graphics, tRows, this.targetScroll, x + TARGET_LIST_X, y + LIST_Y, true);
        renderChipColumn(graphics, sRows, this.sacrificeScroll, x + SACRIFICE_LIST_X, y + LIST_Y, false);
    }

    private static int clampScroll(int scroll, int rowCount) {
        return Math.max(0, Math.min(scroll, Math.max(0, rowCount - MAX_ROWS)));
    }

    private void drawPanel(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + PANEL_W, y + PANEL_H, 0xFF1E1E1E);
        graphics.fill(x + 1, y + 1, x + PANEL_W - 1, y + PANEL_H - 1, 0xFF3A3A3A);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF5A5A5A);
    }

    /**
     * Armor renders on a slowly rotating armor stand (Tooltip-Overhaul style);
     * anything else as an enlarged item icon.
     */
    private void renderPreview(GuiGraphics graphics, ItemStack stack, boolean isTarget, int panelX, int panelY) {
        boolean isArmor = !stack.isEmpty()
            && LivingEntity.getEquipmentSlotForItem(stack).getType() == EquipmentSlot.Type.ARMOR;

        if ((stack.isEmpty() || isArmor) && this.minecraft.level != null) {
            ArmorStand stand = isTarget ? this.targetStand : this.sacrificeStand;
            if (stand == null) {
                stand = new ArmorStand(this.minecraft.level, 0, 0, 0);
                if (isTarget) this.targetStand = stand;
                else this.sacrificeStand = stand;
            }
            for (EquipmentSlot es : EquipmentSlot.values()) {
                stand.setItemSlot(es, ItemStack.EMPTY);
            }
            if (isArmor) {
                stand.setItemSlot(LivingEntity.getEquipmentSlotForItem(stack), stack);
            }

            // Continuous slow spin, independent of the mouse.
            float angle = (Util.getMillis() % 6000L) / 6000.0F * 360.0F;
            stand.yBodyRot = angle;
            stand.yBodyRotO = angle;
            stand.setYRot(angle);
            stand.yRotO = angle;
            stand.yHeadRot = angle;
            stand.yHeadRotO = angle;
            stand.setXRot(0.0F);
            stand.xRotO = 0.0F;

            int centerX = panelX + PANEL_W / 2;
            int feetY = panelY + PANEL_H - 6;
            Quaternionf pose = new Quaternionf().rotationZ((float) Math.PI);
            InventoryScreen.renderEntityInInventory(graphics, centerX, feetY, 30, pose, null, stand);
        } else if (!stack.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(panelX + PANEL_W / 2.0 - 20, panelY + PANEL_H / 2.0 - 20, 0);
            graphics.pose().scale(2.5F, 2.5F, 2.5F);
            graphics.renderItem(stack, 0, 0);
            graphics.pose().popPose();
        }
    }

    private void renderChipColumn(GuiGraphics graphics, List<Row> rows, int scroll, int x, int y, boolean isTarget) {
        for (int visible = 0; visible < MAX_ROWS; visible++) {
            int index = scroll + visible;
            if (index >= rows.size()) break;
            Row row = rows.get(index);
            int border;
            int textColor = 0xFF6A9BFF; // stat-blue
            if (row.transferred()) {
                border = 0xFF20A020;     // incoming stat, green
            } else if (row.selected()) {
                border = isTarget ? 0xFFB02020 : 0xFF20A020;
                if (isTarget) textColor = 0xFFFF8080;
            } else {
                border = 0xFF6A3FB0;
            }
            String label = this.font.plainSubstrByWidth(row.label(), LIST_W - 8);
            drawChip(graphics, x, y + visible * CHIP_STEP, LIST_W, label, border, textColor);
        }

        // Scrollbar hint when the list overflows
        if (rows.size() > MAX_ROWS) {
            int trackH = MAX_ROWS * CHIP_STEP - 2;
            int thumbH = Math.max(6, trackH * MAX_ROWS / rows.size());
            int maxScroll = rows.size() - MAX_ROWS;
            int thumbY = y + (trackH - thumbH) * scroll / Math.max(1, maxScroll);
            int barX = x + LIST_W + 1;
            graphics.fill(barX, y, barX + 2, y + trackH, 0xFF8B8B8B);
            graphics.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xFF303030);
        }
    }

    /** Tooltip-style chip: dark background, colored border. */
    private void drawChip(GuiGraphics graphics, int x, int y, int w, String label, int borderColor, int textColor) {
        graphics.fill(x, y, x + w, y + CHIP_H, borderColor);
        graphics.fill(x + 1, y + 1, x + w - 1, y + CHIP_H - 1, 0xF0100010);
        graphics.drawString(this.font, label, x + 4, y + 2, textColor, false);
    }

    private void renderChipTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (boolean isTarget : new boolean[] {true, false}) {
            Row row = rowAt(mouseX, mouseY, isTarget);
            if (row != null) {
                graphics.renderTooltip(this.font, Component.literal(row.label()), mouseX, mouseY);
                return;
            }
        }
    }

    // === row model ===========================================================

    /**
     * Target column: the target's own affixes (removals marked red) followed by
     * the transferred stats coming in from the sacrifice (green).
     */
    private List<Row> targetRows() {
        List<Row> rows = affixRows(this.getMenu().getTarget(), this.getMenu().getRemovals(), false);
        List<Row> incoming = affixRows(this.getMenu().getSacrifice(), Set.of(), true);
        for (Row row : incoming) {
            if (this.getMenu().getTransfers().contains(row.id())) {
                rows.add(row);
            }
        }
        return rows;
    }

    /** Sacrifice column: its affixes minus the ones already moved over. */
    private List<Row> sacrificeRows() {
        List<Row> rows = affixRows(this.getMenu().getSacrifice(), Set.of(), false);
        rows.removeIf(row -> this.getMenu().getTransfers().contains(row.id()));
        return rows;
    }

    private List<Row> affixRows(ItemStack stack, Set<ResourceLocation> selectedSet, boolean transferred) {
        List<Row> rows = new ArrayList<>();
        if (stack.isEmpty()) return rows;

        Map<DynamicHolder<? extends Affix>, AffixInstance> affixes = AffixHelper.getAffixes(stack);
        if (!affixes.isEmpty()) {
            for (Map.Entry<DynamicHolder<? extends Affix>, AffixInstance> e : affixes.entrySet()) {
                ResourceLocation id = e.getKey().getId();
                rows.add(new Row(id, statText(stack, id, e.getValue()), selectedSet.contains(id), transferred));
            }
        } else {
            // Raw NBT fallback: affix_data.affixes { "<id>": <level> }
            CompoundTag afx = stack.getTagElement("affix_data");
            if (afx != null && afx.contains("affixes")) {
                CompoundTag map = afx.getCompound("affixes");
                for (String key : map.getAllKeys()) {
                    ResourceLocation id = ResourceLocation.tryParse(key);
                    if (id == null) continue;
                    String label = Component.translatable("affix." + key).getString();
                    rows.add(new Row(id, label, selectedSet.contains(id), transferred));
                }
            }
        }
        rows.sort((a, b) -> a.id().compareTo(b.id()));
        return rows;
    }

    /**
     * The stat text of an affix: its attribute modifiers with values
     * ("+2 Armor", "+35% Speed"). Falls back to description, then name.
     */
    private String statText(ItemStack stack, ResourceLocation id, AffixInstance instance) {
        List<String> parts = new ArrayList<>();
        try {
            EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(stack);
            instance.addModifiers(slot, (attribute, modifier) -> parts.add(formatModifier(attribute, modifier)));
        } catch (Exception ignored) {}
        if (!parts.isEmpty()) return String.join(", ", parts);

        try {
            String desc = instance.getDescription().getString();
            if (desc != null && !desc.isBlank()) return desc;
        } catch (Exception ignored) {}
        try {
            String name = instance.getName(true).getString();
            if (name != null && !name.isBlank()) return name;
        } catch (Exception ignored) {}
        return Component.translatable("affix." + id).getString();
    }

    private static String formatModifier(net.minecraft.world.entity.ai.attributes.Attribute attribute, AttributeModifier modifier) {
        String name = Component.translatable(attribute.getDescriptionId()).getString();
        double amount = modifier.getAmount();
        return switch (modifier.getOperation()) {
            case ADDITION -> (amount >= 0 ? "+" : "") + num(amount) + " " + name;
            case MULTIPLY_BASE, MULTIPLY_TOTAL -> (amount >= 0 ? "+" : "") + num(amount * 100) + "% " + name;
        };
    }

    private static String num(double d) {
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.format(Locale.ROOT, "%.1f", d);
    }

    // === interaction =========================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Panel click: insert/extract carried item
            int panel = panelAt(mouseX, mouseY);
            if (panel >= 0) {
                this.getMenu().panelClicked(panel); // client mirror for responsiveness
                TrifectaNetwork.CHANNEL.sendToServer(new FusionPanelClickPacket(panel));
                return true;
            }

            // Chip drag start
            Row hit = rowAt(mouseX, mouseY, false);
            if (hit != null) {
                this.drag = new DragState(false, hit.id(), hit.label(), mouseX, mouseY);
                return true;
            }
            hit = rowAt(mouseX, mouseY, true);
            if (hit != null) {
                if (hit.transferred()) {
                    // Click on an incoming (green) chip sends it back to the sacrifice.
                    this.getMenu().getTransfers().remove(hit.id());
                    this.getMenu().updateSelection(this.getMenu().getTransfers(), this.getMenu().getRemovals(), false);
                    sendSelection();
                    return true;
                }
                this.drag = new DragState(true, hit.id(), hit.label(), mouseX, mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.drag != null) {
            DragState d = this.drag;
            this.drag = null;

            boolean clicked = Math.abs(mouseX - d.startX()) < 4 && Math.abs(mouseY - d.startY()) < 4;
            Set<ResourceLocation> transfers = this.getMenu().getTransfers();
            Set<ResourceLocation> removals = this.getMenu().getRemovals();
            boolean changed = false;

            if (d.fromTarget()) {
                // Click toggles removal; dragging it off the target side marks removal.
                if (clicked || !overTargetSide(mouseX, mouseY)) {
                    if (!removals.remove(d.id())) removals.add(d.id());
                    changed = true;
                }
            } else {
                // Click or drop onto the target side moves the stat over.
                if (clicked || overTargetSide(mouseX, mouseY)) {
                    changed = transfers.add(d.id());
                }
            }

            if (changed) {
                this.getMenu().updateSelection(transfers, removals, false);
                sendSelection();
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** @return 0 = target panel, 1 = sacrifice panel, -1 = none */
    private int panelAt(double mouseX, double mouseY) {
        int y = this.topPos + PANEL_Y;
        if (mouseY < y || mouseY >= y + PANEL_H) return -1;
        int tx = this.leftPos + TARGET_PANEL_X;
        if (mouseX >= tx && mouseX < tx + PANEL_W) return FusionTableMenu.TARGET_SLOT;
        int sx = this.leftPos + SACRIFICE_PANEL_X;
        if (mouseX >= sx && mouseX < sx + PANEL_W) return FusionTableMenu.SACRIFICE_SLOT;
        return -1;
    }

    @Nullable
    private Row rowAt(double mouseX, double mouseY, boolean isTarget) {
        int listX = this.leftPos + (isTarget ? TARGET_LIST_X : SACRIFICE_LIST_X);
        int listY = this.topPos + LIST_Y;
        if (mouseX < listX || mouseX >= listX + LIST_W) return null;
        if (mouseY < listY) return null;
        int visibleIndex = (int) ((mouseY - listY) / CHIP_STEP);
        if (visibleIndex < 0 || visibleIndex >= MAX_ROWS) return null;
        if ((mouseY - listY) % CHIP_STEP >= CHIP_H) return null;

        List<Row> rows = isTarget ? targetRows() : sacrificeRows();
        int index = (isTarget ? this.targetScroll : this.sacrificeScroll) + visibleIndex;
        return index < rows.size() ? rows.get(index) : null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        boolean overLists = mouseY >= this.topPos + LIST_Y
            && mouseY < this.topPos + LIST_Y + MAX_ROWS * CHIP_STEP;
        if (overLists) {
            int step = delta > 0 ? -1 : 1;
            int tx = this.leftPos + TARGET_LIST_X;
            int sx = this.leftPos + SACRIFICE_LIST_X;
            if (mouseX >= tx && mouseX < tx + LIST_W + 4) {
                this.targetScroll = clampScroll(this.targetScroll + step, targetRows().size());
                return true;
            }
            if (mouseX >= sx && mouseX < sx + LIST_W + 4) {
                this.sacrificeScroll = clampScroll(this.sacrificeScroll + step, sacrificeRows().size());
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** Everything left of the sacrifice column counts as the "target side" drop zone. */
    private boolean overTargetSide(double mouseX, double mouseY) {
        int borderX = this.leftPos + SACRIFICE_PANEL_X - 2;
        return mouseX >= this.leftPos && mouseX < borderX
            && mouseY >= this.topPos + PANEL_Y && mouseY < this.topPos + LIST_Y + MAX_ROWS * CHIP_STEP;
    }
}
