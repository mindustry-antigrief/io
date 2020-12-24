package mindustry.creeper;

import arc.Events;
import arc.graphics.Color;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.StaticWall;

import java.util.HashMap;

import static mindustry.Vars.world;

public class CreeperUtils {
    public static float transferRate = 0.2f;
    public static float creeperDamage = 5f;
    public static Team creeperTeam = Team.blue;

    public static HashMap<Float, Block> creeperBlocks = new HashMap<>();
    public static HashMap<Block, Emitter> emitterBlocks = new HashMap<>();

    public static Seq<Emitter> creeperEmitters = new Seq<>();
    public static Timer.Task runner;

    public static void init(){
        creeperBlocks.put(1f, Blocks.conveyor);
        creeperBlocks.put(2f, Blocks.titaniumConveyor);
        creeperBlocks.put(3f, Blocks.armoredConveyor);
        creeperBlocks.put(4f, Blocks.plastaniumConveyor);
        creeperBlocks.put(5f, Blocks.scrapWall);
        creeperBlocks.put(6f, Blocks.titaniumWall);
        creeperBlocks.put(7f, Blocks.thoriumWall);
        creeperBlocks.put(8f, Blocks.plastaniumWall);
        creeperBlocks.put(9f, Blocks.phaseWall);
        creeperBlocks.put(10f, Blocks.surgeWall);

        emitterBlocks.put(Blocks.coreShard, new Emitter(30, 5));
        emitterBlocks.put(Blocks.coreFoundation, new Emitter(15, 5));
        emitterBlocks.put(Blocks.coreNucleus, new Emitter(15, 15));

        Events.on(EventType.GameOverEvent.class, e -> {
            if(runner != null)
                runner.cancel();

            creeperEmitters.clear();
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            // DOES NOT SIGNIFY WORLD IS LOADED, need to wait
            try {
                Thread.sleep(2000);
                creeperEmitters.clear();

                Seq<Building> iterated = new Seq<>();
                for(Tile tile : world.tiles){
                    if(tile.block() != null && emitterBlocks.containsKey(tile.block()) && !iterated.contains(tile.build) && tile.build.team == creeperTeam){
                        iterated.add(tile.build);
                        creeperEmitters.add(new Emitter(tile.build));
                    }
                }

                Log.info(creeperEmitters.size + " emitters");

            } catch (InterruptedException interruptedException) {
                interruptedException.printStackTrace();
            }
        });

        Events.on(EventType.CreeperDestroyEvent.class, e -> {
            if(e.tile.creep > 0)
                e.tile.creep--;

            drawCreeper(e.tile);
        });

        runner = Timer.schedule(CreeperUtils::updateCreeper, 0, 0.01666666666f);
    }


    public static void updateCreeper(){

        // update emitters
        for(Emitter emitter : creeperEmitters){
            emitter.update();
        }

        // update creeper flow
        for(Tile tile : world.tiles){
            transferCreeper(tile, world.tile(tile.x+1, tile.y));
            transferCreeper(tile, world.tile(tile.x-1, tile.y));
            transferCreeper(tile, world.tile(tile.x, tile.y+1));
            transferCreeper(tile, world.tile(tile.x, tile.y-1));
        }

        // clamp creeper
        for(Tile tile : world.tiles){
            if(tile.newCreep > 10)
                tile.newCreep = 10;
            else if (tile.newCreep < 0.01)
                tile.newCreep = 0;
            tile.creep = tile.newCreep;
        }
    }

    // creates appropiate blocks for creeper OR damages the tile that it wants to take
    public static void drawCreeper(Tile tile){
        if(tile.creep >= 1f) {
            if(tile.build != null && tile.build.team != creeperTeam){
                tile.build.damage(creeperDamage);
                Call.effect(Fx.bubble, tile.build.x, tile.build.y, 0, Color.acid);
            }else if (tile.build == null){
                tile.setNet(creeperBlocks.get(Math.round(tile.creep)), creeperTeam, 0);
            }
        }
    }

    public static boolean canTransfer(Tile source, Tile target){
        return (source != null && target != null && !(target.block() instanceof StaticWall));
    }

    public static void transferCreeper(Tile source, Tile target) {
        if(!canTransfer(source, target))
            return;

        if(source.creepHeight > -1 && target.creepHeight > -1){
            float sourceCreeper = source.creep;

            if (sourceCreeper > 0){
                float sourceTotal = source.creepHeight + source.creep;
                float targetTotal = target.creepHeight + target.creep;
                float delta = 0;

                if (sourceTotal > targetTotal) {
                    delta = sourceTotal - targetTotal;
                    if (delta > sourceCreeper)
                        delta = sourceCreeper;
                    float adjustedDelta = delta * transferRate;
                    source.newCreep -= adjustedDelta;
                    target.newCreep += adjustedDelta;
                }
            }
        }
    }
}
