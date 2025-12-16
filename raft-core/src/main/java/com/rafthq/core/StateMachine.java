package com.rafthq.core;

public interface StateMachine {
    void onCommit(byte[] command);
}
