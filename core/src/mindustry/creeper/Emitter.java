package mindustry.creeper;

import arc.graphics.*;
import arc.math.geom.*;
import mindustry.content.*;
import mindustry.gen.*;

public class Emitter implements Position{
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
            build.tile.getLinkedTiles(t -> {
                t.creep += amt;
            });
        }
        counter++;

        return true;
    }

    // updates every 1 second
    public void fixedUpdate(){
        if(nullified){
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

    @Override
    public float getX(){
        return build.x;
    }

    @Override
    public float getY(){
        return build.y;
    }
}
