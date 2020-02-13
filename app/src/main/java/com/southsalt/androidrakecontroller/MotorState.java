package com.southsalt.androidrakecontroller;

public class MotorState {
    String name;
    boolean mDirection;
    boolean gate;
    Integer mSpeed;
    Integer stateTime;

    public MotorState(String name, int mSpeed, int stateTime, boolean mDirection, boolean gate){
        this.name = name;
        this.mSpeed = mSpeed;
        this.stateTime = stateTime;
        this.mDirection = mDirection;
        this.gate = gate;
    }

    @Override
    public String toString() { return this.name; }
}
