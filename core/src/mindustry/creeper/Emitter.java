package mindustry.creeper;

import arc.Events;
import mindustry.game.EventType;
import mindustry.gen.Building;

public class Emitter {
    public int interval;
    public int amt;
    public Building build;

    protected int counter;

    public void update(){
        if(counter >= interval) {
            counter = 0;
            build.tile.creep += amt;
        }
        counter++;
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

        // dispose this emitter
        Events.on(EventType.BlockDestroyEvent.class, e -> {
            if(e.tile.build != null && e.tile.build == build)
                CreeperUtils.creeperEmitters.remove(this);
        });
    }
}
