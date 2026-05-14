package dev.duong2012g.combatgun.data;

public class RecoilData {

    private final double pitch;
    private final double yaw;
    private final double spread;
    private final double recovery;

    public RecoilData(double pitch, double yaw, double spread, double recovery) {
        this.pitch = pitch;
        this.yaw = yaw;
        this.spread = spread;
        this.recovery = recovery;
    }

    public double getPitch()    { return pitch; }
    public double getYaw()      { return yaw; }
    public double getSpread()   { return spread; }
    public double getRecovery() { return recovery; }
}
