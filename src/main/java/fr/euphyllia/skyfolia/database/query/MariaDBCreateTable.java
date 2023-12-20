package fr.euphyllia.skyfolia.database.query;

import fr.euphyllia.skyfolia.api.InterneAPI;
import fr.euphyllia.skyfolia.api.skyblock.model.Position;
import fr.euphyllia.skyfolia.configuration.ConfigToml;
import fr.euphyllia.skyfolia.configuration.section.MariaDBConfig;
import fr.euphyllia.skyfolia.database.execute.MariaDBExecute;
import fr.euphyllia.skyfolia.utils.RegionUtils;
import fr.euphyllia.skyfolia.utils.exception.DatabaseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;

import java.sql.SQLException;
import java.util.List;

public class MariaDBCreateTable {

    private final Logger logger = LogManager.getLogger(this);

    private static final String CREATE_DATABASE = """
            CREATE DATABASE IF NOT EXISTS `%s`;
            """;

    private static final String CREATE_ISLANDS = """
             CREATE TABLE IF NOT EXISTS `%s`.`islands` (
             `island_type` VARCHAR(36) NOT NULL,
             `island_id` VARCHAR(36) NOT NULL,
             `uuid_owner` varchar(36) NOT NULL,
             `disable` TINYINT DEFAULT '0',
             `region_x` INT NOT NULL,
             `region_z` INT NOT NULL,
             `private` TINYINT DEFAULT '0',
             `create_time` TIMESTAMP,
             PRIMARY KEY (`island_id`, `uuid_owner`, `region_x`, `region_z`),
             UNIQUE KEY `unique_region` (`region_x`, `region_z`)
             ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
             """;

    private static final String CREATE_ISLANDS_MEMBERS = """
                CREATE TABLE IF NOT EXISTS `%s`.`members_in_islands` (
                  `island_id` varchar(36) NOT NULL,
                  `uuid_player` varchar(36) NOT NULL,
                  `role` varchar(40) DEFAULT NULL,
                  `joined` TIMESTAMP,
                  PRIMARY KEY (`island_id`,`uuid_player`),
                  CONSTRAINT `members_in_islands_FK` FOREIGN KEY (`island_id`) REFERENCES `islands` (`island_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
            """;

    private static final String CREATE_ISLANDS_WARP = """
                CREATE TABLE IF NOT EXISTS `%s`.`islands_warp` (
                  `id` int unsigned NOT NULL AUTO_INCREMENT,
                  `island_id` varchar(36) NOT NULL,
                  `warp_name` varchar(100) DEFAULT NULL,
                  `world_name` varchar(100) DEFAULT NULL,
                  `x` int DEFAULT NULL,
                  `y` int DEFAULT NULL,
                  `z` int DEFAULT NULL,
                  `pitch` FLOAT DEFAULT NULL,
                  `yaw` FLOAT DEFAULT NULL,
                  PRIMARY KEY (`id`),
                  KEY `islands_warp_FK` (`island_id`),
                  CONSTRAINT `islands_warp_FK` FOREIGN KEY (`island_id`) REFERENCES `islands` (`island_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
            """;

    private static final String CREATE_SPIRAL = """
                CREATE TABLE IF NOT EXISTS `%s`.`spiral` (
                  `id` int NOT NULL,
                  `region_x` int NOT NULL,
                  `region_z` int NOT NULL,
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
            """;

    private static final String INSERT_SPIRAL = """
                INSERT IGNORE INTO `%s`.`spiral`
                (id, region_x, region_z)
                VALUES(?, ?, ?);
            """;


    private final String database;
    private final InterneAPI api;

    public MariaDBCreateTable(InterneAPI interneAPI) throws DatabaseException {
        this.api = interneAPI;
        MariaDBConfig dbConfig = ConfigToml.mariaDBConfig;
        if (dbConfig == null) {
            throw new DatabaseException("No database is mentioned in the configuration of the plugin.", null);
        }
        this.database = ConfigToml.mariaDBConfig.database();
        try {
            this.init();
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    private void init() throws SQLException {
        // DATABASE
        MariaDBExecute.executeQuery(api, CREATE_DATABASE.formatted(this.database));
        MariaDBExecute.executeQuery(api, CREATE_ISLANDS.formatted(this.database));
        MariaDBExecute.executeQuery(api, CREATE_ISLANDS_MEMBERS.formatted(this.database));
        MariaDBExecute.executeQuery(api, CREATE_ISLANDS_WARP.formatted(this.database));
        MariaDBExecute.executeQuery(api, CREATE_SPIRAL.formatted(this.database));
        Bukkit.getAsyncScheduler().runNow(this.api.getPlugin(), scheduledTask -> {
            for(int i = 1; i < ConfigToml.maxIsland; i++) {
                Position position = RegionUtils.getPosition(i);
                MariaDBExecute.executeQuery(api, INSERT_SPIRAL.formatted(this.database), List.of(i, position.regionX(), position.regionZ()), null, null);
                if (i % 1000 == 0) {
                    logger.log(Level.INFO, "Insertion en cours (" + i + "/" + ConfigToml.maxIsland + ")");
                }
            }
        });
    }
}
