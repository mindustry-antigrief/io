package mindustry.world.blocks.power;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.annotations.Annotations.*;
import mindustry.content.*;
import mindustry.creeper.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;
import static mindustry.creeper.CreeperUtils.*;

public class ImpactReactor extends PowerGenerator{
    public final int timerUse = timers++;

    public float warmupSpeed = 0.001f;
    public float itemDuration = 60f;
    public int explosionRadius = 23;
    public int explosionDamage = 1900;
    public Effect explodeEffect = Fx.impactReactorExplosion;

    public Color plasma1 = Color.valueOf("ffd06b"), plasma2 = Color.valueOf("ff361b");

    public @Load("@-bottom") TextureRegion bottomRegion;
    public @Load(value = "@-plasma-#", length = 4) TextureRegion[] plasmaRegions;

    public ImpactReactor(String name){
        super(name);
        hasPower = true;
        hasLiquids = true;
        liquidCapacity = 30f;
        hasItems = true;
        outputsPower = consumesPower = true;
        flags = EnumSet.of(BlockFlag.reactor, BlockFlag.generator);
        lightRadius = 115f;
        emitLight = true;
    }

    @Override
    public void setBars(){
        super.setBars();

        bars.add("poweroutput", (GeneratorBuild entity) -> new Bar(() ->
        Core.bundle.format("bar.poweroutput",
        Strings.fixed(Math.max(entity.getPowerProduction() - consumes.getPower().usage, 0) * 60 * entity.timeScale, 1)),
        () -> Pal.powerBar,
        () -> entity.productionEfficiency));
    }

    @Override
    public void setStats(){
        super.setStats();

        if(hasItems){
            stats.add(Stat.productionTime, itemDuration / 60f, StatUnit.seconds);
        }
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{bottomRegion, region};
    }

    public class ImpactReactorBuild extends GeneratorBuild{
        public float warmup;
        public int lastFx = 0;
        public int finFx = 0;

        @Override
        public void updateTile(){
            if (lastFx > (2f - warmup) * 50) {
                lastFx = 0;
                Geometry.circle(tile.x, tile.y, (int) nullifierRange, (cx, cy) -> {
                    Tile t = world.tile(cx, cy);
                    if (t != null && t.team() != team() && creeperBlocks.containsValue(t.block()) && t.block() instanceof CoreBlock)

                        Geometry.iterateLine(0f, x, y, t.getX(), t.getY(), Math.max((1f - warmup) * 16f, 4f), (x, y) -> {
                            Timer.schedule(() -> {
                                Call.effect(Fx.lancerLaserChargeBegin, x, y, 1, Pal.accent);
                            }, dst(x, y) / tilesize / nullifierRange);
                        });
                });
            } else {
                lastFx += 1;
            }

            if(consValid() && power.status >= 0.99f){
                boolean prevOut = getPowerProduction() <= consumes.getPower().requestedPower(this);

                warmup = Mathf.lerpDelta(warmup, 1f, warmupSpeed * timeScale);
                if(Mathf.equal(warmup, 1f, 0.001f)){
                    warmup = 1f;
                }

                if (finFx > (1.1f - warmup) * 50) {
                    finFx = 0;
                    Geometry.circle(tile.x, tile.y, (int) nullifierRange, (cx, cy) -> {
                        Tile t = world.tile(cx, cy);
                        if (t != null && t.team() != team() && creeperBlocks.containsValue(t.block()) && t.block() instanceof CoreBlock) {
                            Call.effect(Fx.breakBlock, cx * tilesize, cy * tilesize, warmup * 5f, Pal.accent);

                            if (Mathf.chance(warmup)) {
                                if (Mathf.chance(0.1f))
                                    Call.effect(Fx.cloudsmoke, x + Mathf.range(0, 36), y + Mathf.range(0, 36), 1f, Pal.gray);
                                if (Mathf.chance(0.2f))
                                    Call.soundAt(Mathf.chance(0.7f) ? Sounds.flame2 : Sounds.flame, x, y, 0.8f, Mathf.range(0.8f, 1.5f));
                            }
                        }
                    });
                } else{
                    finFx += 1;
                }
                Building build;
                while (Mathf.equal(warmup, 1f, 0.01f) && (build = Units.findEnemyTile(team, x, y, nullifierRange * tilesize, b -> b.block instanceof CoreBlock && creeperBlocks.containsValue(b.block))) != null) {
                    Call.effect(Fx.massiveExplosion, x, y, 2f, Pal.accentBack);
                    for (Emitter e : creeperEmitters) if (e.build == build) creeperEmitters.remove(e);

                    Damage.damage(x, y, 16f * tilesize, explosionDamage);

                    Call.effect(Fx.shockwave, x, y, 16f, Pal.accent);
                    Call.soundAt(Sounds.corexplode, x, y, 0.8f, 1.5f);

                    tile.setNet(Blocks.air); // We dont want polys rebuilding this

                    Building finalBuild = build;
                    Core.app.post(() -> {
                        finalBuild.tile.setNet(Blocks.coreShard, Team.sharded, 0);
                    });
                }

                if(!prevOut && (getPowerProduction() > consumes.getPower().requestedPower(this))){
                    Events.fire(Trigger.impactPower);
                }

                if(timer(timerUse, itemDuration / timeScale)){
                    consume();
                }
            }else{
                warmup = Mathf.lerpDelta(warmup, 0f, 0.01f);
            }

            productionEfficiency = Mathf.pow(warmup, 5f);
        }

        @Override
        public float ambientVolume(){
            return warmup;
        }

        @Override
        public void draw(){
            Draw.rect(bottomRegion, x, y);

            for(int i = 0; i < plasmaRegions.length; i++){
                float r = size * tilesize - 3f + Mathf.absin(Time.time, 2f + i * 1f, 5f - i * 0.5f);

                Draw.color(plasma1, plasma2, (float)i / plasmaRegions.length);
                Draw.alpha((0.3f + Mathf.absin(Time.time, 2f + i * 2f, 0.3f + i * 0.05f)) * warmup);
                Draw.blend(Blending.additive);
                Draw.rect(plasmaRegions[i], x, y, r, r, Time.time * (12 + i * 6f) * warmup);
                Draw.blend();
            }

            Draw.color();

            Draw.rect(region, x, y);

            Draw.color();
        }

        @Override
        public void drawLight(){
            Drawf.light(team, x, y, (110f + Mathf.absin(5, 5f)) * warmup, Tmp.c1.set(plasma2).lerp(plasma1, Mathf.absin(7f, 0.2f)), 0.8f * warmup);
        }
        
        @Override
        public double sense(LAccess sensor){
            if(sensor == LAccess.heat) return warmup;
            return super.sense(sensor);
        }

        @Override
        public void onDestroyed(){
            super.onDestroyed();

            if(warmup < 0.3f || !state.rules.reactorExplosions) return;

            Sounds.explosionbig.at(tile);

            Damage.damage(x, y, explosionRadius * tilesize, explosionDamage * 4);

            Effect.shake(6f, 16f, x, y);
            explodeEffect.at(x, y);
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(warmup);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            warmup = read.f();
        }
    }
}
