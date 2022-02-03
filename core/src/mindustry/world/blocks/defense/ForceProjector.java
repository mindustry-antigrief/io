package mindustry.world.blocks.defense;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.annotations.Annotations.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;
import static mindustry.creeper.CreeperUtils.*;

public class ForceProjector extends Block{
    public final int timerUse = timers++;
    public float phaseUseTime = 350f;

    public float phaseRadiusBoost = 80f;
    public float phaseShieldBoost = 400f;
    public float radius = 101.7f;
    public float shieldHealth = 700f;
    public float regen = 1.8f;
    public float cooldownNormal = 1.75f;
    public float cooldownLiquid = 1.5f;
    public float cooldownBrokenBase = 0.35f;
    public Effect absorbEffect = Fx.absorb;
    public Effect shieldBreakEffect = Fx.shieldBreak;
    public @Load("@-top")
    TextureRegion topRegion;

    static ForceBuild paramEntity;
    static Effect paramEffect;
    static final Cons<Bullet> shieldConsumer = trait -> {
        if(trait.team != paramEntity.team && trait.type.absorbable && inForceField(trait)){
            trait.absorb();
            paramEffect.at(trait);
            paramEntity.hit = 1f;
            paramEntity.buildup += trait.damage();
        }
    };

    private static final Cons<Tile> creeperConsumer = tile -> {
        if(((tile.creep >= 1f && creeperableTiles.contains(tile))
        || (creeperBlocks.containsValue(tile.block()) && tile.team() == creeperTeam))
        && inForceField(tile)){
            if (paramEntity.team != creeperTeam){
                Call.effect(Fx.absorb, tile.worldx(), tile.worldy(), 1, Color.blue);

                paramEntity.hit = 1f;
                paramEntity.healthLeft -= creeperDamage * buildShieldDamageMultiplier * (tile.creep / 2f) * Math.max(shieldBoostProtectionMultiplier, 1f - paramEntity.phaseHeat) + ((closestEmitterDist(tile) < 5 * tilesize) ? 2 : 0);

                if(tile.build != null && tile.build.team == creeperTeam)
                    tile.build.damage(Blocks.conveyor.health / 2f);
            }else{
                Call.effect(Fx.absorb, tile.worldx(), tile.worldy(), 1, Color.blue);

                if(tile.build != null && tile.build.team == creeperTeam)
                    tile.build.heal(Blocks.conveyor.health / 2f);
            }
        }
    };

    private static boolean inForceField(Position pos){
        return Intersector.isInsideHexagon(paramEntity.x, paramEntity.y, paramEntity.realRadius() * 2f, pos.getX(), pos.getY());
    }

    public ForceProjector(String name){
        super(name);
        update = true;
        solid = true;
        group = BlockGroup.projectors;
        hasPower = true;
        hasLiquids = true;
        hasItems = true;
        ambientSound = Sounds.shield;
        ambientSoundVolume = 0.08f;
        consumes.add(new ConsumeCoolant(0.1f)).boost().update(false);
        envEnabled |= Env.space;
    }

    @Override
    public void init(){
        clipSize = Math.max(clipSize, (radius + phaseRadiusBoost + 3f) * 2f);
        super.init();
    }

    @Override
    public void setBars(){
        super.setBars();
        bars.add("shield", (ForceBuild entity) -> new Bar("stat.shieldhealth", Pal.accent, () -> entity.broken ? 0f : 1f - entity.buildup / (shieldHealth + phaseShieldBoost * entity.phaseHeat)).blink(Color.white));
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    @Override
    public void setStats(){
        stats.timePeriod = phaseUseTime;
        super.setStats();
        stats.add(Stat.shieldHealth, shieldHealth, StatUnit.none);
        stats.add(Stat.cooldownTime, (int)(shieldHealth / cooldownBrokenBase / 60f), StatUnit.seconds);
        stats.add(Stat.boostEffect, phaseRadiusBoost / tilesize, StatUnit.blocks);
        stats.add(Stat.boostEffect, phaseShieldBoost, StatUnit.shieldHealth);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);

        Draw.color(Pal.gray);
        Lines.stroke(3f);
        Lines.poly(x * tilesize + offset, y * tilesize + offset, 6, radius);
        Draw.color(player.team().color);
        Lines.stroke(1f);
        Lines.poly(x * tilesize + offset, y * tilesize + offset, 6, radius);
        Draw.color();
    }

