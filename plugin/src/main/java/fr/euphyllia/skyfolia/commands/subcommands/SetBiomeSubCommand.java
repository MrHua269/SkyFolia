package fr.euphyllia.skyfolia.commands.subcommands;

import fr.euphyllia.skyfolia.Main;
import fr.euphyllia.skyfolia.api.skyblock.Island;
import fr.euphyllia.skyfolia.api.skyblock.Players;
import fr.euphyllia.skyfolia.api.skyblock.model.Position;
import fr.euphyllia.skyfolia.cache.CommandCacheExecution;
import fr.euphyllia.skyfolia.commands.SubCommandInterface;
import fr.euphyllia.skyfolia.configuration.LanguageToml;
import fr.euphyllia.skyfolia.managers.skyblock.SkyblockManager;
import fr.euphyllia.skyfolia.utils.PlayerUtils;
import fr.euphyllia.skyfolia.utils.RegionUtils;
import fr.euphyllia.skyfolia.utils.WorldEditUtils;
import fr.euphyllia.skyfolia.utils.WorldUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class SetBiomeSubCommand implements SubCommandInterface {

    private final Logger logger = LogManager.getLogger(SetBiomeSubCommand.class);

    @Override
    public boolean onCommand(@NotNull Main plugin, @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (!player.hasPermission("skyfolia.island.command.biome")) {
            LanguageToml.sendMessage(plugin, player, LanguageToml.messagePlayerPermissionDenied);
            return true;
        }
        if (args.length < 1) {
            LanguageToml.sendMessage(plugin, player, LanguageToml.messageBiomeCommandNotEnoughArgs);
            return true;
        }
        if (CommandCacheExecution.isAlreadyExecute(player.getUniqueId(), "biome")) {
            LanguageToml.sendMessage(plugin, player, LanguageToml.messageCommandAlreadyExecution);
            return true;
        }

        Location playerLocation = player.getLocation();
        int chunkLocX = playerLocation.getChunk().getX();
        int chunkLocZ = playerLocation.getChunk().getZ();
        String selectBiome = args[0];

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            executor.execute(() -> {
                CommandCacheExecution.addCommandExecute(player.getUniqueId(), "biome");
                try {
                    Biome biome;
                    try {
                        biome = Biome.valueOf(selectBiome.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        LanguageToml.sendMessage(plugin, player, LanguageToml.messageBiomeNotExist.formatted(selectBiome));
                        return;
                    }

                    SkyblockManager skyblockManager = plugin.getInterneAPI().getSkyblockManager();

                    if (Boolean.FALSE.equals(WorldUtils.isWorldSkyblock(playerLocation.getWorld().getName()))) {
                        LanguageToml.sendMessage(plugin, player, LanguageToml.messageBiomeOnlyIsland);
                        return;
                    }

                    Island island = skyblockManager.getIslandByOwner(player.getUniqueId()).join();

                    if (island == null) {
                        LanguageToml.sendMessage(plugin, player, LanguageToml.messagePlayerHasNotIsland);
                        return;
                    }

                    Position islandPosition = island.getPosition();
                    Position playerRegionPosition = RegionUtils.getRegionInChunk(chunkLocX, chunkLocZ);

                    if (islandPosition.regionX() != playerRegionPosition.regionX() || islandPosition.regionZ() != playerRegionPosition.regionZ()) {
                        LanguageToml.sendMessage(plugin, player, LanguageToml.messagePlayerNotInIsland);
                        return;
                    }

                    World world = player.getWorld();
                    LanguageToml.sendMessage(plugin, player, LanguageToml.messageBiomeChangeInProgress);

                    boolean biomeChanged = WorldEditUtils.changeBiomeChunk(plugin, world, biome, new Position(chunkLocX, chunkLocZ)).join();
                    if (biomeChanged) {
                        LanguageToml.sendMessage(plugin, player, LanguageToml.messageBiomeChangeSuccess);
                        for (Players players : island.getMembers()) {
                            Player bPlayer = Bukkit.getPlayer(players.getMojangId());
                            if (bPlayer != null && bPlayer.isOnline()) {
                                PlayerUtils.updateChunk(plugin, player, chunkLocX, chunkLocZ);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.FATAL, e.getMessage(), e);
                    LanguageToml.sendMessage(plugin, player, LanguageToml.messageError);

                }
            });
        } finally {
            executor.shutdown();
            CommandCacheExecution.removeCommandExec(player.getUniqueId(), "biome");
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Main plugin, @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> biomesList = new ArrayList<>();
        if (args.length == 1) {
            for (Biome biome : Biome.values()) {
                biomesList.add(biome.name());
            }
        }
        return biomesList;
    }
}
