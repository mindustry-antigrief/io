package mindustry.creeper;

import arc.math.geom.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.world.*;

import java.util.*;

import static mindustry.creeper.CreeperUtils.*;

public class ChargedEmitter implements Position{
    public ChargedEmitterType type;
    public Building build;

    public int counter;
    public float buildup;
    public float overflow;
    public boolean emitting;
    public StringBuilder sb = new StringBuilder();

    public static HashMap<Block, ChargedEmitterType> chargedEmitterTypes = new HashMap<>();

    public boolean update(){
        if(build == null || build.health <= 1f)
            return false;

        if(build.health < build.maxHealth && overflow > 0){
            overflow--;
            build.heal(build.maxHealth);
            Call.effect(Fx.healBlock, build.x, build.y, build.block.size, creeperTeam.color);
        }

        if(emitting){
            if(++counter >= type.interval){
                counter = 0;
                build.tile.getLinkedTiles(t -> t.creep += type.amt);
            }

            if(--buildup <= 0){
                emitting = false;
                overflow = Math.min(type.chargeCap, overflow + (build.tile.creep / 100));
                build.tile.getLinkedTiles(t -> t.creep = Math.min(t.creep, maxTileCreep));
            }
        }else if((buildup += type.chargePulse) > type.chargeCap){
            emitting = true;
        }
        return true;
    }

    public void fixedUpdate(){
        sb.setLength(0);
        if(overflow > 0){
            sb.append(Strings.format("[green]@[] - [stat]@%", type.upgradable() ? "\ue804" : "\ue813", (int)(overflow * 100 / type.chargeCap)));
        }
        if(emitting){
            Call.effect(Fx.launch, build.x, build.y, build.block.size, creeperTeam.color);
        }else{
            if (sb.length() > 0) sb.append("\n");
            sb.append(Strings.format("[red]⚠[] - [stat] @%", (int)(buildup * 100 / type.chargeCap)));
        }
        if(sb.length() > 0){
            Call.label(sb.toString(), 1f, build.x, build.y);
        }
        if(type.upgradable() && type.chargeCap > 0 && build != null && build.tile != null && overflow >= type.chargeCap){
            ChargedEmitterType next = type.getNext();
            if(next != null){
                this.type = next;
                build.tile.setNet(next.block, creeperTeam, 0);
                this.build = build.tile.build;
            }
        }
    }

    public ChargedEmitter(Building build){
        this.build = build;
        this.type = chargedEmitterTypes.get(build.block);
    }

    public static void init(){
        chargedEmitterTypes.put(Blocks.launchPad, ChargedEmitterType.launcher);
        chargedEmitterTypes.put(Blocks.interplanetaryAccelerator, ChargedEmitterType.accelerator);
    }

    @Override
    public float getX(){
        return build.x;
    }

    @Override
    public float getY(){
        return build.y;
    }

    enum ChargedEmitterType{
        launcher(2, 9, 1, 0.4f, 600, Blocks.launchPad),
        accelerator(1, 10, 2, 0.7f, 1800, Blocks.interplanetaryAccelerator);

        public final int amt;
        public final int level;
        public final int interval;
        public final int chargeCap;
        public final float chargePulse;
        public final Block block;

        ChargedEmitterType(int interval, int amt, int level, float chargePulse, int chargeCap, Block block){
            this.amt = amt;
            this.block = block;
            this.level = level;
            this.interval = interval;
            this.chargeCap = chargeCap;
            this.chargePulse = chargePulse;
        }

        public boolean upgradable(){
            return level < values().length;
        }

        public ChargedEmitterType getNext(){
            for(ChargedEmitterType t : values()){
                if(t.level == (level + 1)){
                    return t;
                }
            }
            return null;
        }
    }
}