    public class ForceBuild extends Building implements Ranged{
        public boolean broken = true;
        public float buildup, radscl, hit, warmup, phaseHeat, healthLeft;

        @Override
        public float range(){
            return realRadius();
        }

        @Override
        public void created(){
            healthLeft = shieldHealth;
            shields.add(this);
        }

        @Override
        public boolean shouldAmbientSound(){
            return !broken && realRadius() > 1f;
        }

        @Override
        public void onRemoved(){
            float radius = realRadius();
            if(!broken && radius > 1f) Fx.forceShrink.at(x, y, radius, team.color);
            super.onRemoved();
            dead = true;
        }

        @Override
        public void pickedUp(){
            super.pickedUp();
            radscl = warmup = 0f;
        }

        @Override
        public void updateTile(){
            boolean phaseValid = consumes.get(ConsumeType.item).valid(this);

            phaseHeat = Mathf.lerpDelta(phaseHeat, Mathf.num(phaseValid), 0.1f);

            if(phaseValid && !broken && timer(timerUse, phaseUseTime) && efficiency() > 0){
                consume();
            }

            radscl = Mathf.lerpDelta(radscl, broken ? 0f : warmup, 0.05f);

            if(Mathf.chanceDelta(buildup / shieldHealth * 0.1f)){
                Fx.reactorsmoke.at(x + Mathf.range(tilesize / 2f), y + Mathf.range(tilesize / 2f));
            }

            warmup = Mathf.lerpDelta(warmup, efficiency(), 0.1f);

            if(broken && buildup <= 0){
                broken = false;
            }

            if(buildup >= shieldHealth + phaseShieldBoost * phaseHeat && !broken){
                broken = true;
                buildup = shieldHealth;
                shieldBreakEffect.at(x, y, realRadius(), team.color);
            }

            if(hit > 0f){
                hit -= 1f / 5f * Time.delta;
            }

            float realRadius = realRadius();

            if(realRadius > 0 && !broken){
                paramEntity = this;
                paramEffect = absorbEffect;
                Groups.bullet.intersect(x - realRadius, y - realRadius, realRadius * 2f, realRadius * 2f, shieldConsumer);

                Geometry.circle(tile.x, tile.y, (int)(((int)realRadius / Vars.tilesize) * 3), (cx, cy) -> {
                    if(Intersector.isInsideHexagon(tile.worldx(), tile.worldy(), realRadius * 2f, cx * Vars.tilesize, cy * Vars.tilesize) && Vars.world.tile(cx, cy) != null)
                        creeperConsumer.get(Vars.world.tile(cx, cy));
                });
            }

            ConsumeLiquidFilter cons = consumes.get(ConsumeType.liquid);
            if(cons.valid(this)){
                cons.update(this);
                if(liquids.currentAmount() > 0f){
                    liquids.remove(liquids.current(), 0.5f);
                    healthLeft = Math.min(healthLeft + regen * liquids.current().heatCapacity, shieldHealth);
                }
            }

            if(broken || healthLeft <= 0f){
                Core.app.post(this::kill);
            }

        }

        public float realRadius(){
            return (radius + phaseHeat * phaseRadiusBoost) * radscl;
        }

        @Override
        public double sense(LAccess sensor){
            if(sensor == LAccess.heat) return buildup;
            return super.sense(sensor);
        }

        @Override
        public void draw(){
            super.draw();

            if(buildup > 0f){
                Draw.alpha(buildup / shieldHealth * 0.75f);
                Draw.blend(Blending.additive);
                Draw.rect(topRegion, x, y);
                Draw.blend();
                Draw.reset();
            }

            drawShield();
        }

        public void drawShield(){
            if(!broken){
                float radius = realRadius();

                Draw.z(Layer.shields);

                Draw.color(team.color, Color.white, Mathf.clamp(hit));

                if(Core.settings.getBool("animatedshields")){
                    Fill.poly(x, y, 6, radius);
                }else{
                    Lines.stroke(1.5f);
                    Draw.alpha(0.09f + Mathf.clamp(0.08f * hit));
                    Fill.poly(x, y, 6, radius);
                    Draw.alpha(1f);
                    Lines.poly(x, y, 6, radius);
                    Draw.reset();
                }
            }

            Draw.reset();
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.bool(broken);
            write.f(buildup);
            write.f(radscl);
            write.f(warmup);
            write.f(phaseHeat);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            broken = read.bool();
            buildup = read.f();
            radscl = read.f();
            warmup = read.f();
            phaseHeat = read.f();
        }
    }
}
