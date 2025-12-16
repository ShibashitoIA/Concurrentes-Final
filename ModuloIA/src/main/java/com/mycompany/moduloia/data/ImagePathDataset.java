package com.mycompany.moduloia.data;

import java.util.List;

public class ImagePathDataset {

    public final List<String> paths;
    public final List<double[]> y;

    public ImagePathDataset(List<String> paths, List<double[]> y) {
        this.paths = paths;
        this.y = y;
    }
}
