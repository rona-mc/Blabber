/*
 * Blabber
 * Copyright (C) 2022-2024 Ladysnake
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; If not, see <https://www.gnu.org/licenses>.
 */
package org.ladysnake.blabber.api.client;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.Options;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.ladysnake.blabber.Blabber;
import org.ladysnake.blabber.api.client.illustration.DialogueIllustrationRenderer;
import org.ladysnake.blabber.api.layout.DialogueLayout;
import org.ladysnake.blabber.impl.client.BlabberClient;
import org.ladysnake.blabber.impl.common.DialogueScreenHandler;
import org.ladysnake.blabber.impl.common.illustrations.PositionTransform;
import org.ladysnake.blabber.impl.common.machine.AvailableChoice;
import org.ladysnake.blabber.impl.common.model.ChoiceResult;
import org.ladysnake.blabber.impl.common.model.IllustrationAnchor;
import org.ladysnake.blabber.impl.common.settings.BlabberSetting;
import org.ladysnake.blabber.impl.common.settings.BlabberSettingsComponent;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@ApiStatus.Experimental // half internal, expect some things to change
public class BlabberDialogueScreen<P extends DialogueLayout.Params> extends AbstractContainerScreen<DialogueScreenHandler> {
    public static final List<ResourceLocation> DIALOGUE_ARROWS = IntStream.range(1, 6).mapToObj(i -> Blabber.id("textures/gui/sprites/container/dialogue/dialogue_arrow_" + i + ".png")).toList();
    public static final List<ResourceLocation> DIALOGUE_LOCKS = IntStream.range(1, 4).mapToObj(i -> Blabber.id("textures/gui/sprites/container/dialogue/dialogue_lock_" + i + ".png")).toList();
    public static final int DEFAULT_TITLE_GAP = 20;
    public static final int DEFAULT_TEXT_MAX_WIDTH = 300;
    public static final int DEFAULT_INSTRUCTIONS_BOTTOM_MARGIN = 30;
    public static final int[] DEBUG_COLORS = new int[] {
            0x42b862,
            0xb84242,
            0xb86a42,
            0x42b87d,
            0x42b8b8,
            0x426ab8,
            0x6a42b8,
            0xb842b8,
    };

    protected final Component instructions;

    // Things that could be constants but may be mutated by subclasses
    protected ResourceLocation selectionIconTexture = DIALOGUE_ARROWS.get(0);
    protected ResourceLocation lockIconTexture = DIALOGUE_LOCKS.get(0);
    /**
     * Margin from the top of the screen to the dialogue's main text
     */
    protected int mainTextMinY = 40;
    /**
     * Gap between each choice in the list
     */
    protected int choiceGap = 8;
    protected int mainTextMinX = 10;
    protected int instructionsMinY;
    protected int mainTextMaxWidth = DEFAULT_TEXT_MAX_WIDTH;
    /**
     * Max width for the choice texts
     */
    protected int choiceListMaxWidth = DEFAULT_TEXT_MAX_WIDTH;
    /**
     * Margin from the left of the screen to the choice list (includes the space for the selection icon)
     */
    protected int choiceListMinX = 25;
    /**
     * Margin from the left of the screen to the choice selection icon
     */
    protected int selectionIconMinX = 4;
    /**
     * Vertical offset for the selection/lock icon, based on the individual choice's Y
     */
    protected int selectionIconMarginTop = -4;
    protected int selectionIconSize = 16;
    protected EnumMap<IllustrationAnchor, Vector2i> illustrationSlots;
    protected int mainTextColor = 0xFFFFFF;
    protected int lockedChoiceColor = 0x808080;
    protected int selectedChoiceColor = 0xE0E044;
    protected int choiceColor = 0xA0A0A0;

    // Things that are mutated during state changes
    protected int choiceListMinY;

    // Screen state
    protected int selectedChoice;
    protected boolean hoveringChoice;

    protected Map<String, DialogueIllustrationRenderer<?>> illustrations = new HashMap<>();

    public BlabberDialogueScreen(DialogueScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        Options options = Minecraft.getInstance().options;
        this.instructions = Component.translatable("blabber:dialogue.instructions", options.keyUp.getTranslatedKeyMessage(), options.keyDown.getTranslatedKeyMessage(), options.keyInventory.getTranslatedKeyMessage());
        this.illustrationSlots = new EnumMap<>(IllustrationAnchor.class);
        for (IllustrationAnchor anchor : IllustrationAnchor.values()) {
            this.illustrationSlots.put(anchor, new Vector2i(-999, -999));
        }
    }

    @SuppressWarnings("unchecked")
    protected P params() {
        return (P) this.menu.getLayout().params();
    }

    @Override
    protected void init() {
        super.init();
        this.prepareLayout();
        this.illustrations.clear();
        this.menu.getIllustrations().forEach((key, illustration) -> this.illustrations.put(key, BlabberClient.createRenderer(illustration)));
    }

