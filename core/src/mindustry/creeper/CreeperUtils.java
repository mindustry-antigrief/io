package mindustry.creeper;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
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
    public static float updateInterval = 0.1f;
    public static float transferRate = 0.249f;
    public static float evaporationRate = 0.001f;
    public static float creeperDamage = 1f;
    public static Team creeperTeam = Team.blue;

    public static HashMap<Integer, Block> creeperBlocks = new HashMap<>();
    public static HashMap<Block, Integer> creeperLevels = new HashMap<>();

    public static HashMap<Block, Emitter> emitterBlocks = new HashMap<>();

    public static Seq<Emitter> creeperEmitters = new Seq<>();
    public static Timer.Task runner;

    public static void init(){
        creeperBlocks.put(0, Blocks.air);
        creeperBlocks.put(1, Blocks.conveyor);
        creeperBlocks.put(2, Blocks.titaniumConveyor);
        creeperBlocks.put(3, Blocks.armoredConveyor);
        creeperBlocks.put(4, Blocks.plastaniumConveyor);
        creeperBlocks.put(5, Blocks.scrapWall);
        creeperBlocks.put(6, Blocks.titaniumWall);
        creeperBlocks.put(7, Blocks.thoriumWall);
        creeperBlocks.put(8, Blocks.plastaniumWall);
        creeperBlocks.put(9, Blocks.phaseWall);
        creeperBlocks.put(10, Blocks.surgeWall);

        for(var set : creeperBlocks.entrySet()){
            creeperLevels.put(set.getValue(), set.getKey());
        }

        emitterBlocks.put(Blocks.coreShard, new Emitter(15, 10));
        emitterBlocks.put(Blocks.coreFoundation, new Emitter(8, 20));
        emitterBlocks.put(Blocks.coreNucleus, new Emitter(3, 30));

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

        Events.on(EventType.BlockDestroyEvent.class, e -> {
            if(CreeperUtils.creeperBlocks.containsValue(e.tile.block()))
                onCreeperDestroy(e.tile);
        });

        runner = Timer.schedule(CreeperUtils::updateCreeper, 0, updateInterval);
    }

    private static void onCreeperDestroy(Tile tile) {
        tile.creep = 0;
    }

    public static void updateCreeper(){
        // update emitters
        for(Emitter emitter : creeperEmitters){
            if(!emitter.update())
                creeperEmitters.remove(emitter);
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
            drawCreeper(tile);
        }

    }

    // creates appropiate blocks for creeper OR damages the tile that it wants to take
    public static void drawCreeper(Tile tile){

        // check if can transfer anyway because weird
        if(tile.creep >= 1f) {
            if(tile.build != null && tile.build.team != creeperTeam){
                if(Mathf.chance(0.05f))
                    Call.effect(Fx.bubble, tile.build.x, tile.build.y, 0, Color.blue);

                Core.app.post(() -> {
                    if(tile.build != null)
                        tile.build.damageContinuous(creeperDamage);
                });

            }else if (tile.creep >= 1f && tile.block().size == 1 && tile.block() != creeperBlocks.get(Mathf.clamp(Math.round(tile.creep), 1, 10)) && creeperLevels.get(tile.block()) < tile.creep){
                tile.setNet(creeperBlocks.get(Mathf.clamp(Math.round(tile.creep), 0, 10)), creeperTeam, Mathf.random(0, 3));
            }
        }
    }

    public static boolean canTransfer(Tile source, Tile target){
        if(source == null || target == null)
            return false;

        if(target.block() instanceof StaticWall || (target.floor() != null && !target.floor().placeableOn))
            return false;

        if(source.build != null && source.build.team != creeperTeam) {
            // wall or something, decline transfer but damage the wall
            drawCreeper(source);
            return false;
        }

        return true;
    }

    public static void transferCreeper(Tile source, Tile target) {
        if(canTransfer(source, target)){
            float sourceCreeper = source.creep;

            if (sourceCreeper > 0){
                float sourceTotal = source.creep;
                float targetTotal = target.creep;
                float delta = 0;

                if (sourceTotal > targetTotal) {
                    delta = sourceTotal - targetTotal;
                    if (delta > sourceCreeper)
                        delta = sourceCreeper;

                    float adjustedDelta = delta * transferRate;
                    source.newCreep -= adjustedDelta;
                    target.newCreep += adjustedDelta - evaporationRate;
                }
            }
        }
    }
}
