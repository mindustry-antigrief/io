package mindustry.creeper;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.gen.Building;
import mindustry.gen.Call;

public class ChargedEmitter extends Emitter {

    public int charge;
    public int chargeTime;

    public int activationEpoch;
    public int splurgeTime; // (i came up with worse names for this variable than this)


    public boolean update(){
        if(build == null || build.health <= 1f)
            return false;

        if(charge < chargeTime) {
            charge++;
            return true;
        } else {
            charge = 0;
            activationEpoch = splurgeTime;
        }

        if(activationEpoch > 0) {
            activationEpoch--;

            if (counter >= interval && !nullified) {
                counter = 0;
                build.tile.creep += amt;
            }
            counter++;
        }

        return true;
    }

    // updates every 1 second
    public void fixedUpdate(){
        // is currently charging
        if(activationEpoch <= 0) {
            Call.label("[red]⚠[] - [stat]" + (chargeTime - charge) / 60, 1f, build.x, build.y);
        } else{
            // is currently emitting - "splurging"
            Call.effect(Fx.launch, build.x, build.y, build.block.size, CreeperUtils.creeperTeam.color);
        }
    }

    // _chargeTime in seconds
    // _splurgeTime in ticks (how long it emits)
    public ChargedEmitter(int _interval, int _amt, int _chargeTime, int _splurgeTime){
        super(_interval, _amt);

        interval = _interval;
        amt = _amt;
        chargeTime = _chargeTime;
        splurgeTime = _splurgeTime;
    }

    public ChargedEmitter(Building _build){
        super(_build);

        build = _build;

        var ref = CreeperUtils.chargedEmitterBlocks.get(build.block);
        interval = ref.interval;
        amt = ref.amt;
        chargeTime = ref.chargeTime;
        splurgeTime = ref.splurgeTime;
    }

}
