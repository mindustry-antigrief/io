package mindustry.creeper;

import arc.Events;
import arc.util.Log;
import mindustry.game.EventType;
import mindustry.gen.Building;

public class Emitter {
    public int interval;
    public int amt;
    public Building build;

    protected int counter;

    public boolean update(){
        if(build == null || build != null && build.health <= 1f)
            return false;

        if(counter >= interval) {
            counter = 0;
            build.tile.creep += amt;
        }
        counter++;

        return true;
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
