package es.jaie55.boatracing.team;

import es.jaie55.boatracing.BoatRacingPlugin;
import es.jaie55.boatracing.util.DocumentStore;
import es.jaie55.boatracing.util.Text;
import org.bukkit.DyeColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class TeamManager {
    private static final String TEAMS_DOCUMENT = "teams.yml";
    private static final String RACERS_DOCUMENT = "racers.yml";

    private final BoatRacingPlugin plugin;
    private final DocumentStore documentStore;
    private final Map<UUID, Team> teams = new LinkedHashMap<>();
    private int maxMembers;

    public TeamManager(BoatRacingPlugin plugin) {
        this.plugin = plugin;
        this.documentStore = plugin.getDocumentStore();
        this.maxMembers = Math.max(1, plugin.getConfig().getInt("max-members-per-team", 2));
        load();
    }

    public Collection<Team> getTeams() {
        return teams.values();
    }

    public java.util.List<Team> getTeamsSnapshot() {
        return new ArrayList<>(teams.values());
    }

    public Optional<Team> findByName(String name) {
        if (name == null) return Optional.empty();
        return teams.values().stream().filter(t -> t.getName().equalsIgnoreCase(name)).findFirst();
    }

    public Optional<Team> getTeamByMember(UUID player) {
        return teams.values().stream().filter(t -> t.isMember(player)).findFirst();
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public boolean isNumberTaken(int number) {
        return false;
    }

    public Team createTeam(Player leader, String name, DyeColor color) {
        UUID id = UUID.randomUUID();
        Team team = new Team(id, name, color, leader.getUniqueId());
        teams.put(id, team);
        save();
        leader.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("team.created", "name", name)));
        return team;
    }

    public Team createTeam(UUID firstMember, String name, DyeColor color) {
        UUID id = UUID.randomUUID();
        Team team = new Team(id, name, color, firstMember);
        teams.put(id, team);
        save();
        return team;
    }

    public boolean removeTeam(Team team) {
        if (team == null) return false;
        teams.remove(team.getId());
        save();
        return true;
    }

    public boolean addMember(Team team, UUID playerId) {
        if (team == null || playerId == null) return false;
        boolean ok = team.addMember(playerId);
        if (ok) save();
        return ok;
    }

    public boolean removeMember(Team team, UUID playerId) {
        if (team == null || playerId == null) return false;
        boolean ok = team.removeMember(playerId);
        if (ok) save();
        return ok;
    }

    public boolean setLeader(Team team, UUID playerId) {
        if (team == null || playerId == null || !team.isMember(playerId)) return false;
        team.setLeader(playerId);
        save();
        return true;
    }

    public void deleteTeam(Team team) {
        if (team == null) return;
        teams.remove(team.getId());
        save();
    }

    public void save() {
        if (documentStore == null) {
            plugin.getLogger().warning("Document store not available; team data was not persisted.");
            return;
        }

        YamlConfiguration teamsCfg = new YamlConfiguration();
        YamlConfiguration racersCfg = new YamlConfiguration();

        for (Team team : teams.values()) {
            String path = "teams." + team.getId();
            teamsCfg.set(path + ".name", team.getName());
            teamsCfg.set(path + ".color", team.getColor().name());
            teamsCfg.set(path + ".leader", team.getLeader() != null ? team.getLeader().toString() : null);
            teamsCfg.set(path + ".members", team.getMembers().stream().map(UUID::toString).toList());

            for (UUID memberId : team.getMembers()) {
                String base = "racers." + memberId;
                int racerNumber = team.getRacerNumber(memberId);
                if (racerNumber > 0) {
                    racersCfg.set(base + ".number", racerNumber);
                }
                String boatType = team.getBoatType(memberId);
                if (boatType != null && !boatType.isBlank()) {
                    racersCfg.set(base + ".boat", boatType);
                }
            }
        }

        try {
            documentStore.write(TEAMS_DOCUMENT, teamsCfg.saveToString());
            documentStore.write(RACERS_DOCUMENT, racersCfg.saveToString());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to persist team data: " + e.getMessage());
        }
    }

    public void load() {
        teams.clear();

        YamlConfiguration teamsCfg = loadDocument(TEAMS_DOCUMENT);
        YamlConfiguration racersCfg = loadDocument(RACERS_DOCUMENT);

        boolean loadedTeams = loadTeamsFromDocument(teamsCfg);
        boolean loadedRacers = loadRacersFromDocument(racersCfg);

        if (!loadedTeams) {
            boolean migrated = migrateTeamsFromLegacyConfig();
            if (migrated) {
                save();
                plugin.getLogger().info("Migrated team data from old config.yml into document storage");
            }
            return;
        }

        if (!loadedRacers) {
            boolean migrated = migrateRacersFromLegacyConfig();
            if (migrated) {
                save();
                plugin.getLogger().info("Migrated racer numbers and boat types from old config.yml into document storage");
            }
        }
    }

    private YamlConfiguration loadDocument(String documentName) {
        YamlConfiguration configuration = new YamlConfiguration();
        if (documentStore == null) {
            return configuration;
        }

        try {
            String content = documentStore.read(documentName);
            if (content != null && !content.isBlank()) {
                configuration.loadFromString(content);
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Failed to load " + documentName + ": " + e.getMessage());
        }
        return configuration;
    }

    private boolean loadTeamsFromDocument(YamlConfiguration teamsCfg) {
        ConfigurationSection section = teamsCfg.getConfigurationSection("teams");
        if (section == null || section.getKeys(false).isEmpty()) {
            return false;
        }

        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String path = "teams." + key;
                String name = teamsCfg.getString(path + ".name", "Team");
                DyeColor color = parseColor(teamsCfg.getString(path + ".color", DyeColor.WHITE.name()));
                UUID leader = null;
                String leaderRaw = teamsCfg.getString(path + ".leader");
                if (leaderRaw != null && !leaderRaw.isBlank()) {
                    leader = UUID.fromString(leaderRaw);
                }

                Team team = new Team(id, name, color, null);
                Set<String> members = new LinkedHashSet<>(teamsCfg.getStringList(path + ".members"));
                for (String member : members) {
                    try {
                        team.addMemberUnchecked(UUID.fromString(member));
                    } catch (Exception ignored) {
                        // Skip invalid member ids.
                    }
                }
                if (leader != null && team.isMember(leader)) {
                    team.setLeader(leader);
                }
                teams.put(id, team);
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping invalid team entry '" + key + "': " + e.getMessage());
            }
        }

        return !teams.isEmpty();
    }

    private boolean loadRacersFromDocument(YamlConfiguration racersCfg) {
        ConfigurationSection section = racersCfg.getConfigurationSection("racers");
        if (section == null || section.getKeys(false).isEmpty()) {
            return false;
        }

        boolean loaded = false;
        for (String key : section.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                int racerNumber = racersCfg.getInt("racers." + key + ".number", 0);
                String boatType = racersCfg.getString("racers." + key + ".boat", null);
                Optional<Team> team = getTeamByMember(playerId);
                if (team.isPresent()) {
                    if (racerNumber > 0) {
                        team.get().setRacerNumber(playerId, racerNumber);
                    }
                    if (boatType != null && !boatType.isBlank()) {
                        team.get().setBoatType(playerId, boatType);
                    }
                }
                loaded = true;
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping invalid racer entry '" + key + "': " + e.getMessage());
            }
        }

        return loaded;
    }

    private boolean migrateTeamsFromLegacyConfig() {
        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection old = cfg.getConfigurationSection("teams");
        if (old == null || old.getKeys(false).isEmpty()) {
            return false;
        }

        boolean migrated = false;
        for (String key : old.getKeys(false)) {
            try {
                String path = "teams." + key;
                UUID id = UUID.fromString(key);
                String name = cfg.getString(path + ".name", "Team");
                DyeColor color = parseColor(cfg.getString(path + ".color", DyeColor.WHITE.name()));

                UUID leader = null;
                String leaderRaw = cfg.getString(path + ".leader");
                if (leaderRaw != null && !leaderRaw.isBlank()) {
                    leader = UUID.fromString(leaderRaw);
                }

                Team team = new Team(id, name, color, leader);

                for (String member : cfg.getStringList(path + ".members")) {
                    try {
                        team.addMemberUnchecked(UUID.fromString(member));
                    } catch (Exception ignored) {
                        // Ignore invalid entries.
                    }
                }

                ConfigurationSection racerNumbers = cfg.getConfigurationSection(path + ".racerNumbers");
                if (racerNumbers != null) {
                    for (String memberId : racerNumbers.getKeys(false)) {
                        try {
                            team.setRacerNumber(UUID.fromString(memberId), racerNumbers.getInt(memberId, 0));
                        } catch (Exception ignored) {
                        }
                    }
                }

                ConfigurationSection boatTypes = cfg.getConfigurationSection(path + ".boatTypes");
                if (boatTypes != null) {
                    for (String memberId : boatTypes.getKeys(false)) {
                        try {
                            String boatType = boatTypes.getString(memberId, null);
                            if (boatType != null && !boatType.isBlank()) {
                                team.setBoatType(UUID.fromString(memberId), boatType);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }

                teams.put(id, team);
                migrated = true;
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping legacy team entry '" + key + "': " + e.getMessage());
            }
        }

        return migrated;
    }

    private boolean migrateRacersFromLegacyConfig() {
        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection legacyTeams = cfg.getConfigurationSection("teams");
        if (legacyTeams == null || legacyTeams.getKeys(false).isEmpty()) {
            return false;
        }

        boolean migrated = false;
        for (Team team : teams.values()) {
            String base = "teams." + team.getId();
            ConfigurationSection legacyNumbers = cfg.getConfigurationSection(base + ".racerNumbers");
            if (legacyNumbers != null) {
                for (String memberId : legacyNumbers.getKeys(false)) {
                    try {
                        UUID playerId = UUID.fromString(memberId);
                        int racerNumber = legacyNumbers.getInt(memberId, 0);
                        if (racerNumber > 0 && team.isMember(playerId)) {
                            team.setRacerNumber(playerId, racerNumber);
                            migrated = true;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            ConfigurationSection legacyBoats = cfg.getConfigurationSection(base + ".boatTypes");
            if (legacyBoats != null) {
                for (String memberId : legacyBoats.getKeys(false)) {
                    try {
                        UUID playerId = UUID.fromString(memberId);
                        String boatType = legacyBoats.getString(memberId, null);
                        if (boatType != null && !boatType.isBlank() && team.isMember(playerId)) {
                            team.setBoatType(playerId, boatType);
                            migrated = true;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return migrated;
    }

    private static DyeColor parseColor(String raw) {
        try {
            return DyeColor.valueOf(raw == null ? DyeColor.WHITE.name() : raw);
        } catch (IllegalArgumentException ex) {
            return DyeColor.WHITE;
        }
    }
}
