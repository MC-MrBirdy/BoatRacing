package es.jaie55.boatracing.team;

import org.bukkit.DyeColor;
import org.bukkit.Material;

import java.util.*;

public class Team {
    private final UUID id;
    private String name;
    private DyeColor color;
    private final Set<UUID> members = new LinkedHashSet<>();
    private UUID leader;
    // Per-member preferences
    private final Map<UUID, Integer> racerNumbers = new HashMap<>();
    private final Map<UUID, String> boatTypes = new HashMap<>(); // Boat.Type name

    public Team(UUID id, String name, DyeColor color, UUID firstMember) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.leader = firstMember;
        if (firstMember != null) {
            this.members.add(firstMember);
        }
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public DyeColor getColor() { return color; }
    public Set<UUID> getMembers() { return Collections.unmodifiableSet(members); }
    public UUID getLeader() { return leader; }

    public void setName(String name) { this.name = name; }
    public void setColor(DyeColor color) { this.color = color; }
    public void setLeader(UUID leader) {
        this.leader = leader != null && members.contains(leader) ? leader : null;
    }

    public boolean addMember(UUID uuid) {
        int max = es.jaie55.boatracing.BoatRacingPlugin.getInstance().getTeamManager().getMaxMembers();
        if (members.size() >= max) return false;
        boolean added = members.add(uuid);
        if (added && leader == null) leader = uuid;
        return added;
    }

    // Unchecked add used only when loading from storage to preserve existing membership regardless of caps
    boolean addMemberUnchecked(UUID uuid) {
        boolean added = members.add(uuid);
        if (added && leader == null) leader = uuid;
        return added;
    }

    public boolean removeMember(UUID uuid) {
        boolean removed = members.remove(uuid);
        if (removed && Objects.equals(leader, uuid)) {
            leader = members.isEmpty() ? null : members.iterator().next();
        }
        return removed;
    }

    public boolean isMember(UUID uuid) { return members.contains(uuid); }

    // --- Member preferences ---
    public int getRacerNumber(UUID uuid) {
        return racerNumbers.getOrDefault(uuid, 0);
    }
    public void setRacerNumber(UUID uuid, int racerNumber) {
        racerNumbers.put(uuid, racerNumber);
    }
    public String getBoatType(UUID uuid) { return boatTypes.getOrDefault(uuid, Material.OAK_BOAT.name()); }
    public void setBoatType(UUID uuid, String boatType) {
        boatTypes.put(uuid, boatType);
    }
    public Map<UUID, Integer> getAllRacerNumbers() { return Collections.unmodifiableMap(racerNumbers); }
    public Map<UUID, String> getAllBoatTypes() { return Collections.unmodifiableMap(boatTypes); }
}
