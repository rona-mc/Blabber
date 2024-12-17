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
package org.ladysnake.blabber.impl.common;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.MenuType;
import org.ladysnake.blabber.Blabber;
import org.ladysnake.blabber.api.DialogueActionV2;
import org.ladysnake.blabber.api.illustration.DialogueIllustrationType;
import org.ladysnake.blabber.api.layout.DefaultLayoutParams;
import org.ladysnake.blabber.api.layout.DialogueLayoutType;
import org.ladysnake.blabber.impl.common.commands.SettingArgumentType;
import org.ladysnake.blabber.impl.common.machine.DialogueStateMachine;
import org.ladysnake.blabber.impl.common.packets.ChoiceAvailabilityPacket;
import org.ladysnake.blabber.impl.common.packets.DialogueListPacket;
import org.ladysnake.blabber.impl.common.packets.SelectedDialogueStatePacket;
import org.ladysnake.blabber.impl.common.settings.BlabberSettingsComponent;

import java.util.Optional;
import java.util.Set;

public final class BlabberRegistrar implements EntityComponentInitializer {
    
    public static final MenuType<DialogueScreenHandler> DIALOGUE_SCREEN_HANDLER = Registry.register(BuiltInRegistries.MENU, Blabber.id("dialogue"), new ExtendedScreenHandlerType<>((syncId, inventory, buf) -> {
        DialogueStateMachine dialogue = new DialogueStateMachine(buf);
        Optional<Entity> interlocutor = buf.readOptional(FriendlyByteBuf::readVarInt).map(inventory.player.level()::getEntity);
        ChoiceAvailabilityPacket choicesAvailability = new ChoiceAvailabilityPacket(buf);
        dialogue.applyAvailabilityUpdate(choicesAvailability);
        return new DialogueScreenHandler(syncId, dialogue, interlocutor.orElse(null));
    }));
    public static final ResourceLocation DIALOGUE_ACTION = Blabber.id("dialogue_action");
    
    public static final ResourceKey<Registry<Codec<? extends DialogueActionV2>>> ACTION_REGISTRY_KEY = ResourceKey.createRegistryKey(Blabber.id("dialogue_actions"));
    
    public static final Registry<Codec<? extends DialogueActionV2>> ACTION_REGISTRY = FabricRegistryBuilder.from(
            new MappedRegistry<>(ACTION_REGISTRY_KEY, Lifecycle.stable(), false)
    ).buildAndRegister();

    
    public static final ResourceKey<Registry<DialogueIllustrationType<?>>> ILLUSTRATION_REGISTRY_KEY = ResourceKey.createRegistryKey(Blabber.id("dialogue_illustrations"));
    
    public static final Registry<DialogueIllustrationType<?>> ILLUSTRATION_REGISTRY = FabricRegistryBuilder.from(
            new MappedRegistry<>(ILLUSTRATION_REGISTRY_KEY, Lifecycle.stable(), false)
    ).buildAndRegister();

    
    public static final ResourceKey<Registry<DialogueLayoutType<?>>> LAYOUT_REGISTRY_KEY = ResourceKey.createRegistryKey(Blabber.id("dialogue_layouts"));
    
    public static final Registry<DialogueLayoutType<?>> LAYOUT_REGISTRY = FabricRegistryBuilder.from(
            new MappedRegistry<>(LAYOUT_REGISTRY_KEY, Lifecycle.stable(), false)
    ).buildAndRegister();
    public static final DialogueLayoutType<DefaultLayoutParams> CLASSIC_LAYOUT = new DialogueLayoutType<>(DefaultLayoutParams.CODEC, DefaultLayoutParams.DEFAULT, DefaultLayoutParams::new, DefaultLayoutParams::writeToPacket);
    public static final DialogueLayoutType<DefaultLayoutParams> RPG_LAYOUT = new DialogueLayoutType<>(DefaultLayoutParams.CODEC, DefaultLayoutParams.DEFAULT, DefaultLayoutParams::new, DefaultLayoutParams::writeToPacket);

    
    public static final SuggestionProvider<CommandSourceStack> ALL_DIALOGUES = SuggestionProviders.register(
            Blabber.id("available_dialogues"),
            (context, builder) -> SharedSuggestionProvider.suggestResource(context.getSource() instanceof CommandSourceStack ? DialogueRegistry.getIds() : DialogueRegistry.getClientIds(), builder)
    );

    public static void init() {
        Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, Blabber.id("interlocutor_properties"), InterlocutorPropertiesLootCondition.TYPE);
        
        ArgumentTypeRegistry.registerArgumentType(Blabber.id("setting"), SettingArgumentType.class, SingletonArgumentInfo.contextFree(SettingArgumentType::setting));

        DialogueLoader.init();
        
        ServerPlayNetworking.registerGlobalReceiver(DIALOGUE_ACTION, (server, player, handler, buf, responseSender) -> {
            int choice = buf.readByte(); 
            server.execute(() -> { 
                if (player.containerMenu instanceof DialogueScreenHandler dialogueHandler) { 
                    if (!dialogueHandler.makeChoice(player, choice)) {
                        
                        responseSender.sendPacket(new SelectedDialogueStatePacket(dialogueHandler.getCurrentStateKey()));
                    }
                }
            });
        });
        
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            
            if (ServerPlayNetworking.canSend(handler, DialogueListPacket.TYPE)) {
                Set<ResourceLocation> dialogueIds = DialogueRegistry.getIds();
                
                sender.sendPacket(new DialogueListPacket(dialogueIds));
            } else {
                
                Blabber.LOGGER.warn("{} does not have Blabber installed, this will cause issues if they trigger a dialogue", handler.getPlayer().getName());
            }
        });
    }

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(PlayerDialogueTracker.KEY, PlayerDialogueTracker::new, RespawnCopyStrategy.ALWAYS_COPY);
        registry.registerForPlayers(BlabberSettingsComponent.KEY, BlabberSettingsComponent::new, RespawnCopyStrategy.ALWAYS_COPY);
    }
}
