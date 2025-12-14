package com.mycompany.moduloia.data;

import java.util.List;

public class TabularDataset {

    public final List<double[]> x;
    public final List<double[]> y;

    public TabularDataset(List<double[]> x, List<double[]> y) {
        this.x = x;
        this.y = y;
    }
}
