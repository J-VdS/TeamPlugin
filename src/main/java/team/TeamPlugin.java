package team;

import arc.*;
import arc.struct.ObjectIntMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.*;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.mod.Plugin;
import mindustry.world.Tile;

// use java.util for now
//import java.util.Arrays;

// constants
import static team.constants.*;

public class TeamPlugin extends Plugin {
    private ObjectMap<Player, Long> teamTimers = new ObjectMap<>();
    private ObjectMap<Player, Team> rememberSpectate = new ObjectMap<>();
    private ObjectIntMap<Team> teamCounters = new ObjectIntMap<>();

    //register event handlers and create variables in the constructor
    public TeamPlugin(){
        Events.on(PlayerLeave.class, event -> {
            //update teamCounters
            if(event.player.team() != null && event.player.team() != spectateTeam) {
                teamCounters.put(event.player.team(), teamCounters.get(event.player.team(), 0) - 1);
            }

            if(rememberSpectate.containsKey(event.player)){
                rememberSpectate.remove(event.player);
            }
            if(teamTimers.containsKey(event.player)){
                teamTimers.remove(event.player);
            }
        });

        Events.on(PlayerJoin.class, event -> {
           teamCounters.put(event.player.team(), teamCounters.get(event.player.team(), 0)+1);
        });

        Events.on(WorldLoadEvent.class, event -> {
            if(!Vars.state.rules.pvp) return;
            //get all available teams
            teamCounters.clear();
            for(Team t: Team.baseTeams){
                if(!t.cores().isEmpty()){
                    teamCounters.put(t,0);
                }
            }

            Seq<Player> copyOfPlayers = Groups.player.copy(new Seq<Player>());
            Team playerTeam;
            Player p;
            for(int index = 0; index<copyOfPlayers.size; index++){
                p = copyOfPlayers.get(index);
                if(p == null) continue;
                playerTeam = p.team();
                teamCounters.put(playerTeam, teamCounters.get(playerTeam)+1);
            }
        });

    }

    //register commands that run on the server
    @Override
    public void registerServerCommands(CommandHandler handler){
    }

    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("ut","unit type", (args, player) ->{
           player.sendMessage(player.unit().type().name);
        });

        handler.<Player>register("team", "[teamname]","change team - cooldown", (args, player) ->{
            if(player.unit().type != UnitTypes.alpha && player.unit().type != UnitTypes.beta && player.unit().type != UnitTypes.gamma){
                player.sendMessage("\n> [orange] Go back to [sky]player mode[] before switching teams!\n");
                return;
            }
            if(rememberSpectate.containsKey(player)){
                player.sendMessage(">[orange] transferring back to last team");
                player.team(rememberSpectate.get(player));
                Call.setPlayerTeamEditor(player, rememberSpectate.get(player));
                rememberSpectate.remove(player);
                return;
            }

            if(!Vars.state.rules.pvp && !Vars.state.rules.infiniteResources){
                player.sendMessage("[orange] only usefull in PVP");
                return;
            }
            if(Vars.state.rules.tags.getBool("forceteam") && !player.admin()){
                player.sendMessage("You can't change teams. An admin activated forceTeam!");
                Log.info("<TeamPlugin> @ can't change team - forceteam", Strings.stripColors(player.name));
                return;
            }

            if(System.currentTimeMillis() < teamTimers.get(player,0L)){
                player.sendMessage(">[orange] command is on a 10 second cooldown...");
                return;
            }
            coreTeamReturn ret = null;
            if(args.length == 1){
                Team retTeam;
                switch (args[0]) {
                    case "sharded":
                        retTeam = Team.sharded;
                        break;
                    case "blue":
                        retTeam = Team.blue;
                        break;
                    case "crux":
                        retTeam = Team.crux;
                        break;
                    case "derelict":
                        retTeam = Team.derelict;
                        break;
                    case "green":
                        retTeam = Team.green;
                        break;
                    case "purple":
                        retTeam = Team.purple;
                        break;
                    default:
                        player.sendMessage("[scarlet]ABORT: Team not found[] - available teams:");
                        for (int i = 0; i < 6; i++) {
                            if (!Team.baseTeams[i].cores().isEmpty()) {
                                player.sendMessage(Team.baseTeams[i].name);
                            }
                        }
                        return;
                }
                if(retTeam.cores().isEmpty()) {
                    player.sendMessage("This team has no core - can't change!");
                    return;
                }else if(teamCounters.get(retTeam) - maxDiff < teamCounters.get(player.team())){
                    player.sendMessage(String.format("[orange]<team>[white] You can't change to this team (max difference %d)[]", maxDiff));
                    return;
                }else{
                    Tile coreTile = retTeam.core().tileOn();
                    ret =  new coreTeamReturn(retTeam, coreTile.drawx(), coreTile.drawy());
                }
            }else{
                ret = getPosTeamLoc(player);

            }

            //move team mechanic
            if(ret != null) {
                teamCounters.put(player.team(), teamCounters.get(player.team())-1);
                Call.setPlayerTeamEditor(player, ret.team);
                player.team(ret.team);
                teamCounters.put(ret.team, teamCounters.get(ret.team)+1);
                //maybe not needed
                Call.setPosition(player.con, ret.x, ret.y);
                player.unit().set(ret.x, ret.y);
                player.snapSync();
                teamTimers.put(player, System.currentTimeMillis()+TEAM_CD);
                Call.sendMessage(String.format("> %s []changed to team [sky]%s", player.name, ret.team));
            }else{
                player.sendMessage("[scarlet]You can't change teams ...");
            }
        });

        handler.<Player>register("spectate", "[scarlet]Admin only[]", (args, player) -> {
            if(!player.admin()){
               player.sendMessage("[scarlet]This command is only for admins.");
               return;
            }
            if(rememberSpectate.containsKey(player)){
                player.team(rememberSpectate.get(player));
                Call.setPlayerTeamEditor(player, rememberSpectate.get(player));
                rememberSpectate.remove(player);
                player.sendMessage("[gold]PLAYER MODE[]");
            }else{
                rememberSpectate.put(player, player.unit().team);
                player.team(spectateTeam);
                Call.setPlayerTeamEditor(player, spectateTeam);
                player.unit().kill();
                player.sendMessage("[green]SPECTATE MODE[]");
                player.sendMessage("use /team or /spectate to go back to player mode");
            }
        });
    }
    //search a possible team
    private Team getPosTeam(Player p){
        Team currentTeam = p.team();
        Seq<Team> posTeams = teamCounters.keys().toArray();

        int c_index = posTeams.indexOf(currentTeam);//Arrays.asList(Team.baseTeams).indexOf(currentTeam);
        int i = (c_index+1)%posTeams.size;
        while (i != c_index){
            if (teamCounters.get(posTeams.get(i)) - maxDiff < teamCounters.get(currentTeam)){
                return Team.baseTeams[i];
            }
            i = (i + 1) % teamCounters.size;
        }
        return currentTeam;
    }

    private coreTeamReturn getPosTeamLoc(Player p){
        Team currentTeam = p.team();
        Team newTeam = getPosTeam(p);
        if (newTeam == currentTeam){
            return null;
        }else{
            Tile coreTile = newTeam.core().tileOn();
            return new coreTeamReturn(newTeam, coreTile.drawx(), coreTile.drawy());
        }
    }

    class coreTeamReturn{
        Team team;
        float x,y;
        public coreTeamReturn(Team _t, float _x, float _y){
            team = _t;
            x = _x;
            y = _y;
        }
    }
}