    protected void prepareLayout() {
        this.computeMargins();
        this.layoutIllustrationAnchors();
    }

    protected void computeMargins() {
        this.instructionsMinY = this.height - DEFAULT_INSTRUCTIONS_BOTTOM_MARGIN;
        Component text = this.menu.getCurrentText();
        this.choiceListMinY = mainTextMinY + this.font.wordWrapHeight(text, mainTextMaxWidth) + DEFAULT_TITLE_GAP;
    }

    protected void layoutIllustrationAnchors() {
        this.illustrationSlots.get(IllustrationAnchor.BEFORE_MAIN_TEXT).set(this.mainTextMinX, this.mainTextMinY);
        this.illustrationSlots.get(IllustrationAnchor.SPOT_1).set(this.width * 3/4, this.choiceListMinY);
        this.illustrationSlots.get(IllustrationAnchor.SPOT_2).set(this.width * 2/5, this.height * 2/3);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !this.menu.isUnskippable();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (hoveringChoice) {
            this.confirmChoice(this.selectedChoice);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        Options options = Minecraft.getInstance().options;
        if (key == GLFW.GLFW_KEY_ENTER || options.keyInventory.matches(key, scancode)) {
            this.confirmChoice(this.selectedChoice);
            return true;
        }
        boolean tab = GLFW.GLFW_KEY_TAB == key;
        boolean down = options.keyDown.matches(key, scancode);
        boolean shift = (GLFW.GLFW_MOD_SHIFT & modifiers) != 0;
        if (tab || down || options.keyUp.matches(key, scancode)) {
            scrollDialogueChoice(tab && !shift || down ? -1 : 1);
            return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    private @Nullable ChoiceResult confirmChoice(int selectedChoice) {
        assert this.minecraft != null;
        if (this.menu.getAvailableChoices().get(selectedChoice).unavailabilityMessage().isPresent()) {
            return null;
        }

        ChoiceResult result = this.makeChoice(selectedChoice);

        switch (result) {
            case END_DIALOGUE -> this.minecraft.setScreen(null);
            case ASK_CONFIRMATION -> {
                ImmutableList<AvailableChoice> choices = this.menu.getAvailableChoices();
                this.minecraft.setScreen(new ConfirmScreen(
                        this::onBigChoiceMade,
                        this.menu.getCurrentText(),
                        Component.empty(),
                        choices.get(0).text(),
                        choices.get(1).text()
                ));
            }
            default -> {
                this.selectedChoice = 0;
                this.hoveringChoice = false;
                this.prepareLayout();
            }
        }

        return result;
    }

    private void onBigChoiceMade(boolean yes) {
        assert minecraft != null;
        if (this.confirmChoice(yes ? 0 : 1) == ChoiceResult.DEFAULT) {
            this.minecraft.setScreen(this);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        this.scrollDialogueChoice(Mth.clamp(verticalAmount, -1.0, 1.0));
        return true;
    }

    protected void scrollDialogueChoice(double scrollAmount) {
        ImmutableList<AvailableChoice> availableChoices = this.menu.getAvailableChoices();
        if (!availableChoices.isEmpty()) {
            this.selectedChoice = Math.floorMod((int) (this.selectedChoice - scrollAmount), availableChoices.size());
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        ImmutableList<AvailableChoice> choices = this.menu.getAvailableChoices();
        int y = this.choiceListMinY;
        for (int i = 0; i < choices.size(); i++) {
            Component choice = choices.get(i).text();
            int strHeight = this.font.wordWrapHeight(choice, choiceListMaxWidth);
            int strWidth = strHeight == 9 ? this.font.width(choice) : choiceListMaxWidth;
            if (this.shouldSelectChoice(mouseX, mouseY, y, strHeight, strWidth)) {
                this.selectedChoice = i;
                this.hoveringChoice = true;
                return;
            }
            y += strHeight + choiceGap;
            this.hoveringChoice = false;
        }
    }

    protected boolean shouldSelectChoice(double mouseX, double mouseY, int choiceY, int choiceHeight, int choiceWidth) {
        return mouseX >= 0 && mouseX < (choiceListMinX + choiceWidth) && mouseY > choiceY && mouseY < choiceY + choiceHeight;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float tickDelta) {
        this.renderBackground(context);

        assert minecraft != null;
        assert minecraft.player != null;

        PositionTransform positionTransform = this.createPositionTransform();
        positionTransform.setControlPoints(0, 0, this.width, this.height);

        int y = mainTextMinY;

        for (String illustrationName : this.menu.getCurrentIllustrations()) {
            this.getIllustrationRenderer(illustrationName).render(context, this.font, positionTransform, mouseX, mouseY, tickDelta);
        }

        Component mainText = this.menu.getCurrentText();

        context.drawWordWrap(this.font, mainText, mainTextMinX, y, mainTextMaxWidth, mainTextColor);
        y = this.choiceListMinY;
        ImmutableList<AvailableChoice> choices = this.menu.getAvailableChoices();

        for (int i = 0; i < choices.size(); i++) {
            AvailableChoice choice = choices.get(i);
            int strHeight = this.font.wordWrapHeight(choice.text(), choiceListMaxWidth);
            boolean selected = i == this.selectedChoice;
            int choiceColor = choice.unavailabilityMessage().isPresent() ? lockedChoiceColor : selected ? selectedChoiceColor : this.choiceColor;
            context.drawWordWrap(this.font, choice.text(), choiceListMinX, y, choiceListMaxWidth, choiceColor);

            positionTransform.setControlPoints(choiceListMinX, y, choiceListMinX + choiceListMaxWidth, y + strHeight);

            for (String illustrationName : choice.illustrations()) {
                this.getIllustrationRenderer(illustrationName).render(context, this.font, positionTransform, mouseX, mouseY, tickDelta);
            }

            if (selected) {
                if (choice.unavailabilityMessage().isPresent()) {
                    context.blit(
                            lockIconTexture,
                            selectionIconMinX,
                            y + selectionIconMarginTop,
                            0,
                            0,
                            0,
                            selectionIconSize,
                            selectionIconSize,
                            selectionIconSize,
                            selectionIconSize
                    );
                    context.renderTooltip(
                            this.font,
                            choice.unavailabilityMessage().get(),
                            this.hoveringChoice ? mouseX : choiceListMaxWidth,
                            this.hoveringChoice ? mouseY : y
                    );
                } else {
                    context.blit(
                            selectionIconTexture,
                            selectionIconMinX,
                            y + selectionIconMarginTop,
                            0,
                            0,
                            0,
                            selectionIconSize,
                            selectionIconSize,
                            selectionIconSize,
                            selectionIconSize
                    );
                }
            }
            y += strHeight + choiceGap;
        }

        context.drawWordWrap(this.font, instructions, Math.max((this.width - this.font.width(instructions)) / 2, 5), instructionsMinY, this.width - 5, 0x808080);

        BlabberSettingsComponent settings = BlabberSettingsComponent.get(minecraft.player);
        if (settings.isDebugEnabled()) {
            positionTransform.setControlPoints(0, 0, this.width, this.height);
            renderDebugInfo(settings, context, positionTransform, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float v, int i, int i1) {

    }

    private DialogueIllustrationRenderer<?> getIllustrationRenderer(String illustrationName) {
        DialogueIllustrationRenderer<?> renderer = this.illustrations.get(illustrationName);
        if (renderer == null) throw new IllegalArgumentException("Unknown illustration " + illustrationName);
        return renderer;
    }

    protected @NotNull PositionTransform createPositionTransform() {
        return new PositionTransform(this.illustrationSlots);
    }

    protected void renderDebugInfo(BlabberSettingsComponent settings, GuiGraphics context, PositionTransform positionTransform, int mouseX, int mouseY) {
        if (settings.isEnabled(BlabberSetting.DEBUG_ANCHORS)) {
            this.renderAnchorDebugInfo(context, positionTransform, mouseX, mouseY);
        }
    }

    protected void renderAnchorDebugInfo(GuiGraphics context, PositionTransform positionTransform, int mouseX, int mouseY) {
        for (IllustrationAnchor anchor : IllustrationAnchor.values()) {
            int color = DEBUG_COLORS[anchor.ordinal() % DEBUG_COLORS.length];
            context.drawString(this.font, "x", positionTransform.transformX(anchor, -3), positionTransform.transformY(anchor, -5), color, true);
            MutableComponent text = Component.empty().append(Component.literal(anchor.getSerializedName()).withStyle(s -> s.withColor(color))).append(" > X: " + positionTransform.inverseTransformX(anchor, mouseX) + ", Y: " + positionTransform.inverseTransformY(anchor, mouseY));
            switch (anchor) {
                case TOP_LEFT, TOP_RIGHT -> context.renderTooltip(
                        this.font,
                        text,
                        positionTransform.transformX(anchor, 0),
                        15
                );

                default -> context.renderTooltip(
                        this.font,
                        text,
                        positionTransform.transformX(anchor, 0),
                        positionTransform.transformY(anchor, 0)
                );
            }
        }
    }


    public ChoiceResult makeChoice(int choice) {
        int originalChoiceIndex = this.menu.getAvailableChoices().get(choice).originalChoiceIndex();
        ChoiceResult result = this.menu.makeChoice(originalChoiceIndex);
        BlabberClient.sendDialogueActionMessage(originalChoiceIndex);
        return result;
    }
}
