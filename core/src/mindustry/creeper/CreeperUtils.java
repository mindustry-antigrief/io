package mindustry.creeper;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.func.Intc;
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
import mindustry.world.blocks.environment.Cliff;
import mindustry.world.blocks.environment.StaticWall;
import mindustry.world.blocks.environment.TreeBlock;
import mindustry.world.blocks.storage.CoreBlock;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import static mindustry.Vars.state;
import static mindustry.Vars.world;

public class CreeperUtils {
    public static float updateInterval = 0.025f; // Base update interval in seconds
    public static float transferRate = 0.249f; // Base transfer rate NOTE: keep below 0.25f
    public static float evaporationRate = 0f; // Base creeper evaporation
    public static float creeperDamage = 0.025f; // Base creeper damage
    public static float minCreeper = 0.5f; // Minimum amount of creeper required for transfer

    public static float nullifyDamage = 1500f; // Damage that needs to be applied for the core to be suspended
    public static float nullifyTimeout = 360f; // The amount of time a core remains suspended (resets upon enough damage applied)

    public static float nullificationPeriod = 10f; // How many seconds all cores have to be nullified (suspended) in order for the game to end
    private static int nullifiedCount = 0;

    public static Team creeperTeam = Team.blue;

    public static HashMap<Integer, Block> creeperBlocks = new HashMap<>();
    public static HashMap<Block, Integer> creeperLevels = new HashMap<>();

    public static HashMap<Block, Emitter> emitterBlocks = new HashMap<>();

    public static Seq<Emitter> creeperEmitters = new Seq<>();

    public static Timer.Task runner;
    public static Timer.Task fixedRunner;

    public static String getTrafficlightColor(double value){
        return "#"+Integer.toHexString(java.awt.Color.HSBtoRGB((float)value/3f, 1f, 1f)).substring(2);
    }

    static String HealthToColor(double percentage) {
        if (percentage > 1) {
            percentage = 1;
        }
        else if (percentage < 0) {
            percentage = 0;
        }
        int red = (int)(255.0 * (1 - percentage));
        int green = (int)(255.0 * (percentage));
        int blue = 0;

        String str = new Color(red, green, blue).toString();
        return "#" + str.substring(0, str.length() - 2);
    }

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

        // this is purely for damage multiplication
        creeperBlocks.put(20, Blocks.coreShard);
        creeperBlocks.put(35, Blocks.coreFoundation);
        creeperBlocks.put(50, Blocks.coreNucleus);

        for(var set : creeperBlocks.entrySet()){
            creeperLevels.put(set.getValue(), set.getKey());
        }

        emitterBlocks.put(Blocks.coreShard, new Emitter(20, 20));
        emitterBlocks.put(Blocks.coreFoundation, new Emitter(8, 20));
        emitterBlocks.put(Blocks.coreNucleus, new Emitter(3, 30));

        // todo: add "spore launchers", etc. (yes creeper world ripoff i know)

        Events.on(EventType.GameOverEvent.class, e -> {
            if(runner != null)
                runner.cancel();
            if(fixedRunner != null)
                fixedRunner.cancel();

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

                runner = Timer.schedule(CreeperUtils::updateCreeper, 0, updateInterval);
                fixedRunner = Timer.schedule(CreeperUtils::fixedUpdate, 0, 1);

            } catch (InterruptedException interruptedException) {
                interruptedException.printStackTrace();
            }
        });

        Events.on(EventType.BlockDestroyEvent.class, e -> {
            if(CreeperUtils.creeperBlocks.containsValue(e.tile.block()))
                onCreeperDestroy(e.tile);
        });

        Timer.schedule(() -> {

            Call.infoPopup("\uE88B [" + HealthToColor((double) Mathf.clamp((CreeperUtils.nullifiedCount / (double) Math.max(1.0, (double) creeperEmitters.size)), 0f, 1f)) + "]" + CreeperUtils.nullifiedCount + "/" + CreeperUtils.creeperEmitters.size + "[] emitters suspended", 10f, 20, 50, 20, 450, 0);
            // check for gameover
            if(CreeperUtils.nullifiedCount == CreeperUtils.creeperEmitters.size){
                Timer.schedule(() -> {
                    if(CreeperUtils.nullifiedCount == CreeperUtils.creeperEmitters.size){
                        // gameover
                        state.gameOver = true;
                        Events.fire(new EventType.GameOverEvent(Team.sharded));
                    }
                    // failed to win, core got unsuspended
                }, nullificationPeriod);
            }

            }, 0, 10);

    }

    private static void onCreeperDestroy(Tile tile) {
        tile.creep = 0;
        tile.newCreep = 0;
    }

    public static void fixedUpdate(){
        int newcount = 0;
        for(Emitter emitter : creeperEmitters){
            emitter.fixedUpdate();
            if(emitter.nullified)
                newcount++;
        }

        nullifiedCount = newcount;
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

                // deal continuous damage
                if (tile.build != null && tile.build.team != creeperTeam && tile.touchingCreeper()) {
                    if (Mathf.chance(0.05f))
                        Call.effect(Fx.bubble, tile.build.x, tile.build.y, 0, Color.blue);

                    Core.app.post(() -> {
                        if (tile.build != null)
                            tile.build.damageContinuous(creeperDamage * tile.creep);
                    });
                }

        }
        if (tile != null && tile.x < world.width() && tile.y < world.height() && tile.creep >= 1f &&
                !(tile.block() instanceof CoreBlock) &&
                (creeperLevels.getOrDefault(tile.block(), 10)) < Math.round(tile.creep) || tile.block() instanceof TreeBlock){
            tile.setNet(creeperBlocks.get(Mathf.clamp(Math.round(tile.creep), 0, 10)), creeperTeam, Mathf.random(0, 3));
        }
    }

    public static boolean canTransfer(Tile source, Tile target){
        boolean amountValid = source.creep > minCreeper;

        if(source == null || target == null)
            return false;

        if(target.block() instanceof TreeBlock && amountValid)
            return true;

        if(target.block() instanceof StaticWall || (target.floor() != null && !target.floor().placeableOn || target.floor().isDeep() || target.block() instanceof Cliff))
            return false;

        if(source.build != null && source.build.team != creeperTeam) {
            // wall or something, decline transfer but damage the wall
            drawCreeper(source);
            return false;
        }

        return amountValid;
    }

    public static void transferCreeper(Tile source, Tile target) {
        if(canTransfer(source, target)){
            float sourceCreeper = source.creep;

            if (sourceCreeper > 0){
                float sourceTotal = source.creep;
                float targetTotal = target.creep;
                float delta;

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
