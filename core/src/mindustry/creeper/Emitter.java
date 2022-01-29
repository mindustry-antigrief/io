package mindustry.creeper;

import arc.graphics.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.world.*;

import java.util.*;

import static mindustry.creeper.CreeperUtils.*;

public class Emitter implements Position{
    public Building build;
    public EmitterType type;
    public boolean nullified;

    protected int counter;

    public static HashMap<Block, EmitterType> emitterTypes = new HashMap<>();

    public Emitter(Building build){
        if (build == null) {
            creeperEmitters.remove(this);
            return;
        }
        this.build = build;
        this.type = emitterTypes.get(build.block);
    }

    // updates every interval in CreeperUtils
    public boolean update(){
        if(build == null || build.health <= 1f)
            return false;

        nullified = build.nullifyTimeout > 0f;

        if(counter >= type.interval){
            counter = 0;
            build.tile.getLinkedTiles(t ->
            t.creep = nullified ? Math.min(t.creep, maxTileCreep) : t.creep + type.amt
            );
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
        if (build != null && build.tile != null && type.upgradeThreshold > 0 && build.tile.creep > 20){
            Call.label(Strings.format("[green]*[white] UPGRADING []@% *[]", (int) (build.tile.creep * 100 / type.upgradeThreshold)), 1f, build.x, build.y);
            if (build.tile.creep > type.upgradeThreshold){
                EmitterType next = type.getNext();
                if(next != null){
                    build.tile.setNet(next.block, creeperTeam, 0);
                    this.build = build.tile.build;
                    this.type = next;
                }
            }
        }
    }

    @Override
    public float getX(){
        return build.x;
    }

    @Override
    public float getY(){
        return build.y;
    }

    public static void init(){
        emitterTypes.put(Blocks.coreShard, EmitterType.shard);
        emitterTypes.put(Blocks.coreFoundation, EmitterType.foundation);
        emitterTypes.put(Blocks.coreNucleus, EmitterType.nucleus);
    }

    public enum EmitterType{
        shard(3, 30, 1, 30, Blocks.coreShard),
        foundation(5, 20, 2, 3000, Blocks.coreFoundation),
        nucleus(7, 15, 3, -1, Blocks.coreNucleus);

        public final int amt;
        public final int level;
        public final int interval;
        public final int upgradeThreshold;
        public final Block block;

        EmitterType(int amt, int interval, int level, int upgradeThreshold, Block block){
            this.amt = amt;
            this.level = level;
            this.block = block;
            this.interval = interval;
            this.upgradeThreshold = upgradeThreshold;
        }

        public EmitterType getNext(){
            for(EmitterType t : values()){
                if(t.level == (level + 1)){
                    return t;
                }
            }
            return null;
        }
    }
}
