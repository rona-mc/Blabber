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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.ladysnake.blabber.Blabber;
import org.ladysnake.blabber.impl.common.model.DialogueTemplate;
import org.ladysnake.blabber.impl.common.packets.DialogueListPacket;
import org.ladysnake.blabber.impl.common.validation.DialogueLoadingException;
import org.ladysnake.blabber.impl.common.validation.DialogueValidator;
import org.ladysnake.blabber.impl.common.validation.ValidationResult;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Blabber.MOD_ID)
public final class DialogueLoader extends SimplePreparableReloadListener<Map<ResourceLocation, DialogueTemplate>> {
    public static final String BLABBER_DIALOGUES_PATH = "blabber/dialogues";
    public static final ResourceLocation ID = Blabber.id("dialogue_loader");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DialogueLoader INSTANCE = new DialogueLoader();

    public static void init() {
        // Registration is handled by Forge events
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        Set<ResourceLocation> dialogueIds = DialogueRegistry.getIds();
        if (event.getPlayer() != null) {
            // Sync to specific player
            BlabberRegistrar.NETWORK.sendTo(
                new DialogueListPacket(dialogueIds),
                event.getPlayer().connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
            );
            PlayerDialogueTracker.get(event.getPlayer()).updateDialogue();
        } else {
            // Sync to all players
            for (ServerPlayer player : event.getPlayerList().getPlayers()) {
                BlabberRegistrar.NETWORK.sendTo(
                    new DialogueListPacket(dialogueIds),
                    player.connection.connection,
                    net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
                );
                PlayerDialogueTracker.get(player).updateDialogue();
            }
        }
    }

    @Override
    protected Map<ResourceLocation, DialogueTemplate> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, DialogueTemplate> data = new HashMap<>();
        manager.listResources(BLABBER_DIALOGUES_PATH, (res) -> res.getPath().endsWith(".json")).forEach((location, resource) -> {
            try (Reader in = new InputStreamReader(resource.open())) {
                JsonObject jsonObject = GSON.fromJson(in, JsonObject.class);
                ResourceLocation id = new ResourceLocation(location.getNamespace(), location.getPath().substring(BLABBER_DIALOGUES_PATH.length() + 1, location.getPath().length() - 5));
                DialogueTemplate dialogue = DialogueTemplate.CODEC.parse(JsonOps.INSTANCE, jsonObject).getOrThrow(false, message -> Blabber.LOGGER.error("(Blabber) Could not parse dialogue file from {}: {}", location, message));
                ValidationResult result = DialogueValidator.validateStructure(dialogue);
                // TODO GIVE ME PATTERN MATCHING IN SWITCHES
                if (result instanceof ValidationResult.Error error) {
                    Blabber.LOGGER.error("(Blabber) Could not validate dialogue {}: {}", id, error.message());
                    throw new DialogueLoadingException("Could not validate dialogue file from " + location);
                } else if (result instanceof ValidationResult.Warnings warnings) {
                    Blabber.LOGGER.warn("(Blabber) Dialogue {} had warnings: {}", id, warnings.message());
                }

                data.put(id, dialogue);
            } catch (IOException | JsonParseException e) {
                Blabber.LOGGER.error("(Blabber) Could not read dialogue file from {}", location, e);
                throw new DialogueLoadingException("Could not read dialogue file from " + location, e);
            }
        });
        return data;
    }

    @Override
    protected void apply(Map<ResourceLocation, DialogueTemplate> data, ResourceManager manager, ProfilerFiller profiler) {
        DialogueRegistry.setEntries(data);
    }

    private DialogueLoader() {}
}
