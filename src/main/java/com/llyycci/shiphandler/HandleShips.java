package com.llyycci.shiphandler;

import com.llyycci.shiphandler.util.EntityUtils;
import com.llyycci.shiphandler.util.MathUtils;
import com.llyycci.shiphandler.util.TextUtils;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.apigame.world.ServerShipWorldCore;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.*;

public class HandleShips {
    private static Set<Ship> previousShips = new HashSet<>(); // Track ship IDs from previous tick
    
    public static void init() {
        // 注册Fabric事件
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getServer().overworld() == world) {
                onLevelLoad(server, world);
            }
        });
        
        ServerTickEvents.END_SERVER_TICK.register(HandleShips::onServerTick);
        
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            onPlayerJoin(handler, server);
        });
    }
    
    // Static method to handle deleting ships
    public static void deleteShips(MinecraftServer server, List<Ship> ships) {
        try {
            for (Ship ship : ships) {
                ServerShipWorldCore shipWorld = VSGameUtilsKt.getVsPipeline(server).getShipWorld();
                ServerShip deleteShip = (ServerShip) ship;
                shipWorld.deleteShip(deleteShip);
            }
        } catch (Exception e) {
            ShipHandlerMod.LOGGER.error("", e.getCause());
        }
    }
    
    // Static method that performs ship handling
    public static void handle() {
        MinecraftServer server = ShipHandlerMod.getServer();
        ShipDataStore shipDataStore = ShipDataStore.get(server.overworld());
        List<Long> storedShipIds = shipDataStore.getAllRegisteredShipIDs();
        
        List<Ship> shipsToDelete = new ArrayList<>();
        for (ServerShip ship : VSGameUtilsKt.getVsPipeline(server).getShipWorld().getAllShips()) {
            if (!storedShipIds.contains(ship.getId()) || ship.getInertiaData().getMass() == 0.0) {
                shipsToDelete.add(ship);
            }
        }
        // Delete all ships not in ShipDataStore
        deleteShips(server, shipsToDelete);
    }
    
    // Method to get all current ships in the server
    public static Set<Ship> getCurrentShips() {
        Set<Ship> shipIds = new HashSet<>();
        MinecraftServer server = ShipHandlerMod.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            shipIds.addAll(VSGameUtilsKt.getAllShips(level));
        }
        return shipIds;
    }
    
    public static void onLevelLoad(MinecraftServer server, ServerLevel level) {
        ShipHandlerMod.LOGGER.debug("----------------------------------------------------------------");
        ShipHandlerMod.LOGGER.debug("Server initialised");
        
        ShipDataStore shipDataStore = ShipDataStore.get(level.getServer().overworld());
        previousShips = getCurrentShips(); // Initialize ship IDs when level loads
        
        // Remove ships that weren't picked up when they were deleted
        HashMap<Long, ShipDataStore.ShipData> copiedMap = new HashMap<>(shipDataStore.getShipData());
        ShipHandlerMod.LOGGER.debug(String.valueOf(copiedMap));
        for (Ship ship : previousShips) {
            copiedMap.remove(ship.getId());
            ShipHandlerMod.LOGGER.debug(String.valueOf(ship.getId()));
        }
        ShipHandlerMod.LOGGER.debug(String.valueOf(copiedMap));
        for (Long remainingId : copiedMap.keySet()) {
            shipDataStore.removeShip(remainingId);
            ShipHandlerMod.LOGGER.debug(String.valueOf(remainingId));
        }
        ShipHandlerMod.LOGGER.debug(String.valueOf(copiedMap));
        
        ShipHandlerMod.LOGGER.debug("Level loaded: " + level.toString());
        ShipHandlerMod.LOGGER.debug("----------------------------------------------------------------");
    }
    
    public static void onPlayerJoin(ServerGamePacketListenerImpl handler, MinecraftServer server) {
        Player player = handler.getPlayer();
        ShipDataStore shipDataStore = ShipDataStore.get(server.overworld());
        
        if (!shipDataStore.hasPlayer(player)) {
            shipDataStore.addPlayer(player, ShiphandlerConfig.maxShips.get(), ShiphandlerConfig.autoRegister.get());
        }
    }
    
    public static void onServerTick(MinecraftServer server) {
        Set<Ship> currentShips = getCurrentShips();
        
        if (!currentShips.equals(previousShips)) {
            // Ships added
            Set<Ship> newShips = new HashSet<>(currentShips);
            newShips.removeAll(previousShips);
            
            // Ships removed
            Set<Ship> removedShips = new HashSet<>(previousShips);
            removedShips.removeAll(currentShips);
            
            // Handle new ships
            for (Ship ship : newShips) {
                ShipHandlerMod.LOGGER.debug("New ship detected with Slug: {}", ship.getSlug());
                //EntityUtils.sendChatMessage(server, Component.literal("New ship detected with Slug: " + ship.getSlug()));
                
                Level level = server.overworld();
                for (Level level_ : server.getAllLevels()) {
                    if (TextUtils.formatDimensionId(level_.dimension().toString()).equals(ship.getChunkClaimDimension())) {
                        level = server.getLevel(level_.dimension());
                        break;
                    }
                }
                
                Player player = null;
                
                ShipDataStore dataStore = ShipDataStore.get(server.overworld());
                int inflateSize = 0;
                while (player == null) {
                    if (inflateSize > ShiphandlerConfig.maxShipFindDistance.get()) {
                        break;
                    }
                    player = EntityUtils.getNearestPlayerToBlock(level,
                            MathUtils.getCenterPosition(
                                    new BlockPos((int) ship.getWorldAABB().minX(),
                                            (int) ship.getWorldAABB().minY(),
                                            (int) ship.getWorldAABB().minZ()
                                    ),
                                    new BlockPos((int) ship.getWorldAABB().maxX(),
                                            (int) ship.getWorldAABB().maxY(),
                                            (int) ship.getWorldAABB().maxZ()
                                    )
                            ),
                            MathUtils.AABBdc2AABB(ship.getWorldAABB()).inflate(inflateSize, inflateSize, inflateSize)
                    );
                    
                    inflateSize++;
                }
                if (player != null) {
                    ShipHandlerMod.LOGGER.info("Added newly-created ship: " + ship.getSlug() + ", made by player: " + player.getDisplayName().getString());
                    
                    dataStore.addShip(player, ship);
                    
                    if (dataStore.usesAutoRegister(player)) {
                        dataStore.registerShip(player, ship);
                    }
                } else {
                    EntityUtils.sendChatMessage(server, Component.literal("§4Unable to find player"));
                    
                    dataStore.addShip(null, ship);
                }
            }
            
            // Handle removed ships
            for (Ship ship : removedShips) {
                ShipHandlerMod.LOGGER.debug("Ship removed with Slug: {}", ship.getSlug());
                
                ShipDataStore dataStore = ShipDataStore.get(server.overworld());
                dataStore.removeShip(ship.getId());
            }
        }
        
        // Update the previousShipIds for the next tick
        previousShips = currentShips;
    }
}