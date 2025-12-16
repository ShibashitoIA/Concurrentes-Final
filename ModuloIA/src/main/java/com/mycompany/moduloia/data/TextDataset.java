package com.mycompany.moduloia.data;

import java.util.List;

public class TextDataset {

    public final List<String> texts;
    public final List<double[]> y;

    public TextDataset(List<String> texts, List<double[]> y) {
        this.texts = texts;
        this.y = y;
    }
}
