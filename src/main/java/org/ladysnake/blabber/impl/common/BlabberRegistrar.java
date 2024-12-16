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
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.commands.suggestion.SuggestionProviders;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
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

public final class BlabberRegistrar {
    public static final MenuType<DialogueScreenHandler> DIALOGUE_SCREEN_HANDLER = Registry.register(BuiltInRegistries.MENU, Blabber.id("dialogue"), new MenuType<>((syncId, inventory, buf) -> {
        DialogueStateMachine dialogue = new DialogueStateMachine(buf);
        Optional<Entity> interlocutor = buf.readOptional(FriendlyByteBuf::readVarInt).map(inventory.player.level().getEntity());
        ChoiceAvailabilityPacket choicesAvailability = new ChoiceAvailabilityPacket(buf);
        dialogue.applyAvailabilityUpdate(choicesAvailability);
        return new DialogueScreenHandler(syncId, dialogue, interlocutor.orElse(null));
    }));
    public static final ResourceLocation DIALOGUE_ACTION = Blabber.id("dialogue_action");
    public static final ResourceKey<Registry<Codec<? extends DialogueActionV2>>> ACTION_REGISTRY_KEY = ResourceKey.createRegistryKey(Blabber.id("dialogue_actions"));
    public static final Registry<Codec<? extends DialogueActionV2>> ACTION_REGISTRY = new MappedRegistry<>(ACTION_REGISTRY_KEY, Lifecycle.stable(), false);

    public static final ResourceKey<Registry<DialogueIllustrationType<?>>> ILLUSTRATION_REGISTRY_KEY = ResourceKey.createRegistryKey(Blabber.id("dialogue_illustrations"));
    public static final Registry<DialogueIllustrationType<?>> ILLUSTRATION_REGISTRY = new MappedRegistry<>(ILLUSTRATION_REGISTRY_KEY, Lifecycle.stable(), false);

    public static final ResourceKey<Registry<DialogueLayoutType<?>>> LAYOUT_REGISTRY_KEY = ResourceKey.createRegistryKey(Blabber.id("dialogue_layouts"));
    public static final Registry<DialogueLayoutType<?>> LAYOUT_REGISTRY = new MappedRegistry<>(LAYOUT_REGISTRY_KEY, Lifecycle.stable(), false);
    public static final DialogueLayoutType<DefaultLayoutParams> CLASSIC_LAYOUT = new DialogueLayoutType<>(DefaultLayoutParams.CODEC, DefaultLayoutParams.DEFAULT, DefaultLayoutParams::new, DefaultLayoutParams::writeToPacket);
    public static final DialogueLayoutType<DefaultLayoutParams> RPG_LAYOUT = new DialogueLayoutType<>(DefaultLayoutParams.CODEC, DefaultLayoutParams.DEFAULT, DefaultLayoutParams::new, DefaultLayoutParams::writeToPacket);

    public static final SuggestionProvider<CommandSourceStack> ALL_DIALOGUES = SuggestionProviders.register(
            Blabber.id("available_dialogues"),
            (context, builder) -> CommandSource.suggestResource(context.getSource() instanceof CommandSourceStack ? DialogueRegistry.getIds() : DialogueRegistry.getClientIds(), builder)
    );

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            Blabber.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void init() {
        Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, Blabber.id("interlocutor_properties"), InterlocutorPropertiesLootCondition.TYPE);
        ArgumentTypeInfos.register(Blabber.id("setting").toString(), SettingArgumentType.class, SingletonArgumentInfo.contextFree(SettingArgumentType::setting));

        DialogueLoader.init();

        // Register network messages
        int id = 0;
        NETWORK.messageBuilder(DialogueListPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DialogueListPacket::write)
                .decoder(DialogueListPacket::new)
                .consumerMainThread(DialogueListPacket::handle)
                .add();

        NETWORK.messageBuilder(ChoiceAvailabilityPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ChoiceAvailabilityPacket::write)
                .decoder(ChoiceAvailabilityPacket::new)
                .consumerMainThread(ChoiceAvailabilityPacket::handle)
                .add();

        NETWORK.messageBuilder(SelectedDialogueStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SelectedDialogueStatePacket::write)
                .decoder(SelectedDialogueStatePacket::new)
                .consumerMainThread(SelectedDialogueStatePacket::handle)
                .add();

        // Handle dialogue action from client
        NETWORK.messageBuilder(Byte.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FriendlyByteBuf::writeByte)
                .decoder(FriendlyByteBuf::readByte)
                .consumerMainThread((choice, ctx) -> {
                    NetworkEvent.Context context = ctx.get();
                    if (context.getSender() != null && context.getSender().containerMenu instanceof DialogueScreenHandler dialogueHandler) {
                        if (!dialogueHandler.makeChoice(context.getSender(), choice)) {
                            NETWORK.sendTo(new SelectedDialogueStatePacket(dialogueHandler.getCurrentStateKey()), context.getSender().connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                        }
                    }
                })
                .add();
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlayerDialogueTracker.class);
        event.register(BlabberSettingsComponent.class);
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            LazyOptional<PlayerDialogueTracker> oldCap = event.getOriginal().getCapability(PlayerDialogueTracker.CAPABILITY);
            LazyOptional<PlayerDialogueTracker> newCap = event.getEntity().getCapability(PlayerDialogueTracker.CAPABILITY);
            
            oldCap.ifPresent(old -> newCap.ifPresent(newT -> newT.copyFrom(old)));

            LazyOptional<BlabberSettingsComponent> oldSettings = event.getOriginal().getCapability(BlabberSettingsComponent.CAPABILITY);
            LazyOptional<BlabberSettingsComponent> newSettings = event.getEntity().getCapability(BlabberSettingsComponent.CAPABILITY);
            
            oldSettings.ifPresent(old -> newSettings.ifPresent(newT -> newT.copyFrom(old)));
        }
    }
}
