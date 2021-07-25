package mindustry.ai.formations.patterns;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.ai.formations.*;

public class LayeredFormation extends FormationPattern{
    /** Angle offset. */
    public float angleOffset = 0;

    @Override
    public Vec3 calculateSlotLocation(Vec3 outLocation, int slotNumber){
        if(slots > 1){
            int row = row(slotNumber);
            int cap = Math.min(slots - 8 * row * (row - 1) / 2, row * 8);
            float angle = (360f * (slotNumber % cap)) / cap;
            float radius = spacing + spacing * row * 2;
            outLocation.set(Angles.trnsx(angle, radius), Angles.trnsy(angle, radius), angle);
        }else{
            outLocation.set(0, spacing * 1.1f, 360f * slotNumber);
        }

        outLocation.z += angleOffset;

        return outLocation;
    }

    private int row(int slot){
        for(int i = 0; i < slots; i++){
            if(slot - 8 * i * (i + 1) / 2 < 8 * (i + 1)) return i + 1;
        }
        // This should never get here but if it does its fine i guess
        return 1;
    }
}
