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
package org.ladysnake.blabber.api.client.illustrations;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.ladysnake.blabber.api.client.illustration.DialogueIllustrationRenderer;
import org.ladysnake.blabber.impl.common.illustrations.PositionTransform;
import org.ladysnake.blabber.impl.common.illustrations.entity.DialogueIllustrationEntity;
import org.ladysnake.blabber.impl.common.illustrations.entity.StareTarget;

public abstract class EntityIllustrationRenderer<I extends DialogueIllustrationEntity> extends DialogueIllustrationRenderer<I> {
    private @Nullable LivingEntity renderedEntity;

    public EntityIllustrationRenderer(I illustration) {
        super(illustration);
    }

    protected abstract @Nullable LivingEntity getRenderedEntity(Level world);

    @Override
    public void render(GuiGraphics context, Font textRenderer, PositionTransform positionTransform, int mouseX, int mouseY, float tickDelta) {
        LivingEntity e = this.renderedEntity == null
                ? this.renderedEntity = this.getRenderedEntity(Minecraft.getInstance().level)
                : this.renderedEntity;

        if (e == null) return; // Something went wrong creating the entity, so don't render.

        int x1 = illustration.minX(positionTransform);
        int y1 = illustration.minY(positionTransform);
        int x2 = illustration.maxX(positionTransform);
        int y2 = illustration.maxY(positionTransform);

        StareTarget stareTarget = illustration.stareAt();
        int fakedMouseX = stareTarget.x().isPresent() ? stareTarget.anchor().isPresent() ? positionTransform.transformX(stareTarget.anchor().get(), stareTarget.x().getAsInt()) : stareTarget.x().getAsInt() + (x1 + x2) / 2 : mouseX;
        int fakedMouseY = stareTarget.y().isPresent() ? stareTarget.anchor().isPresent() ? positionTransform.transformY(stareTarget.anchor().get(), stareTarget.y().getAsInt()) : stareTarget.y().getAsInt() + (y1 + y2) / 2 : mouseY;

        renderEntityInInventory(context,
                x1,
                y1,
                x2,
                y2,
                illustration.entitySize(),
                illustration.yOffset(),
                fakedMouseX,
                fakedMouseY,
                e);
    }

    // Copy-pasted from MC 1.20.4 InventoryScreen#drawEntity
    public static void renderEntityInInventory(GuiGraphics p_282802_, int p_275688_, int p_275245_, int p_275535_, int p_301381_, int p_299741_, float p_275604_, float p_275546_, float p_300682_, LivingEntity p_275689_) {
        float $$10 = (float)(p_275688_ + p_275535_) / 2.0F;
        float $$11 = (float)(p_275245_ + p_301381_) / 2.0F;
        p_282802_.enableScissor(p_275688_, p_275245_, p_275535_, p_301381_);
        float $$12 = (float)Math.atan((double)(($$10 - p_275546_) / 40.0F));
        float $$13 = (float)Math.atan((double)(($$11 - p_300682_) / 40.0F));
        Quaternionf $$14 = (new Quaternionf()).rotateZ(3.1415927F);
        Quaternionf $$15 = (new Quaternionf()).rotateX($$13 * 20.0F * 0.017453292F);
        $$14.mul($$15);
        float $$16 = p_275689_.yBodyRot;
        float $$17 = p_275689_.getYRot();
        float $$18 = p_275689_.getXRot();
        float $$19 = p_275689_.yHeadRotO;
        float $$20 = p_275689_.yHeadRot;
        p_275689_.yBodyRot = 180.0F + $$12 * 20.0F;
        p_275689_.setYRot(180.0F + $$12 * 40.0F);
        p_275689_.setXRot(-$$13 * 20.0F);
        p_275689_.yHeadRot = p_275689_.getYRot();
        p_275689_.yHeadRotO = p_275689_.getYRot();
        Vector3f $$21 = new Vector3f(0.0F, p_275689_.getBbHeight() / 2.0F + p_275604_, 0.0F);
        renderEntityInInventory(p_282802_, $$10, $$11, p_299741_, $$21, $$14, $$15, p_275689_);
        p_275689_.yBodyRot = $$16;
        p_275689_.setYRot($$17);
        p_275689_.setXRot($$18);
        p_275689_.yHeadRotO = $$19;
        p_275689_.yHeadRot = $$20;
        p_282802_.disableScissor();
    }

    public static void renderEntityInInventory(GuiGraphics p_282665_, float p_300023_, float p_301239_, int p_283622_, Vector3f p_298037_, Quaternionf p_281880_, @javax.annotation.Nullable Quaternionf p_282882_, LivingEntity p_282466_) {
        p_282665_.pose().pushPose();
        p_282665_.pose().translate((double)p_300023_, (double)p_301239_, 50.0);
        p_282665_.pose().mulPoseMatrix((new Matrix4f()).scaling((float)p_283622_, (float)p_283622_, (float)(-p_283622_)));
        p_282665_.pose().translate(p_298037_.x, p_298037_.y, p_298037_.z);
        p_282665_.pose().mulPose(p_281880_);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher $$8 = Minecraft.getInstance().getEntityRenderDispatcher();
        if (p_282882_ != null) {
            p_282882_.conjugate();
            $$8.overrideCameraOrientation(p_282882_);
        }

        $$8.setRenderShadow(false);
        RenderSystem.runAsFancy(() -> {
            $$8.render(p_282466_, 0.0, 0.0, 0.0, 0.0F, 1.0F, p_282665_.pose(), p_282665_.bufferSource(), 15728880);
        });
        p_282665_.flush();
        $$8.setRenderShadow(true);
        p_282665_.pose().popPose();
        Lighting.setupFor3DItems();
    }
}
