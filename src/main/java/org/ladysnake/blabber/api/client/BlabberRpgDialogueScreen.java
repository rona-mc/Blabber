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
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.player.Inventory;
import org.joml.Matrix4f;
import org.ladysnake.blabber.api.layout.DefaultLayoutParams;
import org.ladysnake.blabber.api.layout.Margins;
import org.ladysnake.blabber.impl.common.DialogueScreenHandler;
import org.ladysnake.blabber.impl.common.machine.AvailableChoice;
import org.ladysnake.blabber.impl.common.model.IllustrationAnchor;

public class BlabberRpgDialogueScreen extends BlabberDialogueScreen<DefaultLayoutParams> {
    public static final int INSTRUCTIONS_BOTTOM_MARGIN = 6;
    public static final int TEXT_TOP_MARGIN = 12;
    protected int choiceListMaxY;

    public BlabberRpgDialogueScreen(DialogueScreenHandler menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.selectionIconTexture = DIALOGUE_ARROWS.get(1);
        this.lockIconTexture = DIALOGUE_LOCKS.get(2);
        this.choiceColor = 0xD0D0D0;
        this.lockedChoiceColor = 0xA0A0A0;
        this.selectedChoiceColor = 0xF0F066;
    }

    @Override
    protected void computeMargins() {
        super.computeMargins();
        Margins mainTextMargins = this.params().getMainTextMargins();
        this.choiceListMaxWidth = 150;
        this.mainTextMaxWidth = Math.min(400, this.width) - mainTextMargins.left() - mainTextMargins.right();
        this.instructionsMinY = this.height - INSTRUCTIONS_BOTTOM_MARGIN - this.font.wordWrapHeight(instructions, this.width - 5);
        this.mainTextMinY = this.height - 60 - mainTextMargins.bottom();
        this.mainTextMinX = Math.max(mainTextMargins.left(), (this.width / 2) - (Math.min(font.width(menu.getCurrentText()), mainTextMaxWidth) / 2));
        this.illustrationSlots.get(IllustrationAnchor.BEFORE_MAIN_TEXT).set(
                Math.max(mainTextMargins.left(), (this.width / 2) - (mainTextMaxWidth / 2)),
                this.height - 60
        );
        this.choiceListMaxY = mainTextMinY - 25 - mainTextMargins.top();
        this.choiceListMinY = choiceListMaxY;
        for (AvailableChoice choice : menu.getAvailableChoices()) {
            this.choiceListMinY -= font.wordWrapHeight(choice.text(), choiceListMaxWidth) + choiceGap;
        }
        this.choiceListMinX = this.width - choiceListMaxWidth;
        this.selectionIconMinX = choiceListMinX - selectionIconSize - 4;
    }

    @Override
    protected void layoutIllustrationAnchors() {
        // No super call, we redefine every illustration slot either here or in the previous method
        this.illustrationSlots.get(IllustrationAnchor.SPOT_1).set(
                this.width / 4,
                this.mainTextMinY - TEXT_TOP_MARGIN
        );
        this.illustrationSlots.get(IllustrationAnchor.SPOT_2).set(
                (this.choiceListMinX + this.width) / 2,
                this.choiceListMinY * 3/4
        );
    }

    @Override
    protected boolean shouldSelectChoice(double mouseX, double mouseY, int choiceY, int choiceHeight, int choiceWidth) {
        return mouseX > choiceListMinX - 4 && mouseX <= width && mouseY > choiceY && mouseY < choiceY + choiceHeight;
    }

    @Override
    public void renderBackground(GuiGraphics context) {
        // Side background
        int y = this.choiceListMinY;
        ImmutableList<AvailableChoice> availableChoices = menu.getAvailableChoices();
        for (int i = 0; i < availableChoices.size(); i++) {
            AvailableChoice choice = availableChoices.get(i);
            int strHeight = this.font.wordWrapHeight(choice.text(), choiceListMaxWidth);
            fillHorizontalGradient(context, this.choiceListMinX - 2, y, this.width, y + strHeight, 0xc0101010, 0x80101010);
            if (i == selectedChoice) this.selectionIconMarginTop = ((strHeight - 9) / 2) - 4;
            y += strHeight + choiceGap;
        }
        // Bottom background
        context.fillGradient(0, this.mainTextMinY - 20, this.width, this.mainTextMinY - TEXT_TOP_MARGIN, 0x00101010, 0xc0101010);
        context.fillGradient(0, this.mainTextMinY - TEXT_TOP_MARGIN, this.width, this.height, 0xc0101010, 0xd0101010);
    }

    public static void fillHorizontalGradient(GuiGraphics context, int startX, int startY, int endX, int endY, int colorStart, int colorEnd) {
        final int z = 0;
        final int verticalPadding = 2;
        VertexConsumer vertexConsumer = context.bufferSource().getBuffer(RenderType.gui());
        float a0 = (float) FastColor.ARGB32.alpha(colorStart) / 255.0F;
        float r0 = (float) FastColor.ARGB32.red(colorStart) / 255.0F;
        float g0 = (float) FastColor.ARGB32.green(colorStart) / 255.0F;
        float b0 = (float) FastColor.ARGB32.blue(colorStart) / 255.0F;
        float a1 = (float) FastColor.ARGB32.alpha(colorEnd) / 255.0F;
        float r1 = (float) FastColor.ARGB32.red(colorEnd) / 255.0F;
        float g1 = (float) FastColor.ARGB32.green(colorEnd) / 255.0F;
        float b1 = (float) FastColor.ARGB32.blue(colorEnd) / 255.0F;
        Matrix4f matrix4f = context.pose().last().pose();
        vertexConsumer.vertex(matrix4f, (float)startX, (float)startY - verticalPadding, (float)z).color(r1, g1, b1, a1).endVertex();
        vertexConsumer.vertex(matrix4f, (float)startX, (float)startY, (float)z).color(r0, g0, b0, a0).endVertex();
        vertexConsumer.vertex(matrix4f, (float)endX, (float)startY, (float)z).color(r1, g1, b1, a1).endVertex();
        vertexConsumer.vertex(matrix4f, (float)endX, (float)startY - verticalPadding, (float)z).color(r1, g1, b1, a1).endVertex();

        vertexConsumer.vertex(matrix4f, (float)startX, (float)startY, (float)z).color(r0, g0, b0, a0).endVertex();
        vertexConsumer.vertex(matrix4f, (float)startX, (float)endY, (float)z).color(r0, g0, b0, a0).endVertex();
        vertexConsumer.vertex(matrix4f, (float)endX, (float)endY, (float)z).color(r1, g1, b1, a1).endVertex();
        vertexConsumer.vertex(matrix4f, (float)endX, (float)startY, (float)z).color(r1, g1, b1, a1).endVertex();

        vertexConsumer.vertex(matrix4f, (float)startX, (float)endY, (float)z).color(r0, g0, b0, a0).endVertex();
        vertexConsumer.vertex(matrix4f, (float)startX, (float)endY + verticalPadding, (float)z).color(r1, g1, b1, a1).endVertex();
        vertexConsumer.vertex(matrix4f, (float)endX, (float)endY + verticalPadding, (float)z).color(r1, g1, b1, a1).endVertex();
        vertexConsumer.vertex(matrix4f, (float)endX, (float)endY, (float)z).color(r1, g1, b1, a1).endVertex();
    }
}
