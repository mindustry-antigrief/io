package mindustry.creeper;

import arc.Events;
import arc.graphics.Color;
import arc.util.Log;
import mindustry.content.Fx;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Call;

public class Emitter {
    public int interval;
    public int amt;
    public Building build;
    public boolean nullified;

    protected int counter;

    // updates every interval in CreeperUtils
    public boolean update(){
        if(build == null || build.health <= 1f)
            return false;

        nullified = build.nullifyTimeout > 0f;

        if(counter >= interval && !nullified){
            counter = 0;
            spawnCreep();
        }
        counter++;

        return true;
    }

    public void spawnCreep(){
        if(build.block == null) return;
        Tile on = build.tile;
        int offset = -(build.block.size - 1) / 2;

        for(int dx = 0; dx < build.block.size; dx++){
            for(int dy = 0; dy < build.block.size; dy++){
                int wx = dx + on.x + offset, wy = dy + on.y + offset;
                Tile tile = world.tile(wx, wy);
                if (tile != null) tile.creep = Math.min(10, tile.creep + amt);
            }
        }
    }

    // updates every 1 second
    public void fixedUpdate(){
        if(nullified) {
            Call.label("[red]*[] SUSPENDED [red]*[]", 1f, build.x, build.y);
            Call.effect(Fx.placeBlock, build.x, build.y, build.block.size, Color.yellow);
        }
    }

    public Emitter(int _interval, int _amt){
        interval = _interval;
        amt = _amt;
    }

    public Emitter(Building _build){
        build = _build;

        var ref = CreeperUtils.emitterBlocks.get(build.block);
        interval = ref.interval;
        amt = ref.amt;
    }
}
